package com.example.nearbyappswidget.feature.widgetlist

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log

/**
 * Transparent trampoline activity for widget item taps.
 *
 * Widget items cannot use setOnClickPendingIntent, so we use a
 * setPendingIntentTemplate that targets this activity. Each item's
 * fill-in intent supplies the package name as an extra. Being an
 * Activity, this can freely call startActivity() on all Android versions.
 */
class LaunchProxyActivity : Activity() {

    companion object {
        private const val TAG = "LaunchProxyActivity"
        const val EXTRA_PACKAGE_NAME = "package_name"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate called")   // before super — confirms the activity started at all
        super.onCreate(savedInstanceState)
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        Log.d(TAG, "onCreate: packageName=$packageName")

        if (!packageName.isNullOrEmpty()) {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                Log.d(TAG, "Launching installed app: $packageName")
                startActivity(launchIntent)
            } else {
                Log.d(TAG, "App not installed, opening Play Store for: $packageName")
                try {
                    startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=$packageName")))
                } catch (e: ActivityNotFoundException) {
                    startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
                }
            }
        } else {
            Log.e(TAG, "No package name extra — doing nothing")
        }

        finish()
    }
}
