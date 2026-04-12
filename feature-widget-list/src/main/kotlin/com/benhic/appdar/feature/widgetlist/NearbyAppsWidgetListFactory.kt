package com.benhic.appdar.feature.widgetlist

import android.appwidget.AppWidgetManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.annotation.RequiresApi
import com.benhic.appdar.data.repository.BusinessAppRepository
import com.benhic.appdar.data.local.settings.SettingsRepository
import com.benhic.appdar.data.local.settings.UserPreferences
import com.benhic.appdar.data.local.profiles.LocationProfileRepository
import com.benhic.appdar.data.local.profiles.PROFILE_GEOFENCE_METERS
import com.benhic.appdar.data.local.profiles.ProfileId
import com.benhic.appdar.feature.widgetlist.R
import com.benhic.appdar.feature.widgetlist.di.WidgetListEntryPoint
import com.benhic.appdar.feature.widgetlist.util.AppIconLoader
import com.benhic.appdar.data.nearby.NearbyBranchFinder
import com.benhic.appdar.feature.location.DistanceCalculator
import com.benhic.appdar.feature.location.LocationProvider
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Creates RemoteViews for each item in the scrollable widget list.
 *
 * This factory is responsible for:
 * - Loading business‑app mappings (from repository)
 * - Creating a RemoteViews for each business
 * - Binding data (name, distance, icon, status) to the item layout
 * - Setting click intents to launch the corresponding app
 *
 * On API 31+, after loading data the factory calls AppWidgetManager.updateAppWidget()
 * directly with RemoteCollectionItems so each item gets its own setOnClickPendingIntent,
 * bypassing the setPendingIntentTemplate fill-in mechanism which some launchers (MIUI) don't fire.
 */
