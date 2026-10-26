package com.example.tada.domain

import com.example.tada.data.repository.SelectionRepositoryImpl
import com.example.tada.domain.model.ActionStep
import com.example.tada.domain.model.AirQualityFailedException
import com.example.tada.domain.model.BookedSide
import com.example.tada.domain.model.Booking
import com.example.tada.domain.model.DomainResult
import com.example.tada.domain.model.GeoCoordinate
import com.example.tada.domain.model.SavedPlace
import com.example.tada.domain.model.Side
import com.example.tada.domain.repository.AqiRepository
import com.example.tada.domain.repository.MapCenterRequest
import com.example.tada.domain.repository.SelectionRepository
import com.example.tada.domain.usecase.MatchSavedPlace
import com.example.tada.domain.usecase.RestoreBookingUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RestoreBookingTest {
    @Test
    fun tappedBookingRestoresBothSidesOnBookAndFetchesFreshAqi() = runTest {
        val selection = SelectionRepositoryImpl()
        val places = MemoryPlaces()
        places.places += SavedPlace(37.5644, 127.0011, "older cached", "old", 1L)
        places.places += SavedPlace(37.5645, 127.0018, "cached address", "home", 5L)
        val points = RecordingPoints()
        val center = RecordingCenter()
        val aqi = ProbeAqi(
            selection = selection,
            values = mapOf(
                37.5642 to 127.0016 to 80,
                10.5 to 20.25 to 70,
            ),
        )
        val restore = RestoreBookingUseCase(
            aqiRepository = aqi,
            placesRepository = places,
            pointsRepository = points,
            selectionRepository = selection,
            matchSavedPlace = MatchSavedPlace(),
            mapCenter = center,
        )
        val booking = Booking(
            id = "ride-1",
            timestamp = "2026-05-01T00:00:00.000Z",
            price = 4500,
            a = BookedSide(37.5642, 127.0016, aqi = 12, name = "Seocho District, Yangjae 2(i)-dong"),
            b = BookedSide(10.5, 20.25, aqi = 99, name = "Gangnam"),
        )

        val restored = restore(booking) as DomainResult.Ok

        assertEquals(ActionStep.Book, restored.value.step)
        assertEquals(37.5642, restored.value.a?.latitude ?: 0.0, 0.0)
        assertEquals(127.0016, restored.value.a?.longitude ?: 0.0, 0.0)
        assertEquals("Seocho District, Yangjae 2(i)-dong", restored.value.a?.address)
        assertEquals("home", restored.value.a?.nickname)
        assertEquals(10.5, restored.value.b?.latitude ?: 0.0, 0.0)
        assertEquals(20.25, restored.value.b?.longitude ?: 0.0, 0.0)
        assertEquals("Gangnam", restored.value.b?.address)
        assertEquals("", restored.value.b?.nickname)
        assertEquals(80, restored.value.a?.aqi)
        assertEquals(70, restored.value.b?.aqi)
        assertNotEquals(12, restored.value.a?.aqi)
        assertNotEquals(99, restored.value.b?.aqi)
        assertEquals(setOf(37.5642 to 127.0016, 10.5 to 20.25), aqi.calls.toSet())
        assertEquals(ActionStep.Book, aqi.stepWhenFetched)
        assertEquals("Seocho District, Yangjae 2(i)-dong", aqi.addressWhenFetched[Side.A])
        assertEquals("Gangnam", aqi.addressWhenFetched[Side.B])
        assertEquals("home", aqi.nicknameWhenFetched[Side.A])
        assertNotEquals(12, aqi.aqiWhenFetched[Side.A])
        assertNotEquals(99, aqi.aqiWhenFetched[Side.B])
        assertEquals(listOf(GeoCoordinate(37.5642, 127.0016)), center.centers)
        assertEquals(80, points.upserts.first { it.first == Side.A }.second.aqi)
        assertEquals(70, points.upserts.first { it.first == Side.B }.second.aqi)
        assertEquals(ActionStep.Book, selection.current().step)
    }
}

private class ProbeAqi(
    private val selection: SelectionRepository,
    private val values: Map<Pair<Double, Double>, Int>,
) : AqiRepository {
    val calls = mutableListOf<Pair<Double, Double>>()
    var stepWhenFetched: ActionStep? = null
    val addressWhenFetched = mutableMapOf<Side, String>()
    val nicknameWhenFetched = mutableMapOf<Side, String>()
    val aqiWhenFetched = mutableMapOf<Side, Int>()

    override suspend fun fetch(latitude: Double, longitude: Double): Int {
        calls += latitude to longitude
        val current = selection.current()
        stepWhenFetched = current.step
        current.a?.let { point ->
            addressWhenFetched[Side.A] = point.address
            nicknameWhenFetched[Side.A] = point.nickname
            aqiWhenFetched[Side.A] = point.aqi
        }
        current.b?.let { point ->
            addressWhenFetched[Side.B] = point.address
            nicknameWhenFetched[Side.B] = point.nickname
            aqiWhenFetched[Side.B] = point.aqi
        }
        return values[latitude to longitude] ?: throw AirQualityFailedException()
    }
}

private class RecordingCenter : MapCenterRequest {
    val centers = mutableListOf<GeoCoordinate>()

    override fun centerOn(latitude: Double, longitude: Double) {
        centers += GeoCoordinate(latitude, longitude)
    }

    override fun observe(): Flow<GeoCoordinate> = emptyFlow()
}
