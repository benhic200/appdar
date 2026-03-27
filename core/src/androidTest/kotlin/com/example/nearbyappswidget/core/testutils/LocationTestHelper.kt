package com.benhic.appdar.core.testutils

import android.content.Context
import android.location.Location
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await

/**
 * Helper to simulate location changes in instrumented tests.
 * Requires the app to have the `android.permission.ACCESS_FINE_LOCATION` permission
 * and mock‑location enabled (via ADB or a test‑rule).
 */
class LocationTestHelper private constructor(
    private val context: Context,
    private val fusedClient: FusedLocationProviderClient
) {
    companion object {
        fun create(): LocationTestHelper {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            return LocationTestHelper(context, fusedClient)
        }
    }

    /**
     * Push a single mock location.
     * Caller must have previously enabled mock mode via `FusedLocationProviderClient.setMockMode(true)`.
     */
    suspend fun pushLocation(lat: Double, lng: Double, accuracy: Float = 10f) {
        val mockLocation = Location("test").apply {
            latitude = lat
            longitude = lng
            accuracy = accuracy
            time = System.currentTimeMillis()
        }
        fusedClient.setMockLocation(mockLocation).await()
    }

    /**
     * Simulate a geofence entry by moving from outside to inside the given radius.
     * Returns the location that would trigger the geofence.
     */
    fun simulateGeofenceEntry(
        centerLat: Double,
        centerLng: Double,
        radiusMeters: Float = 500f
    ): Location {
        // Move to a point just inside the radius
        val bearing = 0.0
        val distance = radiusMeters * 0.9 // inside the radius
        return computeOffset(centerLat, centerLng, distance.toDouble(), bearing)
    }

    /**
     * Simulate network loss by toggling airplane mode (requires ADB shell).
     * Only works if the test device grants the `WRITE_SECURE_SETTINGS` permission.
     */
    fun simulateNetworkLoss() {
        runBlocking {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("settings put global airplane_mode_on 1")
        }
    }

    /** Restore network (turn airplane mode off). */
    fun restoreNetwork() {
        runBlocking {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("settings put global airplane_mode_on 0")
        }
    }

    private fun computeOffset(
        lat: Double,
        lng: Double,
        distance: Double,
        bearing: Double
    ): Location {
        // Simplified spherical‑earth offset; for tests this approximation is sufficient.
        val rad = 6371000.0
        val latRad = Math.toRadians(lat)
        val lngRad = Math.toRadians(lng)
        val bearingRad = Math.toRadians(bearing)
        val angular = distance / rad
        val newLatRad = latRad + angular * Math.cos(bearingRad)
        val newLngRad = lngRad + angular * Math.sin(bearingRad) / Math.cos(latRad)
        return Location("test").apply {
            latitude = Math.toDegrees(newLatRad)
            longitude = Math.toDegrees(newLngRad)
            accuracy = 10f
            time = System.currentTimeMillis()
        }
    }
}