package com.benhic.appdar.billing

import android.content.Context

/**
 * Persists Pro unlock state in SharedPreferences.
 *
 * This is checked on every screen load for fast UI rendering.
 * BillingManager.checkExistingPurchases() re-validates with Play Store on each app start,
 * so if a purchase is refunded this will be corrected on the next launch.
 */
object ProManager {

    private const val PREF_NAME = "appdar_prefs"  // shared with the rest of the app
    private const val KEY_IS_PRO = "is_pro"

    fun setPro(context: Context, isPro: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_IS_PRO, isPro).apply()
    }

    fun isPro(context: Context): Boolean =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_IS_PRO, false)
}
