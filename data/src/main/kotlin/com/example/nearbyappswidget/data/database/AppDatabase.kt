package com.example.nearbyappswidget.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.nearbyappswidget.data.local.BusinessAppMapping
import com.example.nearbyappswidget.data.local.BusinessAppMappingDao
import com.example.nearbyappswidget.data.local.geocoding.CachedAddress
import com.example.nearbyappswidget.data.local.geocoding.CachedAddressDao
import com.example.nearbyappswidget.data.local.location.LocationHistory
import com.example.nearbyappswidget.data.local.location.LocationHistoryDao
import android.database.sqlite.SQLiteException

@Database(
    entities = [
        BusinessAppMapping::class,
        CachedAddress::class,
        LocationHistory::class
    ],
    version = 6,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun businessAppMappingDao(): BusinessAppMappingDao
    abstract fun cachedAddressDao(): CachedAddressDao
    abstract fun locationHistoryDao(): LocationHistoryDao

    companion object {
        /**
         * Migration from version 1 to 2.
         * Adds missing columns (app_name, category, latitude, longitude, geofence_radius, last_updated)
         * plus bounding‑box columns (min_lat, max_lat, min_lon, max_lon) and version column.
         * For existing rows, fills default values and computes approximate bounding box
         * using the geofence radius (default 200 m).
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Helper to add a column safely (ignore if already exists)
                fun addColumnIfNotExists(table: String, column: String, type: String) {
                    try {
                        database.execSQL("ALTER TABLE $table ADD COLUMN $column $type")
                    } catch (e: SQLiteException) {
                        // Column likely already exists; ignore
                    }
                }

                // Add missing columns (nullable or with defaults)
                addColumnIfNotExists("business_app_mappings", "app_name", "TEXT NOT NULL DEFAULT ''")
                addColumnIfNotExists("business_app_mappings", "category", "TEXT NOT NULL DEFAULT 'unknown'")
                addColumnIfNotExists("business_app_mappings", "latitude", "REAL")
                addColumnIfNotExists("business_app_mappings", "longitude", "REAL")
                addColumnIfNotExists("business_app_mappings", "geofence_radius", "INTEGER NOT NULL DEFAULT 200")
                addColumnIfNotExists("business_app_mappings", "last_updated", "INTEGER NOT NULL DEFAULT (strftime('%s','now'))")
                addColumnIfNotExists("business_app_mappings", "min_lat", "REAL")
                addColumnIfNotExists("business_app_mappings", "max_lat", "REAL")
                addColumnIfNotExists("business_app_mappings", "min_lon", "REAL")
                addColumnIfNotExists("business_app_mappings", "max_lon", "REAL")
                addColumnIfNotExists("business_app_mappings", "version", "INTEGER NOT NULL DEFAULT 1")

                // Create indices that may be missing (Room expects them per @Entity annotation)
                try {
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_business_app_mappings_business_name ON business_app_mappings (business_name)")
                } catch (e: SQLiteException) { /* ignore */ }
                try {
                    database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_business_app_mappings_package_name ON business_app_mappings (package_name)")
                } catch (e: SQLiteException) { /* ignore */ }
                try {
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_business_app_mappings_category ON business_app_mappings (category)")
                } catch (e: SQLiteException) { /* ignore */ }
                try {
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_business_app_mappings_bounding_box ON business_app_mappings (min_lat, max_lat, min_lon, max_lon)")
                } catch (e: SQLiteException) { /* ignore */ }

                // For rows that have latitude/longitude, compute bounding box
                // Radius in degrees ≈ radius_meters / 111319.9 (meters per degree at equator)
                database.execSQL("""
                    UPDATE business_app_mappings 
                    SET min_lat = latitude - (geofence_radius / 111319.9),
                        max_lat = latitude + (geofence_radius / 111319.9),
                        min_lon = longitude - (geofence_radius / 111319.9),
                        max_lon = longitude + (geofence_radius / 111319.9)
                    WHERE latitude IS NOT NULL AND longitude IS NOT NULL
                """.trimIndent())
            }
        }

        /**
         * Migration from version 2 to 3.
         * Adds tables for cached reverse‑geocoding addresses and location history.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create cached_addresses table
                database.execSQL("""
                    CREATE TABLE cached_addresses (
                        id TEXT PRIMARY KEY NOT NULL,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        display_name TEXT NOT NULL,
                        fetched_at INTEGER NOT NULL
                    )
                """.trimIndent())
                // Indices for cached_addresses
                database.execSQL("CREATE INDEX idx_cached_addresses_lat_lon ON cached_addresses (latitude, longitude)")
                database.execSQL("CREATE INDEX idx_cached_addresses_fetched_at ON cached_addresses (fetched_at)")

                // Create location_history table
                database.execSQL("""
                    CREATE TABLE location_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        accuracy REAL,
                        timestamp INTEGER NOT NULL
                    )
                """.trimIndent())
                // Index for location_history
                database.execSQL("CREATE INDEX idx_location_history_timestamp ON location_history (timestamp)")
            }
        }

        /**
         * Migration from version 3 to 4.
         * Adds raw_json column to cached_addresses table (nullable, for storing raw JSON response).
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE cached_addresses ADD COLUMN raw_json TEXT")
            }
        }

        /**
         * Migration from version 4 to 5.
         * Recreates business_app_mappings with the exact schema Room generates from the entity,
         * fixing any column DEFAULT mismatches introduced by earlier migrations (1→2).
         * Room validates schemas strictly — DEFAULT clauses must match the entity exactly.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Rename the old table
                database.execSQL("ALTER TABLE `business_app_mappings` RENAME TO `business_app_mappings_old`")

                // Create the table with the exact schema Room generates from the entity
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `business_app_mappings` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `business_name` TEXT NOT NULL,
                        `package_name` TEXT NOT NULL,
                        `app_name` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `latitude` REAL,
                        `longitude` REAL,
                        `geofence_radius` INTEGER NOT NULL,
                        `min_lat` REAL,
                        `max_lat` REAL,
                        `min_lon` REAL,
                        `max_lon` REAL,
                        `version` INTEGER NOT NULL,
                        `last_updated` INTEGER NOT NULL
                    )
                """.trimIndent())

                // Copy data, supplying fallback values for any missing NOT NULL columns
                database.execSQL("""
                    INSERT INTO `business_app_mappings`
                        (`id`, `business_name`, `package_name`, `app_name`, `category`,
                         `latitude`, `longitude`, `geofence_radius`,
                         `min_lat`, `max_lat`, `min_lon`, `max_lon`,
                         `version`, `last_updated`)
                    SELECT
                        `id`,
                        `business_name`,
                        `package_name`,
                        COALESCE(`app_name`, ''),
                        COALESCE(`category`, 'unknown'),
                        `latitude`,
                        `longitude`,
                        COALESCE(`geofence_radius`, 200),
                        `min_lat`,
                        `max_lat`,
                        `min_lon`,
                        `max_lon`,
                        COALESCE(`version`, 1),
                        COALESCE(`last_updated`, 0)
                    FROM `business_app_mappings_old`
                """.trimIndent())

                database.execSQL("DROP TABLE `business_app_mappings_old`")

                // Recreate all indices
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_business_app_mappings_business_name` ON `business_app_mappings` (`business_name`)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_business_app_mappings_package_name` ON `business_app_mappings` (`package_name`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_business_app_mappings_category` ON `business_app_mappings` (`category`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_business_app_mappings_min_lat_max_lat_min_lon_max_lon` ON `business_app_mappings` (`min_lat`, `max_lat`, `min_lon`, `max_lon`)")
            }
        }

        /**
         * Migration from version 5 to 6.
         * Fixes the bounding box index name created by MIGRATION_4_5 (was incorrectly named
         * index_business_app_mappings_bounding_box; Room expects
         * index_business_app_mappings_min_lat_max_lat_min_lon_max_lon).
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP INDEX IF EXISTS `index_business_app_mappings_bounding_box`")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_business_app_mappings_min_lat_max_lat_min_lon_max_lon` ON `business_app_mappings` (`min_lat`, `max_lat`, `min_lon`, `max_lon`)")
            }
        }
    }
}