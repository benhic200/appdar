package com.benhic.appdar.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single physical branch of a business chain, as resolved from OpenStreetMap.
 *
 * All UK branches for every enabled brand are downloaded once (TTL 30 days) and
 * stored here. Nearest-branch calculation is then instant local maths — no network
 * call is needed when the user changes location.
 */
@Entity(
    tableName = "branch_locations",
    indices = [
        Index("brand_tag"),
        Index(value = ["lat", "lon"])
    ]
)
data class BranchLocation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "brand_tag") val brandTag: String,
    @ColumnInfo(name = "lat") val lat: Double,
    @ColumnInfo(name = "lon") val lon: Double
)
