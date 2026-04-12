package com.benhic.appdar.data.database

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.RoomDatabase
import androidx.room.RoomDatabase.Callback
import androidx.room.Room
import android.content.Context
import android.util.Log
import com.benhic.appdar.data.local.BusinessAppMappingDao
import com.benhic.appdar.data.local.InitialDataset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Utility to seed the Room database with initial data.
 */
object DatabaseInitializer {

    private const val TAG = "DatabaseInitializer"

    /**
     * Creates a RoomDatabase.Callback that seeds the database with the initial dataset
     * when the database is first created.
     */
    fun getSeedCallback(): Callback {
        return object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                Log.d(TAG, "Database created")
            }
        }
    }

    /**
     * Seeds the database on first install, and keeps existing entries in sync with
     * [InitialDataset] on subsequent launches.
     *
     * Matching is done by **package name** (the unique key) rather than business name.
     * This correctly handles renames (e.g. "McDonald's (US)" → "McDonald's" with a
     * regionHint) where multiple entries now share the same business name.
     *
     * Rules (per dataset entry):
     * - Not in DB → insert
     * - In DB, isCustom → promote to seeded (preserve isEnabled)
     * - In DB, seeded → update businessName / appName / category / regionHint if changed
     *   while preserving the user's isEnabled toggle
     * - Dataset marks isEnabled=false and DB has isEnabled=true → disable for existing users
     */
    suspend fun seedIfNeeded(dao: BusinessAppMappingDao) {
        val count = dao.count()
        Log.d(TAG, "Database row count: $count")
        val allDataset = InitialDataset.getMappings()
        if (count == 0) {
            Log.d(TAG, "Seeding database with initial dataset (${allDataset.size} entries)")
            dao.insertAll(allDataset)
            Log.d(TAG, "Seeding complete")
            return
        }

        var insertedCount = 0; var updatedCount = 0
        for (datasetMapping in allDataset) {
            val existing = dao.getByPackageName(datasetMapping.packageName)

            if (existing == null) {
                dao.insert(datasetMapping)
                insertedCount++
                Log.d(TAG, "Inserted new entry: ${datasetMapping.businessName}")
                continue
            }

            if (existing.isCustom) {
                // Promote: user-added entry now in the official dataset
                dao.update(existing.copy(
                    isCustom = false,
                    businessName = datasetMapping.businessName,
                    appName = datasetMapping.appName,
                    category = datasetMapping.category,
                    regionHint = datasetMapping.regionHint
                ))
                updatedCount++
                Log.d(TAG, "Promoted custom entry to seeded: ${datasetMapping.businessName}")
                continue
            }

            // Sync metadata that may have changed in the dataset (e.g. rename, new regionHint)
            // while preserving the user's isEnabled toggle — unless the dataset now marks this
            // entry as disabled (e.g. a brand reclassified as region-specific).
            //
            // Important: both checks are combined into a single dao.update() call to avoid the
            // double-update bug where a second existing.copy() would revert metadata changes made
            // by the first update.
            val metaChanged = existing.businessName != datasetMapping.businessName ||
                existing.appName != datasetMapping.appName ||
                existing.category != datasetMapping.category ||
                existing.regionHint != datasetMapping.regionHint
            val needsDisable = !datasetMapping.isEnabled && existing.isEnabled && !existing.isCustom
            if (metaChanged || needsDisable) {
                dao.update(existing.copy(
                    businessName = datasetMapping.businessName,
                    appName = datasetMapping.appName,
                    category = datasetMapping.category,
                    regionHint = datasetMapping.regionHint,
                    isEnabled = if (needsDisable) false else existing.isEnabled
                ))
                updatedCount++
                if (metaChanged) Log.d(TAG, "Updated metadata for ${datasetMapping.businessName}")
                if (needsDisable) Log.d(TAG, "Disabled region-specific entry: ${datasetMapping.businessName}")
            }
        }
        if (insertedCount > 0 || updatedCount > 0) {
            Log.d(TAG, "Seed sync: inserted=$insertedCount updated=$updatedCount")
        }
    }

}