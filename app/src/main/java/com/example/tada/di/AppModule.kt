package com.example.tada.di

import android.content.Context
import com.example.tada.BuildConfig
import com.example.tada.data.local.BookingsFileStore
import com.example.tada.data.local.PointsFileStore
import com.example.tada.data.mock.MockBooksApi
import com.example.tada.data.price.RandomPriceGenerator
import com.example.tada.data.remote.ApiCredentials
import com.example.tada.data.remote.AqiApi
import com.example.tada.data.remote.BooksApi
import com.example.tada.data.remote.GeocodeApi
import com.example.tada.data.repository.AqiRepositoryImpl
import com.example.tada.data.repository.BookingsRepositoryImpl
import com.example.tada.data.repository.FusedDeviceLocationRepository
import com.example.tada.data.repository.GeocodeRepositoryImpl
import com.example.tada.data.repository.PointsRepositoryImpl
import com.example.tada.data.repository.SelectionRepositoryImpl
import com.example.tada.data.repository.SystemAppClock
import com.example.tada.data.repository.SystemYearMonthSource
import com.example.tada.data.repository.UuidIdFactory
import com.example.tada.domain.repository.AqiRepository
import com.example.tada.domain.repository.AppClock
import com.example.tada.domain.repository.BookingsRepository
import com.example.tada.domain.repository.DeviceLocationRepository
import com.example.tada.domain.repository.GeocodeRepository
import com.example.tada.domain.repository.IdFactory
import com.example.tada.domain.repository.PointsRepository
import com.example.tada.domain.repository.PriceGenerator
import com.example.tada.domain.repository.SelectionRepository
import com.example.tada.domain.repository.YearMonthSource
import dagger.Binds
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AqiRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GeocodeRetrofit

@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {
    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    @Provides
    @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun converterFactory(json: Json): Converter.Factory =
        json.asConverterFactory("application/json".toMediaType())

    @Provides
    @Singleton
    fun credentials(): ApiCredentials = ApiCredentials(
        aqiToken = BuildConfig.AQICN_TOKEN,
        geocodeKey = BuildConfig.BIGDATACLOUD_API_KEY,
    )

    @Provides
    @Singleton
    @AqiRetrofit
    fun aqiRetrofit(client: OkHttpClient, converter: Converter.Factory): Retrofit =
        retrofit(AQICN_BASE_URL, client, converter)

    @Provides
    @Singleton
    @GeocodeRetrofit
    fun geocodeRetrofit(client: OkHttpClient, converter: Converter.Factory): Retrofit =
        retrofit(BIGDATACLOUD_BASE_URL, client, converter)

    @Provides
    @Singleton
    fun aqiApi(@AqiRetrofit retrofit: Retrofit): AqiApi = retrofit.create(AqiApi::class.java)

    @Provides
    @Singleton
    fun geocodeApi(@GeocodeRetrofit retrofit: Retrofit): GeocodeApi =
        retrofit.create(GeocodeApi::class.java)

    /**
     * Blank [BuildConfig.BOOKS_BASE_URL] binds the in-app mock.
     * A base URL binds Retrofit instead. Use cases and screens stay the same.
     */
    @Provides
    @Singleton
    fun booksApi(
        mock: Lazy<MockBooksApi>,
        client: OkHttpClient,
        converter: Converter.Factory,
    ): BooksApi {
        val baseUrl = BuildConfig.BOOKS_BASE_URL.trim()
        if (baseUrl.isEmpty()) return mock.get()
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return retrofit(normalized, client, converter).create(BooksApi::class.java)
    }

    private fun retrofit(
        baseUrl: String,
        client: OkHttpClient,
        converter: Converter.Factory,
    ): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(converter)
        .build()

    private const val AQICN_BASE_URL = "https://api.waqi.info/"
    private const val BIGDATACLOUD_BASE_URL = "https://api.bigdatacloud.net/"
}

@Module
@InstallIn(SingletonComponent::class)
object LocalStoreModule {
    @Provides
    @Singleton
    fun pointsFile(
        @ApplicationContext context: Context,
        json: Json,
    ): PointsFileStore = PointsFileStore(File(context.filesDir, "points.json"), json)

    @Provides
    @Singleton
    fun bookingsFile(
        @ApplicationContext context: Context,
        json: Json,
    ): BookingsFileStore = BookingsFileStore(File(context.filesDir, "bookings.json"), json)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun aqi(impl: AqiRepositoryImpl): AqiRepository

    @Binds
    @Singleton
    abstract fun geocode(impl: GeocodeRepositoryImpl): GeocodeRepository

    @Binds
    @Singleton
    abstract fun points(impl: PointsRepositoryImpl): PointsRepository

    @Binds
    @Singleton
    abstract fun bookings(impl: BookingsRepositoryImpl): BookingsRepository

    @Binds
    @Singleton
    abstract fun location(impl: FusedDeviceLocationRepository): DeviceLocationRepository

    @Binds
    @Singleton
    abstract fun selection(impl: SelectionRepositoryImpl): SelectionRepository

    @Binds
    @Singleton
    abstract fun clock(impl: SystemAppClock): AppClock

    @Binds
    @Singleton
    abstract fun ids(impl: UuidIdFactory): IdFactory

    @Binds
    @Singleton
    abstract fun prices(impl: RandomPriceGenerator): PriceGenerator

    @Binds
    @Singleton
    abstract fun yearMonth(impl: SystemYearMonthSource): YearMonthSource
}
