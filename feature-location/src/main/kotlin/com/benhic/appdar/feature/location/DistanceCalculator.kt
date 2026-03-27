package com.benhic.appdar.feature.location

import android.location.Location
import com.benhic.appdar.data.local.settings.DistanceUnit
import com.benhic.appdar.data.local.settings.UserPreferences
import javax.inject.Inject
import javax.inject.Singleton
import java.lang.Math
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Calculates distances between geographic coordinates and formats them for display.
 *
 * Uses the Haversine formula for great-circle distance on a sphere (Earth).
 */
@Singleton
class DistanceCalculator @Inject constructor() {

    companion object {
        private const val EARTH_RADIUS_METERS: Double = 6_371_000.0 // Earth's radius in meters
        private const val METERS_TO_KILOMETERS: Double = 0.001
        private const val METERS_TO_MILES: Double = 0.000621371
        private const val METERS_TO_FEET: Double = 3.28084
    }

    /**
     * Calculates the great-circle distance between two points in meters.
     *
     * @param lat1 Latitude of first point (degrees)
     * @param lon1 Longitude of first point (degrees)
     * @param lat2 Latitude of second point (degrees)
     * @param lon2 Longitude of second point (degrees)
     * @return Distance in meters
     */
    fun calculateDistanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val lat1Rad = Math.toRadians(lat1)
        val lon1Rad = Math.toRadians(lon1)
        val lat2Rad = Math.toRadians(lat2)
        val lon2Rad = Math.toRadians(lon2)

        val dLat = lat2Rad - lat1Rad
        val dLon = lon2Rad - lon1Rad

        val a = Math.pow(Math.sin(dLat / 2.0), 2.0) + Math.cos(lat1Rad) * Math.cos(lat2Rad) * Math.pow(Math.sin(dLon / 2.0), 2.0)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))

        return EARTH_RADIUS_METERS * c
    }

    /**
     * Calculates distance between two [Location] objects in meters.
     */
    fun calculateDistanceMeters(location1: Location, location2: Location): Double {
        return calculateDistanceMeters(
            location1.latitude,
            location1.longitude,
            location2.latitude,
            location2.longitude
        )
    }

    /**
     * Converts meters to the specified unit.
     */
    fun convertMetersToUnit(meters: Double, unit: DistanceUnit): Double {
        return when (unit) {
            DistanceUnit.METERS -> meters
            DistanceUnit.KILOMETERS -> meters * METERS_TO_KILOMETERS
            DistanceUnit.MILES -> meters * METERS_TO_MILES
            DistanceUnit.FEET -> meters * METERS_TO_FEET
        }
    }

    /**
     * Formats a distance for display, including unit abbreviation.
     *
     * Example: "150 m", "0.5 km", "1.2 mi"
     */
    fun formatDistance(meters: Double, unit: DistanceUnit): String {
        val value = convertMetersToUnit(meters, unit)
        val abbreviation = when (unit) {
            DistanceUnit.METERS -> "m"
            DistanceUnit.KILOMETERS -> "km"
            DistanceUnit.MILES -> "mi"
            DistanceUnit.FEET -> "ft"
        }

        // Format with appropriate precision
        return when {
            value < 0.01 -> "< 0.01 $abbreviation"
            value < 10 -> String.format("%.1f $abbreviation", value)
            value < 1000 -> String.format("%.0f $abbreviation", value)
            else -> String.format("%.1f $abbreviation", value)
        }
    }

    /**
     * Formats a distance using user preferences (unit from settings).
     */
    fun formatDistanceWithPreferences(meters: Double, preferences: UserPreferences): String {
        return formatDistance(meters, preferences.distanceUnit)
    }
}

