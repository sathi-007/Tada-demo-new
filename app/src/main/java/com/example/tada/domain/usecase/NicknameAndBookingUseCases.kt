package com.example.tada.domain.usecase

import com.example.tada.domain.model.ActionStep
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
import com.example.tada.domain.model.Selection
import com.example.tada.domain.model.Side
import com.example.tada.domain.model.SidePresentation
import com.example.tada.domain.repository.AppClock
import com.example.tada.domain.repository.BookingsRepository
import com.example.tada.domain.repository.PlacesRepository
import com.example.tada.domain.repository.PointsRepository
import com.example.tada.domain.repository.SelectionRepository
import com.example.tada.domain.repository.YearMonthSource
import kotlinx.coroutines.CancellationException
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
                    HistoryRow(aName = booking.a.name, bName = booking.b.name)
                },
            ),
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        DomainResult.Err(FailureReason.History)
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
