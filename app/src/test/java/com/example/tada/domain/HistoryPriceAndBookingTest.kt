package com.example.tada.domain

import com.example.tada.data.local.BookingsFileStore
import com.example.tada.data.mock.MockBooksApi
import com.example.tada.data.price.RandomPriceGenerator
import com.example.tada.data.remote.BookEndpointDto
import com.example.tada.data.remote.StoredBookingDto
import com.example.tada.data.repository.BookingsRepositoryImpl
import com.example.tada.data.repository.SelectionRepositoryImpl
import com.example.tada.domain.model.ActionStep
import com.example.tada.domain.model.BookedSide
import com.example.tada.domain.model.Booking
import com.example.tada.domain.model.DomainResult
import com.example.tada.domain.model.MapPoint
import com.example.tada.domain.model.Selection
import com.example.tada.domain.model.YearMonthQuery
import com.example.tada.domain.repository.AppClock
import com.example.tada.domain.repository.BookingsRepository
import com.example.tada.domain.repository.IdFactory
import com.example.tada.domain.repository.PriceGenerator
import com.example.tada.domain.repository.YearMonthSource
import com.example.tada.domain.usecase.BookRideUseCase
import com.example.tada.domain.usecase.LoadHistoryUseCase
import com.example.tada.domain.usecase.PresentBookingUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class HistoryPriceAndBookingTest {
    private val json = Json { encodeDefaults = true; prettyPrint = true }

    @Test
    fun priceIsInclusiveFrom1000Through10000() {
        val low = object : Random() {
            override fun nextBits(bitCount: Int): Int = 0
            override fun nextInt(from: Int, until: Int): Int = from
        }
        val high = object : Random() {
            override fun nextBits(bitCount: Int): Int = 0
            override fun nextInt(from: Int, until: Int): Int = until - 1
        }

        assertEquals(1000, RandomPriceGenerator.nextPrice(low))
        assertEquals(10000, RandomPriceGenerator.nextPrice(high))
        repeat(400) {
            val price = RandomPriceGenerator.nextPrice(Random.Default)
            assertTrue(price in 1000..10000)
        }
    }

    @Test
    fun historyIsNewestFirstFromTheFileAndIgnoresTheOnScreenBooking() = runTest {
        val file = tempFile("bookings.json")
        val store = BookingsFileStore(file, json)
        var millis = 1_700_000_000_000L
        val clock = AppClock {
            millis += 86_400_000L
            millis
        }
        var nextId = 0
        val prices = object : PriceGenerator {
            private val values = ArrayDeque(listOf(1000, 10000))
            override fun nextPrice(): Int = values.removeFirst()
        }
        val mock = MockBooksApi(
            store = store,
            prices = prices,
            clock = clock,
            ids = IdFactory { "id-${nextId++}" },
        )
        val repository = BookingsRepositoryImpl(mock, Dispatchers.Unconfined)
        val selection = SelectionRepositoryImpl()
        val book = BookRideUseCase(selection, repository)
        val history = LoadHistoryUseCase(
            repository,
            YearMonthSource { YearMonthQuery(year = 1999, month = 1) },
        )
        val onScreenOnly = Booking(
            id = "not-stored",
            timestamp = "2099-01-01T00:00:00.000Z",
            price = 1,
            a = BookedSide(0.0, 0.0, 1, "should-not-appear"),
            b = BookedSide(0.0, 0.0, 1, "also-absent"),
        )

        selection.update(selectionOf("first A", "first B", nickname = "home"))
        val first = (book() as DomainResult.Ok).value
        selection.update(selectionOf("second A", "second B", nickname = "office"))
        val second = (book() as DomainResult.Ok).value

        assertEquals(1000, first.price)
        assertEquals(10000, second.price)
        assertEquals("first A", first.a.name)
        assertEquals("second B", second.b.name)

        store.append(
            StoredBookingDto(
                id = "older-month",
                timestamp = "1998-12-31T00:00:00.000Z",
                price = 2222,
                a = BookEndpointDto(1.0, 2.0, 3, "old A"),
                b = BookEndpointDto(1.0, 2.0, 3, "old B"),
            ),
        )

        val summary = (history() as DomainResult.Ok).value

        assertEquals(1999, mock.queriedYear)
        assertEquals(1, mock.queriedMonth)
        assertEquals(3, summary.totalCount)
        assertEquals(1000 + 10000 + 2222, summary.totalPrice)
        assertEquals(listOf("second A", "first A", "old A"), summary.rows.map { it.aName })
        assertFalse(summary.rows.any { it.aName == onScreenOnly.a.name })
        assertEquals("second A", second.a.name)
        assertEquals("office", PresentBookingUseCase(selection)()?.a?.nickname)
    }

    @Test
    fun blankNicknameLineStaysOnTheBookingPresentation() {
        val selection = SelectionRepositoryImpl()
        selection.update(selectionOf("Seocho District, Yangjae 2(i)-dong", "Gangnam", nickname = ""))
        val presentation = PresentBookingUseCase(selection)()

        assertEquals("Seocho District, Yangjae 2(i)-dong", presentation?.a?.locationName)
        assertEquals("", presentation?.a?.nickname)
        assertEquals("", presentation?.b?.nickname)
        assertEquals(9, presentation?.b?.aqi)
    }

    @Test
    fun historyUseCasePreservesRepositoryOrder() = runTest {
        val onScreenOnly = Booking(
            id = "memory",
            timestamp = "2099-01-01T00:00:00.000Z",
            price = 5,
            a = BookedSide(0.0, 0.0, 1, "memory-a"),
            b = BookedSide(0.0, 0.0, 1, "memory-b"),
        )
        val repository = object : BookingsRepository {
            var year: Int? = null
            var month: Int? = null
            override suspend fun book(request: com.example.tada.domain.model.BookRequest): Booking = error("unused")
            override suspend fun history(year: Int, month: Int): List<Booking> {
                this.year = year
                this.month = month
                return listOf(
                    booking("newer", "2026-05-02T00:00:00.000Z", 40),
                    booking("older", "2026-05-01T00:00:00.000Z", 10),
                )
            }
        }
        val summary = LoadHistoryUseCase(
            repository,
            YearMonthSource { YearMonthQuery(2026, 5) },
        )() as DomainResult.Ok

        assertEquals(2026, repository.year)
        assertEquals(5, repository.month)
        assertEquals(listOf("newer-a", "older-a"), summary.value.rows.map { it.aName })
        assertEquals(50, summary.value.totalPrice)
        assertFalse(summary.value.rows.any { it.aName == onScreenOnly.a.name })
    }
}

private fun selectionOf(aName: String, bName: String, nickname: String): Selection = Selection(
    step = ActionStep.Book,
    a = MapPoint(37.1, 127.1, aName, 8, nickname),
    b = MapPoint(37.2, 127.2, bName, 9, nickname),
)

private fun booking(name: String, timestamp: String, price: Int): Booking = Booking(
    id = name,
    timestamp = timestamp,
    price = price,
    a = BookedSide(1.0, 2.0, 3, "$name-a"),
    b = BookedSide(1.0, 2.0, 3, "$name-b"),
)
