package com.example.nearbyappswidget.data.database

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.RoomDatabase
import androidx.room.RoomDatabase.Callback
import androidx.room.Room
import android.content.Context
import android.util.Log
import com.example.nearbyappswidget.data.local.BusinessAppMappingDao
import com.example.nearbyappswidget.data.local.InitialDataset
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
     * Seeds the database on first install, and inserts any new entries from InitialDataset
     * that are not yet present (identified by package name). Existing entries are untouched
     * so user toggles are preserved.
     */
    suspend fun seedIfNeeded(dao: BusinessAppMappingDao) {
        val count = dao.count()
        Log.d(TAG, "Database row count: $count")
        val allDataset = InitialDataset.getMappings()
        if (count == 0) {
            Log.d(TAG, "Seeding database with initial dataset (${allDataset.size} entries)")
            dao.insertAll(allDataset)
            Log.d(TAG, "Seeding complete")
        } else {
            // Insert any new entries added since the user first installed
            val newEntries = allDataset.filter { dao.getByPackageName(it.packageName) == null }
            if (newEntries.isNotEmpty()) {
                Log.d(TAG, "Inserting ${newEntries.size} new dataset entries")
                dao.insertAll(newEntries)
            } else {
                Log.d(TAG, "No new dataset entries to insert")
            }
        }
    }

    /**
     * Force re‑seed the database by clearing all existing data and inserting the initial dataset.
     */
    suspend fun forceReseed(dao: BusinessAppMappingDao) {
        Log.d(TAG, "Force reseeding database")
        dao.deleteAll()
        val mappings = InitialDataset.getMappings()
        Log.d(TAG, "Inserting ${mappings.size} mappings")
        dao.insertAll(mappings)
        Log.d(TAG, "Force reseed complete")
    }
}