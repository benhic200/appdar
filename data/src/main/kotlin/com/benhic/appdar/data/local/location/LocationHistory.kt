package com.benhic.appdar.data.local.location

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Record of device location used for distance‑trend analysis and debugging.
 * Entries are automatically pruned after 7 days (see [LocationHistoryDao]).
 *
 * Location history is stored only when the user opts in via [UserPreferences.enableLocationHistory].
 */
@Entity(
    tableName = "location_history",
    indices = [
        Index(name = "idx_location_history_timestamp", value = ["timestamp"], unique = false)
    ]
)
data class LocationHistory(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "latitude")
    val latitude: Double,

    @ColumnInfo(name = "longitude")
    val longitude: Double,

    @ColumnInfo(name = "accuracy")
    val accuracy: Float?,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis()
)