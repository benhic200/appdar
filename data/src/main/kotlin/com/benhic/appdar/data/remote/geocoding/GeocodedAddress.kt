package com.benhic.appdar.data.remote.geocoding

/**
 * Domain representation of a geocoded address.
 */
data class GeocodedAddress(
    val displayName: String,
    val road: String?,
    val suburb: String?,
    val city: String?,
    val county: String?,
    val state: String?,
    val postcode: String?,
    val country: String?,
    val countryCode: String?,
    val latitude: Double,
    val longitude: Double
)