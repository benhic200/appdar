package com.benhic.appdar.feature.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.benhic.appdar.feature.widget.di.WidgetEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Listens for newly installed apps and automatically enables the matching
 * Places entry if one exists in the database.
 *
 * Only fires for fresh installs — app updates (EXTRA_REPLACING = true) are ignored
 * since the mapping was already enabled when the app was first installed.
 */
class PackageInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_ADDED) return
        if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return

        val packageName = intent.data?.schemeSpecificPart ?: return
        Log.d(TAG, "Package installed: $packageName")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val ep = EntryPointAccessors.fromApplication(
                    context.applicationContext, WidgetEntryPoint::class.java
                )
                val repo = ep.businessAppRepository()
                repo.initialize()

                val mapping = repo.getMappingByPackageName(packageName)
                if (mapping != null && !mapping.isEnabled) {
                    repo.toggleEnabled(mapping)
                    Log.d(TAG, "Auto-enabled place: ${mapping.businessName} ($packageName)")
                    refreshAllWidgets(context)
                } else if (mapping != null) {
                    Log.d(TAG, "Package $packageName found but already enabled")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling package install for $packageName", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun refreshAllWidgets(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        val providers = listOf(
            NearbyAppsWidgetProvider::class.java,
            NearbyAppsWidgetProviderNano::class.java,
            NearbyAppsWidgetProviderStrip::class.java,
            NearbyAppsWidgetProviderGrid::class.java,
            NearbyAppsWidgetProviderNarrow::class.java
        )
        for (cls in providers) {
            val ids = mgr.getAppWidgetIds(ComponentName(context, cls))
            if (ids.isNotEmpty()) {
                context.sendBroadcast(
                    Intent(context, cls).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    }
                )
            }
        }
    }

    companion object {
        private const val TAG = "PackageInstallReceiver"
    }
}
