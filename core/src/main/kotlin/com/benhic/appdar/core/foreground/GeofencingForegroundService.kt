package com.benhic.appdar.core.foreground

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.benhic.appdar.core.R

private const val TAG = "GeofencingFgService"
private const val ACTION_SCHEDULED_UPDATE = "com.benhic.appdar.ACTION_SCHEDULED_UPDATE"
/** Minimum distance in metres the user must move before a background refresh fires. */
private const val LOCATION_CHANGE_THRESHOLD_M = 100f
/** Minimum time between location-triggered refreshes (10 minutes). */
private const val LOCATION_MIN_INTERVAL_MS = 5 * 60 * 1000L

class GeofencingForegroundService : Service() {

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "geofencing_foreground_v2"
        private const val NOTIFICATION_ID = 1001

        fun startIfNeeded(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val intent = Intent(context, GeofencingForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stopIfNeeded(context: Context) {
            val intent = Intent(context, GeofencingForegroundService::class.java)
            context.stopService(intent)
        }
    }

    private var locationManager: LocationManager? = null
    private var lastKnownLocation: Location? = null
    private var lastUnlockUpdateMs = 0L

    // Fires when the user unlocks the screen — triggers an immediate widget refresh
    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_USER_PRESENT) return
            val now = System.currentTimeMillis()
            if (now - lastUnlockUpdateMs < 60_000L) return  // max once per minute
            lastUnlockUpdateMs = now
            Log.d(TAG, "Screen unlocked — triggering widget refresh")
            sendBroadcast(Intent(ACTION_SCHEDULED_UPDATE).apply { setPackage(packageName) })
        }
    }

    private val locationListener = LocationListener { location ->
        val last = lastKnownLocation
        if (last == null || last.distanceTo(location) >= LOCATION_CHANGE_THRESHOLD_M) {
            Log.d(TAG, "Significant location change detected (${last?.distanceTo(location)?.toInt() ?: 0}m) — triggering widget refresh")
            lastKnownLocation = location
            sendBroadcast(
                Intent(ACTION_SCHEDULED_UPDATE).apply { setPackage(packageName) }
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        startLocationMonitoring()
        registerReceiver(unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))
        return START_STICKY
    }

    override fun onDestroy() {
        locationManager?.removeUpdates(locationListener)
        try { unregisterReceiver(unlockReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startLocationMonitoring() {
        val hasPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            Log.w(TAG, "Location permission not granted — skipping location monitoring")
            return
        }

        locationManager = getSystemService(LocationManager::class.java)
        try {
            // PASSIVE_PROVIDER piggybacks on other apps' location requests — zero extra battery drain
            locationManager?.requestLocationUpdates(
                LocationManager.PASSIVE_PROVIDER,
                LOCATION_MIN_INTERVAL_MS,
                LOCATION_CHANGE_THRESHOLD_M,
                locationListener,
                Looper.getMainLooper()
            )
            Log.d(TAG, "Location monitoring started (passive, ${LOCATION_CHANGE_THRESHOLD_M.toInt()}m threshold)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start location monitoring", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.geofencing_foreground_channel_name),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = getString(R.string.geofencing_foreground_channel_description)
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.geofencing_notification_title))
            .setContentText(getString(R.string.geofencing_notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}
