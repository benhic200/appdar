package com.benhic.appdar.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.benhic.appdar.data.local.BranchLocation
import com.benhic.appdar.data.local.BranchLocationDao
import com.benhic.appdar.data.local.BusinessAppMapping
import com.benhic.appdar.data.local.BusinessAppMappingDao
import com.benhic.appdar.data.local.geocoding.CachedAddress
import com.benhic.appdar.data.local.geocoding.CachedAddressDao
import com.benhic.appdar.data.local.location.LocationHistory
import com.benhic.appdar.data.local.location.LocationHistoryDao
import android.database.sqlite.SQLiteException

@Database(
    entities = [
        BusinessAppMapping::class,
        CachedAddress::class,
        LocationHistory::class,
        BranchLocation::class
    ],
    version = 12,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun businessAppMappingDao(): BusinessAppMappingDao
    abstract fun cachedAddressDao(): CachedAddressDao
    abstract fun locationHistoryDao(): LocationHistoryDao
    abstract fun branchLocationDao(): BranchLocationDao

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

        /**
         * Migration from version 6 to 7.
         * Adds is_enabled (default 1/true) and is_custom (default 0/false) columns.
         * Existing seeded rows get is_enabled=1, is_custom=0 which is the correct default.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `business_app_mappings` ADD COLUMN `is_enabled` INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE `business_app_mappings` ADD COLUMN `is_custom` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Migration from version 7 to 8.
         * Adds osm_brand_tag column (nullable TEXT) for custom places that have been validated
         * against OpenStreetMap. NULL for all existing rows — built-in places use BRAND_TAGS in
         * NearbyBranchFinder; custom places without a brand tag use their stored lat/lon.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `business_app_mappings` ADD COLUMN `osm_brand_tag` TEXT")
            }
        }

        /**
         * Migration from version 8 to 9.
         * Nulls out the London/NY city-centre placeholder coordinates that were hard-coded in
         * InitialDataset for all non-custom entries. Real branch coordinates are now resolved
         * from OpenStreetMap at runtime (50 km search radius) and persisted back to the DB by
         * NearbyBranchFinder. Businesses without a nearby branch are excluded from the widget
         * rather than showing a misleading placeholder location.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    UPDATE business_app_mappings
                    SET latitude = NULL, longitude = NULL,
                        min_lat = NULL, max_lat = NULL,
                        min_lon = NULL, max_lon = NULL
                    WHERE is_custom = 0
                """.trimIndent())
            }
        }

        /**
         * Migration from version 9 to 10.
         * Adds branch_locations table to store ALL physical branches of each chain within
         * the UK (downloaded once from Overpass, TTL 30 days). Nearest-branch calculation
         * is now instant local maths — no Overpass call on location change.
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `branch_locations` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `brand_tag` TEXT NOT NULL,
                        `lat` REAL NOT NULL,
                        `lon` REAL NOT NULL
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_branch_locations_brand_tag` ON `branch_locations` (`brand_tag`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_branch_locations_lat_lon` ON `branch_locations` (`lat`, `lon`)")
            }
        }

        /**
         * Migration from version 10 to 11.
         * Adds region_hint column (nullable TEXT) to business_app_mappings.
         * NULL = visible in all regions (existing rows get NULL — backward compatible).
         * Non-null values are comma-separated region names, e.g. "UK" or "US,AU,NZ".
         * Used when the same brand has different apps per region.
         */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `business_app_mappings` ADD COLUMN `region_hint` TEXT")
            }
        }

        /**
         * Migration from version 11 to 12.
         * Purges all seeded (non-custom) entries so the seeder re-inserts them fresh.
         *
         * Why: Migration 6→7 set is_enabled=1 for every entry that existed at that time.
         * Entries predating the regionHint system survived with regionHint=NULL (global) and
         * isEnabled=true, so they appeared in the widget for every region. The seeder could not
         * reach entries whose package names changed or were removed between builds.
         *
         * Deleting seeded rows here is safe — user-added custom entries are preserved, and the
         * seeder re-inserts all built-in brands from InitialDataset with correct isEnabled values
         * (false for US/AU/NZ brands that are not yet verified).
         */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DELETE FROM `business_app_mappings` WHERE `is_custom` = 0")
            }
        }
    }
}