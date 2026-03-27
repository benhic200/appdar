package com.benhic.appdar.core.location

object LocationUtils {
    /**
     * Haversine formula to calculate distance between two points in meters.
     */
    fun calculateDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val earthRadius = 6371000.0 // meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return earthRadius * c
    }

    /**
     * Returns true if the given point (lat, lon) is within [radiusMeters] of the geofence center.
     */
    fun isInsideGeofence(
        userLat: Double,
        userLon: Double,
        centerLat: Double?,
        centerLon: Double?,
        radiusMeters: Int?
    ): Boolean {
        if (centerLat == null || centerLon == null || radiusMeters == null) return false
        val distance = calculateDistance(userLat, userLon, centerLat, centerLon)
        return distance <= radiusMeters
    }
}