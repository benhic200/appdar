package com.benhic.appdar.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.benhic.appdar.data.local.profiles.PROFILE_GEOFENCE_METERS
import com.benhic.appdar.data.local.profiles.ProfileId
import com.benhic.appdar.data.local.settings.WidgetTheme
import com.benhic.appdar.data.repository.BusinessAppRepository
import com.benhic.appdar.feature.widget.di.WidgetEntryPoint
import com.benhic.appdar.feature.widgetlist.NearbyAppsWidgetListService
import com.benhic.appdar.feature.widgetlist.WidgetClickReceiver
import com.benhic.appdar.feature.widgetlist.di.WidgetListEntryPoint
import com.benhic.appdar.feature.widgetlist.util.AppIconLoader
import com.benhic.appdar.feature.widgetlist.R as WidgetListR
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

private const val ACTION_PAGE_CHANGE = "com.benhic.appdar.ACTION_PAGE_CHANGE"
private const val EXTRA_WIDGET_ID = "widget_id"
private const val EXTRA_PAGE_DELTA = "page_delta"
private const val PREFS_NAME = "NearbyAppsWidgetPrefs"
private const val PAGE_SIZE = 5

private const val TAG = "NearbyAppsWidget"

/**
 * Widget provider for the Nearby Apps Widget.
 * Updates the widget with data from the local repository.
 */
