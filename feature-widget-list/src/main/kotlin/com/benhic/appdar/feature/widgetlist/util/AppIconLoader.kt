package com.benhic.appdar.feature.widgetlist.util

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import com.benhic.appdar.feature.widgetlist.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads and caches app icons as bitmaps for use in RemoteViews.
 * Uses an in‑memory LruCache to avoid repeated PackageManager calls.
 * Falls back to a generic placeholder if the app is not installed or icon cannot be loaded.
 */
@Singleton
class AppIconLoader @Inject constructor(@ApplicationContext private val context: Context) {

    companion object {
        private const val CACHE_SIZE = 50 // number of icons to cache
        private const val BITMAP_SIZE_DP = 40 // desired icon size in dp (80 px at 2× density)
    }

    private val cache = object : LruCache<String, Bitmap>(CACHE_SIZE) {
        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: Bitmap,
            newValue: Bitmap?
        ) {
            // Optional: cleanup if needed
        }
    }

    private val bitmapSizePx: Int by lazy {
        val scale = context.resources.displayMetrics.density
        (BITMAP_SIZE_DP * scale + 0.5f).toInt()
    }

    /**
     * Returns a bitmap for the given package name.
     * If the app is installed, returns its launcher icon (cached) scaled to [BITMAP_SIZE_DP].
     * If not installed, returns `null` (caller should use a placeholder resource).
     */
    fun getIconBitmap(packageName: String): Bitmap? {
        // Check cache first
        cache.get(packageName)?.let { return it }

        // Try to load from PackageManager
        val icon = try {
            val packageManager = context.packageManager
            val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.MATCH_ALL)
            packageManager.getApplicationIcon(appInfo)
        } catch (e: PackageManager.NameNotFoundException) {
            // App not installed
            return null
        } catch (e: Exception) {
            // Any other error (e.g., security exception) – treat as not installed
            return null
        }

        // Convert drawable to bitmap with consistent size
        val bitmap = drawableToBitmap(icon, bitmapSizePx, bitmapSizePx)
        cache.put(packageName, bitmap)
        return bitmap
    }

    /**
     * Clears the icon cache.
     * Call this when the widget is refreshed or when installed apps may have changed.
     */
    fun clearCache() {
        cache.evictAll()
    }

    /**
     * Pre‑loads icons for a list of package names.
     * Useful when the widget is about to display a known set of businesses.
     */
    fun preloadIcons(packageNames: List<String>) {
        packageNames.forEach { getIconBitmap(it) }
    }

    private fun drawableToBitmap(drawable: Drawable, width: Int, height: Int): Bitmap {
        return if (drawable is BitmapDrawable && drawable.bitmap != null) {
            drawable.bitmap
        } else {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        }
    }
}