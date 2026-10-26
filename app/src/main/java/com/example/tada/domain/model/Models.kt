package com.example.tada.domain.model

data class GeoCoordinate(
    val latitude: Double,
    val longitude: Double,
)

data class AdminArea(
    val name: String,
    val order: Int,
)

enum class Side {
    A,
    B,
}

enum class ActionStep {
    SetA,
    SetB,
    Book,
}

data class MapPoint(
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val aqi: Int,
    val nickname: String,
)

data class SavedPlace(
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val nickname: String,
    val savedAtMillis: Long,
)

data class Selection(
    val step: ActionStep,
    val a: MapPoint?,
    val b: MapPoint?,
) {
    val hasAnyPoint: Boolean
        get() = a != null || b != null

    companion object {
        val Empty = Selection(step = ActionStep.SetA, a = null, b = null)
    }
}

data class BookedSide(
    val latitude: Double,
    val longitude: Double,
    val aqi: Int,
    val name: String,
)

data class BookRequest(
    val a: BookedSide,
    val b: BookedSide,
)

data class Booking(
    val id: String,
    val timestamp: String,
    val price: Int,
    val a: BookedSide,
    val b: BookedSide,
)

data class SidePresentation(
    val locationName: String,
    val aqi: Int,
    val nickname: String,
)

data class BookingPresentation(
    val a: SidePresentation,
    val b: SidePresentation,
)

data class HistoryRow(
    val id: String,
    val aName: String,
    val bName: String,
    val a: BookedSide,
    val b: BookedSide,
)

data class HistorySummary(
    val totalCount: Int,
    val totalPrice: Int,
    val rows: List<HistoryRow>,
)

data class YearMonthQuery(
    val year: Int,
    val month: Int,
)

enum class FailureReason {
    Geocode,
    AirQuality,
    Network,
    Location,
    Permission,
    NicknameTooLong,
    NotReady,
    Booking,
    History,
}

sealed interface DomainResult<out T> {
    data class Ok<T>(val value: T) : DomainResult<T>
    data class Err(val reason: FailureReason) : DomainResult<Nothing>
}

class GeocodeFailedException : RuntimeException()

class AirQualityFailedException : RuntimeException()

object NicknameRules {
    const val MAX_LENGTH = 20

    fun accept(value: String): Boolean = value.length <= MAX_LENGTH
}
