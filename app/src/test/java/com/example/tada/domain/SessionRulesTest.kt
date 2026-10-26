package com.example.tada.domain

import com.example.tada.data.local.PointsFileStore
import com.example.tada.data.remote.PointDto
import com.example.tada.data.repository.PointsRepositoryImpl
import com.example.tada.data.repository.SelectionRepositoryImpl
import com.example.tada.domain.model.ActionStep
import com.example.tada.domain.model.AdminArea
import com.example.tada.domain.model.AirQualityFailedException
import com.example.tada.domain.model.DomainResult
import com.example.tada.domain.model.FailureReason
import com.example.tada.domain.model.GeocodeFailedException
import com.example.tada.domain.model.MapPoint
import com.example.tada.domain.model.Selection
import com.example.tada.domain.model.Side
import com.example.tada.domain.model.SavedPlace
import com.example.tada.domain.repository.AqiRepository
import com.example.tada.domain.repository.AppClock
import com.example.tada.domain.repository.GeocodeRepository
import com.example.tada.domain.repository.PlacesRepository
import com.example.tada.domain.repository.PointsRepository
import com.example.tada.domain.usecase.FormatAdministrativeAddress
import com.example.tada.domain.usecase.MatchSavedPlace
import com.example.tada.domain.usecase.ResetSelectionUseCase
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
import java.io.File
import java.nio.file.Files

class SessionRulesTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    @Test
    fun setAThenSetBThenBookStep_andFailureDoesNotWrite() = runTest {
        val geocode = FakeGeocode()
        val aqi = FakeAqi()
        val points = RecordingPoints()
        val selection = SelectionRepositoryImpl()
        val setPoint = setPointUseCase(geocode, aqi, points, selection)

        val failedAir = setPoint(Side.A, 37.0, 127.0)
        assertEquals(FailureReason.AirQuality, (failedAir as DomainResult.Err).reason)
        assertEquals(ActionStep.SetA, selection.current().step)
        assertTrue(points.upserts.isEmpty())

        aqi.fail = false
        geocode.fail = true
        val failedGeo = setPoint(Side.A, 37.0, 127.0)
        assertEquals(FailureReason.Geocode, (failedGeo as DomainResult.Err).reason)
        assertTrue(points.upserts.isEmpty())
        assertNull(selection.current().a)

        geocode.fail = false
        val setA = setPoint(Side.A, 37.1, 127.1) as DomainResult.Ok
        assertEquals(ActionStep.SetB, setA.value.step)
        assertEquals("Seocho District, Yangjae 2(i)-dong", setA.value.a?.address)
        assertEquals(55, setA.value.a?.aqi)
        assertEquals("", setA.value.a?.nickname)

        val wrongStep = setPoint(Side.A, 1.0, 1.0)
        assertEquals(FailureReason.NotReady, (wrongStep as DomainResult.Err).reason)
        assertEquals(1, points.upserts.size)

        val setB = setPoint(Side.B, 37.2, 127.2) as DomainResult.Ok
        assertEquals(ActionStep.Book, setB.value.step)
        assertEquals(setA.value.a, setB.value.a)
        assertEquals(37.2, setB.value.b?.latitude)
        assertEquals(2, points.upserts.size)
        assertEquals(Side.A, points.upserts[0].first)
        assertEquals(Side.B, points.upserts[1].first)
    }

    @Test
    fun pointsFileWritesOnlyOnSetASetBAndNickname_backDoesNotDelete() = runTest {
        val file = tempFile("points.json")
        val store = PointsFileStore(file, json)
        store.upsert(Side.B, PointDto(9.0, 8.0, "kept address", 3, "office"))
        val points = PointsRepositoryImpl(store, Dispatchers.Unconfined)
        val selection = SelectionRepositoryImpl()
        val setPoint = setPointUseCase(FakeGeocode(), FakeAqi().apply { fail = false }, points, selection)
        val saveNickname = SaveNicknameUseCase(
            pointsRepository = points,
            placesRepository = MemoryPlaces(),
            selectionRepository = selection,
            matchSavedPlace = MatchSavedPlace(),
            clock = AppClock { 0L },
        )
        val reset = ResetSelectionUseCase(selection)

        setPoint(Side.A, 37.1, 127.1)
        val afterSetA = store.read()
        assertEquals("Seocho District, Yangjae 2(i)-dong", afterSetA.a?.address)
        assertEquals(55, afterSetA.a?.aqi)
        assertEquals("kept address", afterSetA.b?.address)
        assertEquals("office", afterSetA.b?.nickname)

        setPoint(Side.B, 37.2, 127.2)
        assertEquals("Seocho District, Yangjae 2(i)-dong", store.read().a?.address)
        assertEquals(37.2, store.read().b?.latitude)

        val saved = saveNickname(Side.A, "home") as DomainResult.Ok
        assertEquals(ActionStep.Book, saved.value.step)
        assertEquals("home", saved.value.a?.nickname)
        assertEquals("Seocho District, Yangjae 2(i)-dong", store.read().a?.address)
        assertEquals(37.2, store.read().b?.latitude ?: 0.0, 0.001)

        val bookedElsewhere = file.readText()
        reset()
        assertEquals(Selection.Empty, selection.current())
        assertEquals(bookedElsewhere, file.readText())
        assertTrue(file.exists())
        assertEquals("home", store.read().a?.nickname)
        assertEquals(37.2, store.read().b?.latitude ?: 0.0, 0.001)
        assertEquals("", store.read().b?.nickname)
    }

    @Test
    fun nicknameLengthAndBlankLine() = runTest {
        val points = RecordingPoints()
        val selection = SelectionRepositoryImpl()
        val save = SaveNicknameUseCase(
            pointsRepository = points,
            placesRepository = MemoryPlaces(),
            selectionRepository = selection,
            matchSavedPlace = MatchSavedPlace(),
            clock = AppClock { 0L },
        )
        val point = MapPoint(1.0, 2.0, "Seocho District, Yangjae 2(i)-dong", 4, "seed")
        selection.update(Selection(ActionStep.SetB, point, null))

        val tooLong = save(Side.A, "n".repeat(21))
        assertEquals(FailureReason.NicknameTooLong, (tooLong as DomainResult.Err).reason)
        assertEquals("seed", selection.current().a?.nickname)
        assertTrue(points.upserts.isEmpty())

        val max = save(Side.A, "n".repeat(20)) as DomainResult.Ok
        assertEquals("n".repeat(20), max.value.a?.nickname)
        assertEquals(1, points.upserts.size)

        val blank = save(Side.A, "") as DomainResult.Ok
        assertEquals("", blank.value.a?.nickname)
        assertEquals(2, points.upserts.size)
        assertEquals(point.address, com.example.tada.domain.usecase.PointRowLabel().label(blank.value.a))
    }

    private fun setPointUseCase(
        geocode: GeocodeRepository,
        aqi: AqiRepository,
        points: PointsRepository,
        selection: SelectionRepositoryImpl,
        places: PlacesRepository = MemoryPlaces(),
    ) = SetPointUseCase(
        geocodeRepository = geocode,
        aqiRepository = aqi,
        pointsRepository = points,
        placesRepository = places,
        selectionRepository = selection,
        formatAddress = FormatAdministrativeAddress(),
        matchSavedPlace = MatchSavedPlace(),
        clock = AppClock { 1_000L },
    )
}

