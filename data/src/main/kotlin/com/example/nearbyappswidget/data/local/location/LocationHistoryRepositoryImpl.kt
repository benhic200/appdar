package com.example.nearbyappswidget.data.local.location

import android.util.Log
import com.example.nearbyappswidget.data.local.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real implementation of [LocationHistoryRepository] that respects user preferences
 * and automatically prunes old entries.
 */
@Singleton
class LocationHistoryRepositoryImpl @Inject constructor(
    private val locationHistoryDao: LocationHistoryDao,
    private val settingsRepository: SettingsRepository
) : LocationHistoryRepository {

    companion object {
        private const val TAG = "LocationHistoryRepo"
        // Keep at most 1000 location entries (roughly 2 weeks of hourly updates)
        private const val MAX_ENTRIES = 1000
        // Minimum time between recordings (milliseconds) to avoid flooding
        private const val MIN_RECORDING_INTERVAL_MS = 60_000L // 1 minute
    }

    private var lastRecordingTimestamp: Long = 0L

    override suspend fun recordLocation(
        latitude: Double,
        longitude: Double,
        accuracy: Float?,
        timestamp: Long
    ) {
        // Check if location history is enabled
        val preferences = settingsRepository.getCurrentPreferences()
        if (!preferences.enableLocationHistory) {
            Log.d(TAG, "Location history is disabled, skipping recording")
            return
        }

        // Throttle recordings to avoid excessive writes
        val now = System.currentTimeMillis()
        if (now - lastRecordingTimestamp < MIN_RECORDING_INTERVAL_MS) {
            Log.d(TAG, "Skipping recording due to throttling")
            return
        }

        try {
            val entry = LocationHistory(
                latitude = latitude,
                longitude = longitude,
                accuracy = accuracy,
                timestamp = timestamp
            )
            locationHistoryDao.insert(entry)
            lastRecordingTimestamp = now
            Log.d(TAG, "Recorded location (${latitude}, ${longitude}) accuracy=$accuracy")

            // Prune if needed
            prune()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record location", e)
        }
    }

    override fun getHistory(): Flow<List<LocationHistory>> {
        return locationHistoryDao.getAll()
    }

    override suspend fun getHistoryBetween(from: Long, to: Long): List<LocationHistory> {
        return locationHistoryDao.getBetween(from, to)
    }

    override suspend fun getLatest(): LocationHistory? {
        return locationHistoryDao.getLatest()
    }

    override suspend fun deleteOlderThan(olderThan: Long) {
        locationHistoryDao.deleteOlderThan(olderThan)
    }

    override suspend fun clear() {
        locationHistoryDao.deleteAll()
    }

    override suspend fun count(): Int {
        return locationHistoryDao.count()
    }

    override suspend fun prune() {
        locationHistoryDao.pruneToMaxEntries(MAX_ENTRIES)
    }
}