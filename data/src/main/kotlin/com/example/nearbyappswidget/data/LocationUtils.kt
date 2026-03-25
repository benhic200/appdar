package com.example.nearbyappswidget.data

import kotlin.math.*

/**
 * Utilities for geographic calculations.
 */
object LocationUtils {
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    /**
     * Calculates the great‑circle distance between two points on the Earth
     * using the Haversine formula.
     * @return distance in meters
     */
    fun calculateDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    /**
     * Returns a rough bounding box (latitude and longitude deltas) that encloses
     * a circle of given radius around a point.
     * @param radiusMeters radius in meters
     * @return pair (deltaLat, deltaLon) in degrees
     */
    fun boundingBoxDeltas(
        latitude: Double,
        radiusMeters: Double
    ): Pair<Double, Double> {
        // Approximate 1 degree of latitude in meters
        val metersPerDegreeLat = 111_320.0
        val deltaLat = radiusMeters / metersPerDegreeLat
        // Longitudinal degrees vary with latitude
        val metersPerDegreeLon = metersPerDegreeLat * cos(Math.toRadians(latitude))
        val deltaLon = if (metersPerDegreeLon == 0.0) 0.0 else radiusMeters / metersPerDegreeLon
        return Pair(deltaLat, deltaLon)
    }

    /**
     * Checks whether a point (lat, lon) is inside a geofence defined by center and radius.
     */
    fun isInsideGeofence(
        pointLat: Double,
        pointLon: Double,
        centerLat: Double?,
        centerLon: Double?,
        radiusMeters: Int
    ): Boolean {
        if (centerLat == null || centerLon == null) return false
        return calculateDistance(pointLat, pointLon, centerLat, centerLon) <= radiusMeters
    }
}