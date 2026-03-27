package com.benhic.appdar.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BusinessAppMappingDao {

    @Query("SELECT * FROM business_app_mappings")
    fun getAll(): Flow<List<BusinessAppMapping>>

    @Query("SELECT * FROM business_app_mappings WHERE business_name LIKE :businessName LIMIT 1")
    suspend fun getByBusinessName(businessName: String): BusinessAppMapping?

    @Query("SELECT * FROM business_app_mappings WHERE package_name = :packageName LIMIT 1")
    suspend fun getByPackageName(packageName: String): BusinessAppMapping?

    @Query("SELECT * FROM business_app_mappings WHERE latitude IS NOT NULL AND longitude IS NOT NULL")
    suspend fun getMappingsWithCoordinates(): List<BusinessAppMapping>

    /** Returns all enabled mappings that have a validated OSM brand tag (custom places using OSM lookup). */
    @Query("SELECT * FROM business_app_mappings WHERE osm_brand_tag IS NOT NULL AND is_enabled = 1")
    suspend fun getCustomOsmBrandMappings(): List<BusinessAppMapping>

    /**
     * Returns business‑app mappings whose bounding box intersects the given latitude/longitude,
     * ordered by distance (closest first). This query uses the pre‑computed bounding‑box columns
     * to quickly filter candidates before applying the Haversine formula.
     *
     * @param lat Latitude of the search center.
     * @param lon Longitude of the search center.
     * @param radiusMeters Maximum distance in meters (optional). If provided, the bounding box
     *                     is expanded by this radius to ensure all candidates within the radius
     *                     are included.
     */
    @Query("""
        SELECT * FROM business_app_mappings 
        WHERE min_lat IS NOT NULL 
          AND max_lat IS NOT NULL 
          AND min_lon IS NOT NULL 
          AND max_lon IS NOT NULL
          AND min_lat <= :lat + (:radiusMeters / 111319.9)
          AND max_lat >= :lat - (:radiusMeters / 111319.9)
          AND min_lon <= :lon + (:radiusMeters / 111319.9)
          AND max_lon >= :lon - (:radiusMeters / 111319.9)
        ORDER BY (
          (latitude - :lat) * (latitude - :lat) +
          (longitude - :lon) * (longitude - :lon)
        )
    """)
    suspend fun getMappingsNearLocation(
        lat: Double,
        lon: Double,
        radiusMeters: Int = 5000
    ): List<BusinessAppMapping>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mapping: BusinessAppMapping)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(mappings: List<BusinessAppMapping>)

    @Update
    suspend fun update(mapping: BusinessAppMapping)

    @Delete
    suspend fun delete(mapping: BusinessAppMapping)

    @Query("DELETE FROM business_app_mappings")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM business_app_mappings")
    suspend fun count(): Int
}