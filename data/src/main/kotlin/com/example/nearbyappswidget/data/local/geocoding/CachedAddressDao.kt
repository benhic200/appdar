package com.example.nearbyappswidget.data.local.geocoding

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedAddressDao {

    /**
     * Inserts or replaces a cached address.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(address: CachedAddress)

    /**
     * Inserts or replaces multiple cached addresses.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(addresses: List<CachedAddress>)

    /**
     * Returns the cached address by its exact ID.
     */
    @Query("SELECT * FROM cached_addresses WHERE id = :id")
    suspend fun getById(id: String): CachedAddress?

    /**
     * Returns the cached address for the given latitude/longitude, if any.
     * Uses the same rounding as [CachedAddress.makeId] to find a nearby match.
     */
    @Transaction
    @Query("""
        SELECT * FROM cached_addresses 
        WHERE ABS(latitude - :lat) < 0.0001 
          AND ABS(longitude - :lon) < 0.0001
        LIMIT 1
    """)
    suspend fun getForLocation(lat: Double, lon: Double): CachedAddress?

    /**
     * Returns all cached addresses, ordered by most recently fetched.
     */
    @Query("SELECT * FROM cached_addresses ORDER BY fetched_at DESC")
    fun getAll(): Flow<List<CachedAddress>>

    /**
     * Deletes cached addresses older than the given timestamp.
     */
    @Query("DELETE FROM cached_addresses WHERE fetched_at < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long)

    /**
     * Deletes all cached addresses.
     */
    @Query("DELETE FROM cached_addresses")
    suspend fun deleteAll()

    /**
     * Returns the number of cached addresses.
     */
    @Query("SELECT COUNT(*) FROM cached_addresses")
    suspend fun count(): Int
}