class FakeGeocode : GeocodeRepository {
    var fail: Boolean = false
    var calls: Int = 0
    var areas: List<AdminArea> = listOf(
        AdminArea("South Korea", 2),
        AdminArea("Seocho District", 6),
        AdminArea("Yangjae 2(i)-dong", 8),
    )

    override suspend fun reverse(latitude: Double, longitude: Double): List<AdminArea> {
        calls += 1
        if (fail) throw GeocodeFailedException()
        return areas
    }
}

class FakeAqi : AqiRepository {
    var fail: Boolean = true
    var calls: Int = 0
    var value: Int = 55

    override suspend fun fetch(latitude: Double, longitude: Double): Int {
        calls += 1
        if (fail) throw AirQualityFailedException()
        return value
    }
}

class MemoryPlaces : PlacesRepository {
    val places = mutableListOf<SavedPlace>()

    override suspend fun read(): List<SavedPlace> = places.toList()

    override suspend fun append(place: SavedPlace) {
        places += place
    }

    override suspend fun write(places: List<SavedPlace>) {
        this.places.clear()
        this.places += places
    }
}

class RecordingPoints : PointsRepository {
    val upserts = mutableListOf<Pair<Side, MapPoint>>()
    var clears: Int = 0

    override suspend fun upsert(side: Side, point: MapPoint) {
        upserts += side to point
    }

    override suspend fun clear() {
        clears += 1
    }
}

internal fun tempFile(name: String): File {
    val directory = Files.createTempDirectory("tada-test").toFile()
    return File(directory, name)
}
