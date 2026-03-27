package com.benhic.appdar.data.remote.geocoding

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit service for the OpenStreetMap Nominatim API.
 * Requires respectful usage (max 1 request per second, provide a user‑agent).
 */
interface GeocodingService {
    @GET("reverse")
    suspend fun reverseGeocode(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
        @Query("format") format: String = "jsonv2",
        @Query("addressdetails") addressDetails: Int = 1,
        @Query("accept-language") acceptLanguage: String = "en"
    ): GeocodingResponse
}

/**
 * Minimal representation of a Nominatim reverse‑geocoding response.
 */
data class GeocodingResponse(
    val display_name: String?,
    val address: Address?
)

data class Address(
    val road: String?,
    val suburb: String?,
    val city: String?,
    val county: String?,
    val state: String?,
    val postcode: String?,
    val country: String?,
    val country_code: String?
)