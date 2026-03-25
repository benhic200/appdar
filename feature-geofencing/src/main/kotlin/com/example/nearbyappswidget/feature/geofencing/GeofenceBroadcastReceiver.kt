package com.example.nearbyappswidget.feature.geofencing

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.nearbyappswidget.feature.widget.NearbyAppsWidgetProvider
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: run {
            Log.e(TAG, "GeofencingEvent is null")
            return
        }
        if (geofencingEvent.hasError()) {
            Log.e(TAG, "Geofencing error: ${geofencingEvent.errorCode}")
            return
        }

        val triggeringGeofences = geofencingEvent.triggeringGeofences
        val transitionType = geofencingEvent.geofenceTransition

        when (transitionType) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                Log.d(TAG, "Geofence ENTER: ${triggeringGeofences?.size} geofences")
                triggeringGeofences?.forEach { geofence ->
                    val businessName = GeofenceCreator.businessNameFromRequestId(geofence.requestId)
                    Log.d(TAG, "Entered geofence for business: $businessName")
                }
                // Notify widget that nearby businesses have changed
                updateWidget(context)
            }
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                Log.d(TAG, "Geofence EXIT: ${triggeringGeofences?.size} geofences")
                // For Phase 1, we still update the widget (it will show all businesses)
                updateWidget(context)
            }
            Geofence.GEOFENCE_TRANSITION_DWELL -> {
                Log.d(TAG, "Geofence DWELL: ${triggeringGeofences?.size} geofences")
                // Not needed for Phase 1
            }
        }
    }

    private fun updateWidget(context: Context) {
        try {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val provider = ComponentName(context, NearbyAppsWidgetProvider::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(provider)
            Log.d(TAG, "Updating widget IDs: ${widgetIds.joinToString()}")
            for (widgetId in widgetIds) {
                NearbyAppsWidgetProvider.updateAppWidget(context, appWidgetManager, widgetId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update widget after geofence transition", e)
        }
    }

    companion object {
        private const val TAG = "GeofenceBroadcastReceiver"
    }
}