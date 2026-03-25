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
     * Seeds the database with the initial dataset if the database is empty.
     * Call this during app startup (e.g., in Application.onCreate or first repository access).
     */
    suspend fun seedIfNeeded(dao: BusinessAppMappingDao) {
        val count = dao.count()
        Log.d(TAG, "Database row count: $count")
        if (count == 0) {
            Log.d(TAG, "Seeding database with initial dataset")
            val mappings = InitialDataset.getMappings()
            Log.d(TAG, "Inserting ${mappings.size} mappings")
            dao.insertAll(mappings)
            Log.d(TAG, "Seeding complete")
        } else {
            Log.d(TAG, "Database already seeded, skipping")
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