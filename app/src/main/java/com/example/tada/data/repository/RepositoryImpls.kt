package com.example.tada.data.repository

import android.annotation.SuppressLint
import android.content.Context
import com.example.tada.data.remote.ApiCredentials
import com.example.tada.data.remote.AqiApi
import com.example.tada.data.remote.BookEndpointDto
import com.example.tada.data.remote.BookRequestDto
import com.example.tada.data.remote.BooksApi
import com.example.tada.data.remote.GeocodeApi
import com.example.tada.data.remote.PointDto
import com.example.tada.data.remote.SavedPlaceDto
import com.example.tada.data.remote.StoredBookingDto
import com.example.tada.data.remote.toAdminAreas
import com.example.tada.data.local.PlacesFileStore
import com.example.tada.data.local.PointsFileStore
import com.example.tada.di.IoDispatcher
import com.example.tada.domain.model.AdminArea
import com.example.tada.domain.model.AirQualityFailedException
import com.example.tada.domain.model.BookRequest
import com.example.tada.domain.model.BookedSide
import com.example.tada.domain.model.Booking
import com.example.tada.domain.model.GeoCoordinate
import com.example.tada.domain.model.GeocodeFailedException
import com.example.tada.domain.model.MapPoint
import com.example.tada.domain.model.SavedPlace
import com.example.tada.domain.model.Selection
import com.example.tada.domain.model.Side
import com.example.tada.domain.model.YearMonthQuery
import com.example.tada.domain.repository.AqiRepository
import com.example.tada.domain.repository.AppClock
import com.example.tada.domain.repository.BookingsRepository
import com.example.tada.domain.repository.DeviceLocationRepository
import com.example.tada.domain.repository.GeocodeRepository
import com.example.tada.domain.repository.IdFactory
import com.example.tada.domain.repository.PlacesRepository
import com.example.tada.domain.repository.PointsRepository
import com.example.tada.domain.repository.SelectionRepository
import com.example.tada.domain.repository.YearMonthSource
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import com.example.tada.domain.repository.MapCenterRequest
import com.example.tada.domain.repository.RestoreBookingQueue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

