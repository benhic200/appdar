package com.benhic.appdar.data.local.location

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationHistoryDao {

    /**
     * Inserts a location history entry.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: LocationHistory)

    /**
     * Inserts multiple location history entries.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<LocationHistory>)

    /**
     * Returns the most recent location entries, newest first.
     */
    @Query("SELECT * FROM location_history ORDER BY timestamp DESC")
    fun getAll(): Flow<List<LocationHistory>>

    /**
     * Returns location entries within the specified time range.
     */
    @Query("SELECT * FROM location_history WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp DESC")
    suspend fun getBetween(from: Long, to: Long): List<LocationHistory>

    /**
     * Returns the most recent location entry, if any.
     */
    @Query("SELECT * FROM location_history ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(): LocationHistory?

    /**
     * Deletes location entries older than the given timestamp.
     * Call this periodically (e.g., daily) to keep the table size manageable.
     */
    @Query("DELETE FROM location_history WHERE timestamp < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long)

    /**
     * Deletes all location history entries.
     */
    @Query("DELETE FROM location_history")
    suspend fun deleteAll()

    /**
     * Returns the number of location history entries.
     */
    @Query("SELECT COUNT(*) FROM location_history")
    suspend fun count(): Int

    /**
     * Prunes the table to keep at most [maxEntries] most recent entries.
     * This transaction ensures we never store more than a reasonable amount of location data.
     */
    @Transaction
    suspend fun pruneToMaxEntries(maxEntries: Int = 1000) {
        val total = count()
        if (total > maxEntries) {
            // Find the timestamp of the entry at position maxEntries when sorted ascending
            val cutoff = getNthTimestampAsc(maxEntries)
            deleteOlderThan(cutoff)
        }
    }

    /**
     * Returns the timestamp of the N‑th oldest entry (1‑based).
     * Used by [pruneToMaxEntries] to find the cutoff.
     */
    @Query("""
        SELECT timestamp FROM location_history 
        ORDER BY timestamp ASC 
        LIMIT 1 OFFSET :n
    """)
    suspend fun getNthTimestampAsc(n: Int): Long
}