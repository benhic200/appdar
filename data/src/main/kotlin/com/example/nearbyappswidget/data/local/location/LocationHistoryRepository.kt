package com.example.nearbyappswidget.data.local.location

import kotlinx.coroutines.flow.Flow

/**
 * Repository for storing and retrieving device location history.
 * Supports automatic pruning to limit storage and respect privacy.
 */
interface LocationHistoryRepository {

    /**
     * Records a location snapshot if location history is enabled.
     * The repository may decide to skip recording based on settings or throttling.
     *
     * @param latitude Location latitude
     * @param longitude Location longitude
     * @param accuracy Location accuracy in meters (nullable)
     * @param timestamp Timestamp (defaults to current time)
     */
    suspend fun recordLocation(
        latitude: Double,
        longitude: Double,
        accuracy: Float?,
        timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Returns the most recent location entries (newest first).
     */
    fun getHistory(): Flow<List<LocationHistory>>

    /**
     * Returns location entries within the specified time range (inclusive).
     */
    suspend fun getHistoryBetween(from: Long, to: Long): List<LocationHistory>

    /**
     * Returns the most recent location entry, if any.
     */
    suspend fun getLatest(): LocationHistory?

    /**
     * Deletes location entries older than the given timestamp.
     */
    suspend fun deleteOlderThan(olderThan: Long)

    /**
     * Deletes all location history entries.
     */
    suspend fun clear()

    /**
     * Returns the number of stored location entries.
     */
    suspend fun count(): Int

    /**
     * Ensures the location history table does not exceed the configured maximum size.
     * Call this periodically (e.g., after recording a new location) to automatically prune.
     */
    suspend fun prune()
}