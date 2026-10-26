package com.example.tada.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tada.domain.model.ActionStep
import com.example.tada.domain.model.DomainResult
import com.example.tada.domain.model.FailureReason
import com.example.tada.domain.model.GeoCoordinate
import com.example.tada.domain.model.Side
import com.example.tada.domain.repository.SelectionRepository
import com.example.tada.domain.usecase.ClearPointsUseCase
import com.example.tada.domain.usecase.LoadCameraTargetUseCase
import com.example.tada.domain.usecase.PointRowLabel
import com.example.tada.domain.usecase.RefreshCenterAirQualityUseCase
import com.example.tada.domain.usecase.SetPointUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MapUiState(
    val step: ActionStep = ActionStep.SetA,
    val aLabel: String = "",
    val bLabel: String = "",
    val aSet: Boolean = false,
    val bSet: Boolean = false,
    val clearEnabled: Boolean = false,
    val cornerAqi: Int? = null,
    val cornerAqiLoading: Boolean = false,
    val cornerError: FailureReason? = null,
    val blocking: Boolean = true,
    val bannerError: FailureReason? = null,
    val cameraTarget: GeoCoordinate? = null,
    val currentCenter: GeoCoordinate? = null,
    val recenterToken: Int = 0,
) {
    val canAct: Boolean
        get() = !blocking && (currentCenter != null || cameraTarget != null)
}

sealed interface MapEvent {
    data object OpenBooking : MapEvent
}

@HiltViewModel
class MapViewModel @Inject constructor(
    private val selectionRepository: SelectionRepository,
    private val setPoint: SetPointUseCase,
    private val clearPoints: ClearPointsUseCase,
    private val refreshCenterAirQuality: RefreshCenterAirQualityUseCase,
    private val loadCameraTarget: LoadCameraTargetUseCase,
    private val pointRowLabel: PointRowLabel,
) : ViewModel() {
    private val _ui = MutableStateFlow(MapUiState())
    val ui: StateFlow<MapUiState> = _ui.asStateFlow()

    private val _events = Channel<MapEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var centerAqiJob: Job? = null
    private var locationStarted = false
    private var bookingOpened = false

    init {
        viewModelScope.launch {
            selectionRepository.observe().collect { selection ->
                if (selection.step != ActionStep.Book) {
                    bookingOpened = false
                }
                _ui.update { state ->
                    state.copy(
                        step = selection.step,
                        aLabel = pointRowLabel.label(selection.a),
                        bLabel = pointRowLabel.label(selection.b),
                        aSet = selection.a != null,
                        bSet = selection.b != null,
                        clearEnabled = selection.hasAnyPoint,
                    )
                }
            }
        }
    }

    fun onLocationPermissionGranted() {
        if (locationStarted) return
        locationStarted = true
        loadFix()
    }

    fun onLocationPermissionDenied() {
        _ui.update { it.copy(blocking = false, bannerError = FailureReason.Permission) }
    }

    fun retryLocation() {
        loadFix()
    }

    fun onCameraMoving(latitude: Double, longitude: Double) {
        centerAqiJob?.cancel()
        centerAqiJob = null
        _ui.update {
            it.copy(
                currentCenter = GeoCoordinate(latitude, longitude),
                cornerAqi = null,
                cornerAqiLoading = false,
                cornerError = null,
            )
        }
    }

    fun onCameraIdle(latitude: Double, longitude: Double) {
        if (_ui.value.cameraTarget == null) return
        val center = GeoCoordinate(latitude, longitude)
        centerAqiJob?.cancel()
        centerAqiJob = viewModelScope.launch {
            _ui.update {
                it.copy(
                    currentCenter = center,
                    cornerAqi = null,
                    cornerAqiLoading = true,
                    cornerError = null,
                )
            }
            try {
                val aqi = refreshCenterAirQuality(latitude, longitude)
                ensureActive()
                _ui.update {
                    it.copy(cornerAqiLoading = false, cornerAqi = aqi, cornerError = null)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (!isActive) return@launch
                _ui.update {
                    it.copy(
                        cornerAqiLoading = false,
                        cornerAqi = null,
                        cornerError = FailureReason.AirQuality,
                    )
                }
            }
        }
    }

    fun onAction() {
        val state = _ui.value
        if (!state.canAct) return
        val center = state.currentCenter ?: state.cameraTarget ?: return
        when (state.step) {
            ActionStep.SetA -> store(Side.A, center)
            ActionStep.SetB -> store(Side.B, center)
            ActionStep.Book -> openBooking()
        }
    }

    fun onClear() {
        val state = _ui.value
        if (!state.clearEnabled || state.blocking) return
        viewModelScope.launch {
            _ui.update { it.copy(blocking = true, bannerError = null) }
            try {
                clearPoints()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _ui.update { it.copy(blocking = false, bannerError = FailureReason.Network) }
                return@launch
            }
            publishFix(loadCameraTarget())
        }
    }

    private fun store(side: Side, center: GeoCoordinate) {
        viewModelScope.launch {
            _ui.update { it.copy(blocking = true, bannerError = null) }
            when (val result = setPoint(side, center.latitude, center.longitude)) {
                is DomainResult.Ok -> _ui.update { it.copy(blocking = false) }
                is DomainResult.Err -> _ui.update {
                    it.copy(blocking = false, bannerError = result.reason)
                }
            }
        }
    }

    private fun openBooking() {
        if (bookingOpened) return
        bookingOpened = true
        viewModelScope.launch { _events.send(MapEvent.OpenBooking) }
    }

    private fun loadFix() {
        viewModelScope.launch {
            _ui.update { it.copy(blocking = true, bannerError = null) }
            publishFix(loadCameraTarget())
        }
    }

    private fun publishFix(result: DomainResult<GeoCoordinate>) {
        when (result) {
            is DomainResult.Ok -> _ui.update {
                it.copy(
                    blocking = false,
                    cameraTarget = result.value,
                    recenterToken = it.recenterToken + 1,
                )
            }
            is DomainResult.Err -> _ui.update {
                it.copy(blocking = false, bannerError = result.reason)
            }
        }
    }
}
