package com.example.tada.data.mock

import com.example.tada.data.local.BookingsFileStore
import com.example.tada.data.remote.BookRequestDto
import com.example.tada.data.remote.BooksApi
import com.example.tada.data.remote.StoredBookingDto
import com.example.tada.domain.repository.AppClock
import com.example.tada.domain.repository.IdFactory
import com.example.tada.domain.repository.PriceGenerator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-app stand-in for the books server. It stores each response in the bookings
 * file and returns that file from GET, newest timestamp first. `year` and `month`
 * are accepted and do not filter the list.
 */
@Singleton
class MockBooksApi @Inject constructor(
    private val store: BookingsFileStore,
    private val prices: PriceGenerator,
    private val clock: AppClock,
    private val ids: IdFactory,
) : BooksApi {
    var queriedYear: Int? = null
        private set
    var queriedMonth: Int? = null
        private set

    override suspend fun postBook(request: BookRequestDto): StoredBookingDto {
        val stored = StoredBookingDto(
            id = ids.newId(),
            timestamp = isoTimestamp(clock.nowMillis()),
            price = prices.nextPrice(),
            a = request.a,
            b = request.b,
        )
        store.append(stored)
        return stored
    }

    override suspend fun getBooks(year: Int, month: Int): List<StoredBookingDto> {
        queriedYear = year
        queriedMonth = month
        return store.readNewestFirst()
    }
}

internal fun isoTimestamp(millis: Long): String {
    val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    format.timeZone = TimeZone.getTimeZone("UTC")
    return format.format(Date(millis))
}
