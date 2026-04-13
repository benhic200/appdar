package com.benhic.appdar.feature.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Restarts the widget refresh loop after device reboot or app update.
 * Only acts if at least one widget is active on the home screen.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                val mgr = AppWidgetManager.getInstance(context)
                val provider = ComponentName(context, NearbyAppsWidgetProvider::class.java)
                if (mgr.getAppWidgetIds(provider).isNotEmpty()) {
                    WidgetUpdateScheduler.schedule(context)
                    WidgetUpdateScheduler.scheduleWatchdog(context)
                }
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                // App was updated/reinstalled — restart the loop immediately without
                // requiring any user interaction.
                val mgr = AppWidgetManager.getInstance(context)
                val hasWidgets = NearbyAppsWidgetProvider.ALL_WIDGET_CLASSES.any { cls ->
                    mgr.getAppWidgetIds(ComponentName(context, cls)).isNotEmpty()
                }
                if (hasWidgets) {
                    WidgetUpdateScheduler.triggerNow(context)
                    WidgetUpdateScheduler.scheduleWatchdog(context)
                }
            }
        }
    }
}
