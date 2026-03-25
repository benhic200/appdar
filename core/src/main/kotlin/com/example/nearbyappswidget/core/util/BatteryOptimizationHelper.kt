package com.example.nearbyappswidget.core.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Helper for dealing with battery‑optimisation restrictions on Android 6+ and OEM skins.
 *
 * Some OEMs (Xiaomi, Huawei, OnePlus, etc.) have aggressive battery‑saving features
 * that can prevent geofence triggers and background location updates.
 * This utility checks whether the app is whitelisted and can open the appropriate
 * system settings page for the user to disable optimisation.
 */
object BatteryOptimizationHelper {

    private const val TAG = "BatteryOptimizationHelper"

    /**
     * Returns `true` if the app is ignoring battery optimisations (i.e., whitelisted).
     * On API < 23 (Android 6.0), battery optimisations do not exist, so returns `true`.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Opens the system settings page where the user can disable battery optimisations
     * for this app. Works on standard Android 6+ and also tries OEM‑specific intents
     * for Xiaomi, Huawei, OnePlus, etc.
     *
     * @param context an [android.app.Activity] context (needed to startActivity)
     */
    fun openBatteryOptimizationSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return
        }

        // Standard Android intent
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }

        // Try OEM‑specific intents if the standard one is not available
        val oemIntent = getOemBatteryOptimizationIntent(context)
        if (oemIntent != null) {
            try {
                context.startActivity(oemIntent)
                Log.d(TAG, "Opened OEM battery optimisation settings")
                return
            } catch (e: Exception) {
                Log.w(TAG, "OEM intent failed, falling back to standard intent", e)
            }
        }

        try {
            context.startActivity(intent)
            Log.d(TAG, "Opened standard battery optimisation settings")
        } catch (e: Exception) {
            Log.e(TAG, "Could not open battery optimisation settings", e)
            // Optionally show a toast or snackbar
        }
    }

    /**
     * Returns an OEM‑specific intent to open battery‑optimisation settings, or `null`
     * if no known OEM is detected.
     *
     * Supports:
     * - Xiaomi (MIUI)
     * - Huawei (EMUI)
     * - OnePlus (OxygenOS)
     * - Samsung (One UI)
     * - Oppo / Realme / Vivo (ColorOS / FuntouchOS)
     */
    private fun getOemBatteryOptimizationIntent(context: Context): Intent? {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> {
                // MIUI battery settings
                Intent().apply {
                    setClassName(
                        "com.miui.powerkeeper",
                        "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                    )
                    putExtra("package_name", context.packageName)
                    putExtra("package_label", context.applicationInfo.loadLabel(context.packageManager))
                }
            }
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                // EMUI battery protection
                Intent().apply {
                    setClassName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"
                    )
                }
            }
            manufacturer.contains("oneplus") -> {
                // OxygenOS battery optimisation
                Intent().apply {
                    setClassName(
                        "com.oneplus.security",
                        "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
                    )
                }
            }
            manufacturer.contains("samsung") -> {
                // Samsung battery optimisation (may vary)
                Intent().apply {
                    action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    data = Uri.parse("package:${context.packageName}")
                }
            }
            manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("vivo") -> {
                // ColorOS / FuntouchOS battery optimisation
                Intent().apply {
                    action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    data = Uri.parse("package:${context.packageName}")
                }
            }
            else -> null
        }
    }

    /**
     * Convenience method to check and optionally request battery‑optimisation whitelisting
     * with a simple dialog. Call this from an Activity's onCreate or onResume.
     */
    fun checkAndRequestBatteryOptimization(context: Context, showDialogIfNeeded: Boolean = true) {
        if (isIgnoringBatteryOptimizations(context)) {
            Log.d(TAG, "App is already whitelisted from battery optimisations")
            return
        }
        Log.w(TAG, "App is NOT whitelisted from battery optimisations; geofencing may be unreliable")
        if (showDialogIfNeeded) {
            // In a real app, you would show a custom dialog explaining the need,
            // then call openBatteryOptimizationSettings when the user agrees.
            // For simplicity, we just open the settings directly.
            openBatteryOptimizationSettings(context)
        }
    }
}