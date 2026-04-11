package com.benhic.appdar.data.nearby

import android.content.Context
import android.net.Uri
import android.util.Log
import com.benhic.appdar.data.local.BranchLocation
import com.benhic.appdar.data.local.BranchLocationDao
import com.benhic.appdar.data.local.BusinessAppMappingDao
import com.benhic.appdar.data.local.settings.RegionPreference
import com.benhic.appdar.data.local.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/**
 * Finds the nearest branch of each business chain to the user's current location.
 *
 * ## Region-aware data
 * Branches are downloaded by geographic region (UK or US) using bounding boxes.
 * UK-only brands (Tesco, Greggs, etc.) have no entries in the US download and are
 * automatically hidden when the user is in the US — and vice versa.
 * Global brands (McDonald's, Starbucks, etc.) appear in both regions.
 *
 * ## How it works
 * On first launch, a single Overpass query downloads ALL branches for the current region
 * and stores them in the [BranchLocationDao] Room table. Nearest-branch lookups are then
 * instant local maths with no network call needed when the user moves.
 * After 30 days the data refreshes silently in the background.
 */
@Singleton
class NearbyBranchFinder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: BusinessAppMappingDao,
    private val branchLocationDao: BranchLocationDao,
    private val settingsRepository: SettingsRepository
) {
    // ── Region ───────────────────────────────────────────────────────────────

    enum class Region(val displayName: String, val bbox: String) {
        UK("UK", "49.5,-11.0,61.0,2.0"),          // Great Britain + Ireland
        US("US", "24.0,-125.0,49.5,-66.0"),
        UNKNOWN("Unknown", "49.5,-11.0,61.0,2.0")  // fallback to UK/IE bbox
    }

    fun detectRegion(lat: Double, lon: Double): Region = when {
        lat in 49.5..61.0 && lon in -11.0..2.0  -> Region.UK
        lat in 24.0..49.5 && lon in -125.0..-66.0 -> Region.US
        else -> Region.UNKNOWN
    }

    /**
     * Returns the last GPS-detected region stored in SharedPreferences,
     * or UNKNOWN if never detected. Used to seed UI before location resolves.
     */
    fun lastKnownRegion(): Region {
        val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(PREF_LAST_REGION, null)
        return Region.values().firstOrNull { it.name == name } ?: Region.UNKNOWN
    }

    /**
     * Returns the effective region to use for downloads and filtering.
     * If the user has pinned a region in Settings, that takes priority over GPS detection.
     */
    suspend fun resolveRegion(userLat: Double, userLon: Double): Region {
        return when (settingsRepository.getCurrentPreferences().regionPreference) {
            RegionPreference.UK   -> Region.UK
            RegionPreference.US   -> Region.US
            RegionPreference.AUTO -> detectRegion(userLat, userLon)
        }
    }

    companion object {
        private const val TAG = "NearbyBranchFinder"

        /** Overpass endpoints tried in order. On 429 the next mirror is used automatically. */
        private val OVERPASS_ENDPOINTS = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
            "https://maps.mail.ru/osm/tools/overpass/api/interpreter"
        )

        /** How long to keep the downloaded branch database before re-fetching. */
        private const val BRANCH_DB_TTL_MS = 30 * 24 * 60 * 60 * 1000L  // 30 days

        /** After receiving HTTP 429/503, skip Overpass for this long before trying again. */
        private const val OVERPASS_BACKOFF_MS = 10 * 60 * 1000L  // 10 minutes

        /** Number of brands per Overpass query group — ~5 groups for the default brand set. */
        private const val DOWNLOAD_GROUP_SIZE = 12

        /**
         * Increment whenever the brand list changes in a way that existing users should re-download
         * (e.g. Irish brands added in v1.120, regional UK/US split in v1.115).
         * Old installs store 0 (key absent), so any value > 0 triggers a one-time wipe + re-download.
         */
        private const val BRAND_DB_VERSION = 3

        private const val PREFS = "NearbyBranchCache"
        private const val PREF_BRAND_DB_VERSION = "brand_db_version"

        /** Per-region download timestamps, e.g. "branch_db_downloaded_at_UK" */
        private fun prefKeyForRegion(region: Region) = "branch_db_downloaded_at_${region.name}"

        /** Last region the user was detected in — triggers a download when they cross a border. */
        private const val PREF_LAST_REGION = "last_region"

        // ── Brand tags ───────────────────────────────────────────────────────
        // Brands are tagged by the regions they're relevant to.
        // The bbox download automatically filters:
        //   • UK bbox  → only returns UK/GLOBAL branches → US-only brands show nothing
        //   • US bbox  → only returns US/GLOBAL branches → UK-only brands show nothing
        // No manual hiding logic needed in the UI.

        /** UK + Ireland brands — not present in US bbox results. */
        private val UK_BRANDS = mapOf(
            // ── UK supermarkets ──────────────────────────────────────────────
            "Tesco"          to "Tesco",
            "Sainsbury's"    to "Sainsbury's",
            "Asda"           to "Asda",
            "Morrisons"      to "Morrisons",
            "Aldi"           to "Aldi",
            "Lidl"           to "Lidl",
            "Waitrose"       to "Waitrose",
            "M&S"            to "Marks & Spencer",
            "Iceland"        to "Iceland",
            "Co-op"          to "Co-op",
            "Spar"           to "Spar",
            "Londis"         to "Londis",
            "Primark"        to "Primark",
            // ── Irish supermarkets & convenience ─────────────────────────────
            "SuperValu"      to "SuperValu",
            "Centra"         to "Centra",
            "Dunnes Stores"  to "Dunnes Stores",
            "Penneys"        to "Penneys",
            "Circle K"       to "Circle K",
            "Applegreen"     to "Applegreen",
            // ── UK coffee & food ─────────────────────────────────────────────
            "Costa Coffee"   to "Costa Coffee",
            "Caffè Nero"     to "Caffè Nero",
            "Pret A Manger"  to "Pret A Manger",
            "Greggs"         to "Greggs",
            "Nando's"        to "Nando's",
            "Five Guys"      to "Five Guys",
            "Wagamama"       to "wagamama",
            "Papa John's"    to "Papa John's",
            "Leon"           to "LEON",
            "Wetherspoons"   to "Wetherspoon",
            "Pizza Express"  to "Pizza Express",
            "Zizzi"          to "Zizzi",
            "Yo! Sushi"      to "Yo! Sushi",
            "TGI Fridays"    to "TGI Friday's",
            // ── UK retail & travel ───────────────────────────────────────────
            "Boots"          to "Boots",
            "Superdrug"      to "Superdrug",
            "WHSmith"        to "WH Smith",
            "Argos"          to "Argos",
            "Next"           to "Next",
            "JD Sports"      to "JD Sports",
            "Sports Direct"  to "Sports Direct",
            "Currys"         to "Currys",
            "Odeon"          to "Odeon",
            "Vue"            to "Vue",
            "Cineworld"      to "Cineworld",
            "Premier Inn"    to "Premier Inn",
            "Travelodge"     to "Travelodge"
        )

        /** US-only brands — not present in UK bbox results. */
        private val US_BRANDS = mapOf(
            "Walmart"        to "Walmart",
            "Target"         to "Target",
            "Whole Foods"    to "Whole Foods Market",
            "Walgreens"      to "Walgreens",
            "CVS"            to "CVS",
            "Panera Bread"   to "Panera Bread"
        )

        /**
         * Global brands — present in both UK and US bbox results.
         * A single entry covers both regions; no "(US)" duplicate needed.
         */
        private val GLOBAL_BRANDS = mapOf(
            "McDonald's"     to "McDonald's",
            "Burger King"    to "Burger King",
            "KFC"            to "KFC",
            "Subway"         to "Subway",
            "Starbucks"      to "Starbucks",
            "Pizza Hut"      to "Pizza Hut",
            "Domino's"       to "Domino's",
            "IKEA"           to "IKEA",
            "Hilton"         to "Hilton",
            "Marriott"       to "Marriott",
            "Holiday Inn"    to "Holiday Inn",
            "BP"             to "BP",
            "Shell"          to "Shell",
            // US-origin brands now with UK presence
            "Costco"         to "Costco",
            "Taco Bell"      to "Taco Bell",
            "Chipotle"       to "Chipotle Mexican Grill",
            "Chick-fil-A"    to "Chick-fil-A",
            "Dunkin'"        to "Dunkin'",
            "Shake Shack"    to "Shake Shack"
        )

        /**
         * All brand tags combined — used for DB seeding and validation lookups.
         * UK brands, US brands, and global brands merged into one map.
         */
        val BRAND_TAGS: Map<String, String> = UK_BRANDS + US_BRANDS + GLOBAL_BRANDS

        /** Business names that are UK-only — hidden when the user is detected in the US. */
        val UK_BRAND_NAMES: Set<String> = UK_BRANDS.keys

        /**
         * Business names that are US-only — hidden when the user is detected in the UK.
         * Includes the "(US)" variant entries seeded for global brands that have separate
         * UK and US app packages (e.g. "McDonald's (US)" uses com.mcdonalds.app rather
         * than the UK package com.mcdonalds.app.uk).
         */
        val US_BRAND_NAMES: Set<String> = US_BRANDS.keys + setOf(
            "McDonald's (US)", "Burger King (US)", "KFC (US)", "Domino's (US)"
        )
    }

    /**
     * Shared state observed by all screens.
     * [statusMessage] is non-null only during the one-time regional data download.
     * [downloadProgress] is non-null while chunked group downloads are in progress;
     * the pair is (completedGroups, totalGroups).
     */
    data class BranchFetch(
        val branches: Map<String, Pair<Double, Double>> = emptyMap(),
        val isLoading: Boolean = false,
        val isOffline: Boolean = false,
        val statusMessage: String? = null,
        val downloadProgress: Pair<Int, Int>? = null
    )

    private val _fetchState = MutableStateFlow(BranchFetch())
    val fetchState: StateFlow<BranchFetch> = _fetchState.asStateFlow()

    /** Serialises the Overpass download so concurrent callers don't double-fetch. */
    private val downloadMutex = Mutex()

    /** Background scope for silent refreshes that must not block the caller. */
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** System.currentTimeMillis() until which Overpass calls should be skipped (after 429/503). */
    @Volatile private var overpassBackoffUntilMs = 0L

    /** OkHttp client for large bulk downloads (long read timeout to match Overpass timeout:300). */
    private val bulkClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(360, TimeUnit.SECONDS)   // 300s Overpass server timeout + 60s transfer headroom
        .build()

    /** OkHttp client for quick validation and single-brand queries. */
    private val quickClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Reads the nearest branch per brand from the local DB **without triggering any network call**.
     * Returns an empty map if the DB has not been populated yet.
     *
     * Use this from the widget — downloads are the app's job, not the widget's.
     */
    suspend fun getLocalNearestBranches(
        userLat: Double,
        userLon: Double
    ): Map<String, Pair<Double, Double>> {
        val count = withContext(Dispatchers.IO) { branchLocationDao.count() }
        if (count == 0) return emptyMap()
        return withContext(Dispatchers.Default) { calculateNearestFromLocalDb(userLat, userLon) }
    }

    /** Call before launching [findNearestBranches] so collectors see isLoading = true immediately. */
    fun markLoading() {
        _fetchState.update { it.copy(isLoading = true) }
    }

    /**
     * Clears the TTL for the given region so the next call re-downloads branch data.
     * If no region is given, clears all regions.
     */
    fun clearCache(region: Region? = null) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (region != null) {
            prefs.remove(prefKeyForRegion(region))
            Log.d(TAG, "Branch DB TTL cleared for ${region.displayName}")
        } else {
            Region.values().forEach { prefs.remove(prefKeyForRegion(it)) }
            Log.d(TAG, "Branch DB TTL cleared for all regions")
        }
        prefs.apply()
    }

    /**
     * Clears the TTL cache AND deletes all downloaded branch locations from the local DB.
     *
     * After this call [findNearestBranches] will follow the "DB empty" path and perform a
     * foreground download (same as first launch), showing the progress indicator to the user.
     *
     * Use this for user-initiated "Force Re-download" so the dashboard always shows fresh
     * data rather than serving stale results while quietly refreshing in the background.
     */
    suspend fun clearCacheAndWipeDb() {
        clearCache()
        withContext(Dispatchers.IO) { branchLocationDao.deleteAll() }
        Log.i(TAG, "Branch DB wiped — next findNearestBranches will re-download")
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                as android.net.ConnectivityManager
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.isConnected == true
        }
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Returns a map of businessName → (latitude, longitude) of the nearest branch.
     *
     * - **First launch / new region:** downloads all branches for the current region, then returns.
     * - **Normal use (DB fresh for this region):** instant local calculation, no network.
     * - **After 30-day TTL:** returns existing results immediately, refreshes silently in background.
     * - **Region change (e.g. UK → US):** serves existing data instantly, downloads new region in background.
     */
    suspend fun findNearestBranches(
        userLat: Double,
        userLon: Double
    ): Map<String, Pair<Double, Double>> {
        val currentRegion = resolveRegion(userLat, userLon)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        // One-time migration: if the stored brand-DB version is older than BRAND_DB_VERSION,
        // wipe all TTL timestamps so every region re-downloads on the next open.
        // This runs once then writes the new version — subsequent launches skip it instantly.
        val storedVersion = prefs.getInt(PREF_BRAND_DB_VERSION, 0)
        if (storedVersion < BRAND_DB_VERSION) {
            Log.i(TAG, "Brand DB version $storedVersion → $BRAND_DB_VERSION — clearing TTL for fresh download")
            Region.values().forEach { r -> prefs.edit().remove(prefKeyForRegion(r)).apply() }
            prefs.edit().putInt(PREF_BRAND_DB_VERSION, BRAND_DB_VERSION).apply()
        }
        val lastRegionName = prefs.getString(PREF_LAST_REGION, null)
        val regionChanged = lastRegionName != null && lastRegionName != currentRegion.name
        val dbCount = withContext(Dispatchers.IO) { branchLocationDao.count() }
        val isStale = isRegionStale(currentRegion)

        when {
            dbCount == 0 -> {
                // First launch — must download before we can show anything
                Log.i(TAG, "Branch DB empty — downloading ${currentRegion.displayName} data")
                ensureBranchDbPopulated(currentRegion, background = false)
                // If the DB is still empty the download failed (offline / Overpass unreachable).
                // Return empty now so the ViewModel can show an error state.
                val newCount = withContext(Dispatchers.IO) { branchLocationDao.count() }
                if (newCount == 0) return emptyMap()
            }
            regionChanged -> {
                // User has crossed into a new region — serve existing data immediately,
                // download new region in background so it's ready on next refresh
                Log.i(TAG, "Region changed $lastRegionName → ${currentRegion.name} — background download")
                backgroundScope.launch { ensureBranchDbPopulated(currentRegion, background = true) }
            }
            isStale -> {
                // Same region but 30-day TTL expired — serve immediately, refresh silently
                Log.d(TAG, "Branch DB stale for ${currentRegion.displayName} — background refresh")
                backgroundScope.launch { ensureBranchDbPopulated(currentRegion, background = true) }
            }
        }

        // Always record the current region
        prefs.edit().putString(PREF_LAST_REGION, currentRegion.name).apply()

        // Calculate nearest from local DB — instant, no network
        return withContext(Dispatchers.Default) {
            calculateNearestFromLocalDb(userLat, userLon)
        }
    }

    private fun isRegionStale(region: Region): Boolean {
        val downloadedAt = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(prefKeyForRegion(region), 0L)
        return System.currentTimeMillis() - downloadedAt > BRANCH_DB_TTL_MS
    }

    // ── Local DB calculation ─────────────────────────────────────────────────

    private suspend fun calculateNearestFromLocalDb(
        userLat: Double,
        userLon: Double
    ): Map<String, Pair<Double, Double>> {
        val enabledNames = withContext(Dispatchers.IO) { dao.getEnabledBusinessNames().toSet() }
        val enabledBuiltInBrands = BRAND_TAGS.filterKeys { it in enabledNames }
        val customBrands = withContext(Dispatchers.IO) {
            dao.getCustomOsmBrandMappings().associate { it.businessName to it.osmBrandTag!! }
        }
        val allBrands = enabledBuiltInBrands + customBrands
        val enabledBrandTags = allBrands.values.toSet()

        val allBranches = withContext(Dispatchers.IO) { branchLocationDao.getAll() }

        val nearest     = mutableMapOf<String, Pair<Double, Double>>()
        val nearestDist = mutableMapOf<String, Double>()

        for (branch in allBranches) {
            // Normalise curly apostrophe at read-time for rows stored before the parse-time fix.
            val brandTag = branch.brandTag.replace('\u2019', '\'')
            if (brandTag !in enabledBrandTags) continue
            val dist = haversineMeters(userLat, userLon, branch.lat, branch.lon)
            if (dist < (nearestDist[brandTag] ?: Double.MAX_VALUE)) {
                nearest[brandTag]     = branch.lat to branch.lon
                nearestDist[brandTag] = dist
            }
        }

        val result = allBrands.mapNotNull { (businessName, brandTag) ->
            nearest[brandTag]?.let { coords -> businessName to coords }
        }.toMap()

        Log.d(TAG, "Nearest branches: ${result.size} brands from ${allBranches.size} locations in DB")
        _fetchState.value = BranchFetch(
            branches = result,
            isLoading = false,
            isOffline = !isNetworkAvailable()
        )
        return result
    }

    // ── Branch DB population ─────────────────────────────────────────────────

    private suspend fun ensureBranchDbPopulated(region: Region, background: Boolean) {
        if (!isNetworkAvailable()) {
            val count = withContext(Dispatchers.IO) { branchLocationDao.count() }
            if (count == 0) {
                Log.w(TAG, "Branch DB empty and no network — cannot download")
                _fetchState.value = BranchFetch(isLoading = false, isOffline = true)
            } else {
                Log.w(TAG, "Branch DB stale but no network — using existing $count locations")
            }
            return
        }

        val nowMs = System.currentTimeMillis()
        if (nowMs < overpassBackoffUntilMs) {
            Log.w(TAG, "Overpass backoff active — skipping download")
            return
        }

        downloadMutex.withLock {
            // Re-check after acquiring lock — bail if fresh data appeared while we were waiting,
            // or if the backoff was set by the coroutine that held the mutex before us.
            if (!isRegionStale(region) && withContext(Dispatchers.IO) { branchLocationDao.count() } > 0) {
                return@withLock
            }
            if (System.currentTimeMillis() < overpassBackoffUntilMs) {
                Log.w(TAG, "Overpass backoff active (set while waiting for mutex) — skipping")
                return@withLock
            }

            Log.i(TAG, if (background) "Background: Downloading ${region.displayName}" else "Downloading ${region.displayName}")
            if (!background) _fetchState.update { it.copy(isLoading = true, statusMessage = "Downloading ${region.displayName} locations…") }

            try {
                val downloaded = downloadAllBranchesForRegion(region) { current, total ->
                    if (!background) {
                        _fetchState.update { it.copy(
                            downloadProgress = current to total,
                            statusMessage = "Downloading ${region.displayName} locations…"
                        )}
                    }
                }

                // If Overpass returned HTTP 200 but zero locations (runtime error, empty bbox, etc.)
                // treat it as a failure so we retry next time rather than caching a broken TTL.
                if (downloaded.isEmpty()) {
                    Log.e(TAG, "${region.displayName} download returned 0 locations — not saving TTL")
                    if (!background) {
                        _fetchState.update { it.copy(isLoading = false, statusMessage = null, downloadProgress = null, isOffline = true) }
                    }
                    return@withLock
                }

                if (!background) _fetchState.update { it.copy(
                    downloadProgress = null,
                    statusMessage = "Saving ${downloaded.size} locations to your device…"
                )}
                withContext(Dispatchers.IO) {
                    // Only delete branches from this region's bbox area before inserting fresh data.
                    // This preserves the other region's data if already downloaded.
                    // bbox format: "minLat,minLon,maxLat,maxLon" (Overpass south,west,north,east)
                    val (bboxMinLat, bboxMinLon, bboxMaxLat, bboxMaxLon) =
                        region.bbox.split(",").map { it.trim().toDouble() }
                    branchLocationDao.deleteByBbox(bboxMinLat, bboxMaxLat, bboxMinLon, bboxMaxLon)
                    branchLocationDao.insertAll(downloaded)
                }
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putLong(prefKeyForRegion(region), System.currentTimeMillis()).apply()
                Log.i(TAG, "Branch DB updated for ${region.displayName}: ${downloaded.size} locations")
                if (!background) _fetchState.update { it.copy(statusMessage = null, downloadProgress = null) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Do not swallow coroutine cancellation — let it propagate so OkHttp calls
                // are properly cancelled via invokeOnCompletion and connections are released.
                Log.w(TAG, "${region.displayName} download cancelled")
                if (!background) _fetchState.update { it.copy(isLoading = false, statusMessage = null, downloadProgress = null) }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "${region.displayName} branch download failed: ${e.message}")
                if (!background) {
                    val dbStillEmpty = withContext(Dispatchers.IO) { branchLocationDao.count() } == 0
                    _fetchState.update { it.copy(isLoading = false, statusMessage = null, downloadProgress = null, isOffline = dbStillEmpty) }
                }
            }
        }
    }

    /**
     * Downloads brands in chunks of [DOWNLOAD_GROUP_SIZE], calling [onProgress] after each group
     * completes with (completedGroups, totalGroups). Throws on unrecoverable failure.
     */
    private suspend fun downloadAllBranchesForRegion(
        region: Region,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): List<BranchLocation> {
        val customBrands = withContext(Dispatchers.IO) {
            dao.getCustomOsmBrandMappings().map { it.osmBrandTag!! }
        }
        val allBrandTags = (BRAND_TAGS.values + customBrands).distinct()
        val groups = allBrandTags.chunked(DOWNLOAD_GROUP_SIZE)
        val total = groups.size
        val allLocations = mutableListOf<BranchLocation>()

        // Emit (0, total) immediately so the UI switches from indeterminate to determinate right away
        onProgress?.invoke(0, total)

        for ((index, group) in groups.withIndex()) {
            val groupNum = index + 1
            Log.i(TAG, "Downloading group $groupNum/$total for ${region.displayName} (${group.size} brands)")
            val locations = downloadBrandGroup(group, region)
            allLocations.addAll(locations)
            onProgress?.invoke(groupNum, total)
        }
        return allLocations
    }

    /** Runs a single Overpass query for a subset of brand tags within [region]. */
    private suspend fun downloadBrandGroup(brandTags: List<String>, region: Region): List<BranchLocation> {
        val brandsQuery = brandTags.joinToString("\n") { brand ->
            val escaped = brand.replace("\"", "\\\"")
            """  nwr["brand"="$escaped"](${region.bbox});"""
        }
        val query = "[out:json][timeout:120][maxsize:52428800];\n(\n$brandsQuery\n);\nout center;"
        val body = "data=${Uri.encode(query)}".toRequestBody("application/x-www-form-urlencoded".toMediaType())

        var lastException: Exception? = null
        for (url in OVERPASS_ENDPOINTS) {
            val request = Request.Builder().url(url).post(body)
                .header("User-Agent", "Appdar-Android/1.0 (nearby business widget; low-frequency cached queries)")
                .build()
            try {
                val call = bulkClient.newCall(request)
                currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
                val (code, json) = withContext(Dispatchers.IO) {
                    call.execute().use { response ->
                        response.code to (response.body?.string() ?: "")
                    }
                }
                if (code == 429 || code == 503) {
                    Log.w(TAG, "Group download: $url rate-limited (HTTP $code) — trying next mirror")
                    lastException = IOException("HTTP $code")
                    continue
                }
                if (code !in 200..299) throw IOException("Overpass HTTP $code")
                if (json.isEmpty()) throw IllegalStateException("Empty response body")
                if (url != OVERPASS_ENDPOINTS.first()) Log.i(TAG, "Group succeeded via mirror: $url")
                // Log first 300 chars so we can see Overpass errors (runtime errors, empty results) in logcat
                Log.d(TAG, "Overpass response preview: ${json.take(300).replace('\n', ' ')}")
                return parseBranchLocations(json)
            } catch (e: IOException) {
                lastException = e
                Log.w(TAG, "Group download failed at $url — trying next mirror")
            }
        }
        overpassBackoffUntilMs = System.currentTimeMillis() + OVERPASS_BACKOFF_MS
        Log.w(TAG, "All mirrors failed for group — backing off ${OVERPASS_BACKOFF_MS / 60000} min")
        throw lastException ?: IOException("All Overpass mirrors failed")
    }

    private fun parseBranchLocations(json: String): List<BranchLocation> {
        val elements = try {
            JSONObject(json).getJSONArray("elements")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Overpass JSON", e)
            return emptyList()
        }
        val result = mutableListOf<BranchLocation>()
        for (i in 0 until elements.length()) {
            val el    = elements.getJSONObject(i)
            val tags  = el.optJSONObject("tags") ?: continue
            // Normalise curly/smart apostrophe (U+2019) → straight apostrophe (U+0027) so OSM
            // tags like "Domino\u2019s" match the straight-quote strings in BRAND_TAGS.
            val brand = tags.optString("brand")
                .takeIf { it.isNotEmpty() }
                ?.replace('\u2019', '\'')
                ?: continue
            val (lat, lon) = when (el.getString("type")) {
                "node" -> el.getDouble("lat") to el.getDouble("lon")
                else   -> {
                    val center = el.optJSONObject("center") ?: continue
                    center.getDouble("lat") to center.getDouble("lon")
                }
            }
            result.add(BranchLocation(brandTag = brand, lat = lat, lon = lon))
        }
        Log.d(TAG, "Parsed ${result.size} branch locations from Overpass response")
        return result
    }

    // ── Single-brand download (new custom place) ─────────────────────────────

    /**
     * Downloads all branches for a single [brandTag] within the user's current region bbox
     * and appends them to the local DB. Called immediately after a new custom place passes
     * OSM validation so the new brand appears instantly without waiting for the 30-day refresh.
     */
    fun downloadBrandAndStore(brandTag: String, userLat: Double? = null, userLon: Double? = null) {
        val region = if (userLat != null && userLon != null) detectRegion(userLat, userLon) else Region.UK
        backgroundScope.launch {
            if (!isNetworkAvailable()) {
                Log.w(TAG, "No network — cannot download branches for '$brandTag'")
                return@launch
            }
            Log.i(TAG, "Downloading ${region.displayName} branches for new brand: '$brandTag'")
            try {
                val escaped = brandTag.replace("\"", "\\\"")
                val query = "[out:json][timeout:60][maxsize:10485760];\n" +
                    "(\n  nwr[\"brand\"=\"$escaped\"](${region.bbox});\n);\nout center;"
                val body = "data=${Uri.encode(query)}".toRequestBody("application/x-www-form-urlencoded".toMediaType())

                var responseJson: String? = null
                for (url in OVERPASS_ENDPOINTS) {
                    val request = Request.Builder().url(url).post(body)
                        .header("User-Agent", "Appdar-Android/1.0 (nearby business widget; low-frequency cached queries)")
                        .build()
                    try {
                        val json = withContext(Dispatchers.IO) {
                            quickClient.newCall(request).execute().use { resp ->
                                if (!resp.isSuccessful) { Log.w(TAG, "Brand download $url returned ${resp.code}"); null }
                                else resp.body?.string()
                            }
                        }
                        if (json != null) { responseJson = json; break }
                    } catch (e: IOException) {
                        Log.w(TAG, "Brand download failed at $url: ${e.message}")
                    }
                }
                val json = responseJson ?: run { Log.e(TAG, "All mirrors failed for '$brandTag'"); return@launch }
                val branches = parseBranchLocations(json)
                if (branches.isNotEmpty()) {
                    withContext(Dispatchers.IO) { branchLocationDao.insertAll(branches) }
                    Log.i(TAG, "Stored ${branches.size} ${region.displayName} branches for '$brandTag'")
                } else {
                    Log.w(TAG, "No ${region.displayName} branches found for '$brandTag'")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Brand download failed for '$brandTag'", e)
            }
        }
    }

    // ── Brand validation ─────────────────────────────────────────────────────

    suspend fun validateAndResolveBrandName(
        businessName: String,
        userLat: Double? = null,
        userLon: Double? = null
    ): String? {
        val trimmed = businessName.trim()
        BRAND_TAGS[trimmed]?.let { return it }
        BRAND_TAGS.entries.firstOrNull { it.key.equals(trimmed, ignoreCase = true) }?.value?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                val escaped = trimmed.replace("\"", "\\\"")
                val areaFilter = if (userLat != null && userLon != null)
                    "(around:100000,$userLat,$userLon)" else ""
                val query = """[out:json][timeout:30];nwr["brand"~"^${escaped}${"$"}","i"]$areaFilter;out ids 1;"""
                val body = "data=${Uri.encode(query)}".toRequestBody("application/x-www-form-urlencoded".toMediaType())
                val request = Request.Builder().url(OVERPASS_ENDPOINTS.first()).post(body)
                    .header("User-Agent", "Appdar-Android/1.0 (nearby business widget; low-frequency cached queries)")
                    .build()
                val responseJson = quickClient.newCall(request).execute().use { it.body?.string() }
                    ?: return@withContext null
                val elements = JSONObject(responseJson).optJSONArray("elements")
                if (elements != null && elements.length() > 0) {
                    Log.d(TAG, "OSM brand validated: '$trimmed' — ${elements.length()} location(s)")
                    trimmed
                } else {
                    Log.d(TAG, "OSM brand not found: '$trimmed'")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "OSM brand validation failed for '$trimmed'", e)
                null
            }
        }
    }
    // ── Haversine ────────────────────────────────────────────────────────────

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
