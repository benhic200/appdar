package com.example.nearbyappswidget.data.nearby

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/**
 * Finds the nearest real branch of each UK business chain to the user's location
 * using the Overpass API (OpenStreetMap data — free, no API key required).
 *
 * Results are cached in SharedPreferences for [CACHE_TTL_MS] or until the user
 * moves more than [LOCATION_THRESHOLD_M] metres, whichever comes first.
 */
@Singleton
class NearbyBranchFinder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "NearbyBranchFinder"
        private const val OVERPASS_URL = "https://overpass-api.de/api/interpreter"
        private const val SEARCH_RADIUS_M = 15000
        private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L   // 6 hours
        private const val LOCATION_THRESHOLD_M = 2000.0          // 2 km
        private const val PREFS = "NearbyBranchCache"

        /**
         * Maps each business name (as stored in the DB) to its OSM brand tag.
         * OSM uses the brand tag to identify chain stores consistently.
         * Overpass API works globally, so UK and US brands are both covered.
         */
        private val BRAND_TAGS = mapOf(
            // UK supermarkets
            "Tesco"           to "Tesco",
            "Sainsbury's"     to "Sainsbury's",
            "Asda"            to "Asda",
            "Morrisons"       to "Morrisons",
            "Aldi"            to "Aldi",
            "Lidl"            to "Lidl",
            "Waitrose"        to "Waitrose",
            "M&S"             to "Marks & Spencer",
            "Iceland"         to "Iceland",
            "Co-op"           to "Co-op",
            // UK coffee & bakery
            "Costa Coffee"    to "Costa Coffee",
            "Starbucks"       to "Starbucks",
            "Caffè Nero"      to "Caffè Nero",
            "Pret A Manger"   to "Pret A Manger",
            "Greggs"          to "Greggs",
            // UK fast food
            "McDonald's"      to "McDonald's",
            "Burger King"     to "Burger King",
            "KFC"             to "KFC",
            "Subway"          to "Subway",
            "Nando's"         to "Nando's",
            "Five Guys"       to "Five Guys",
            "Wagamama"        to "wagamama",
            "Domino's"        to "Domino's",
            "Papa John's"     to "Papa John's",
            "Pizza Hut"       to "Pizza Hut",
            "Leon"            to "LEON",
            // UK pubs & retail
            "Wetherspoons"    to "Wetherspoons",
            "Boots"           to "Boots",
            "WHSmith"         to "WH Smith",
            // Hotels (global)
            "Premier Inn"     to "Premier Inn",
            "Travelodge"      to "Travelodge",
            "Hilton"          to "Hilton",
            "Marriott"        to "Marriott",
            "Holiday Inn"     to "Holiday Inn",
            // US supermarkets & retail
            "Walmart"         to "Walmart",
            "Target"          to "Target",
            "Costco"          to "Costco",
            "Whole Foods"     to "Whole Foods Market",
            "Walgreens"       to "Walgreens",
            "CVS"             to "CVS",
            // US fast food & coffee — separate entries so both UK+US apps can match
            "McDonald's (US)" to "McDonald's",
            "Burger King (US)"to "Burger King",
            "KFC (US)"        to "KFC",
            "Taco Bell"       to "Taco Bell",
            "Chipotle"        to "Chipotle",
            "Chick-fil-A"     to "Chick-fil-A",
            "Dunkin'"         to "Dunkin'",
            "Panera Bread"    to "Panera Bread",
            "Shake Shack"     to "Shake Shack",
            "Domino's (US)"   to "Domino's"
        )
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * Returns a map of businessName → (latitude, longitude) of the nearest branch
     * within [SEARCH_RADIUS_M] metres of ([userLat], [userLon]).
     *
     * Businesses not found in the search area are omitted from the result —
     * callers should fall back to the database coordinate in that case.
     */
    suspend fun findNearestBranches(
        userLat: Double,
        userLon: Double
    ): Map<String, Pair<Double, Double>> {
        loadCache(userLat, userLon)?.let { cached ->
            Log.d(TAG, "Cache hit: ${cached.size} branches")
            return cached
        }

        Log.d(TAG, "Cache miss — querying Overpass for ($userLat, $userLon)")
        return withContext(Dispatchers.IO) {
            try {
                val result = fetchFromOverpass(userLat, userLon)
                saveCache(userLat, userLon, result)
                Log.d(TAG, "Overpass returned ${result.size} nearest branches")
                result
            } catch (e: Exception) {
                Log.e(TAG, "Overpass query failed", e)
                emptyMap()
            }
        }
    }

    private fun fetchFromOverpass(userLat: Double, userLon: Double): Map<String, Pair<Double, Double>> {
        // nwr = node/way/relation — catches both pin-point nodes and area-mapped stores.
        // "out center" returns the centroid for ways/relations so we always get a lat/lon.
        val brandsQuery = BRAND_TAGS.values.joinToString("\n") { brand ->
            val escaped = brand.replace("\"", "\\\"")
            """  nwr["brand"="$escaped"](around:$SEARCH_RADIUS_M,$userLat,$userLon);"""
        }
        val query = "[out:json][timeout:20];\n(\n$brandsQuery\n);\nout center;"

        val body = "data=${Uri.encode(query)}"
            .toRequestBody("application/x-www-form-urlencoded".toMediaType())
        val request = Request.Builder().url(OVERPASS_URL).post(body).build()

        val responseJson = client.newCall(request).execute().use { response ->
            response.body?.string() ?: throw IllegalStateException("Empty Overpass response")
        }

        return parseResponse(responseJson, userLat, userLon)
    }

    private fun parseResponse(
        json: String,
        userLat: Double,
        userLon: Double
    ): Map<String, Pair<Double, Double>> {
        val elements = JSONObject(json).getJSONArray("elements")

        // For each brand, track the nearest element found
        val nearest    = mutableMapOf<String, Pair<Double, Double>>()
        val nearestDist = mutableMapOf<String, Double>()

        for (i in 0 until elements.length()) {
            val el   = elements.getJSONObject(i)
            val tags = el.optJSONObject("tags") ?: continue
            val brand = tags.optString("brand").takeIf { it.isNotEmpty() } ?: continue

            val (elLat, elLon) = when (el.getString("type")) {
                "node" -> el.getDouble("lat") to el.getDouble("lon")
                else   -> {
                    // way / relation — Overpass returns a "center" object with "out center"
                    val center = el.optJSONObject("center") ?: continue
                    center.getDouble("lat") to center.getDouble("lon")
                }
            }

            val dist = haversineMeters(userLat, userLon, elLat, elLon)
            if (dist < (nearestDist[brand] ?: Double.MAX_VALUE)) {
                nearest[brand]     = elLat to elLon
                nearestDist[brand] = dist
            }
        }

        // Re-key from OSM brand name back to our business name
        return BRAND_TAGS.mapNotNull { (businessName, brandTag) ->
            nearest[brandTag]?.let { coords -> businessName to coords }
        }.toMap().also { result ->
            result.forEach { (name, coords) ->
                Log.d(TAG, "Nearest $name: (${coords.first}, ${coords.second}) " +
                    "dist=${nearestDist[BRAND_TAGS[name]]?.toInt()}m")
            }
        }
    }

    // ── Cache ────────────────────────────────────────────────────────────────

    private fun loadCache(userLat: Double, userLon: Double): Map<String, Pair<Double, Double>>? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val age = System.currentTimeMillis() - prefs.getLong("timestamp", 0)
        if (age > CACHE_TTL_MS) return null

        val cachedLat = prefs.getFloat("lat", 0f).toDouble()
        val cachedLon = prefs.getFloat("lon", 0f).toDouble()
        if (haversineMeters(userLat, userLon, cachedLat, cachedLon) > LOCATION_THRESHOLD_M) return null

        val data = prefs.getString("data", null) ?: return null
        return try {
            val obj = JSONObject(data)
            obj.keys().asSequence().mapNotNull { key ->
                val arr = obj.optJSONArray(key) ?: return@mapNotNull null
                key to (arr.getDouble(0) to arr.getDouble(1))
            }.toMap()
        } catch (e: Exception) {
            Log.w(TAG, "Cache parse failed", e)
            null
        }
    }

    private fun saveCache(
        userLat: Double,
        userLon: Double,
        data: Map<String, Pair<Double, Double>>
    ) {
        val obj = JSONObject()
        data.forEach { (name, coords) ->
            obj.put(name, JSONArray().apply { put(coords.first); put(coords.second) })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("timestamp", System.currentTimeMillis())
            .putFloat("lat", userLat.toFloat())
            .putFloat("lon", userLon.toFloat())
            .putString("data", obj.toString())
            .apply()
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
