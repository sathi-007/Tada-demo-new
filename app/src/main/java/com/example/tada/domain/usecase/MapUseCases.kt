package com.example.tada.domain.usecase

import com.example.tada.domain.model.ActionStep
import com.example.tada.domain.model.AirQualityFailedException
import com.example.tada.domain.model.DomainResult
import com.example.tada.domain.model.FailureReason
import com.example.tada.domain.model.GeoCoordinate
import com.example.tada.domain.model.GeocodeFailedException
import com.example.tada.domain.model.MapPoint
import com.example.tada.domain.model.SavedPlace
import com.example.tada.domain.model.Selection
import com.example.tada.domain.model.Side
import com.example.tada.domain.repository.AqiRepository
import com.example.tada.domain.repository.AppClock
import com.example.tada.domain.repository.DeviceLocationRepository
import com.example.tada.domain.repository.GeocodeRepository
import com.example.tada.domain.repository.PlacesRepository
import com.example.tada.domain.repository.PointsRepository
import com.example.tada.domain.repository.SelectionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

class LoadCameraTargetUseCase @Inject constructor(
    private val deviceLocationRepository: DeviceLocationRepository,
) {
    suspend operator fun invoke(): DomainResult<GeoCoordinate> = try {
        DomainResult.Ok(deviceLocationRepository.current())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        DomainResult.Err(FailureReason.Location)
    }
}

class RefreshCenterAirQualityUseCase @Inject constructor(
    private val aqiRepository: AqiRepository,
) {
    suspend operator fun invoke(latitude: Double, longitude: Double): Int =
        aqiRepository.fetch(latitude, longitude)
}

class SetPointUseCase @Inject constructor(
    private val geocodeRepository: GeocodeRepository,
    private val aqiRepository: AqiRepository,
    private val pointsRepository: PointsRepository,
    private val placesRepository: PlacesRepository,
    private val selectionRepository: SelectionRepository,
    private val formatAddress: FormatAdministrativeAddress,
    private val matchSavedPlace: MatchSavedPlace,
    private val clock: AppClock,
) {
    suspend operator fun invoke(
        side: Side,
        latitude: Double,
        longitude: Double,
    ): DomainResult<Selection> {
        val expected = if (side == Side.A) ActionStep.SetA else ActionStep.SetB
        if (selectionRepository.current().step != expected) {
            return DomainResult.Err(FailureReason.NotReady)
        }

        val cached = try {
            matchSavedPlace.newest(placesRepository.read(), latitude, longitude)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return DomainResult.Err(FailureReason.Network)
        }

        val point = if (cached != null) {
            val aqi = try {
                aqiRepository.fetch(latitude, longitude)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: AirQualityFailedException) {
                return DomainResult.Err(FailureReason.AirQuality)
            } catch (_: Exception) {
                return DomainResult.Err(FailureReason.Network)
            }
            MapPoint(
                latitude = latitude,
                longitude = longitude,
                address = cached.address,
                aqi = aqi,
                nickname = cached.nickname,
            )
        } else {
            val fetched = fetchNewPlace(latitude, longitude)
            when (fetched) {
                is DomainResult.Err -> return fetched
                is DomainResult.Ok -> fetched.value
            }
        }

        val fresh = selectionRepository.current()
        if (fresh.step != expected) {
            return DomainResult.Err(FailureReason.NotReady)
        }
        val updated = when (side) {
            Side.A -> fresh.copy(step = ActionStep.SetB, a = point)
            Side.B -> fresh.copy(step = ActionStep.Book, b = point)
        }
        return try {
            if (cached == null) {
                placesRepository.append(
                    SavedPlace(
                        latitude = latitude,
                        longitude = longitude,
                        address = point.address,
                        nickname = point.nickname,
                        savedAtMillis = clock.nowMillis(),
                    ),
                )
            }
            pointsRepository.upsert(side, point)
            selectionRepository.update(updated)
            DomainResult.Ok(updated)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            DomainResult.Err(FailureReason.Network)
        }
    }

    private suspend fun fetchNewPlace(
        latitude: Double,
        longitude: Double,
    ): DomainResult<MapPoint> {
        val areas: List<com.example.tada.domain.model.AdminArea>
        val aqi: Int
        try {
            coroutineScope {
                val areasDeferred = async { geocodeRepository.reverse(latitude, longitude) }
                val aqiDeferred = async { aqiRepository.fetch(latitude, longitude) }
                areas = areasDeferred.await()
                aqi = aqiDeferred.await()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: AirQualityFailedException) {
            return DomainResult.Err(FailureReason.AirQuality)
        } catch (_: GeocodeFailedException) {
            return DomainResult.Err(FailureReason.Geocode)
        } catch (_: Exception) {
            return DomainResult.Err(FailureReason.Network)
        }

        val address = formatAddress.format(areas)
            ?: return DomainResult.Err(FailureReason.Geocode)
        return DomainResult.Ok(
            MapPoint(
                latitude = latitude,
                longitude = longitude,
                address = address,
                aqi = aqi,
                nickname = "",
            ),
        )
    }
}

class ResetSelectionUseCase @Inject constructor(
    private val selectionRepository: SelectionRepository,
) {
    operator fun invoke() {
        selectionRepository.update(Selection.Empty)
    }
}
