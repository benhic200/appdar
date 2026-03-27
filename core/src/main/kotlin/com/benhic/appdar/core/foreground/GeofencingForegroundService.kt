package com.benhic.appdar.core.foreground

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.benhic.appdar.core.R

/**
 * Foreground service that must be running while geofencing is active on Android 10+.
 *
 * Required for background location access on API 29+. Provides a persistent notification
 * that informs the user geofencing is active.
 */
class GeofencingForegroundService : Service() {

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "geofencing_foreground"
        private const val NOTIFICATION_ID = 1001

        /**
         * Starts the foreground service if the device is Android 10+.
         */
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

        /**
         * Stops the foreground service.
         */
        fun stopIfNeeded(context: Context) {
            val intent = Intent(context, GeofencingForegroundService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.geofencing_foreground_channel_name),
                NotificationManager.IMPORTANCE_LOW
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
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()
    }
}