open class NearbyAppsWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdateScheduler.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetUpdateScheduler.cancel(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        // Show immediate feedback when the user manually taps a refresh button.
        // Scheduled silent updates use ACTION_SCHEDULED_UPDATE, not ACTION_APPWIDGET_UPDATE,
        // so this toast only fires for deliberate user taps.
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE &&
            intent.hasExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)) {
            android.widget.Toast.makeText(context, "Refreshing…", android.widget.Toast.LENGTH_SHORT).show()
        }
        if (intent.action == ACTION_SCHEDULED_UPDATE) {
            // Skip auto-update if low power mode is enabled
            try {
                val ep = EntryPointAccessors.fromApplication(
                    context.applicationContext, WidgetListEntryPoint::class.java
                )
                val prefs = runBlocking { ep.settingsRepository().getCurrentPreferences() }
                if (prefs.lowPowerMode) return
            } catch (_: Exception) { return }
            val mgr = AppWidgetManager.getInstance(context)
            val allClasses = listOf(
                NearbyAppsWidgetProvider::class.java,
                NearbyAppsWidgetProviderNano::class.java,
                NearbyAppsWidgetProviderStrip::class.java,
                NearbyAppsWidgetProviderGrid::class.java,
                NearbyAppsWidgetProviderNarrow::class.java
            )
            for (cls in allClasses) {
                val ids = mgr.getAppWidgetIds(android.content.ComponentName(context, cls))
                for (id in ids) updateAppWidget(context, mgr, id)
            }
            return
        }
        if (intent.action == ACTION_PAGE_CHANGE) {
            val widgetId = intent.getIntExtra(EXTRA_WIDGET_ID, -1)
            val delta = intent.getIntExtra(EXTRA_PAGE_DELTA, 0)
            if (widgetId != -1) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val current = prefs.getInt("page_$widgetId", 0)
                prefs.edit().putInt("page_$widgetId", maxOf(0, current + delta)).apply()
                val mgr = AppWidgetManager.getInstance(context)
                updateAppWidget(context, mgr, widgetId)
            }
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate called for widget IDs: ${appWidgetIds.joinToString()}")
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        Log.d(TAG, "onAppWidgetOptionsChanged id=$appWidgetId")
        updateAppWidget(context, appWidgetManager, appWidgetId)
    }

    companion object {
        // Width thresholds in dp
        private const val THRESHOLD_1X1_WIDTH_DP = 80
        private const val THRESHOLD_NANO_MAX_DP = 130    // ≤130dp → single-business nano card
        private const val THRESHOLD_STRIP_MAX_DP = 260   // 131–260dp → compact 3-item strip
        private const val THRESHOLD_STRIP_HEIGHT_DP = 150 // short widgets force strip even if wide
        private const val THRESHOLD_2COL_MIN_DP = 300    // ≥300dp → 2-column grid

        public fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
            val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 200)
            Log.d(TAG, "updateAppWidget id=$appWidgetId minWidth=${minWidthDp}dp minHeight=${minHeightDp}dp")

            if (minWidthDp < THRESHOLD_1X1_WIDTH_DP) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && minHeightDp >= THRESHOLD_STRIP_HEIGHT_DP) {
                    updateAsNarrowColumn(context, appWidgetManager, appWidgetId, minHeightDp)
                } else {
                    updateAs1x1(context, appWidgetManager, appWidgetId)
                }
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                when {
                    minWidthDp <= THRESHOLD_NANO_MAX_DP ->
                        if (minHeightDp >= THRESHOLD_STRIP_HEIGHT_DP)
                            updateAsStrip(context, appWidgetManager, appWidgetId, minWidthDp, minHeightDp)
                        else
                            updateAsNano(context, appWidgetManager, appWidgetId, minHeightDp)
                    minWidthDp <= THRESHOLD_STRIP_MAX_DP || minHeightDp < THRESHOLD_STRIP_HEIGHT_DP ->
                        updateAsStrip(context, appWidgetManager, appWidgetId, minWidthDp, minHeightDp)
                    else -> {
                        val columnCount = when {
                            minWidthDp < 270 -> 1
                            minWidthDp < 330 -> 2
                            else -> 3
                        }
                        updateWithRemoteCollectionItems(context, appWidgetManager, appWidgetId, columnCount, minHeightDp)
                    }
                }
            } else {
                updateAsScrollableList(context, appWidgetManager, appWidgetId, minWidthDp)
            }
        }

        @RequiresApi(Build.VERSION_CODES.S)
        private fun updateAsNarrowColumn(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            minHeightDp: Int
        ) {
            Log.d(TAG, "updateAsNarrowColumn id=$appWidgetId minHeight=${minHeightDp}dp")
            val darkTheme = resolveDarkTheme(context)
            val rootLayout = if (darkTheme) WidgetListR.layout.widget_narrow_column_dark
                             else WidgetListR.layout.widget_narrow_column
            val views = RemoteViews(context.packageName, rootLayout)
            views.setOnClickPendingIntent(WidgetListR.id.narrow_refresh, buildRefreshPi(context, appWidgetId))
            // Reserve 20dp for the refresh button at the top
            val iconCount = ((minHeightDp - 24) / 50).coerceIn(1, 8)

            runBlocking {
                try {
                    val ep = EntryPointAccessors.fromApplication(
                        context.applicationContext, WidgetListEntryPoint::class.java)
                    val repo = ep.businessAppRepository()
                    val calc = ep.distanceCalculator()
                    val locProvider = ep.locationProvider()
                    val icons = ep.appIconLoader()
                    val prefs = ep.settingsRepository().getCurrentPreferences()
                    repo.initialize()
                    val location = withTimeoutOrNull(5000L) { locProvider.getCurrentLocation() }

                    val profileRepo = ep.locationProfileRepository()
                    val matchedProfile = if (location != null) {
                        ProfileId.values().map { id -> profileRepo.getProfile(id).first() }
                            .firstOrNull { profile ->
                                val lat = profile.latitude ?: return@firstOrNull false
                                val lon = profile.longitude ?: return@firstOrNull false
                                profile.selectedApps.isNotEmpty() &&
                                    calc.calculateDistanceMeters(
                                        location.latitude, location.longitude, lat, lon
                                    ) <= PROFILE_GEOFENCE_METERS
                            }
                    } else null

                    val packages = if (matchedProfile != null) {
                        matchedProfile.selectedApps.take(iconCount)
                    } else {
                        val branchFinder = ep.nearbyBranchFinder()
                        val nearestBranches = if (location != null) {
                            withTimeoutOrNull(10000L) {
                                branchFinder.findNearestBranches(location.latitude, location.longitude)
                            } ?: emptyMap()
                        } else emptyMap()
                        repo.getAllMappings().first().filter { it.isEnabled }.mapNotNull { m ->
                            val (lat, lon) = nearestBranches[m.businessName]
                                ?: ((m.latitude ?: return@mapNotNull null) to (m.longitude ?: return@mapNotNull null))
                            val dist = if (location != null)
                                calc.calculateDistanceMeters(location.latitude, location.longitude, lat, lon).toInt()
                            else m.geofenceRadius
                            m.packageName to dist
                        }.sortedBy { it.second }.take(iconCount).map { it.first }
                    }

                    icons.preloadIcons(packages)
                    views.removeAllViews(WidgetListR.id.narrow_icons_container)
                    packages.forEachIndexed { i, pkg ->
                        val itemLayout = if (darkTheme) WidgetListR.layout.widget_narrow_icon_item_dark
                                         else WidgetListR.layout.widget_narrow_icon_item
                        val item = RemoteViews(context.packageName, itemLayout)
                        val bmp = icons.getIconBitmap(pkg)
                        if (bmp != null) item.setImageViewBitmap(WidgetListR.id.narrow_app_icon, scaledForWidget(bmp, 80))
                        else item.setImageViewResource(WidgetListR.id.narrow_app_icon, android.R.drawable.sym_def_app_icon)
                        val clickIntent = Intent(WidgetClickReceiver.ACTION_WIDGET_ITEM_CLICK).apply {
                            setPackage(context.packageName)
                            putExtra(WidgetClickReceiver.EXTRA_PACKAGE_NAME, pkg)
                        }
                        item.setOnClickPendingIntent(WidgetListR.id.narrow_icon_root,
                            PendingIntent.getBroadcast(context, appWidgetId * 1000 + i, clickIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                        views.addView(WidgetListR.id.narrow_icons_container, item)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to build narrow column widget", e)
                }
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun updateAs1x1(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            Log.d(TAG, "updateAppWidget id=$appWidgetId → 1x1 mode")
            val views = RemoteViews(context.packageName, R.layout.widget_nearby_apps_1x1)

            // Default: tapping opens the host app
            val launchAppIntent = context.packageManager
                .getLaunchIntentForPackage(context.packageName)
                ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                ?: Intent(Intent.ACTION_MAIN)
            val launchPi = PendingIntent.getActivity(
                context, appWidgetId, launchAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.icon_1x1_root, launchPi)

            runBlocking {
                try {
                    val ep = EntryPointAccessors.fromApplication(
                        context.applicationContext, WidgetListEntryPoint::class.java)
                    val repo = ep.businessAppRepository()
                    val calc = ep.distanceCalculator()
                    val locProvider = ep.locationProvider()
                    val icons = ep.appIconLoader()
                    val prefs = ep.settingsRepository().getCurrentPreferences()
                    repo.initialize()
                    val location = withTimeoutOrNull(5000L) { locProvider.getCurrentLocation() }

                    // Profile-aware: show first app of matched profile, else nearest app
                    val profileRepo = ep.locationProfileRepository()
                    val matchedPkg = if (location != null) {
                        ProfileId.values().map { id -> profileRepo.getProfile(id).first() }
                            .firstOrNull { profile ->
                                val lat = profile.latitude ?: return@firstOrNull false
                                val lon = profile.longitude ?: return@firstOrNull false
                                profile.selectedApps.isNotEmpty() &&
                                    calc.calculateDistanceMeters(
                                        location.latitude, location.longitude, lat, lon
                                    ) <= PROFILE_GEOFENCE_METERS
                            }?.selectedApps?.firstOrNull()
                    } else null

                    val pkg = matchedPkg ?: run {
                        val branchFinder = ep.nearbyBranchFinder()
                        val nearestBranches = if (location != null) {
                            withTimeoutOrNull(10000L) {
                                branchFinder.findNearestBranches(location.latitude, location.longitude)
                            } ?: emptyMap()
                        } else emptyMap()
                        repo.getAllMappings().first().filter { it.isEnabled }.mapNotNull { m ->
                            val (lat, lon) = nearestBranches[m.businessName]
                                ?: ((m.latitude ?: return@mapNotNull null) to (m.longitude ?: return@mapNotNull null))
                            val dist = if (location != null)
                                calc.calculateDistanceMeters(location.latitude, location.longitude, lat, lon).toInt()
                            else m.geofenceRadius
                            m.packageName to dist
                        }.minByOrNull { it.second }?.first
                    }

                    if (pkg != null) {
                        icons.preloadIcons(listOf(pkg))
                        val bmp = icons.getIconBitmap(pkg)
                        if (bmp != null) views.setImageViewBitmap(R.id.icon_1x1, scaledForWidget(bmp, 80))
                        else views.setImageViewResource(R.id.icon_1x1, android.R.drawable.sym_def_app_icon)
                        val clickIntent = Intent(WidgetClickReceiver.ACTION_WIDGET_ITEM_CLICK).apply {
                            setPackage(context.packageName)
                            putExtra(WidgetClickReceiver.EXTRA_PACKAGE_NAME, pkg)
                        }
                        views.setOnClickPendingIntent(R.id.icon_1x1_root,
                            PendingIntent.getBroadcast(context, appWidgetId * 1000, clickIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                    } else {
                        // pkg is null, do nothing
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load icon for 1x1 widget", e)
                }
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun updateAsScrollableList(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            minWidthDp: Int = 250
        ) {
            Log.d(TAG, "updateAppWidget id=$appWidgetId (scrollable, API<31)")

            // API < 31: use RemoteViewsService + setPendingIntentTemplate fill-in pattern.
            val views = RemoteViews(context.packageName, WidgetListR.layout.widget_nearby_apps_scrollable)

            val serviceIntent = Intent(context, NearbyAppsWidgetListService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = android.net.Uri.parse("widget://$appWidgetId")
            }
            views.setRemoteAdapter(WidgetListR.id.widget_list, serviceIntent)
            views.setEmptyView(WidgetListR.id.widget_list, WidgetListR.id.empty_state)

            Log.d(TAG, "Setting pending intent template for widget $appWidgetId")
            val clickIntent = Intent(WidgetClickReceiver.ACTION_WIDGET_ITEM_CLICK).apply {
                setPackage(context.packageName)
            }
            val clickPendingIntent = PendingIntent.getBroadcast(
                context, appWidgetId, clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(WidgetListR.id.widget_list, clickPendingIntent)
            Log.d(TAG, "Pending intent template set")

            val refreshIntent = Intent(context, NearbyAppsWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context, appWidgetId, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(WidgetListR.id.refresh_icon, refreshPendingIntent)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        @RequiresApi(Build.VERSION_CODES.S)
        private fun updateWithRemoteCollectionItems(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            columnCount: Int,
            minHeightDp: Int = 200
        ) {
            Log.d(TAG, "updateWithAddView id=$appWidgetId columnCount=$columnCount")
            // Use a LinearLayout-based layout (not ListView). Items are added via addView()
            // so each gets setOnClickPendingIntent — the same mechanism as the working refresh
            // button. MIUI drops collection-widget item clicks but not regular view clicks.

            // Resolve dark/light before we build RemoteViews so we can pick the right layout XML.
            // All colors come from the XML — zero programmatic color calls to avoid MIUI reflection failures.
            val darkThemeEarly = run {
                try {
                    val ep = EntryPointAccessors.fromApplication(
                        context.applicationContext, WidgetListEntryPoint::class.java
                    )
                    val prefs = runBlocking { ep.settingsRepository().getCurrentPreferences() }
                    resolveWidgetDark(context, prefs.widgetTheme)
                } catch (_: Exception) { false }
            }
            val rootLayout = if (darkThemeEarly) WidgetListR.layout.widget_nearby_apps_list_dark
                             else WidgetListR.layout.widget_nearby_apps_list
            val views = RemoteViews(context.packageName, rootLayout)

            val refreshIntent = Intent(context, NearbyAppsWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context, appWidgetId, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(WidgetListR.id.refresh_icon, refreshPendingIntent)

            val launchAppIntent = context.packageManager
                .getLaunchIntentForPackage(context.packageName)
                ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                ?: Intent(Intent.ACTION_MAIN).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            val settingsPendingIntent = PendingIntent.getActivity(
                context, appWidgetId + 500, launchAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(WidgetListR.id.settings_icon, settingsPendingIntent)

            runBlocking {
                try {
                    val ep = EntryPointAccessors.fromApplication(
                        context.applicationContext, WidgetListEntryPoint::class.java
                    )
                    val repo = ep.businessAppRepository()
                    val settings = ep.settingsRepository()
                    val calc = ep.distanceCalculator()
                    val locProvider = ep.locationProvider()
                    val icons = ep.appIconLoader()

                    val prefs = settings.getCurrentPreferences()
                    repo.initialize()
                    val mappings = repo.getAllMappings().first().filter { it.isEnabled }

                    val location = withTimeoutOrNull(5000L) { locProvider.getCurrentLocation() }
                    Log.d(TAG, "Location result: $location")

                    // Check if user is at a saved profile location — if so show that profile's apps.
                    val profileRepo = ep.locationProfileRepository()
                    val matchedProfile = if (location != null) {
                        ProfileId.values().map { id -> profileRepo.getProfile(id).first() }
                            .firstOrNull { profile ->
                                val lat = profile.latitude ?: return@firstOrNull false
                                val lon = profile.longitude ?: return@firstOrNull false
                                profile.selectedApps.isNotEmpty() &&
                                    calc.calculateDistanceMeters(
                                        location.latitude, location.longitude, lat, lon
                                    ) <= PROFILE_GEOFENCE_METERS
                            }
                    } else null

                    if (matchedProfile != null) {
                        Log.d(TAG, "At profile: ${matchedProfile.displayName}")
                        val profileItems = matchedProfile.selectedApps.mapIndexed { idx, pkg ->
                            val installed = try {
                                context.packageManager.getPackageInfo(pkg, PackageManager.MATCH_ALL)
                                true
                            } catch (_: PackageManager.NameNotFoundException) { false }
                            Triple(pkg, matchedProfile.displayName, installed)
                        }
                        icons.preloadIcons(profileItems.map { it.first })

                        val effectivePageSize = rowsPerColumn(minHeightDp) * columnCount
                        val totalPages = maxOf(1, (profileItems.size + effectivePageSize - 1) / effectivePageSize)
                        val widgetPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        val currentPage = widgetPrefs.getInt("page_$appWidgetId", 0).coerceIn(0, totalPages - 1)
                        val pageItems = profileItems.drop(currentPage * effectivePageSize).take(effectivePageSize)

                        setPaginationButtons(context, views, appWidgetId, currentPage, totalPages)
                        views.setTextViewText(WidgetListR.id.tv_page_indicator, "${currentPage + 1}/$totalPages")
                        views.removeAllViews(WidgetListR.id.items_container)

                        val profileDisplayItems = pageItems.map { (pkg, profileName, installed) ->
                            val label = try {
                                val info = context.packageManager.getApplicationInfo(pkg, 0)
                                context.packageManager.getApplicationLabel(info).toString()
                            } catch (_: Exception) { pkg }
                            WidgetDisplayItem(pkg, label, "@ ${profileName.substringBefore(" Apps")}", installed)
                        }
                        addItemsToContainer(context, views, appWidgetId, columnCount, profileDisplayItems, icons, darkThemeEarly)
                        Log.d(TAG, "Profile page built: ${pageItems.size} items for widget $appWidgetId")
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                        return@runBlocking
                    }

                    // Resolve nearest real branch coordinates from Overpass (OSM).
                    // Falls back to database coordinates if offline or no result within 15 km.
                    val branchFinder = ep.nearbyBranchFinder()
                    val nearestBranches = if (location != null) {
                        withTimeoutOrNull(10000L) {
                            branchFinder.findNearestBranches(location.latitude, location.longitude)
                        } ?: emptyMap()
                    } else emptyMap()
                    Log.d(TAG, "Nearest branches resolved: ${nearestBranches.keys}")

                    val allItems = mappings.mapNotNull { m ->
                        val installed = try {
                            context.packageManager.getPackageInfo(m.packageName, PackageManager.MATCH_ALL)
                            true
                        } catch (_: PackageManager.NameNotFoundException) { false }
                        // Prefer OSM branch coordinate; fall back to DB coordinate
                        val (branchLat, branchLon) = nearestBranches[m.businessName]
                            ?: ((m.latitude ?: return@mapNotNull null) to (m.longitude ?: return@mapNotNull null))
                        val dist = if (location != null) {
                            calc.calculateDistanceMeters(location.latitude, location.longitude, branchLat, branchLon).toInt()
                        } else m.geofenceRadius
                        Triple(m, dist, installed)
                    }.sortedBy { it.second }

                    icons.preloadIcons(allItems.map { it.first.packageName })

                    // Pagination: rows fit on screen × column count
                    val effectivePageSize = rowsPerColumn(minHeightDp) * columnCount
                    val totalPages = maxOf(1, (allItems.size + effectivePageSize - 1) / effectivePageSize)
                    val widgetPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    var currentPage = widgetPrefs.getInt("page_$appWidgetId", 0).coerceIn(0, totalPages - 1)
                    val pageItems = allItems.drop(currentPage * effectivePageSize).take(effectivePageSize)

                    setPaginationButtons(context, views, appWidgetId, currentPage, totalPages)
                    views.setTextViewText(WidgetListR.id.tv_page_indicator, "${currentPage + 1}/$totalPages")

                    // Build items for current page
                    views.removeAllViews(WidgetListR.id.items_container)
                    val nearbyDisplayItems = pageItems.map { (m, dist, installed) ->
                        val distText = prefs?.let { calc.formatDistanceWithPreferences(dist.toDouble(), it) }
                            ?: if (dist < 1000) context.getString(WidgetListR.string.distance_format, dist)
                               else context.getString(WidgetListR.string.distance_km_format, dist / 1000.0)
                        WidgetDisplayItem(m.packageName, m.businessName, distText, installed)
                    }
                    addItemsToContainer(context, views, appWidgetId, columnCount, nearbyDisplayItems, icons, darkThemeEarly)
                    Log.d(TAG, "addView page $currentPage/$totalPages built: ${pageItems.size} items for widget $appWidgetId")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to build addView items", e)
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        // ── Nano mode (≤130dp wide): single closest business ──────────────────────────

        @RequiresApi(Build.VERSION_CODES.S)
        private fun updateAsNano(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            minHeightDp: Int = 100
        ) {
            Log.d(TAG, "updateAsNano id=$appWidgetId minHeight=${minHeightDp}dp")
            val darkTheme = resolveDarkTheme(context)
            val isShort = minHeightDp < 90
            val layoutRes = when {
                isShort && darkTheme -> WidgetListR.layout.widget_nano_short_dark
                isShort             -> WidgetListR.layout.widget_nano_short
                darkTheme           -> WidgetListR.layout.widget_nano_dark
                else                -> WidgetListR.layout.widget_nano
            }
            val views = RemoteViews(context.packageName, layoutRes)

            val refreshPi = buildRefreshPi(context, appWidgetId)
            views.setOnClickPendingIntent(WidgetListR.id.nano_refresh, refreshPi)

            runBlocking {
                try {
                    val ep = EntryPointAccessors.fromApplication(
                        context.applicationContext, WidgetListEntryPoint::class.java)
                    val repo = ep.businessAppRepository()
                    val calc = ep.distanceCalculator()
                    val locProvider = ep.locationProvider()
                    val icons = ep.appIconLoader()
                    val prefs = ep.settingsRepository().getCurrentPreferences()
                    repo.initialize()
                    val location = withTimeoutOrNull(5000L) { locProvider.getCurrentLocation() }

                    // Check if at a saved profile location — if so, show that profile's first app.
                    val profileRepo = ep.locationProfileRepository()
                    val matchedProfile = if (location != null) {
                        ProfileId.values().map { id -> profileRepo.getProfile(id).first() }
                            .firstOrNull { profile ->
                                val lat = profile.latitude ?: return@firstOrNull false
                                val lon = profile.longitude ?: return@firstOrNull false
                                profile.selectedApps.isNotEmpty() &&
                                    calc.calculateDistanceMeters(
                                        location.latitude, location.longitude, lat, lon
                                    ) <= PROFILE_GEOFENCE_METERS
                            }
                    } else null

                    if (matchedProfile != null) {
                        val pkg = matchedProfile.selectedApps.first()
                        icons.preloadIcons(listOf(pkg))
                        val installed = try {
                            context.packageManager.getPackageInfo(pkg, PackageManager.MATCH_ALL); true
                        } catch (_: PackageManager.NameNotFoundException) { false }
                        val label = try {
                            val info = context.packageManager.getApplicationInfo(pkg, 0)
                            context.packageManager.getApplicationLabel(info).toString()
                        } catch (_: Exception) { pkg }
                        views.setTextViewText(WidgetListR.id.nano_business_name, label)
                        views.setTextViewText(WidgetListR.id.nano_distance,
                            "@ ${matchedProfile.displayName.substringBefore(" Apps")}")
                        views.setImageViewResource(WidgetListR.id.nano_status_dot,
                            if (installed) WidgetListR.drawable.ic_installed_dot
                            else WidgetListR.drawable.ic_uninstalled_dot)
                        val bmp = icons.getIconBitmap(pkg)
                        if (bmp != null) views.setImageViewBitmap(WidgetListR.id.nano_app_icon, scaledForWidget(bmp, 80))
                        else views.setImageViewResource(WidgetListR.id.nano_app_icon, android.R.drawable.sym_def_app_icon)
                        val clickIntent = Intent(WidgetClickReceiver.ACTION_WIDGET_ITEM_CLICK).apply {
                            setPackage(context.packageName)
                            putExtra(WidgetClickReceiver.EXTRA_PACKAGE_NAME, pkg)
                        }
                        views.setOnClickPendingIntent(WidgetListR.id.nano_root,
                            PendingIntent.getBroadcast(context, appWidgetId * 1000, clickIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                        return@runBlocking
                    }

                    val branchFinder = ep.nearbyBranchFinder()
                    val nearestBranches = if (location != null) {
                        withTimeoutOrNull(10000L) {
                            branchFinder.findNearestBranches(location.latitude, location.longitude)
                        } ?: emptyMap()
                    } else emptyMap()

                    val closest = repo.getAllMappings().first().filter { it.isEnabled }.mapNotNull { m ->
                        val (branchLat, branchLon) = nearestBranches[m.businessName]
                            ?: ((m.latitude ?: return@mapNotNull null) to (m.longitude ?: return@mapNotNull null))
                        val dist = if (location != null)
                            calc.calculateDistanceMeters(location.latitude, location.longitude, branchLat, branchLon).toInt()
                        else m.geofenceRadius
                        val installed = try {
                            context.packageManager.getPackageInfo(m.packageName, PackageManager.MATCH_ALL); true
                        } catch (_: PackageManager.NameNotFoundException) { false }
                        Triple(m, dist, installed)
                    }.minByOrNull { it.second }

                    if (closest != null) {
                        val (m, dist, installed) = closest
                        icons.preloadIcons(listOf(m.packageName))
                        views.setTextViewText(WidgetListR.id.nano_business_name, m.businessName)
                        views.setTextViewText(WidgetListR.id.nano_distance,
                            calc.formatDistanceWithPreferences(dist.toDouble(), prefs))
                        views.setImageViewResource(WidgetListR.id.nano_status_dot,
                            if (installed) WidgetListR.drawable.ic_installed_dot else WidgetListR.drawable.ic_uninstalled_dot)
                        val bmp = icons.getIconBitmap(m.packageName)
                        if (bmp != null) views.setImageViewBitmap(WidgetListR.id.nano_app_icon, scaledForWidget(bmp, 80))
                        else views.setImageViewResource(WidgetListR.id.nano_app_icon, android.R.drawable.sym_def_app_icon)
                        val clickIntent = Intent(WidgetClickReceiver.ACTION_WIDGET_ITEM_CLICK).apply {
                            setPackage(context.packageName)
                            putExtra(WidgetClickReceiver.EXTRA_PACKAGE_NAME, m.packageName)
                        }
                        views.setOnClickPendingIntent(WidgetListR.id.nano_root,
                            PendingIntent.getBroadcast(context, appWidgetId * 1000, clickIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                    } else {
                        views.setTextViewText(WidgetListR.id.nano_business_name, "No businesses")
                        views.setTextViewText(WidgetListR.id.nano_distance, "")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to build nano widget", e)
                }
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        // ── Strip mode (131–260dp wide or short): 3 closest businesses ────────────

        @RequiresApi(Build.VERSION_CODES.S)
        private fun updateAsStrip(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            minWidthDp: Int = 200,
            minHeightDp: Int = 200
        ) {
            val isHorizontal = minHeightDp < THRESHOLD_STRIP_HEIGHT_DP
            Log.d(TAG, "updateAsStrip id=$appWidgetId isHorizontal=$isHorizontal minWidthDp=$minWidthDp minHeightDp=$minHeightDp")
            val darkTheme = resolveDarkTheme(context)

            val views = if (isHorizontal) {
                RemoteViews(context.packageName,
                    if (darkTheme) WidgetListR.layout.widget_strip_horizontal_dark
                    else WidgetListR.layout.widget_strip_horizontal)
            } else {
                RemoteViews(context.packageName,
                    if (darkTheme) WidgetListR.layout.widget_strip_dark else WidgetListR.layout.widget_strip)
            }

            if (isHorizontal) {
                views.setOnClickPendingIntent(WidgetListR.id.strip_h_refresh, buildRefreshPi(context, appWidgetId))
            } else {
                views.setOnClickPendingIntent(WidgetListR.id.strip_refresh, buildRefreshPi(context, appWidgetId))
            }

            val itemCount = if (isHorizontal) {
                when {
                    minWidthDp <= 200 -> 2
                    minWidthDp <= 280 -> 3
                    else -> 4
                }
            } else rowsPerColumnStrip(minHeightDp)

            runBlocking {
                try {
                    val ep = EntryPointAccessors.fromApplication(
                        context.applicationContext, WidgetListEntryPoint::class.java)
                    val repo = ep.businessAppRepository()
                    val calc = ep.distanceCalculator()
                    val locProvider = ep.locationProvider()
                    val icons = ep.appIconLoader()
                    val prefs = ep.settingsRepository().getCurrentPreferences()
                    repo.initialize()
                    val location = withTimeoutOrNull(5000L) { locProvider.getCurrentLocation() }

                    // Check if at a saved profile location — if so, show that profile's apps.
                    val profileRepo = ep.locationProfileRepository()
                    val matchedProfile = if (location != null) {
                        ProfileId.values().map { id -> profileRepo.getProfile(id).first() }
                            .firstOrNull { profile ->
                                val lat = profile.latitude ?: return@firstOrNull false
                                val lon = profile.longitude ?: return@firstOrNull false
                                profile.selectedApps.isNotEmpty() &&
                                    calc.calculateDistanceMeters(
                                        location.latitude, location.longitude, lat, lon
                                    ) <= PROFILE_GEOFENCE_METERS
                            }
                    } else null

                    if (matchedProfile != null) {
                        val profileApps = matchedProfile.selectedApps.take(itemCount)
                        icons.preloadIcons(profileApps)
                        val profileItems = profileApps.mapIndexed { _, pkg ->
                            val installed = try {
                                context.packageManager.getPackageInfo(pkg, PackageManager.MATCH_ALL); true
                            } catch (_: PackageManager.NameNotFoundException) { false }
                            val label = try {
                                val info = context.packageManager.getApplicationInfo(pkg, 0)
                                context.packageManager.getApplicationLabel(info).toString()
                            } catch (_: Exception) { pkg }
                            WidgetDisplayItem(pkg, label,
                                "@ ${matchedProfile.displayName.substringBefore(" Apps")}", installed)
                        }
                        if (isHorizontal) {
                            views.removeAllViews(WidgetListR.id.strip_h_items_container)
                            profileItems.forEachIndexed { i, item ->
                                views.addView(WidgetListR.id.strip_h_items_container,
                                    makeHStripItem(context, appWidgetId, i, item, icons, darkTheme))
                            }
                        } else {
                            views.removeAllViews(WidgetListR.id.strip_items_container)
                            profileItems.forEachIndexed { i, item ->
                                views.addView(WidgetListR.id.strip_items_container,
                                    makeStripItem(context, appWidgetId, i, item, icons, darkTheme))
                            }
                            views.setTextViewText(WidgetListR.id.strip_page_indicator, "")
                        }
                        return@runBlocking
                    }

                    val branchFinder = ep.nearbyBranchFinder()
                    val nearestBranches = if (location != null) {
                        withTimeoutOrNull(10000L) {
                            branchFinder.findNearestBranches(location.latitude, location.longitude)
                        } ?: emptyMap()
                    } else emptyMap()

                    val sorted = repo.getAllMappings().first().filter { it.isEnabled }.mapNotNull { m ->
                        val (branchLat, branchLon) = nearestBranches[m.businessName]
                            ?: ((m.latitude ?: return@mapNotNull null) to (m.longitude ?: return@mapNotNull null))
                        val dist = if (location != null)
                            calc.calculateDistanceMeters(location.latitude, location.longitude, branchLat, branchLon).toInt()
                        else m.geofenceRadius
                        val installed = try {
                            context.packageManager.getPackageInfo(m.packageName, PackageManager.MATCH_ALL); true
                        } catch (_: PackageManager.NameNotFoundException) { false }
                        WidgetDisplayItem(m.packageName, m.businessName,
                            calc.formatDistanceWithPreferences(dist.toDouble(), prefs), installed) to dist
                    }.sortedBy { it.second }.take(itemCount).map { it.first }

                    icons.preloadIcons(sorted.map { it.packageName })

                    if (isHorizontal) {
                        views.removeAllViews(WidgetListR.id.strip_h_items_container)
                        sorted.forEachIndexed { i, item ->
                            views.addView(WidgetListR.id.strip_h_items_container,
                                makeHStripItem(context, appWidgetId, i, item, icons, darkTheme))
                        }
                    } else {
                        views.removeAllViews(WidgetListR.id.strip_items_container)
                        sorted.forEachIndexed { i, item ->
                            views.addView(WidgetListR.id.strip_items_container,
                                makeStripItem(context, appWidgetId, i, item, icons, darkTheme))
                        }
                        views.setTextViewText(WidgetListR.id.strip_page_indicator,
                            if (sorted.isEmpty()) "No businesses nearby" else "")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to build strip widget", e)
                }
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun makeStripItem(
            context: Context,
            appWidgetId: Int,
            globalIdx: Int,
            item: WidgetDisplayItem,
            icons: AppIconLoader,
            darkTheme: Boolean
        ): RemoteViews {
            val rv = RemoteViews(context.packageName,
                if (darkTheme) WidgetListR.layout.widget_strip_item_dark else WidgetListR.layout.widget_strip_item)
            rv.setTextViewText(WidgetListR.id.business_name, item.label)
            rv.setTextViewText(WidgetListR.id.distance, item.subtext)
            rv.setImageViewResource(WidgetListR.id.status_dot,
                if (item.installed) WidgetListR.drawable.ic_installed_dot else WidgetListR.drawable.ic_uninstalled_dot)
            val bmp = icons.getIconBitmap(item.packageName)
            if (bmp != null) rv.setImageViewBitmap(WidgetListR.id.app_icon, scaledForWidget(bmp, 60))
            else rv.setImageViewResource(WidgetListR.id.app_icon, android.R.drawable.sym_def_app_icon)
            val clickIntent = Intent(WidgetClickReceiver.ACTION_WIDGET_ITEM_CLICK).apply {
                setPackage(context.packageName)
                putExtra(WidgetClickReceiver.EXTRA_PACKAGE_NAME, item.packageName)
            }
            rv.setOnClickPendingIntent(WidgetListR.id.item_root,
                PendingIntent.getBroadcast(context, appWidgetId * 1000 + globalIdx, clickIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            return rv
        }

        // ── Shared helpers ─────────────────────────────────────────────────────────

        /** Resolve dark/light without throwing — defaults to light on any error. */
        private fun resolveDarkTheme(context: Context): Boolean = try {
            val ep = EntryPointAccessors.fromApplication(
                context.applicationContext, WidgetListEntryPoint::class.java)
            val prefs = runBlocking { ep.settingsRepository().getCurrentPreferences() }
            resolveWidgetDark(context, prefs.widgetTheme)
        } catch (_: Exception) { false }

        private fun buildRefreshPi(context: Context, appWidgetId: Int): android.app.PendingIntent {
            val intent = Intent(context, NearbyAppsWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
            }
            return PendingIntent.getBroadcast(context, appWidgetId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun resolveWidgetDark(context: Context, theme: WidgetTheme): Boolean = when (theme) {
            WidgetTheme.DARK -> true
            WidgetTheme.LIGHT -> false
            WidgetTheme.SYSTEM -> {
                val uiMgr = context.getSystemService(android.content.Context.UI_MODE_SERVICE)
                    as android.app.UiModeManager
                uiMgr.nightMode == android.app.UiModeManager.MODE_NIGHT_YES
            }
        }

        /**
         * Scales a bitmap down so its longest side is at most [maxPx] pixels.
         * Returns the original if it is already small enough.
         * Keeps bitmaps lean for the RemoteViews IPC parcel (1 MB limit).
         */
        private fun scaledForWidget(bmp: android.graphics.Bitmap, maxPx: Int): android.graphics.Bitmap {
            if (bmp.width <= maxPx && bmp.height <= maxPx) return bmp
            return android.graphics.Bitmap.createScaledBitmap(bmp, maxPx, maxPx, true)
        }

        /** How many item-rows fit in a grid widget of [heightDp] dp (header ~40dp, each compact row ~103dp). */
        private fun rowsPerColumn(heightDp: Int): Int =
            ((heightDp - 40) / 105).coerceIn(1, 4)

        /** How many item-rows fit in a strip widget of [heightDp] dp (header ~30dp, each row ~42dp). */
        private fun rowsPerColumnStrip(heightDp: Int): Int =
            ((heightDp - 30) / 42).coerceIn(1, 8)

        private data class WidgetDisplayItem(
            val packageName: String,
            val label: String,
            val subtext: String,
            val installed: Boolean
        )

        private fun makeRegularItem(
            context: Context,
            appWidgetId: Int,
            globalIdx: Int,
            item: WidgetDisplayItem,
            icons: AppIconLoader,
            darkTheme: Boolean
        ): RemoteViews {
            val layout = if (darkTheme) WidgetListR.layout.widget_list_item_dark
                         else WidgetListR.layout.widget_list_item
            val rv = RemoteViews(context.packageName, layout)
            rv.setTextViewText(WidgetListR.id.business_name, item.label)
            rv.setTextViewText(WidgetListR.id.distance, item.subtext)
            rv.setImageViewResource(
                WidgetListR.id.status_dot,
                if (item.installed) WidgetListR.drawable.ic_installed_dot else WidgetListR.drawable.ic_uninstalled_dot
            )
            val bmp = icons.getIconBitmap(item.packageName)
            if (bmp != null) rv.setImageViewBitmap(WidgetListR.id.app_icon, scaledForWidget(bmp, 80))
            else rv.setImageViewResource(WidgetListR.id.app_icon, android.R.drawable.sym_def_app_icon)
            val clickIntent = Intent(WidgetClickReceiver.ACTION_WIDGET_ITEM_CLICK).apply {
                setPackage(context.packageName)
                putExtra(WidgetClickReceiver.EXTRA_PACKAGE_NAME, item.packageName)
            }
            val pi = PendingIntent.getBroadcast(
                context, appWidgetId * 1000 + globalIdx, clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            rv.setOnClickPendingIntent(WidgetListR.id.item_root, pi)
            return rv
        }

        private fun makeCompactItem(
            context: Context,
            appWidgetId: Int,
            globalIdx: Int,
            item: WidgetDisplayItem,
            icons: AppIconLoader,
            darkTheme: Boolean
        ): RemoteViews {
            val layout = if (darkTheme) WidgetListR.layout.widget_list_item_compact_dark
                         else WidgetListR.layout.widget_list_item_compact
            val rv = RemoteViews(context.packageName, layout)
            rv.setTextViewText(WidgetListR.id.compact_business_name, item.label)
            rv.setTextViewText(WidgetListR.id.compact_distance, item.subtext)
            rv.setImageViewResource(
                WidgetListR.id.compact_status_dot,
                if (item.installed) WidgetListR.drawable.ic_installed_dot else WidgetListR.drawable.ic_uninstalled_dot
            )
            val bmp = icons.getIconBitmap(item.packageName)
            if (bmp != null) rv.setImageViewBitmap(WidgetListR.id.compact_app_icon, scaledForWidget(bmp, 80))
            else rv.setImageViewResource(WidgetListR.id.compact_app_icon, android.R.drawable.sym_def_app_icon)
            val clickIntent = Intent(WidgetClickReceiver.ACTION_WIDGET_ITEM_CLICK).apply {
                setPackage(context.packageName)
                putExtra(WidgetClickReceiver.EXTRA_PACKAGE_NAME, item.packageName)
            }
            val pi = PendingIntent.getBroadcast(
                context, appWidgetId * 1000 + globalIdx, clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            rv.setOnClickPendingIntent(WidgetListR.id.compact_item_root, pi)
            return rv
        }

        /** Compact cell for horizontal 4×1 strip — icon + 1-line name, fits ~55dp height. */
        private fun makeHStripItem(
            context: Context,
            appWidgetId: Int,
            globalIdx: Int,
            item: WidgetDisplayItem,
            icons: AppIconLoader,
            darkTheme: Boolean
        ): RemoteViews {
            val layout = if (darkTheme) WidgetListR.layout.widget_strip_item_h_dark
                         else WidgetListR.layout.widget_strip_item_h
            val rv = RemoteViews(context.packageName, layout)
            rv.setTextViewText(WidgetListR.id.strip_h_business_name, item.label)
            val bmp = icons.getIconBitmap(item.packageName)
            if (bmp != null) rv.setImageViewBitmap(WidgetListR.id.strip_h_app_icon, scaledForWidget(bmp, 64))
            else rv.setImageViewResource(WidgetListR.id.strip_h_app_icon, android.R.drawable.sym_def_app_icon)
            val clickIntent = Intent(WidgetClickReceiver.ACTION_WIDGET_ITEM_CLICK).apply {
                setPackage(context.packageName)
                putExtra(WidgetClickReceiver.EXTRA_PACKAGE_NAME, item.packageName)
            }
            rv.setOnClickPendingIntent(WidgetListR.id.strip_h_item_root,
                PendingIntent.getBroadcast(context, appWidgetId * 1000 + globalIdx, clickIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            return rv
        }

        private fun addItemsToContainer(
            context: Context,
            views: RemoteViews,
            appWidgetId: Int,
            columnCount: Int,
            pageItems: List<WidgetDisplayItem>,
            icons: AppIconLoader,
            darkTheme: Boolean,
            pageOffset: Int = 0
        ) {
            if (columnCount >= 2) {
                // columnCount compact items per horizontal row. Row wrapper is a LinearLayout so
                // no setInt/reflection needed — safe on MIUI.
                var idx = 0
                while (idx < pageItems.size) {
                    val row = RemoteViews(context.packageName, WidgetListR.layout.widget_item_row)
                    for (col in 0 until columnCount) {
                        val itemIdx = idx + col
                        if (itemIdx < pageItems.size) {
                            row.addView(WidgetListR.id.row_container,
                                makeCompactItem(context, appWidgetId, pageOffset + itemIdx, pageItems[itemIdx], icons, darkTheme))
                        }
                    }
                    views.addView(WidgetListR.id.items_container, row)
                    idx += columnCount
                }
            } else {
                for ((i, item) in pageItems.withIndex()) {
                    views.addView(WidgetListR.id.items_container,
                        makeRegularItem(context, appWidgetId, pageOffset + i, item, icons, darkTheme))
                }
            }
        }

        private fun setPaginationButtons(
            context: Context,
            views: RemoteViews,
            appWidgetId: Int,
            currentPage: Int,
            totalPages: Int
        ) {
            val prevIntent = Intent(ACTION_PAGE_CHANGE).apply {
                setPackage(context.packageName)
                putExtra(EXTRA_WIDGET_ID, appWidgetId)
                putExtra(EXTRA_PAGE_DELTA, -1)
            }
            views.setOnClickPendingIntent(
                WidgetListR.id.btn_prev_page,
                PendingIntent.getBroadcast(context, appWidgetId * 100 + 1, prevIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            )
            views.setViewVisibility(
                WidgetListR.id.btn_prev_page,
                if (currentPage > 0) android.view.View.VISIBLE else android.view.View.INVISIBLE
            )
            val nextIntent = Intent(ACTION_PAGE_CHANGE).apply {
                setPackage(context.packageName)
                putExtra(EXTRA_WIDGET_ID, appWidgetId)
                putExtra(EXTRA_PAGE_DELTA, 1)
            }
            views.setOnClickPendingIntent(
                WidgetListR.id.btn_next_page,
                PendingIntent.getBroadcast(context, appWidgetId * 100 + 2, nextIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            )
            views.setViewVisibility(
                WidgetListR.id.btn_next_page,
                if (currentPage < totalPages - 1) android.view.View.VISIBLE else android.view.View.INVISIBLE
            )
        }

        private fun getRepository(context: Context): BusinessAppRepository? {
            return try {
                val appContext = context.applicationContext
                val hiltEntryPoint = EntryPointAccessors.fromApplication(
                    appContext,
                    WidgetEntryPoint::class.java
                )
                hiltEntryPoint.businessAppRepository()
            } catch (e: Exception) {
                Log.e(TAG, "Could not obtain Hilt entry point", e)
                null
            }
        }

        private fun createLaunchPendingIntent(context: Context, business: Business): PendingIntent {
            Log.d(TAG, "Creating launch intent for ${business.name}, package=${business.packageName}")
            
            val intent = business.packageName?.let { packageName ->
                try {
                    // Try with basic flag first
                    context.packageManager.getPackageInfo(packageName, 0)
                    Log.d(TAG, "Package $packageName is installed (getPackageInfo succeeded)")
                    
                    // Get launch intent
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                    if (launchIntent != null) {
                        Log.d(TAG, "Launch intent found for $packageName")
                        launchIntent
                    } else {
                        Log.w(TAG, "No launch intent for $packageName, trying alternative approach")
                        
                        // Try to find any launcher activity
                        val activities = context.packageManager.queryIntentActivities(
                            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                            PackageManager.MATCH_ALL
                        ).filter { it.activityInfo.packageName == packageName }
                        
                        if (activities.isNotEmpty()) {
                            Log.d(TAG, "Found ${activities.size} launcher activities for $packageName")
                            Intent(Intent.ACTION_MAIN).apply {
                                setPackage(packageName)
                                addCategory(Intent.CATEGORY_LAUNCHER)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                        } else {
                            // Last resort: try to open app info in settings
                            Log.w(TAG, "No launcher activity found for $packageName, falling back to Play Store")
                            null
                        }
                    }
                } catch (e: PackageManager.NameNotFoundException) {
                    Log.d(TAG, "Package $packageName not found by getPackageInfo, trying getApplicationInfo")
                    // Try getApplicationInfo instead
                    try {
                        context.packageManager.getApplicationInfo(packageName, 0)
                        Log.d(TAG, "Package $packageName found via getApplicationInfo")
                        
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                        launchIntent ?: run {
                            Log.w(TAG, "No launch intent even though app exists")
                            null
                        }
                    } catch (e2: PackageManager.NameNotFoundException) {
                        Log.d(TAG, "Package $packageName not found by getApplicationInfo either, checking all packages")
                        
                        // Debug: list all packages to see if it exists
                        val allPackages = context.packageManager.getInstalledPackages(0)
                        val matching = allPackages.any { it.packageName == packageName }
                        Log.d(TAG, "Package $packageName exists in getInstalledPackages: $matching")
                        Log.d(TAG, "Total packages: ${allPackages.size}")
                        
                        null
                    } catch (e2: Exception) {
                        Log.e(TAG, "Exception in getApplicationInfo for $packageName", e2)
                        null
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "SecurityException checking package $packageName", e)
                    null
                } catch (e: Exception) {
                    Log.e(TAG, "Exception checking package $packageName", e)
                    null
                }
            } ?: run {
                Log.d(TAG, "No package name or fallback, opening Play Store search for ${business.name}")
                Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://play.google.com/store/search?q=${business.name}")
                }
            }
            
            return PendingIntent.getActivity(
                context,
                business.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun getFallbackBusinesses(): List<Business> {
            // Fallback placeholder data (the 10 UK businesses) with package names - updated to match v12 dataset
            return listOf(
                Business("Tesco", 150, "com.tesco.grocery.view"),
                Business("McDonald's", 450, "com.mcdonalds.app.uk"),
                Business("Greggs", 920, "com.mobile5.greggs"),
                Business("Costa Coffee", 120, "uk.co.club.costa.costa"),
                Business("Asda", 300, "com.asda.rewards"),
                Business("Lidl", 400, "com.lidl.eci.lidlplus"),
                Business("Premier Inn", 600, "com.whitbread.premierinn"),
                Business("Starbucks", 320, "com.starbucks.mobilecard"),
                Business("Boots", 750, "com.boots"),
                Business("WHSmith", 560, "com.whsmith.android")
            )
        }

        private fun getIconResourceForBusiness(context: Context, businessName: String): Int {
            val iconName = when (businessName) {
                "Tesco" -> "ic_business_tesco"
                "Starbucks" -> "ic_business_starbucks"
                "Boots" -> "ic_business_boots"
                "McDonald's" -> "ic_business_mcdonalds"
                "Greggs" -> "ic_business_greggs"
                "Costa Coffee" -> "ic_business_costa"
                "WHSmith" -> "ic_business_whsmith"
                "Waterstones" -> "ic_business_waterstones"
                "Pret A Manger" -> "ic_business_pret"
                "Subway" -> "ic_business_subway"
                "Asda" -> "ic_business_asda"
                "Lidl" -> "ic_business_lidl"
                "Premier Inn" -> "ic_business_premier"
                else -> "ic_app_placeholder"
            }
            return context.resources.getIdentifier(iconName, "drawable", context.packageName)
                .takeIf { it != 0 } ?: R.drawable.ic_app_placeholder
        }

        private data class Business(
            val name: String,
            val distanceMeters: Int,
            val packageName: String? = null
        )
    }
}