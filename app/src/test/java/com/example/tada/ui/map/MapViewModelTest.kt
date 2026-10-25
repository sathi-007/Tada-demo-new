package com.example.tada.ui.map

import com.example.tada.data.local.PointsFileStore
import com.example.tada.data.remote.PointDto
import com.example.tada.data.repository.PointsRepositoryImpl
import com.example.tada.data.repository.SelectionRepositoryImpl
import com.example.tada.domain.FakeGeocode
import com.example.tada.domain.RecordingPoints
import com.example.tada.domain.model.ActionStep
import com.example.tada.domain.model.AirQualityFailedException
import com.example.tada.domain.model.FailureReason
import com.example.tada.domain.model.GeoCoordinate
import com.example.tada.domain.model.MapPoint
import com.example.tada.domain.model.Selection
import com.example.tada.domain.repository.AqiRepository
import com.example.tada.domain.repository.DeviceLocationRepository
import com.example.tada.domain.repository.PointsRepository
import com.example.tada.domain.usecase.ClearPointsUseCase
import com.example.tada.domain.usecase.FormatAdministrativeAddress
import com.example.tada.domain.usecase.LoadCameraTargetUseCase
import com.example.tada.domain.usecase.PointRowLabel
import com.example.tada.domain.usecase.RefreshCenterAirQualityUseCase
import com.example.tada.domain.usecase.ResetSelectionUseCase
import com.example.tada.domain.usecase.SetPointUseCase
import com.example.tada.domain.tempFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {
    @Test
    fun launchStaysEmptyAndDoesNotReadThePointsFile() = runVm { scope ->
        val file = tempFile("points.json")
        val store = PointsFileStore(file, Json { encodeDefaults = true })
        store.upsert(com.example.tada.domain.model.Side.A, PointDto(1.0, 2.0, "saved A", 4, "home"))
        store.upsert(com.example.tada.domain.model.Side.B, PointDto(3.0, 4.0, "saved B", 5, "office"))
        val aqi = DelayingAqi()
        val geocode = FakeGeocode()
        val vm = viewModel(aqi = aqi, geocode = geocode)

        scope.advanceUntilIdle()

        assertEquals(ActionStep.SetA, vm.ui.value.step)
        assertFalse(vm.ui.value.aSet)
        assertFalse(vm.ui.value.bSet)
        assertEquals("", vm.ui.value.aLabel)
        assertEquals(0, vm.ui.value.recenterToken)
        assertEquals(0, aqi.calls.size)
        assertEquals(0, geocode.calls)
        assertEquals("home", store.read().a?.nickname)
        assertEquals("office", store.read().b?.nickname)
    }

    @Test
    fun movingTheCameraCancelsAirQualityAndDoesNotTouchPoints() = runVm { scope ->
        val aqi = DelayingAqi(delayMs = 5_000, result = 7)
        val geocode = FakeGeocode()
        val points = RecordingPoints()
        val selection = SelectionRepositoryImpl()
        selection.update(
            Selection(
                step = ActionStep.Book,
                a = MapPoint(1.0, 2.0, "address A", 1, "home"),
                b = MapPoint(3.0, 4.0, "address B", 2, "office"),
            ),
        )
        val vm = viewModel(selection = selection, aqi = aqi, geocode = geocode, points = points)
        vm.onLocationPermissionGranted()
        scope.advanceUntilIdle()
        val tokenAfterFix = vm.ui.value.recenterToken
        aqi.calls.clear()

        vm.onCameraIdle(10.0, 20.0)
        scope.advanceTimeBy(100)
        assertEquals(1, aqi.calls.size)
        assertTrue(vm.ui.value.cornerAqiLoading)
        assertFalse(vm.ui.value.blocking)
        assertEquals(ActionStep.Book, vm.ui.value.step)

        vm.onCameraMoving(11.0, 21.0)
        scope.advanceUntilIdle()
        assertNull(vm.ui.value.cornerAqi)
        assertFalse(vm.ui.value.cornerAqiLoading)
        assertEquals("home", vm.ui.value.aLabel)
        assertEquals("office", vm.ui.value.bLabel)

        aqi.delayMs = 0
        aqi.result = 42
        vm.onCameraIdle(12.0, 22.0)
        scope.advanceUntilIdle()

        assertEquals(42, vm.ui.value.cornerAqi)
        assertEquals(ActionStep.Book, vm.ui.value.step)
        assertEquals("home", vm.ui.value.aLabel)
        assertEquals(0, geocode.calls)
        assertTrue(points.upserts.isEmpty())
        assertEquals(0, points.clears)
        assertEquals(tokenAfterFix, vm.ui.value.recenterToken)
    }

    @Test
    fun backClearsLabelsWithoutRecenteringOrDeletingPoints() = runVm { scope ->
        val file = tempFile("points.json")
        val store = PointsFileStore(file, Json { encodeDefaults = true })
        store.upsert(com.example.tada.domain.model.Side.A, PointDto(1.0, 2.0, "saved A", 4, "home"))
        val selection = SelectionRepositoryImpl()
        selection.update(
            Selection(
                step = ActionStep.Book,
                a = MapPoint(1.0, 2.0, "saved A", 4, "home"),
                b = MapPoint(3.0, 4.0, "saved B", 5, ""),
            ),
        )
        val points = RecordingPoints()
        val vm = viewModel(selection = selection, points = points)
        vm.onLocationPermissionGranted()
        scope.advanceUntilIdle()
        val token = vm.ui.value.recenterToken
        val fileText = file.readText()

        ResetSelectionUseCase(selection)()
        scope.advanceUntilIdle()

        assertEquals(ActionStep.SetA, vm.ui.value.step)
        assertEquals("", vm.ui.value.aLabel)
        assertFalse(vm.ui.value.clearEnabled)
        assertEquals(token, vm.ui.value.recenterToken)
        assertEquals(0, points.clears)
        assertTrue(points.upserts.isEmpty())
        assertEquals(fileText, file.readText())
    }

    @Test
    fun clearDeletesPointsAndRecenters() = runVm { scope ->
        val file = tempFile("points.json")
        val store = PointsFileStore(file, Json { encodeDefaults = true })
        store.upsert(com.example.tada.domain.model.Side.A, PointDto(1.0, 2.0, "saved A", 4, "home"))
        store.upsert(com.example.tada.domain.model.Side.B, PointDto(3.0, 4.0, "saved B", 5, "office"))
        val selection = SelectionRepositoryImpl()
        selection.update(
            Selection(
                step = ActionStep.Book,
                a = MapPoint(1.0, 2.0, "saved A", 4, "home"),
                b = MapPoint(3.0, 4.0, "saved B", 5, "office"),
            ),
        )
        val vm = viewModel(
            selection = selection,
            points = PointsRepositoryImpl(store, Dispatchers.Unconfined),
        )
        vm.onLocationPermissionGranted()
        scope.advanceUntilIdle()
        val token = vm.ui.value.recenterToken

        vm.onClear()
        scope.advanceUntilIdle()

        assertFalse(file.exists())
        assertEquals(ActionStep.SetA, vm.ui.value.step)
        assertEquals("", vm.ui.value.aLabel)
        assertFalse(vm.ui.value.clearEnabled)
        assertEquals(token + 1, vm.ui.value.recenterToken)
        assertFalse(vm.ui.value.blocking)
    }

    @Test
    fun failedCornerRefreshDoesNotChangeTheStep() = runVm { scope ->
        val aqi = DelayingAqi(fail = true)
        val selection = SelectionRepositoryImpl()
        val vm = viewModel(selection = selection, aqi = aqi)
        vm.onLocationPermissionGranted()
        scope.advanceUntilIdle()

        vm.onCameraIdle(1.0, 2.0)
        scope.advanceUntilIdle()

        assertEquals(FailureReason.AirQuality, vm.ui.value.cornerError)
        assertNull(vm.ui.value.cornerAqi)
        assertFalse(vm.ui.value.blocking)
        assertEquals(ActionStep.SetA, vm.ui.value.step)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun runVm(block: suspend TestScope.(TestScope) -> Unit) = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    Dispatchers.setMain(dispatcher)
    try {
        block(this)
    } finally {
        Dispatchers.resetMain()
    }
}

private class DelayingAqi(
    var delayMs: Long = 0,
    var result: Int = 40,
    var fail: Boolean = false,
) : AqiRepository {
    val calls = mutableListOf<Pair<Double, Double>>()

    override suspend fun fetch(latitude: Double, longitude: Double): Int {
        calls += latitude to longitude
        if (delayMs > 0) delay(delayMs)
        if (fail) throw AirQualityFailedException()
        return result
    }
}

private class FakeLocation : DeviceLocationRepository {
    override suspend fun current(): GeoCoordinate = GeoCoordinate(37.5, 127.0)
}

private fun viewModel(
    selection: SelectionRepositoryImpl = SelectionRepositoryImpl(),
    aqi: AqiRepository = DelayingAqi(),
    geocode: FakeGeocode = FakeGeocode(),
    points: PointsRepository = RecordingPoints(),
): MapViewModel = MapViewModel(
    selectionRepository = selection,
    setPoint = SetPointUseCase(geocode, aqi, points, selection, FormatAdministrativeAddress()),
    clearPoints = ClearPointsUseCase(points, selection),
    refreshCenterAirQuality = RefreshCenterAirQualityUseCase(aqi),
    loadCameraTarget = LoadCameraTargetUseCase(FakeLocation()),
    pointRowLabel = PointRowLabel(),
)
