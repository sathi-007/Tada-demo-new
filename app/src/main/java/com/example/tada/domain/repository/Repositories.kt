package com.example.tada.domain.repository

import com.example.tada.domain.model.AdminArea
import com.example.tada.domain.model.BookRequest
import com.example.tada.domain.model.Booking
import com.example.tada.domain.model.GeoCoordinate
import com.example.tada.domain.model.MapPoint
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
