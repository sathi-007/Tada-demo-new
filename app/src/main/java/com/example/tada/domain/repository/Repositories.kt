package com.example.tada.domain.repository

import com.example.tada.domain.model.AdminArea
import com.example.tada.domain.model.BookRequest
import com.example.tada.domain.model.Booking
import com.example.tada.domain.model.GeoCoordinate
import com.example.tada.domain.model.MapPoint
import com.example.tada.domain.model.SavedPlace
import com.example.tada.domain.model.Selection
import com.example.tada.domain.model.Side
import com.example.tada.domain.model.YearMonthQuery
import kotlinx.coroutines.flow.Flow

interface AqiRepository {
    suspend fun fetch(latitude: Double, longitude: Double): Int
}

interface GeocodeRepository {
    suspend fun reverse(latitude: Double, longitude: Double): List<AdminArea>
}

interface PointsRepository {
    suspend fun upsert(side: Side, point: MapPoint)

    suspend fun clear()
}

interface PlacesRepository {
    suspend fun read(): List<SavedPlace>

    suspend fun append(place: SavedPlace)

    suspend fun write(places: List<SavedPlace>)
}

interface BookingsRepository {
    suspend fun book(request: BookRequest): Booking

    suspend fun history(year: Int, month: Int): List<Booking>
}

interface DeviceLocationRepository {
    suspend fun current(): GeoCoordinate
}

interface SelectionRepository {
    fun observe(): Flow<Selection>

    fun current(): Selection

    fun update(selection: Selection)
}

interface RestoreBookingQueue {
    fun submit(booking: Booking)

    fun observe(): Flow<Booking>
}

interface MapCenterRequest {
    fun centerOn(latitude: Double, longitude: Double)

    fun observe(): Flow<GeoCoordinate>
}

fun interface YearMonthSource {
    fun current(): YearMonthQuery
}

fun interface AppClock {
    fun nowMillis(): Long
}

fun interface IdFactory {
    fun newId(): String
}

fun interface PriceGenerator {
    fun nextPrice(): Int
}
