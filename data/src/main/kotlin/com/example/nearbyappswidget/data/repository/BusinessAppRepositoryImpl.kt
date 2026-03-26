package com.example.nearbyappswidget.data.repository

import android.util.Log
import com.example.nearbyappswidget.data.local.BusinessAppMapping
import com.example.nearbyappswidget.data.local.BusinessAppMappingDao
import com.example.nearbyappswidget.data.local.withBoundingBox
import com.example.nearbyappswidget.data.database.DatabaseInitializer
import com.example.nearbyappswidget.core.location.LocationUtils
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class BusinessAppRepositoryImpl @Inject constructor(
    private val dao: BusinessAppMappingDao
) : BusinessAppRepository {

    private val TAG = "BusinessAppRepository"

    private var isInitialized = false

    private suspend fun ensureInitialized() {
        if (!isInitialized) {
            Log.d(TAG, "Initializing repository, seeding database if needed")
            DatabaseInitializer.seedIfNeeded(dao)
            isInitialized = true
            Log.d(TAG, "Repository initialized")
        }
    }

    /**
     * Ensures that each mapping has its bounding‑box columns populated.
     * If a mapping already has a bounding box, it is kept unchanged.
     */
    private fun computeBoundingBoxForMappings(mappings: List<BusinessAppMapping>): List<BusinessAppMapping> =
        mappings.map { mapping ->
            // If bounding box is already present, keep it
            if (mapping.minLat != null && mapping.maxLat != null &&
                mapping.minLon != null && mapping.maxLon != null) {
                mapping
            } else {
                // Compute bounding box using the mapping's latitude/longitude and geofence radius
                mapping.withBoundingBox() ?: mapping
            }
        }

    override suspend fun initialize() {
        ensureInitialized()
    }

    override suspend fun reseed() {
        DatabaseInitializer.forceReseed(dao)
        isInitialized = true
    }

    override fun getAllMappings(): Flow<List<BusinessAppMapping>> = dao.getAll()

    override suspend fun getMappingByBusinessName(businessName: String): BusinessAppMapping? {
        ensureInitialized()
        return dao.getByBusinessName(businessName)
    }

    override suspend fun getMappingByPackageName(packageName: String): BusinessAppMapping? {
        ensureInitialized()
        return dao.getByPackageName(packageName)
    }

    override suspend fun getBusinessesNearLocation(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int
    ): List<BusinessAppMapping> {
        ensureInitialized()
        // Use bounding‑box pre‑filter to drastically reduce candidate count
        val candidates = dao.getMappingsNearLocation(latitude, longitude, radiusMeters)
        // Exact distance filter using Haversine formula
        return candidates.filter { mapping ->
            LocationUtils.isInsideGeofence(
                latitude,
                longitude,
                mapping.latitude,
                mapping.longitude,
                radiusMeters  // Use search radius, not the mapping's geofence radius
            )
        }.sortedBy { mapping ->
            mapping.latitude?.let { lat ->
                mapping.longitude?.let { lon ->
                    LocationUtils.calculateDistance(latitude, longitude, lat, lon)
                }
            } ?: Double.MAX_VALUE
        }
    }

    override suspend fun updateLocalCache(
        mappings: List<BusinessAppMapping>,
        clearExisting: Boolean
    ) {
        ensureInitialized()
        if (clearExisting) {
            dao.deleteAll()
        }
        // Ensure bounding‑box columns are populated before insertion
        val mappingsWithBoundingBox = computeBoundingBoxForMappings(mappings)
        dao.insertAll(mappingsWithBoundingBox)
    }

    override suspend fun fallbackMapping(businessName: String): BusinessAppMapping? {
        ensureInitialized()
        // In a real implementation, this would:
        // 1. Query a remote API for mapping
        // 2. Search Play Store via unofficial API (cautious of ToS)
        // 3. Return null if no result
        // For now, return null (no fallback).
        return null
    }

    override suspend fun reportMappingIssue(
        businessName: String,
        packageName: String?,
        issue: String
    ) {
        ensureInitialized()
        // Log the issue locally; in a real app, send to backend for review.
        // For now, just a stub.
    }

    override suspend fun getMappingCount(): Int {
        ensureInitialized()
        return dao.count()
    }

    override suspend fun addCustomMapping(mapping: BusinessAppMapping) {
        val withBox = (mapping.withBoundingBox() ?: mapping).copy(isCustom = true)
        dao.insert(withBox)
    }

    override suspend fun deleteMapping(mapping: BusinessAppMapping) {
        dao.delete(mapping)
    }

    override suspend fun toggleEnabled(mapping: BusinessAppMapping) {
        dao.update(mapping.copy(isEnabled = !mapping.isEnabled))
    }
}