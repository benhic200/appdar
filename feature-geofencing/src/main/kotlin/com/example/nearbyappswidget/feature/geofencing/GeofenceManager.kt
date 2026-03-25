package com.example.nearbyappswidget.feature.geofencing

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.nearbyappswidget.data.repository.BusinessAppRepository
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeofenceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: BusinessAppRepository
) {
    private val geofencingClient: GeofencingClient by lazy {
        LocationServices.getGeofencingClient(context)
    }

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    fun startGeofencing(geofences: List<Geofence>) {
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(geofences)
            .build()

        geofencingClient.addGeofences(request, geofencePendingIntent).run {
            addOnSuccessListener {
                Log.d(TAG, "Registered ${geofences.size} geofences successfully")
            }
            addOnFailureListener { exception ->
                Log.e(TAG, "Failed to add geofences", exception)
            }
        }
    }

    fun stopGeofencing() {
        geofencingClient.removeGeofences(geofencePendingIntent).run {
            addOnSuccessListener {
                Log.d(TAG, "Geofences removed successfully")
            }
            addOnFailureListener { exception ->
                Log.e(TAG, "Failed to remove geofences", exception)
            }
        }
    }

    /**
     * Loads all business mappings from the repository and registers geofences for those that have coordinates.
     * Call this after location permission is granted.
     */
    fun startGeofencingForAllBusinesses() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val mappings = repository.getAllMappings().first()
                val geofences = GeofenceCreator.fromMappings(mappings)
                if (geofences.isNotEmpty()) {
                    Log.d(TAG, "Attempting to register ${geofences.size} geofences")
                    startGeofencing(geofences)
                } else {
                    Log.w(TAG, "No geofences to register (missing coordinates?)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load mappings for geofencing", e)
            }
        }
    }

    companion object {
        private const val TAG = "GeofenceManager"
    }
}