package com.example.tada.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class AqiResponseDto(
    val status: String = "",
    val data: AqiDataDto? = null,
)

@Serializable
data class AqiDataDto(
    val aqi: JsonElement? = null,
)

@Serializable
data class ReverseGeocodeDto(
    val localityInfo: LocalityInfoDto? = null,
)

@Serializable
data class LocalityInfoDto(
    val administrative: List<AdminAreaDto> = emptyList(),
    val informative: List<AdminAreaDto> = emptyList(),
)

@Serializable
data class AdminAreaDto(
    val name: String? = null,
    val order: Int? = null,
)

@Serializable
data class BookEndpointDto(
    val latitude: Double,
    val longitude: Double,
    val aqi: Int,
    val name: String,
)

@Serializable
data class BookRequestDto(
    val a: BookEndpointDto,
    val b: BookEndpointDto,
)

@Serializable
data class StoredBookingDto(
    val id: String,
    val timestamp: String,
    val price: Int,
    val a: BookEndpointDto,
    val b: BookEndpointDto,
)

@Serializable
data class PointDto(
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val aqi: Int,
    val nickname: String = "",
)

@Serializable
data class PointsFileDto(
    val a: PointDto? = null,
    val b: PointDto? = null,
)

@Serializable
data class SavedPlaceDto(
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val nickname: String = "",
    val savedAtMillis: Long,
)

data class ApiCredentials(
    val aqiToken: String,
    val geocodeKey: String,
)
