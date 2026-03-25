package com.example.nearbyappswidget.data.local.geocoding

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cached reverse‑geocoding result (latitude/longitude → display address).
 * Caching reduces API calls and respects quotas.
 *
 * The primary key is a composite of latitude and longitude rounded to 5 decimal places
 * (≈1.1 m precision), which is sufficient for address caching.
 */
@Entity(
    tableName = "cached_addresses",
    indices = [
        Index(name = "idx_cached_addresses_lat_lon", value = ["latitude", "longitude"], unique = false),
        Index(name = "idx_cached_addresses_fetched_at", value = ["fetched_at"], unique = false)
    ]
)
data class CachedAddress(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "latitude")
    val latitude: Double,

    @ColumnInfo(name = "longitude")
    val longitude: Double,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "raw_json")
    val rawJson: String?,

    @ColumnInfo(name = "fetched_at")
    val fetchedAt: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * Creates a stable ID from latitude and longitude.
         * Rounds coordinates to 5 decimal places (≈1.1 m) to avoid excessive cache entries
         * for nearly identical locations.
         */
        fun makeId(latitude: Double, longitude: Double): String {
            val roundedLat = "%.5f".format(latitude)
            val roundedLon = "%.5f".format(longitude)
            return "${roundedLat}_${roundedLon}"
        }
    }
}