package com.example.tada.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.Image
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tada.R
import com.example.tada.domain.model.ActionStep
import com.example.tada.maps.MapsApiKeyStatus
import com.example.tada.maps.composeMap
import com.example.tada.maps.mapsApiKeyStatus
import com.example.tada.maps.readMergedMapsApiKey
import com.example.tada.maps.retryMapInit
import com.example.tada.domain.model.FailureReason
import com.example.tada.domain.model.Side
import com.example.tada.ui.components.YellowButton
import com.example.tada.ui.failureMessage
import com.example.tada.ui.theme.TadaMuted
import com.example.tada.ui.theme.TadaRow
import com.example.tada.ui.theme.TadaYellow
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.internal.GoogleMapsInitializer
import com.google.maps.android.compose.internal.InitializationState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive

@Composable
fun MapScreen(
    onOpenNickname: (Side) -> Unit,
    onOpenBooking: () -> Unit,
    viewModel: MapViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (granted) viewModel.onLocationPermissionGranted() else viewModel.onLocationPermissionDenied()
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.onLocationPermissionGranted()
        } else {
            permissionLauncher.launch(locationPermissions())
        }
    }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            if (event is MapEvent.OpenBooking) onOpenBooking()
        }
    }

    val keyStatus = remember(context.packageName) {
        mapsApiKeyStatus(readMergedMapsApiKey(context))
    }
    var initializerStatus by remember { mutableStateOf(ConnectionResult.INTERNAL_ERROR) }
    var mapSlotSize by remember { mutableStateOf(IntSize.Zero) }
    val showMap = keyStatus == MapsApiKeyStatus.Ready &&
        composeMap(initializerStatus, mapSlotSize.width, mapSlotSize.height)
    LaunchedEffect(keyStatus) {
        if (keyStatus != MapsApiKeyStatus.Ready) {
            initializerStatus = ConnectionResult.INTERNAL_ERROR
            return@LaunchedEffect
        }
        while (isActive) {
            val playServices = GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(context)
            val status = if (playServices != ConnectionResult.SUCCESS) {
                Log.w(MAPS_LOG_TAG, "Play services unavailable for ${context.packageName}: $playServices")
                playServices
            } else {
                try {
                    // Called on the main thread. The callback runs before this returns;
                    // the map is composed only from the returned status, not from the callback.
                    MapsInitializer.initialize(
                        context.applicationContext,
                        MapsInitializer.Renderer.LATEST,
                    ) { renderer ->
                        Log.d(MAPS_LOG_TAG, "Maps renderer $renderer")
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    Log.w(MAPS_LOG_TAG, "MapsInitializer threw for ${context.packageName}", error)
                    ConnectionResult.INTERNAL_ERROR
                }
            }
            val composeReady = playServices == ConnectionResult.SUCCESS &&
                status == ConnectionResult.SUCCESS &&
                awaitComposeMaps(context.applicationContext)
            initializerStatus = when {
                composeReady -> ConnectionResult.SUCCESS
                playServices != ConnectionResult.SUCCESS -> playServices
                else -> ConnectionResult.INTERNAL_ERROR
            }
            if (!retryMapInit(playServices, initializerStatus)) break
            Log.w(MAPS_LOG_TAG, "MapsInitializer status $status for ${context.packageName}; retrying")
            delay(MAP_INIT_RETRY_MILLIS)
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), 2f)
    }
    var gesturesArmed by remember { mutableStateOf(false) }
    LaunchedEffect(state.recenterToken, showMap) {
        if (!showMap) return@LaunchedEffect
        val target = state.cameraTarget ?: return@LaunchedEffect
        if (state.recenterToken == 0) return@LaunchedEffect
        gesturesArmed = false
        viewModel.onCameraMoving(target.latitude, target.longitude)
        cameraPositionState.animate(
            CameraUpdateFactory.newLatLngZoom(LatLng(target.latitude, target.longitude), 16f),
        )
        gesturesArmed = true
        viewModel.onCameraIdle(target.latitude, target.longitude)
    }
    LaunchedEffect(gesturesArmed, showMap) {
        if (!showMap || !gesturesArmed) return@LaunchedEffect
        snapshotFlow { cameraPositionState.isMoving to cameraPositionState.position.target }
            .drop(1)
            .distinctUntilChanged()
            .collect { (moving, target) ->
                if (moving) {
                    viewModel.onCameraMoving(target.latitude, target.longitude)
                } else {
                    viewModel.onCameraIdle(target.latitude, target.longitude)
                }
            }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (keyStatus == MapsApiKeyStatus.Ready) {
                AirQualityPanel(
                    aqi = state.cornerAqi,
                    loading = state.cornerAqiLoading,
                    error = state.cornerError,
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { mapSlotSize = it },
            ) {
                when (keyStatus) {
                    MapsApiKeyStatus.Missing -> MapsSetupMessage(
                        stringResource(R.string.maps_key_missing_body),
                    )
                    MapsApiKeyStatus.Unsubstituted -> MapsSetupMessage(
                        stringResource(R.string.maps_key_unsubstituted_body),
                    )
                    MapsApiKeyStatus.Ready -> if (showMap) {
                        GoogleMap(
                            modifier = Modifier.fillMaxSize(),
                            cameraPositionState = cameraPositionState,
                            uiSettings = MapUiSettings(
                                zoomControlsEnabled = false,
                                myLocationButtonEnabled = false,
                                compassEnabled = false,
                            ),
                        )
                        Image(
                            painter = painterResource(R.drawable.map_pin),
                            contentDescription = stringResource(R.string.cd_center_marker),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(CENTER_PIN_WIDTH, CENTER_PIN_HEIGHT)
                                .offset(y = -CENTER_PIN_HEIGHT / 2),
                        )
                    } else {
                        CircularProgressIndicator(
                            color = TadaYellow,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }
            MapCard(
                state = state,
                onAction = viewModel::onAction,
                onOpenNickname = onOpenNickname,
                onRetryPermission = { permissionLauncher.launch(locationPermissions()) },
                onRetryLocation = viewModel::retryLocation,
            )
        }
        if (state.blocking && keyStatus == MapsApiKeyStatus.Ready) {
            BlockingLoader()
        }
    }
}

private const val MAPS_LOG_TAG = "TadaMaps"
private const val MAP_INIT_RETRY_MILLIS = 400L

// GoogleMap draws an empty box until this state is SUCCESS, and a FAILURE is not retried.
@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
private suspend fun awaitComposeMaps(context: android.content.Context): Boolean {
    if (GoogleMapsInitializer.state.value == InitializationState.FAILURE) {
        GoogleMapsInitializer.reset()
    }
    GoogleMapsInitializer.initialize(context)
    return GoogleMapsInitializer.state.value == InitializationState.SUCCESS
}
private val CENTER_PIN_WIDTH = 28.dp
private val CENTER_PIN_HEIGHT = 47.dp

@Composable
private fun MapsSetupMessage(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        color = Color(0xFF1C1B1F),
        fontSize = 16.sp,
    )
}

private fun locationPermissions(): Array<String> = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

@Composable
private fun AirQualityPanel(
    aqi: Int?,
    loading: Boolean,
    error: FailureReason?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = stringResource(R.string.aqi), color = TadaMuted, fontSize = 16.sp, fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(10.dp))
            when {
                loading -> CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = TadaYellow,
                )
                aqi != null -> Text(
                    text = aqi.toString(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
            }
        }
        if (error != null && !loading) {
            Text(
                text = failureMessage(error),
                color = Color(0xFFB3261E),
                fontSize = 12.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun MapCard(
    state: MapUiState,
    onAction: () -> Unit,
    onOpenNickname: (Side) -> Unit,
    onRetryPermission: () -> Unit,
    onRetryLocation: () -> Unit,
) {
    Surface(color = Color.White) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            state.bannerError?.let { reason ->
                Text(text = failureMessage(reason), color = Color(0xFFB3261E))
                when (reason) {
                    FailureReason.Permission -> TextButton(onClick = onRetryPermission) {
                        Text(stringResource(R.string.action_try_again))
                    }
                    FailureReason.Location -> TextButton(onClick = onRetryLocation) {
                        Text(stringResource(R.string.action_try_again))
                    }
                    else -> Unit
                }
                Spacer(modifier = Modifier.size(8.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PointRow(
                        letter = stringResource(R.string.side_a),
                        label = state.aLabel,
                        enabled = state.aSet,
                        onClick = { onOpenNickname(Side.A) },
                    )
                    PointRow(
                        letter = stringResource(R.string.side_b),
                        label = state.bLabel,
                        enabled = state.bSet,
                        onClick = { onOpenNickname(Side.B) },
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                YellowButton(
                    text = actionLabel(state.step),
                    onClick = onAction,
                    enabled = state.canAct,
                    fontSize = 16.sp,
                    modifier = Modifier.width(96.dp).fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun PointRow(
    letter: String,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(TadaRow, RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = letter, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        if (label.isNotEmpty()) {
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = label, fontWeight = FontWeight.Medium, maxLines = 2)
        }
    }
}

@Composable
private fun actionLabel(step: ActionStep): String = when (step) {
    ActionStep.SetA -> stringResource(R.string.action_set_a)
    ActionStep.SetB -> stringResource(R.string.action_set_b)
    ActionStep.Book -> stringResource(R.string.action_book)
}

@Composable
private fun BlockingLoader() {
    val description = stringResource(R.string.cd_loading)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White.copy(alpha = 0.65f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            color = TadaYellow,
            modifier = Modifier.semantics { contentDescription = description },
        )
    }
}