internal class NearbyAppsWidgetListFactory(
    private val context: Context,
    private val appWidgetId: Int
) : RemoteViewsService.RemoteViewsFactory {

    companion object {
        private const val TAG = "NearbyAppsWidgetListFactory"
    }

    private lateinit var businessAppRepository: BusinessAppRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var distanceCalculator: DistanceCalculator
    private lateinit var iconLoader: AppIconLoader
    private lateinit var locationProvider: LocationProvider
    private lateinit var locationProfileRepository: LocationProfileRepository
    private lateinit var nearbyBranchFinder: NearbyBranchFinder
    private var userPreferences: UserPreferences? = null
    private var businessItems: List<BusinessItem> = emptyList()

    init {
        Log.d(TAG, "Widget list factory instance created for widget $appWidgetId")
    }

    override fun onCreate() {
        Log.d(TAG, "Widget list factory created for widget $appWidgetId")
        try {
            // Initialize data sources via Hilt entry point
            val entryPoint = EntryPointAccessors.fromApplication(
                context,
                WidgetListEntryPoint::class.java
            )
            Log.d(TAG, "Acquiring Hilt entry point...")
            businessAppRepository = entryPoint.businessAppRepository().also { Log.d(TAG, "Repository acquired: ${it != null}") }
            settingsRepository = entryPoint.settingsRepository().also { Log.d(TAG, "Settings repository acquired: ${it != null}") }
            distanceCalculator = entryPoint.distanceCalculator().also { Log.d(TAG, "Distance calculator acquired: ${it != null}") }
            locationProvider = entryPoint.locationProvider().also { Log.d(TAG, "Location provider acquired: ${it != null}") }
            iconLoader = entryPoint.appIconLoader().also { Log.d(TAG, "Icon loader acquired: ${it != null}") }
            locationProfileRepository = entryPoint.locationProfileRepository().also { Log.d(TAG, "Location profile repository acquired: ${it != null}") }
            nearbyBranchFinder = entryPoint.nearbyBranchFinder().also { Log.d(TAG, "NearbyBranchFinder acquired: ${it != null}") }
            Log.d(TAG, "Dependencies loaded: repository=${businessAppRepository != null}, " +
                "settings=${settingsRepository != null}, " +
                "location=${locationProvider != null}, " +
                "calculator=${distanceCalculator != null}, " +
                "iconLoader=${iconLoader != null}, " +
                "profileRepo=${locationProfileRepository != null}")
            loadBusinessItems()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize widget list factory", e)
        }
    }

    override fun onDataSetChanged() {
        Log.d(TAG, "Widget list data set changed for widget $appWidgetId")
        // Reload data when the widget notifies that data should be refreshed
        loadBusinessItems()
    }

    override fun onDestroy() {
        Log.d(TAG, "Widget list factory destroyed for widget $appWidgetId")
        // Clean up resources
        iconLoader.clearCache()
    }

    override fun getCount(): Int {
        Log.d(TAG, "getCount: ${businessItems.size} items")
        return businessItems.size
    }

    override fun getViewAt(position: Int): RemoteViews {
        Log.d(TAG, "getViewAt position $position: ${businessItems.getOrNull(position)?.name}")
        val business = businessItems[position]
        val views = buildItemViews(business)

        // Fill-in: supply the package name for WidgetClickReceiver via the template PendingIntent.
        // (Used on API < 31 / launchers that properly support the fill-in mechanism.)
        val fillInIntent = Intent().apply {
            putExtra(WidgetClickReceiver.EXTRA_PACKAGE_NAME, business.packageName)
        }
        views.setOnClickFillInIntent(R.id.item_root, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = businessItems[position].hashCode().toLong()

    override fun hasStableIds(): Boolean = true

    /**
     * Builds the item RemoteViews with name, distance, status dot, and icon populated.
     * Click handler is NOT set here — callers apply either fill-in or direct PendingIntent.
     */
    private fun buildItemViews(business: BusinessItem): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_list_item)

        views.setTextViewText(R.id.business_name, business.name)

        val distanceText = if (business.profileLabel != null) {
            "@ ${business.profileLabel}"
        } else {
            userPreferences?.let { prefs ->
                distanceCalculator.formatDistanceWithPreferences(
                    business.distanceMeters.toDouble(),
                    prefs
                )
            } ?: if (business.distanceMeters < 1000) {
                context.getString(R.string.distance_format, business.distanceMeters)
            } else {
                val km = business.distanceMeters / 1000.0
                context.getString(R.string.distance_km_format, km)
            }
        }
        views.setTextViewText(R.id.distance, distanceText)

        val statusDotRes = if (business.isInstalled) {
            R.drawable.ic_installed_dot
        } else {
            R.drawable.ic_uninstalled_dot
        }
        views.setImageViewResource(R.id.status_dot, statusDotRes)

        val iconBitmap = iconLoader.getIconBitmap(business.packageName)
        if (iconBitmap != null) {
            views.setImageViewBitmap(R.id.app_icon, iconBitmap)
        } else {
            views.setImageViewResource(R.id.app_icon, android.R.drawable.sym_def_app_icon)
        }

        return views
    }

    private fun loadBusinessItems() {
        Log.d(TAG, "loadBusinessItems started")
        runBlocking {
            try {
                // Load user preferences
                userPreferences = settingsRepository.getCurrentPreferences()
                Log.d(TAG, "Loaded user preferences: distance unit = ${userPreferences?.distanceUnit}")

                // Ensure database is seeded
                businessAppRepository.initialize()
                // Load all enabled business mappings — region filter applied below after location is known
                val allEnabledMappings = businessAppRepository.getAllMappings().first().filter { it.isEnabled }
                Log.d(TAG, "Loaded ${allEnabledMappings.size} business mappings")

                // Get current location (fallback to stub London)
                Log.d(TAG, "Attempting to get current location from provider: $locationProvider")
                var currentLocation: android.location.Location? = null
                try {
                    // Use timeout to avoid hanging forever (10 seconds)
                    currentLocation = kotlinx.coroutines.withTimeoutOrNull(10000L) {
                        Log.d(TAG, "Calling getCurrentLocation() with timeout")
                        locationProvider.getCurrentLocation()
                    }
                    Log.d(TAG, "getCurrentLocation() with timeout returned: $currentLocation")
                } catch (e: Exception) {
                    Log.e(TAG, "Exception in getCurrentLocation() with timeout", e)
                }
                if (currentLocation == null) {
                    Log.w(TAG, "Location is null after timeout, distance calculations will fallback to geofence radius")
                } else {
                    Log.d(TAG, "Location coordinates: (${currentLocation.latitude}, ${currentLocation.longitude}) accuracy=${currentLocation.accuracy}")
                }

                // Check if user is at a saved profile location — if so show that profile's apps.
                val matchedProfile = if (currentLocation != null && userPreferences != null) {
                    ProfileId.values().map { id -> locationProfileRepository.getProfile(id).first() }
                        .firstOrNull { profile ->
                            val lat = profile.latitude ?: return@firstOrNull false
                            val lon = profile.longitude ?: return@firstOrNull false
                            profile.selectedApps.isNotEmpty() &&
                                distanceCalculator.calculateDistanceMeters(
                                    currentLocation.latitude, currentLocation.longitude, lat, lon
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
                        Pair(pkg, installed)
                    }
                    iconLoader.preloadIcons(profileItems.map { it.first })
                    businessItems = profileItems.map { (pkg, installed) ->
                        val label = try {
                            val info = context.packageManager.getApplicationInfo(pkg, 0)
                            context.packageManager.getApplicationLabel(info).toString()
                        } catch (_: Exception) { pkg }
                        BusinessItem(
                            name = label,
                            packageName = pkg,
                            distanceMeters = 0,
                            isInstalled = installed,
                            profileLabel = matchedProfile.displayName.substringBefore(" Apps")
                        )
                    }
                    // Log final items
                    businessItems.forEachIndexed { idx, item ->
                        Log.d(TAG, "Item ${idx+1}: ${item.name} - ${item.profileLabel}")
                    }
                    Log.d(TAG, "loadBusinessItems completed with profile items, ${businessItems.size} items")
                    // API 31+ update (same as below)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        updateWidgetWithRemoteCollectionItems()
                    }
                    return@runBlocking
                }

                // Apply region filter — use live GPS region if available, otherwise fall back
                // to the last known region (stored in SharedPrefs). UNKNOWN defaults to "UK".
                val region = if (currentLocation != null)
                    nearbyBranchFinder.resolveRegion(currentLocation.latitude, currentLocation.longitude)
                else
                    nearbyBranchFinder.lastKnownRegion()
                val effectiveRegionName = if (region == NearbyBranchFinder.Region.UNKNOWN) "UK" else region.name
                val mappings = allEnabledMappings.filter { m -> m.isCustom || m.regionHint?.split(",")?.contains(effectiveRegionName) ?: true }

                // Create business items with real distances where possible
                Log.d(TAG, "Starting mapNotNull over ${mappings.size} mappings")
                val itemsWithDistance = mappings.mapNotNull { mapping ->
                    // Check if the corresponding app is installed
                    val installed = try {
                        context.packageManager.getPackageInfo(mapping.packageName, PackageManager.MATCH_ALL)
                        true
                    } catch (e: PackageManager.NameNotFoundException) {
                        false
                    }
                    // Debug log mapping coordinates
                    Log.d(TAG, "Mapping ${mapping.businessName}: lat=${mapping.latitude}, lon=${mapping.longitude}, geofenceRadius=${mapping.geofenceRadius}")

                    // Compute distance if we have both location and mapping coordinates
                    val distanceMeters = if (currentLocation != null &&
                        mapping.latitude != null && mapping.longitude != null) {
                        // Safe to use !! because we just checked for null
                        val lat = mapping.latitude!!
                        val lon = mapping.longitude!!
                        Log.d(TAG, "Computing distance for ${mapping.businessName}: " +
                            "lat=$lat, lon=$lon, " +
                            "location=(${currentLocation.latitude}, ${currentLocation.longitude})")
                        val distance = distanceCalculator.calculateDistanceMeters(
                            currentLocation.latitude,
                            currentLocation.longitude,
                            lat,
                            lon
                        ).toInt()
                        Log.d(TAG, "Computed distance for ${mapping.businessName}: ${distance}m (geofence radius = ${mapping.geofenceRadius}m)")
                        distance
                    } else {
                        // Fallback to geofence radius (placeholder)
                        Log.w(TAG, "Falling back to geofence radius for ${mapping.businessName}: " +
                            "lat=${mapping.latitude}, lon=${mapping.longitude}, " +
                            "location=$currentLocation, radius=${mapping.geofenceRadius}")
                        mapping.geofenceRadius
                    }
                    BusinessItem(
                        name = mapping.businessName,
                        packageName = mapping.packageName,
                        distanceMeters = distanceMeters,
                        isInstalled = installed
                    )
                }
                // Sort by distance (nearest first)
                businessItems = itemsWithDistance
                    .let { if (currentLocation != null) it.filter { item -> item.distanceMeters <= (userPreferences?.searchRadiusMeters ?: Int.MAX_VALUE) } else it }
                    .sortedBy { it.distanceMeters }
                // Log final distances
                businessItems.forEachIndexed { idx, item ->
                    Log.d(TAG, "Item ${idx+1}: ${item.name} - ${item.distanceMeters}m")
                }
                // Preload icons for smoother scrolling
                iconLoader.preloadIcons(businessItems.map { it.packageName })
                Log.d(TAG, "loadBusinessItems completed successfully, ${businessItems.size} items")

                // API 31+: update the widget directly with RemoteCollectionItems so each item
                // gets its own PendingIntent. This bypasses the fill-in template mechanism which
                // some launchers (e.g. MIUI) silently ignore for collection widget item taps.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    updateWidgetWithRemoteCollectionItems()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load business items", e)
                businessItems = emptyList()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun updateWidgetWithRemoteCollectionItems() {
        Log.d(TAG, "updateWidgetWithRemoteCollectionItems: building items for widget $appWidgetId")
        try {
            val appWidgetManager = AppWidgetManager.getInstance(context)

            val mainViews = RemoteViews(context.packageName, R.layout.widget_nearby_apps_scrollable)

            // Build each item with its own direct PendingIntent (no fill-in template needed)
            val collectionBuilder = RemoteViews.RemoteCollectionItems.Builder()
            for ((idx, business) in businessItems.withIndex()) {
                val itemViews = buildItemViews(business)

                // Create a direct activity PendingIntent that launches the target app (or Play Store)
                // This bypasses any broadcast receiver, which MIUI might block for collection widgets.
                Log.d(TAG, "Creating direct activity PendingIntent for package: ${business.packageName}")
                val targetIntent = context.packageManager.getLaunchIntentForPackage(business.packageName)
                val pendingIntent = if (targetIntent != null) {
                    // App is installed — launch it directly
                    Log.d(TAG, "App installed, launching directly: ${business.packageName}")
                    targetIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    PendingIntent.getActivity(
                        context,
                        appWidgetId * 1000 + idx,
                        targetIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                } else {
                    // App not installed — open Play Store via market:// (direct app) or fallback to web
                    Log.d(TAG, "App not installed, opening Play Store for: ${business.packageName}")
                    val playStoreIntent = Intent(Intent.ACTION_VIEW).apply {
                        data = android.net.Uri.parse("market://details?id=${business.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    PendingIntent.getActivity(
                        context,
                        appWidgetId * 1000 + idx,
                        playStoreIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                }
                itemViews.setOnClickPendingIntent(R.id.item_root, pendingIntent)

                collectionBuilder.addItem(idx.toLong(), itemViews)
            }
            mainViews.setRemoteAdapter(R.id.widget_list, collectionBuilder.build())
            mainViews.setEmptyView(R.id.widget_list, R.id.empty_state)

            // Refresh button — targets the widget provider broadcast receiver
            val refreshIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                component = ComponentName(
                    context.packageName,
                    "com.benhic.appdar.feature.widget.NearbyAppsWidgetProvider"
                )
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            mainViews.setOnClickPendingIntent(R.id.refresh_icon, refreshPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, mainViews)
            Log.d(TAG, "Widget $appWidgetId updated with RemoteCollectionItems (${businessItems.size} items)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update widget with RemoteCollectionItems", e)
        }
    }

    private data class BusinessItem(
        val name: String,
        val packageName: String,
        val distanceMeters: Int,
        val isInstalled: Boolean,
        val profileLabel: String? = null
    )
}