class AqiRepositoryImpl @Inject constructor(
    private val api: AqiApi,
    private val credentials: ApiCredentials,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : AqiRepository {
    override suspend fun fetch(latitude: Double, longitude: Double): Int = withContext(io) {
        try {
            val response = api.feed(
                location = "geo:$latitude;$longitude",
                token = credentials.aqiToken,
            )
            val aqi = (response.data?.aqi as? JsonPrimitive)?.intOrNull
                ?: (response.data?.aqi as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
            if (response.status != "ok" || aqi == null) {
                throw AirQualityFailedException()
            }
            aqi
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failed: AirQualityFailedException) {
            throw failed
        } catch (_: Exception) {
            throw AirQualityFailedException()
        }
    }
}

class GeocodeRepositoryImpl @Inject constructor(
    private val api: GeocodeApi,
    private val credentials: ApiCredentials,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : GeocodeRepository {
    override suspend fun reverse(latitude: Double, longitude: Double): List<AdminArea> =
        withContext(io) {
            try {
                api.reverse(
                    latitude = latitude,
                    longitude = longitude,
                    localityLanguage = "en",
                    key = credentials.geocodeKey,
                ).toAdminAreas()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                throw GeocodeFailedException()
            }
        }
}

class PointsRepositoryImpl @Inject constructor(
    private val store: PointsFileStore,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : PointsRepository {
    override suspend fun upsert(side: Side, point: MapPoint) = withContext(io) {
        store.upsert(
            side,
            PointDto(
                latitude = point.latitude,
                longitude = point.longitude,
                address = point.address,
                aqi = point.aqi,
                nickname = point.nickname,
            ),
        )
    }

    override suspend fun clear() = withContext(io) {
        store.clear()
    }
}

class PlacesRepositoryImpl @Inject constructor(
    private val store: PlacesFileStore,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : PlacesRepository {
    override suspend fun read(): List<SavedPlace> = withContext(io) {
        store.read().map { it.toDomain() }
    }

    override suspend fun append(place: SavedPlace) = withContext(io) {
        store.append(place.toDto())
    }

    override suspend fun write(places: List<SavedPlace>) = withContext(io) {
        store.write(places.map { it.toDto() })
    }
}

class BookingsRepositoryImpl @Inject constructor(
    private val api: BooksApi,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : BookingsRepository {
    override suspend fun book(request: BookRequest): Booking = withContext(io) {
        api.postBook(
            BookRequestDto(
                a = request.a.toDto(),
                b = request.b.toDto(),
            ),
        ).toDomain()
    }

    override suspend fun history(year: Int, month: Int): List<Booking> = withContext(io) {
        api.getBooks(year, month).map { it.toDomain() }
    }
}

class FusedDeviceLocationRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : DeviceLocationRepository {
    @SuppressLint("MissingPermission")
    override suspend fun current(): GeoCoordinate {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val location = try {
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
                ?: client.lastLocation.await()
        } catch (_: Exception) {
            client.lastLocation.await()
        } ?: error("Location unavailable")
        return GeoCoordinate(location.latitude, location.longitude)
    }
}

@Singleton
class SelectionRepositoryImpl @Inject constructor() : SelectionRepository {
    private val state = MutableStateFlow(Selection.Empty)

    override fun observe(): Flow<Selection> = state.asStateFlow()

    override fun current(): Selection = state.value

    override fun update(selection: Selection) {
        state.value = selection
    }
}

class SystemAppClock @Inject constructor() : AppClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

class UuidIdFactory @Inject constructor() : IdFactory {
    override fun newId(): String = UUID.randomUUID().toString()
}

@Singleton
class RestoreBookingQueueImpl @Inject constructor() : RestoreBookingQueue {
    private val requests = Channel<Booking>(capacity = Channel.BUFFERED)

    override fun submit(booking: Booking) {
        requests.trySend(booking)
    }

    override fun observe(): Flow<Booking> = requests.receiveAsFlow()
}

@Singleton
class MapCenterRequestImpl @Inject constructor() : MapCenterRequest {
    private val requests = MutableSharedFlow<GeoCoordinate>(extraBufferCapacity = 1)

    override fun centerOn(latitude: Double, longitude: Double) {
        requests.tryEmit(GeoCoordinate(latitude, longitude))
    }

    override fun observe(): Flow<GeoCoordinate> = requests
}

class SystemYearMonthSource @Inject constructor(
    private val clock: AppClock,
) : YearMonthSource {
    override fun current(): YearMonthQuery {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = clock.nowMillis()
        return YearMonthQuery(
            year = calendar.get(Calendar.YEAR),
            month = calendar.get(Calendar.MONTH) + 1,
        )
    }
}

private fun SavedPlace.toDto(): SavedPlaceDto = SavedPlaceDto(
    latitude = latitude,
    longitude = longitude,
    address = address,
    nickname = nickname,
    savedAtMillis = savedAtMillis,
)

private fun SavedPlaceDto.toDomain(): SavedPlace = SavedPlace(
    latitude = latitude,
    longitude = longitude,
    address = address,
    nickname = nickname,
    savedAtMillis = savedAtMillis,
)

private fun BookedSide.toDto(): BookEndpointDto = BookEndpointDto(
    latitude = latitude,
    longitude = longitude,
    aqi = aqi,
    name = name,
)

private fun StoredBookingDto.toDomain(): Booking = Booking(
    id = id,
    timestamp = timestamp,
    price = price,
    a = a.toDomain(),
    b = b.toDomain(),
)

private fun BookEndpointDto.toDomain(): BookedSide = BookedSide(
    latitude = latitude,
    longitude = longitude,
    aqi = aqi,
    name = name,
)
