package com.example.tada.domain.usecase

import com.example.tada.domain.model.ActionStep
import com.example.tada.domain.model.AirQualityFailedException
import com.example.tada.domain.model.BookRequest
import com.example.tada.domain.model.BookedSide
import com.example.tada.domain.model.Booking
import com.example.tada.domain.model.BookingPresentation
import com.example.tada.domain.model.DomainResult
import com.example.tada.domain.model.FailureReason
import com.example.tada.domain.model.HistorySummary
import com.example.tada.domain.model.HistoryRow
import com.example.tada.domain.model.MapPoint
import com.example.tada.domain.model.NicknameRules
import com.example.tada.domain.model.SavedPlace
import com.example.tada.domain.model.Selection
import com.example.tada.domain.model.Side
import com.example.tada.domain.model.SidePresentation
import com.example.tada.domain.repository.AqiRepository
import com.example.tada.domain.repository.AppClock
import com.example.tada.domain.repository.BookingsRepository
import com.example.tada.domain.repository.MapCenterRequest
import com.example.tada.domain.repository.PlacesRepository
import com.example.tada.domain.repository.PointsRepository
import com.example.tada.domain.repository.SelectionRepository
import com.example.tada.domain.repository.YearMonthSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

class SaveNicknameUseCase @Inject constructor(
    private val pointsRepository: PointsRepository,
    private val placesRepository: PlacesRepository,
    private val selectionRepository: SelectionRepository,
    private val matchSavedPlace: MatchSavedPlace,
    private val clock: AppClock,
) {
    suspend operator fun invoke(side: Side, nickname: String): DomainResult<Selection> {
        if (!NicknameRules.accept(nickname)) {
            return DomainResult.Err(FailureReason.NicknameTooLong)
        }
        val current = selectionRepository.current()
        val existing = current.point(side) ?: return DomainResult.Err(FailureReason.NotReady)
        val updatedPoint = existing.copy(nickname = nickname)
        val updated = current.withPoint(side, updatedPoint)
        return try {
            val places = matchSavedPlace.withNickname(
                places = placesRepository.read(),
                latitude = existing.latitude,
                longitude = existing.longitude,
                address = existing.address,
                nickname = nickname,
                savedAtMillis = clock.nowMillis(),
            )
            placesRepository.write(places)
            pointsRepository.upsert(side, updatedPoint)
            selectionRepository.update(updated)
            DomainResult.Ok(updated)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            DomainResult.Err(FailureReason.Network)
        }
    }
}

class PresentBookingUseCase @Inject constructor(
    private val selectionRepository: SelectionRepository,
) {
    operator fun invoke(): BookingPresentation? {
        val selection = selectionRepository.current()
        val a = selection.a ?: return null
        val b = selection.b ?: return null
        return BookingPresentation(
            a = a.toPresentation(),
            b = b.toPresentation(),
        )
    }
}

class BookRideUseCase @Inject constructor(
    private val selectionRepository: SelectionRepository,
    private val bookingsRepository: BookingsRepository,
) {
    suspend operator fun invoke(): DomainResult<Booking> {
        val selection = selectionRepository.current()
        val a = selection.a
        val b = selection.b
        if (selection.step != ActionStep.Book || a == null || b == null) {
            return DomainResult.Err(FailureReason.NotReady)
        }
        return try {
            val booking = bookingsRepository.book(
                BookRequest(
                    a = a.toBookedSide(),
                    b = b.toBookedSide(),
                ),
            )
            DomainResult.Ok(booking)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            DomainResult.Err(FailureReason.Booking)
        }
    }
}

class LoadHistoryUseCase @Inject constructor(
    private val bookingsRepository: BookingsRepository,
    private val yearMonthSource: YearMonthSource,
) {
    suspend operator fun invoke(): DomainResult<HistorySummary> = try {
        val query = yearMonthSource.current()
        val bookings = bookingsRepository.history(query.year, query.month)
        DomainResult.Ok(
            HistorySummary(
                totalCount = bookings.size,
                totalPrice = bookings.sumOf { it.price },
                rows = bookings.map { booking ->
                    HistoryRow(
                        id = booking.id,
                        aName = booking.a.name,
                        bName = booking.b.name,
                        a = booking.a,
                        b = booking.b,
                    )
                },
            ),
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        DomainResult.Err(FailureReason.History)
    }
}

class RestoreBookingUseCase @Inject constructor(
    private val aqiRepository: AqiRepository,
    private val placesRepository: PlacesRepository,
    private val pointsRepository: PointsRepository,
    private val selectionRepository: SelectionRepository,
    private val matchSavedPlace: MatchSavedPlace,
    private val mapCenter: MapCenterRequest,
) {
    suspend operator fun invoke(booking: Booking): DomainResult<Selection> {
        val places = try {
            placesRepository.read()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
        val shown = Selection(
            step = ActionStep.Book,
            a = pointFrom(booking.a, places),
            b = pointFrom(booking.b, places),
        )
        selectionRepository.update(shown)
        mapCenter.centerOn(booking.a.latitude, booking.a.longitude)

        val freshA: Int
        val freshB: Int
        try {
            coroutineScope {
                val aDeferred = async { aqiRepository.fetch(booking.a.latitude, booking.a.longitude) }
                val bDeferred = async { aqiRepository.fetch(booking.b.latitude, booking.b.longitude) }
                freshA = aDeferred.await()
                freshB = bDeferred.await()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: AirQualityFailedException) {
            return DomainResult.Err(FailureReason.AirQuality)
        } catch (_: Exception) {
            return DomainResult.Err(FailureReason.Network)
        }

        val restored = shown.copy(
            a = shown.a?.copy(aqi = freshA),
            b = shown.b?.copy(aqi = freshB),
        )
        return try {
            val a = restored.a ?: return DomainResult.Err(FailureReason.NotReady)
            val b = restored.b ?: return DomainResult.Err(FailureReason.NotReady)
            pointsRepository.upsert(Side.A, a)
            pointsRepository.upsert(Side.B, b)
            selectionRepository.update(restored)
            DomainResult.Ok(restored)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            selectionRepository.update(restored)
            DomainResult.Err(FailureReason.Network)
        }
    }

    private fun pointFrom(side: BookedSide, places: List<SavedPlace>): MapPoint {
        val nickname = matchSavedPlace.newest(places, side.latitude, side.longitude)?.nickname.orEmpty()
        return MapPoint(
            latitude = side.latitude,
            longitude = side.longitude,
            address = side.name,
            aqi = PENDING_AQI,
            nickname = nickname,
        )
    }

    private companion object {
        const val PENDING_AQI = 0
    }
}

private fun Selection.point(side: Side): MapPoint? = when (side) {
    Side.A -> a
    Side.B -> b
}

private fun Selection.withPoint(side: Side, point: MapPoint): Selection = when (side) {
    Side.A -> copy(a = point)
    Side.B -> copy(b = point)
}

private fun MapPoint.toPresentation(): SidePresentation = SidePresentation(
    locationName = address,
    aqi = aqi,
    nickname = nickname,
)

private fun MapPoint.toBookedSide(): BookedSide = BookedSide(
    latitude = latitude,
    longitude = longitude,
    aqi = aqi,
    name = address,
)
