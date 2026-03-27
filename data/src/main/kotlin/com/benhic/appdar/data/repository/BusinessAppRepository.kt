package com.benhic.appdar.data.repository

import com.benhic.appdar.data.local.BusinessAppMapping
import kotlinx.coroutines.flow.Flow

/**
 * Repository for business‑to‑app mappings.
 * Handles local caching, remote updates, and fallback logic.
 */
interface BusinessAppRepository {

    /** Ensure database is initialized (seeded if empty). Should be called before any queries. */
    suspend fun initialize()

    /** Clear all existing data and re‑seed with the initial dataset. */
    suspend fun reseed()

    // Local queries
    fun getAllMappings(): Flow<List<BusinessAppMapping>>
    suspend fun getMappingByBusinessName(businessName: String): BusinessAppMapping?
    suspend fun getMappingByPackageName(packageName: String): BusinessAppMapping?

    /**
     * Returns mappings whose geofence intersects the given location.
     * @param latitude latitude of user location
     * @param longitude longitude of user location
     * @param radiusMeters search radius in meters (default 500)
     * @return list of matching mappings, sorted by distance (closest first)
     */
    suspend fun getBusinessesNearLocation(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int = 500
    ): List<BusinessAppMapping>

    /**
     * Updates the local cache with fresh mappings from a remote source.
     * @param mappings new/updated mappings
     * @param clearExisting if true, replace all existing data; else merge
     */
    suspend fun updateLocalCache(mappings: List<BusinessAppMapping>, clearExisting: Boolean = false)

    /**
     * Fallback logic when no mapping is found locally.
     * Could query a remote API, search Play Store, or return a placeholder.
     * @param businessName business name to look up
     * @return mapping if found, null otherwise
     */
    suspend fun fallbackMapping(businessName: String): BusinessAppMapping?

    /**
     * Reports a missing or incorrect mapping for later correction.
     */
    suspend fun reportMappingIssue(businessName: String, packageName: String?, issue: String)

    /**
     * Returns the number of mappings currently stored.
     */
    suspend fun getMappingCount(): Int

    /**
     * Inserts a user-defined business mapping (with bounding box computed automatically).
     */
    suspend fun addCustomMapping(mapping: BusinessAppMapping)

    /**
     * Deletes a specific business mapping by its entity.
     */
    suspend fun deleteMapping(mapping: BusinessAppMapping)

    /**
     * Flips the isEnabled flag on a mapping (seeded places are toggled, not deleted).
     */
    suspend fun toggleEnabled(mapping: BusinessAppMapping)
}