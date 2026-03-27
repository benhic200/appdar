package com.benhic.appdar.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.math.abs
import java.util.Date

/**
 * Entity representing a mapping between a business chain and its Android app.
 * Geofence coordinates (latitude, longitude) define the center point for the business chain.
 * The geofence radius (in meters) determines the proximity threshold.
 *
 * Bounding‑box columns (minLat, maxLat, minLon, maxLon) speed up spatial queries
 * by allowing SQLite to pre‑filter candidates before applying the Haversine formula.
 * The box is computed as latitude ± radiusInDegrees, longitude ± radiusInDegrees,
 * where radiusInDegrees = geofenceRadius / 111319.9 (meters per degree at equator).
 *
 * Version column supports optimistic concurrency control when syncing with a remote source.
 */
@Entity(
    tableName = "business_app_mappings",
    indices = [
        Index(name = "index_business_app_mappings_business_name", value = ["business_name"], unique = false),
        Index(name = "index_business_app_mappings_package_name", value = ["package_name"], unique = true),
        Index(name = "index_business_app_mappings_category", value = ["category"], unique = false),
        Index(name = "index_business_app_mappings_bounding_box", value = ["min_lat", "max_lat", "min_lon", "max_lon"], unique = false)
    ]
)
data class BusinessAppMapping(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "business_name")
    val businessName: String,

    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "app_name")
    val appName: String,

    @ColumnInfo(name = "category")
    val category: String,

    @ColumnInfo(name = "latitude")
    val latitude: Double? = null,

    @ColumnInfo(name = "longitude")
    val longitude: Double? = null,

    @ColumnInfo(name = "geofence_radius")
    val geofenceRadius: Int = 200, // meters

    @ColumnInfo(name = "min_lat")
    val minLat: Double? = null,

    @ColumnInfo(name = "max_lat")
    val maxLat: Double? = null,

    @ColumnInfo(name = "min_lon")
    val minLon: Double? = null,

    @ColumnInfo(name = "max_lon")
    val maxLon: Double? = null,

    @ColumnInfo(name = "version")
    val version: Int = 1,

    @ColumnInfo(name = "last_updated")
    val lastUpdated: Long = System.currentTimeMillis(),

    /** Whether this place is active in the widget. Seeded places can be toggled off without deletion. */
    @ColumnInfo(name = "is_enabled")
    val isEnabled: Boolean = true,

    /** True only for places added manually by the user — these show a delete button instead of a toggle. */
    @ColumnInfo(name = "is_custom")
    val isCustom: Boolean = false,

    /**
     * The canonical OSM brand tag for this custom place (e.g. "Starbucks", "McDonald's").
     * Non-null means NearbyBranchFinder will query OpenStreetMap for the nearest real branch,
     * just like built-in businesses. Null means the stored lat/lon is used as-is.
     */
    @ColumnInfo(name = "osm_brand_tag")
    val osmBrandTag: String? = null
)

/**
 * Approximate meters per degree at the equator (Earth's circumference / 360).
 * Used to convert geofence radius (meters) to degrees for bounding‑box calculation.
 */
private const val METERS_PER_DEGREE = 111319.9

/**
 * Computes the bounding‑box columns (minLat, maxLat, minLon, maxLon) based on
 * latitude, longitude, and geofence radius.
 *
 * This is a rough approximation that does not account for the Earth's curvature
 * or latitude‑dependent longitudinal distance. It is sufficient for pre‑filtering
 * SQLite queries; exact distance is later computed with the Haversine formula.
 *
 * @return A copy of this mapping with bounding‑box fields populated.
 * Returns `null` if latitude or longitude is `null`.
 */
fun BusinessAppMapping.withBoundingBox(): BusinessAppMapping? {
    val lat = latitude ?: return null
    val lon = longitude ?: return null
    val radiusDegrees = geofenceRadius / METERS_PER_DEGREE
    return copy(
        minLat = lat - radiusDegrees,
        maxLat = lat + radiusDegrees,
        minLon = lon - radiusDegrees,
        maxLon = lon + radiusDegrees
    )
}