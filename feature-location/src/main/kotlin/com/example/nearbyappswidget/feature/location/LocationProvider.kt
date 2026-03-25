package com.example.nearbyappswidget.feature.location

import android.location.Location
import javax.inject.Inject

/**
 * Provides the device's current location.
 * Implementations may use GPS, network, or a cached location.
 */
interface LocationProvider {

    /**
     * Returns the current location, or `null` if location is unavailable.
     */
    suspend fun getCurrentLocation(): Location?
}

/**
 * A stub implementation that returns a fixed location (London) for testing.
 * In production, replace with a real location provider.
 */
class StubLocationProvider @Inject constructor() : LocationProvider {

    companion object {
        private const val LONDON_LAT = 51.5074
        private const val LONDON_LON = -0.1278
    }

    override suspend fun getCurrentLocation(): Location? {
        return Location("stub").apply {
            latitude = LONDON_LAT
            longitude = LONDON_LON
            accuracy = 50f // 50 meters accuracy
            time = System.currentTimeMillis()
        }
    }
}