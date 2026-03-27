package com.benhic.appdar.feature.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock

const val ACTION_SCHEDULED_UPDATE = "com.benhic.appdar.ACTION_SCHEDULED_UPDATE"

private const val REQUEST_CODE = 9901

/**
 * Schedules or cancels a repeating AlarmManager alarm that triggers widget refresh.
 * Disabled automatically when Low Power Mode is enabled in settings.
 */
object WidgetUpdateScheduler {

    fun schedule(context: Context, intervalMinutes: Int = 5) {
        val intervalMs = intervalMinutes.coerceIn(1, 60) * 60 * 1000L
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + intervalMs,
            intervalMs,
            buildPendingIntent(context)
        )
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(buildPendingIntent(context))
    }

    private fun buildPendingIntent(context: Context): PendingIntent {
        // Use package-scoped broadcast (no component) so all widget variants
        // (Nano, Strip, Grid) registered with ACTION_SCHEDULED_UPDATE also receive it.
        val intent = Intent(ACTION_SCHEDULED_UPDATE).apply {
            setPackage(context.packageName)
        }
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
