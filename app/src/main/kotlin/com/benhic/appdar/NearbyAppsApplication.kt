package com.benhic.appdar

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.benhic.appdar.feature.geofencing.GeofenceCreator
import com.benhic.appdar.feature.geofencing.GeofenceManager
import com.benhic.appdar.core.foreground.GeofencingForegroundService
import com.benhic.appdar.feature.widget.NearbyAppsWidgetProvider
import com.benhic.appdar.feature.widget.WidgetUpdateScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application class for Nearby Apps Widget.
 * Enables Hilt dependency injection and starts geofencing.
 */
@HiltAndroidApp
class NearbyAppsApplication : Application() {

    @Inject
    lateinit var geofenceManager: GeofenceManager

    override fun onCreate() {
        super.onCreate()
        startGeofencingIfPermitted()
        ensureWidgetSchedulerRunning()
    }

    private fun startGeofencingIfPermitted() {
        // Check if coarse location permission is granted
        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasCoarseLocation) {
            Log.w(TAG, "Coarse location permission not granted; geofencing will not start.")
            return
        }

        // On Android 10+ (API 29+), background location permission is required for geofencing
        // when the app is in the background.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val hasBackgroundLocation = ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasBackgroundLocation) {
                Log.w(TAG, "Background location permission not granted; geofencing may not trigger when app is in background.")
                // Still start geofencing, but transitions may be delayed or suppressed.
            }
        }

        // Create test geofences and start geofencing
        val geofences = GeofenceCreator.createTestGeofences()
        Log.d(TAG, "Starting geofencing with ${geofences.size} test geofences")
        geofenceManager.startGeofencing(geofences)

        // Start foreground service for Android 10+ to improve geofence reliability
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForegroundServiceIfNeeded()
        }
    }

    private fun startForegroundServiceIfNeeded() {
        try {
            GeofencingForegroundService.startIfNeeded(this)
        } catch (e: Exception) {
            // ForegroundServiceStartNotAllowedException on Android 12+ when the app process
            // is started from the background (e.g. by a widget PendingIntent). Safe to ignore —
            // geofencing will start next time the app comes to the foreground.
            Log.w(TAG, "Could not start foreground service (background start restriction): ${e.message}")
        }
    }

    /**
     * If any Appdar widget is on the home screen, ensure the refresh loop is running.
     * Called every time the app process starts — covers app launches, reinstalls (via
     * ACTION_MY_PACKAGE_REPLACED in BootReceiver), and broadcast-receiver process restarts.
     */
    private fun ensureWidgetSchedulerRunning() {
        try {
            val mgr = AppWidgetManager.getInstance(this)
            val hasActiveWidgets = NearbyAppsWidgetProvider.ALL_WIDGET_CLASSES.any { cls ->
                mgr.getAppWidgetIds(ComponentName(this, cls)).isNotEmpty()
            }
            if (!hasActiveWidgets) return
            WidgetUpdateScheduler.triggerNow(this)
            WidgetUpdateScheduler.scheduleWatchdog(this)
            Log.d(TAG, "Widget scheduler started from Application.onCreate()")
        } catch (e: Exception) {
            Log.w(TAG, "Widget scheduler startup skipped: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "NearbyAppsApplication"
    }
}