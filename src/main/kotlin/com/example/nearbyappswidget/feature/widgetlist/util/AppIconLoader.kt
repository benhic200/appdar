package com.example.nearbyappswidget.feature.widgetlist.util

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap

class AppIconLoader private constructor(private val context: Context) {
    companion object {
        private const val CACHE_SIZE = 50
        private const val BITMAP_SIZE_DP = 40 // desired icon size in dp (80 px at 2× density)

        @Volatile
        private var instance: AppIconLoader? = null

        fun getInstance(context: Context): AppIconLoader =
            instance ?: synchronized(this) {
                instance ?: AppIconLoader(context.applicationContext).also { instance = it }
            }
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

    fun getIconBitmap(packageName: String): Bitmap? {
        cache.get(packageName)?.let { return it }

        val icon = try {
            val packageManager = context.packageManager
            val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.MATCH_ALL)
            packageManager.getApplicationIcon(appInfo)
        } catch (e: PackageManager.NameNotFoundException) {
            return null
        } catch (e: Exception) {
            return null
        }

        val bitmap = drawableToBitmap(icon, bitmapSizePx, bitmapSizePx)
        cache.put(packageName, bitmap)
        return bitmap
    }

    fun clearCache() {
        cache.evictAll()
    }

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
