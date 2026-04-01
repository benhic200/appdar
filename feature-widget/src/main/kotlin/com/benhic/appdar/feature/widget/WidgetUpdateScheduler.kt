package com.benhic.appdar.feature.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock

const val ACTION_SCHEDULED_UPDATE = "com.benhic.appdar.ACTION_SCHEDULED_UPDATE"

private const val REQUEST_CODE = 9901
private const val REQUEST_CODE_SCREEN_ON = 9902

/**
 * Schedules or cancels widget refresh alarms.
 *
 * Two alarm modes:
 *  - **Background (repeating):** [schedule] / [cancel] — uses [AlarmManager.setInexactRepeating]
 *    with a minimum of 60 s. Active while the screen is off or Screen-on Refresh is disabled.
 *  - **Screen-on (one-shot self-scheduling):** [scheduleScreenOnUpdate] / [cancelScreenOnUpdate]
 *    — uses [AlarmManager.set] with no minimum. The widget re-schedules itself after each update
 *    while [android.os.PowerManager.isInteractive] is true. Reverts to background mode when the
 *    screen goes off.
 */
object WidgetUpdateScheduler {

    /**
     * Schedules the background repeating alarm.
     *
     * @param intervalSeconds Desired interval in seconds.
     *   [AlarmManager.setInexactRepeating] enforces a minimum of 60 s — values below that are
     *   silently clamped here.
     */
    fun schedule(context: Context, intervalSeconds: Int = 300) {
        val clampedMs = intervalSeconds.coerceAtLeast(60) * 1000L
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + clampedMs,
            clampedMs,
            buildPendingIntent(context)
        )
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(buildPendingIntent(context))
    }

    /**
     * Schedules a single screen-on update after [intervalMs] milliseconds.
     * The widget receiver calls this again after each update to keep the loop running
     * while the screen stays on. No minimum — Android may batch delivery slightly but
     * will honour short intervals while the screen is interactive.
     */
    fun scheduleScreenOnUpdate(context: Context, intervalMs: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.set(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + intervalMs,
            buildScreenOnPendingIntent(context)
        )
    }

    fun cancelScreenOnUpdate(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(buildScreenOnPendingIntent(context))
    }

    private fun buildPendingIntent(context: Context): PendingIntent {
        val intent = Intent(ACTION_SCHEDULED_UPDATE).apply { setPackage(context.packageName) }
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildScreenOnPendingIntent(context: Context): PendingIntent {
        val intent = Intent(ACTION_SCHEDULED_UPDATE).apply { setPackage(context.packageName) }
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE_SCREEN_ON, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
