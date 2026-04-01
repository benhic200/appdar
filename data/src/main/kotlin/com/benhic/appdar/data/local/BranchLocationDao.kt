package com.benhic.appdar.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BranchLocationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(branches: List<BranchLocation>)

    @Query("DELETE FROM branch_locations")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM branch_locations")
    suspend fun count(): Int

    @Query("SELECT * FROM branch_locations")
    suspend fun getAll(): List<BranchLocation>

    @Query("SELECT * FROM branch_locations WHERE brand_tag = :brandTag")
    suspend fun getByBrand(brandTag: String): List<BranchLocation>

    @Query("DELETE FROM branch_locations WHERE lat BETWEEN :minLat AND :maxLat AND lon BETWEEN :minLon AND :maxLon")
    suspend fun deleteByBbox(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double)
}
