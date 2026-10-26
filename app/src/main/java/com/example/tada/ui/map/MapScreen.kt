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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tada.R
import com.example.tada.domain.model.ActionStep
import com.example.tada.maps.MapsApiKeyStatus
import com.example.tada.maps.mapsApiKeyStatus
import com.example.tada.maps.readMergedMapsApiKey
import com.example.tada.domain.model.FailureReason
import com.example.tada.domain.model.Side
import com.example.tada.ui.components.YellowButton
import com.example.tada.ui.failureMessage
import com.example.tada.ui.theme.TadaMuted
import com.example.tada.ui.theme.TadaRow
import com.example.tada.ui.theme.TadaYellow
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.suspendCancellableCoroutine

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
    var mapsReady by remember { mutableStateOf(false) }
    var initFailed by remember { mutableStateOf(false) }
    LaunchedEffect(keyStatus) {
        if (keyStatus != MapsApiKeyStatus.Ready) {
            mapsReady = false
            initFailed = false
            return@LaunchedEffect
        }
        val initialized = runCatching {
            suspendCancellableCoroutine { continuation ->
                val status = MapsInitializer.initialize(
                    context.applicationContext,
                    MapsInitializer.Renderer.LATEST,
                ) {
                    if (continuation.isActive) continuation.resume(Unit)
                }
                if (status != ConnectionResult.SUCCESS && continuation.isActive) {
                    continuation.resumeWith(
                        Result.failure(IllegalStateException("MapsInitializer status $status")),
                    )
                }
            }
        }
        if (initialized.isSuccess) {
            mapsReady = true
            initFailed = false
        } else {
            mapsReady = false
            initFailed = true
            Log.w(MAPS_LOG_TAG, "MapsInitializer failed for ${context.packageName}", initialized.exceptionOrNull())
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), 2f)
    }
    var gesturesArmed by remember { mutableStateOf(false) }
    LaunchedEffect(state.recenterToken, mapsReady) {
        if (!mapsReady) return@LaunchedEffect
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
    LaunchedEffect(gesturesArmed, mapsReady) {
        if (!mapsReady || !gesturesArmed) return@LaunchedEffect
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
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (keyStatus) {
                    MapsApiKeyStatus.Missing -> MapsSetupMessage(
                        stringResource(R.string.maps_key_missing_body),
                    )
                    MapsApiKeyStatus.Unsubstituted -> MapsSetupMessage(
                        stringResource(R.string.maps_key_unsubstituted_body),
                    )
                    MapsApiKeyStatus.Ready -> if (initFailed) {
                        MapsSetupMessage(
                            stringResource(R.string.maps_init_failed, context.packageName),
                        )
                    } else if (mapsReady) {
                        GoogleMap(
                            modifier = Modifier.fillMaxSize(),
                            cameraPositionState = cameraPositionState,
                            uiSettings = MapUiSettings(
                                zoomControlsEnabled = false,
                                myLocationButtonEnabled = false,
                                compassEnabled = false,
                            ),
                        )
                        Icon(
                            painter = painterResource(R.drawable.ic_center_pin),
                            contentDescription = stringResource(R.string.cd_center_marker),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(40.dp)
                                .offset(y = (-20).dp),
                            tint = Color.Black,
                        )
                        AirQualityChip(
                            aqi = state.cornerAqi,
                            loading = state.cornerAqiLoading,
                            error = state.cornerError,
                            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
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
                onClear = viewModel::onClear,
                onOpenNickname = onOpenNickname,
                onRetryPermission = { permissionLauncher.launch(locationPermissions()) },
                onRetryLocation = viewModel::retryLocation,
            )
        }
        if (state.blocking && keyStatus == MapsApiKeyStatus.Ready && !initFailed) {
            BlockingLoader()
        }
    }
}

private const val MAPS_LOG_TAG = "TadaMaps"

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
private fun AirQualityChip(
    aqi: Int?,
    loading: Boolean,
    error: FailureReason?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        Surface(
            color = Color.White.copy(alpha = 0.94f),
            shape = RoundedCornerShape(8.dp),
            shadowElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = stringResource(R.string.aqi), color = TadaMuted)
                Spacer(modifier = Modifier.width(8.dp))
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
        }
        if (error != null && !loading) {
            Text(
                text = failureMessage(error),
                color = Color(0xFFB3261E),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun MapCard(
    state: MapUiState,
    onAction: () -> Unit,
    onClear: () -> Unit,
    onOpenNickname: (Side) -> Unit,
    onRetryPermission: () -> Unit,
    onRetryLocation: () -> Unit,
) {
    Surface(color = Color.White, shadowElevation = 8.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
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
            Row(verticalAlignment = Alignment.Top) {
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
                Spacer(modifier = Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    YellowButton(
                        text = actionLabel(state.step),
                        onClick = onAction,
                        enabled = state.canAct,
                        modifier = Modifier.width(104.dp).heightIn(min = 84.dp),
                    )
                    OutlinedButton(
                        onClick = onClear,
                        enabled = state.clearEnabled && !state.blocking,
                        modifier = Modifier.width(104.dp),
                    ) {
                        Text(stringResource(R.string.action_clear))
                    }
                }
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
            .heightIn(min = 52.dp)
            .background(TadaRow, RoundedCornerShape(8.dp))
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
