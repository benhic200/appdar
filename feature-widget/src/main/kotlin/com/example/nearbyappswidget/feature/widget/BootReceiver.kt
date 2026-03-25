package com.example.nearbyappswidget.feature.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Reschedules the 5-minute widget refresh alarm after device reboot.
 * Only schedules if at least one widget is active on the home screen.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val mgr = AppWidgetManager.getInstance(context)
        val provider = ComponentName(context, NearbyAppsWidgetProvider::class.java)
        if (mgr.getAppWidgetIds(provider).isNotEmpty()) {
            WidgetUpdateScheduler.schedule(context)
        }
    }
}
