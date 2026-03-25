package com.example.nearbyappswidget.feature.geocoding

import android.location.Geocoder
import android.content.Context
import javax.inject.Inject

/**
 * Provides reverse‑geocoding (coordinates → human‑readable address).
 * Implementations may use Android's Geocoder or a network API.
 */
interface GeocodingProvider {

    /**
     * Returns a human‑readable address for the given coordinates,
     * or `null` if the address cannot be resolved.
     */
    suspend fun geocode(latitude: Double, longitude: Double): String?
}

/**
 * Stub implementation that returns a hard‑coded address for testing.
 * In production, replace with a real geocoder (Android Geocoder or network).
 */
class StubGeocodingProvider @Inject constructor() : GeocodingProvider {

    override suspend fun geocode(latitude: Double, longitude: Double): String? {
        // For Phase 2, return a dummy address near London.
        return "123 Test Street, London, UK"
    }
}