package com.example.tada.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface AqiApi {
    @GET("feed/{location}/")
    suspend fun feed(
        @Path(value = "location", encoded = true) location: String,
        @Query("token") token: String,
    ): AqiResponseDto
}

interface GeocodeApi {
    @GET("data/reverse-geocode")
    suspend fun reverse(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("localityLanguage") localityLanguage: String,
        @Query("key") key: String,
    ): ReverseGeocodeDto
}

/**
 * Assignment contract for `POST /books` and `GET /books`.
 * The in-app mock implements this. A real base URL can replace that binding.
 */
interface BooksApi {
    @POST("books")
    suspend fun postBook(@Body request: BookRequestDto): StoredBookingDto

    @GET("books")
    suspend fun getBooks(
        @Query("year") year: Int,
        @Query("month") month: Int,
    ): List<StoredBookingDto>
}
