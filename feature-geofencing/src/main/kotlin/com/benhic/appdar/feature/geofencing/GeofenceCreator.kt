package com.benhic.appdar.feature.geofencing

import com.benhic.appdar.data.local.BusinessAppMapping
import com.google.android.gms.location.Geofence

/**
 * Utility to convert [BusinessAppMapping] objects into [Geofence] instances
 * and to extract business names from geofence request IDs.
 */
object GeofenceCreator {

    /**
     * Creates a [Geofence] from a [BusinessAppMapping].
     * The request ID is set to the business name.
     */
    fun fromMapping(mapping: BusinessAppMapping): Geofence {
        require(mapping.latitude != null && mapping.longitude != null) {
            "Cannot create geofence for business ${mapping.businessName} without coordinates"
        }
        return Geofence.Builder()
            .setRequestId(mapping.businessName)
            .setCircularRegion(
                mapping.latitude!!,
                mapping.longitude!!,
                mapping.geofenceRadius.toFloat()
            )
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            // .setNotificationResponsiveness(5000) // 5 seconds
            .build()
    }

    /**
     * Extracts the business name from a geofence request ID.
     * For Phase 1, the request ID is the business name itself.
     */
    fun businessNameFromRequestId(requestId: String): String = requestId

    /**
     * Creates a list of geofences from a list of mappings (ignoring those without coordinates).
     */
    fun fromMappings(mappings: List<BusinessAppMapping>): List<Geofence> {
        return mappings
            .filter { it.latitude != null && it.longitude != null }
            .map { fromMapping(it) }
    }

    /**
     * Creates hard‑coded test geofences for the 10 UK businesses in our dataset.
     * Coordinates are dummy locations in central London (spread within ~1km radius).
     * Geofence radius is 500 m, expiration is NEVER_EXPIRE.
     */
    fun createTestGeofences(): List<Geofence> {
        // Central London reference point
        val baseLat = 51.5074
        val baseLon = -0.1278

        // Offsets (in degrees) to spread geofences (~100 m each)
        val offsets = listOf(
            Pair(0.0000, 0.0000),   // Tesco
            Pair(0.0010, 0.0010),   // Starbucks
            Pair(-0.0010, 0.0010),  // Boots
            Pair(0.0010, -0.0010),  // McDonald's
            Pair(-0.0010, -0.0010), // Greggs
            Pair(0.0020, 0.0000),   // Costa Coffee
            Pair(-0.0020, 0.0000),  // WHSmith
            Pair(0.0000, 0.0020),   // Waterstones
            Pair(0.0000, -0.0020),  // Pret A Manger
            Pair(0.0020, 0.0020)    // Subway
        )

        val businessNames = listOf(
            "Tesco",
            "Starbucks",
            "Boots",
            "McDonald's",
            "Greggs",
            "Costa Coffee",
            "WHSmith",
            "Waterstones",
            "Pret A Manger",
            "Subway"
        )

        require(businessNames.size == offsets.size) { "Business names and offsets count mismatch" }

        return businessNames.zip(offsets).map { (businessName, offset) ->
            val latitude = baseLat + offset.first
            val longitude = baseLon + offset.second

            Geofence.Builder()
                .setRequestId(businessName)
                .setCircularRegion(latitude, longitude, 500f) // 500 m radius
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                .setLoiteringDelay(5_000) // 5 seconds dwell time
                .build()
        }
    }
}