package com.example.tada.domain

import com.example.tada.data.local.PlacesFileStore
import com.example.tada.data.local.PointsFileStore
import com.example.tada.data.repository.PlacesRepositoryImpl
import com.example.tada.data.repository.PointsRepositoryImpl
import com.example.tada.data.repository.SelectionRepositoryImpl
import com.example.tada.domain.model.DomainResult
import com.example.tada.domain.model.FailureReason
import com.example.tada.domain.model.SavedPlace
import com.example.tada.domain.model.Selection
import com.example.tada.domain.model.Side
import com.example.tada.domain.repository.AppClock
import com.example.tada.domain.usecase.ClearPointsUseCase
import com.example.tada.domain.usecase.FormatAdministrativeAddress
import com.example.tada.domain.usecase.MatchSavedPlace
import com.example.tada.domain.usecase.PointRowLabel
import com.example.tada.domain.usecase.RefreshCenterAirQualityUseCase
import com.example.tada.domain.usecase.SaveNicknameUseCase
import com.example.tada.domain.usecase.SetPointUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceCacheTest {
    private val match = MatchSavedPlace()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    @Test
    fun thirdDecimalMatchCutsOffAndDoesNotRound() {
        assertTrue(match.samePlace(37.5642, 127.0016, 37.5645, 127.0018))
        assertFalse(match.samePlace(37.5642, 127.0016, 37.5650, 127.0018))
        assertFalse(match.samePlace(37.5650, 127.0016, 37.5645, 127.0018))
    }

    @Test
    fun setHitShowsCachedAddressAndNicknameAndFetchesNewAqi() = runTest {
        val geocode = FakeGeocode()
        val aqi = FakeAqi().apply {
            fail = false
            value = 80
        }
        val places = MemoryPlaces()
        places.append(saved(37.5642, 127.0016, "cached address", "home", 10))
        val points = RecordingPoints()
        val selection = SelectionRepositoryImpl()
        val set = setPoint(geocode, aqi, points, places, selection)

        val result = set(Side.A, 37.5645, 127.0018) as DomainResult.Ok

        assertEquals(0, geocode.calls)
        assertEquals(1, aqi.calls)
        assertEquals(37.5645, result.value.a?.latitude)
        assertEquals(127.0018, result.value.a?.longitude)
        assertEquals("cached address", result.value.a?.address)
        assertEquals("home", result.value.a?.nickname)
        assertEquals(80, result.value.a?.aqi)
        assertEquals("home", PointRowLabel().label(result.value.a))
        assertEquals(1, places.places.size)
        assertEquals("cached address", places.places.single().address)
        assertEquals(1, points.upserts.size)
    }

    @Test
    fun setMissFetchesAddressAndRecordsThePlace() = runTest {
        val geocode = FakeGeocode()
        val aqi = FakeAqi().apply { fail = false }
        val places = MemoryPlaces()
        val points = RecordingPoints()
        val selection = SelectionRepositoryImpl()
        val set = setPoint(geocode, aqi, points, places, selection, clockMillis = 2_000L)

        val result = set(Side.A, 37.1, 127.1) as DomainResult.Ok

        assertEquals(1, geocode.calls)
        assertEquals(1, aqi.calls)
        assertEquals("Seocho District, Yangjae 2(i)-dong", result.value.a?.address)
        assertEquals("", result.value.a?.nickname)
        assertEquals(1, places.places.size)
        assertEquals(37.1, places.places.single().latitude, 0.0)
        assertEquals("Seocho District, Yangjae 2(i)-dong", places.places.single().address)
        assertEquals("", places.places.single().nickname)
        assertEquals(2_000L, places.places.single().savedAtMillis)
    }

    @Test
    fun latitudeThatDiffersAtTheThirdDecimalIsAMiss() = runTest {
        val geocode = FakeGeocode()
        val aqi = FakeAqi().apply { fail = false }
        val places = MemoryPlaces()
        places.append(saved(37.5642, 127.0016, "cached address", "home", 10))
        val set = setPoint(geocode, aqi, RecordingPoints(), places, SelectionRepositoryImpl())

        val result = set(Side.A, 37.5650, 127.0018) as DomainResult.Ok

        assertEquals(1, geocode.calls)
        assertEquals("Seocho District, Yangjae 2(i)-dong", result.value.a?.address)
        assertEquals("", result.value.a?.nickname)
        assertEquals(2, places.places.size)
        assertEquals(37.5650, places.places[1].latitude, 0.0)
    }

    @Test
    fun newestSavedPlaceInTheBucketWins() = runTest {
        val geocode = FakeGeocode()
        val aqi = FakeAqi().apply { fail = false }
        val places = MemoryPlaces()
        places.append(saved(37.5642, 127.0016, "older address", "old", 1))
        places.append(saved(37.5644, 127.0017, "newer address", "new", 2))
        val set = setPoint(geocode, aqi, RecordingPoints(), places, SelectionRepositoryImpl())

        val result = set(Side.A, 37.5645, 127.0018) as DomainResult.Ok

        assertEquals(0, geocode.calls)
        assertEquals("newer address", result.value.a?.address)
        assertEquals("new", result.value.a?.nickname)
        assertEquals(2, places.places.size)
    }

    @Test
    fun airQualityFailureOnHitDoesNotWriteThePointOrTheCache() = runTest {
        val geocode = FakeGeocode()
        val aqi = FakeAqi()
        val places = MemoryPlaces()
        places.append(saved(37.5642, 127.0016, "cached address", "home", 10))
        val points = RecordingPoints()
        val selection = SelectionRepositoryImpl()
        val set = setPoint(geocode, aqi, points, places, selection)

        val result = set(Side.A, 37.5645, 127.0018)

        assertEquals(FailureReason.AirQuality, (result as DomainResult.Err).reason)
        assertEquals(0, geocode.calls)
        assertTrue(points.upserts.isEmpty())
        assertEquals(listOf(saved(37.5642, 127.0016, "cached address", "home", 10)), places.places)
        assertNull(selection.current().a)
    }

    @Test
    fun clearAndRelaunchKeepTheCacheOffTheScreen() = runTest {
        val placesFile = tempFile("places.json")
        val pointsFile = tempFile("points.json")
        val places = PlacesRepositoryImpl(PlacesFileStore(placesFile, json), Dispatchers.Unconfined)
        val points = PointsRepositoryImpl(PointsFileStore(pointsFile, json), Dispatchers.Unconfined)
        val selection = SelectionRepositoryImpl()
        val geocode = FakeGeocode()
        val aqi = FakeAqi().apply { fail = false }
        val set = setPoint(geocode, aqi, points, places, selection, clockMillis = 5L)
        val save = SaveNicknameUseCase(points, places, selection, match, AppClock { 6L })
        val clear = ClearPointsUseCase(points, selection)

        set(Side.A, 37.5642, 127.0016)
        save(Side.A, "gangnam")
        clear()

        assertFalse(pointsFile.exists())
        assertEquals(Selection.Empty, selection.current())
        assertTrue(placesFile.exists())
        assertEquals("gangnam", places.read().single().nickname)
        assertEquals("Seocho District, Yangjae 2(i)-dong", places.read().single().address)

        val relaunch = SelectionRepositoryImpl()
        assertEquals(Selection.Empty, relaunch.current())
        assertNull(relaunch.current().a)
        assertNull(relaunch.current().b)

        aqi.value = 90
        val again = set(Side.A, 37.5645, 127.0018) as DomainResult.Ok
        assertEquals(1, geocode.calls)
        assertEquals("gangnam", again.value.a?.nickname)
        assertEquals("Seocho District, Yangjae 2(i)-dong", again.value.a?.address)
        assertEquals(90, again.value.a?.aqi)
        assertEquals(1, places.read().size)
    }

    @Test
    fun nicknameEditUpdatesTheNewestSavedPlace() = runTest {
        val places = MemoryPlaces()
        places.append(saved(37.5642, 127.0016, "older address", "old", 1))
        places.append(saved(37.5644, 127.0017, "newer address", "new", 2))
        val points = RecordingPoints()
        val selection = SelectionRepositoryImpl()
        val set = setPoint(FakeGeocode(), FakeAqi().apply { fail = false }, points, places, selection)
        set(Side.A, 37.5645, 127.0018)
        val save = SaveNicknameUseCase(points, places, selection, match, AppClock { 9L })

        val saved = save(Side.A, "edited") as DomainResult.Ok

        assertEquals("edited", saved.value.a?.nickname)
        assertEquals("old", places.places[0].nickname)
        assertEquals(1L, places.places[0].savedAtMillis)
        assertEquals("edited", places.places[1].nickname)
        assertEquals("newer address", places.places[1].address)
        assertEquals(2L, places.places[1].savedAtMillis)

        clearScreen(selection)
        val geocode = FakeGeocode()
        val again = setPoint(geocode, FakeAqi().apply { fail = false; value = 11 }, points, places, selection)
        val next = again(Side.A, 37.5642, 127.0016) as DomainResult.Ok
        assertEquals(0, geocode.calls)
        assertEquals("edited", next.value.a?.nickname)
        assertEquals("newer address", next.value.a?.address)
    }

    @Test
    fun cornerRefreshDoesNotWriteTheCache() = runTest {
        val places = MemoryPlaces()
        val recorded = saved(37.5642, 127.0016, "cached address", "home", 10)
        places.append(recorded)
        val aqi = FakeAqi().apply {
            fail = false
            value = 12
        }

        val reading = RefreshCenterAirQualityUseCase(aqi).invoke(37.5645, 127.0018)

        assertEquals(12, reading)
        assertEquals(1, aqi.calls)
        assertEquals(listOf(recorded), places.read())
    }

    private fun clearScreen(selection: SelectionRepositoryImpl) {
        selection.update(Selection.Empty)
    }

    private fun saved(
        latitude: Double,
        longitude: Double,
        address: String,
        nickname: String,
        savedAtMillis: Long,
    ) = SavedPlace(latitude, longitude, address, nickname, savedAtMillis)

    private fun setPoint(
        geocode: FakeGeocode,
        aqi: FakeAqi,
        points: com.example.tada.domain.repository.PointsRepository,
        places: com.example.tada.domain.repository.PlacesRepository,
        selection: SelectionRepositoryImpl,
        clockMillis: Long = 1_000L,
    ) = SetPointUseCase(
        geocodeRepository = geocode,
        aqiRepository = aqi,
        pointsRepository = points,
        placesRepository = places,
        selectionRepository = selection,
        formatAddress = FormatAdministrativeAddress(),
        matchSavedPlace = match,
        clock = AppClock { clockMillis },
    )
}
