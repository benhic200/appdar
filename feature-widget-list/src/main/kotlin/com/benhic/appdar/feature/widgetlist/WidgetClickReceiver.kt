package com.benhic.appdar.feature.widgetlist

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Debug broadcast receiver that logs widget clicks before forwarding to the target app.
 * 
 * This receiver is used when the direct activity‑launch pending intent fails.
 * It logs the incoming intent extras, then launches the appropriate app or Play Store.
 */
class WidgetClickReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "WidgetClickReceiver"
        const val ACTION_WIDGET_ITEM_CLICK = "com.benhic.appdar.ACTION_WIDGET_ITEM_CLICK"
        const val EXTRA_PACKAGE_NAME = "package_name"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive called: action=${intent.action}, extras=${intent.extras}")
        if (intent.action != ACTION_WIDGET_ITEM_CLICK) {
            Log.w(TAG, "Unexpected action: ${intent.action}")
            return
        }
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        if (packageName.isNullOrEmpty()) {
            Log.e(TAG, "Missing package name extra")
            return
        }
        Log.d(TAG, "Widget click for package: $packageName")
        
        // Try to launch the app
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            Log.d(TAG, "Launching app: $packageName")
            launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(launchIntent)
        } else {
            Log.d(TAG, "App not installed, opening Play Store for: $packageName")
            // Try market:// scheme first (opens Play Store app)
            val marketIntent = Intent(Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("market://details?id=$packageName")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(marketIntent)
                Log.d(TAG, "Opened Play Store via market://")
            } catch (e: android.content.ActivityNotFoundException) {
                // Fallback to web URL
                Log.w(TAG, "Play Store app not found, falling back to web")
                val webIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = android.net.Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(webIntent)
            }
        }
    }
}