package com.benhic.appdar.feature.location

import com.benhic.appdar.data.local.BusinessAppMapping
import com.benhic.appdar.data.local.settings.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Represents a business with its calculated distance from the user.
 */
data class BusinessWithDistance(
    val business: BusinessAppMapping,
    val distanceMeters: Double,
    val formattedDistance: String
)

/**
 * Sorts businesses by distance from the user's current location.
 */
@Singleton
class BusinessSorter @Inject constructor(
    private val distanceCalculator: DistanceCalculator
) {

    /**
     * Sorts a list of businesses by distance from the given location.
     *
     * Businesses without coordinates (null latitude/longitude) are placed at the end.
     *
     * @param businesses List of businesses to sort
     * @param currentLat User's current latitude (degrees)
     * @param currentLon User's current longitude (degrees)
     * @param preferences User preferences (for distance unit formatting)
     * @return Sorted list of [BusinessWithDistance]
     */
    fun sortBusinessesByDistance(
        businesses: List<BusinessAppMapping>,
        currentLat: Double?,
        currentLon: Double?,
        preferences: UserPreferences
    ): List<BusinessWithDistance> {
        if (currentLat == null || currentLon == null) {
            // No location available – return businesses in original order without distances
            return businesses.map { business ->
                BusinessWithDistance(
                    business = business,
                    distanceMeters = Double.NaN,
                    formattedDistance = "—"
                )
            }
        }

        val businessesWithDistances = businesses.map { business ->
            val distance = if (business.latitude != null && business.longitude != null) {
                distanceCalculator.calculateDistanceMeters(
                    currentLat, currentLon,
                    business.latitude!!, business.longitude!!
                )
            } else {
                Double.NaN
            }
            BusinessWithDistance(
                business = business,
                distanceMeters = distance,
                formattedDistance = if (distance.isNaN()) {
                    "—"
                } else {
                    distanceCalculator.formatDistance(distance, preferences.distanceUnit)
                }
            )
        }

        // Sort: businesses with valid distances first (ascending), then businesses without coordinates
        return businessesWithDistances.sortedWith(compareBy(
            { it.distanceMeters.isNaN() }, // false (has distance) before true (no distance)
            { it.distanceMeters }           // then by distance
        ))
    }

    /**
     * Creates a [Flow] that emits sorted businesses whenever location or preferences change.
     *
     * @param businessesFlow Flow of business lists
     * @param locationFlow Flow of current location (Pair<Double?, Double?> or null)
     * @param preferencesFlow Flow of user preferences
     * @return Flow of sorted [BusinessWithDistance] lists
     */
    fun sortedBusinessesFlow(
        businessesFlow: Flow<List<BusinessAppMapping>>,
        locationFlow: Flow<Pair<Double?, Double?>?>,
        preferencesFlow: Flow<UserPreferences>
    ): Flow<List<BusinessWithDistance>> {
        return combine(businessesFlow, locationFlow, preferencesFlow) { businesses, location, preferences ->
            val (currentLat, currentLon) = location ?: Pair(null, null)
            sortBusinessesByDistance(businesses, currentLat, currentLon, preferences)
        }
    }

    /**
     * Filters businesses within the search radius defined in preferences.
     */
    fun filterWithinRadius(
        businessesWithDistance: List<BusinessWithDistance>,
        radiusMeters: Int
    ): List<BusinessWithDistance> {
        return businessesWithDistance.filter { businessWithDistance ->
            !businessWithDistance.distanceMeters.isNaN() &&
                    businessWithDistance.distanceMeters <= radiusMeters
        }
    }
}