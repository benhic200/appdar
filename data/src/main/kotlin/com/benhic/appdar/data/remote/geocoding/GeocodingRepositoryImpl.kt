package com.benhic.appdar.data.remote.geocoding

import android.util.LruCache
import com.benhic.appdar.data.local.geocoding.CachedAddress
import com.benhic.appdar.data.local.geocoding.CachedAddressDao
import kotlinx.coroutines.delay
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real implementation of [GeocodingRepository] that caches results in memory, disk, and network.
 * Respects the Nominatim API quota (max 1 request per second).
 */
@Singleton
class GeocodingRepositoryImpl @Inject constructor(
    private val geocodingService: GeocodingService,
    private val cachedAddressDao: CachedAddressDao,
) : GeocodingRepository {
    // In‑memory cache (max 50 entries)
    private val memoryCache = LruCache<String, GeocodedAddress>(50)

    // Moshi instance for JSON parsing (could be injected; we create a simple one inline)
    private val moshi = com.squareup.moshi.Moshi.Builder().build()
    private val geocodingResponseAdapter = moshi.adapter(GeocodingResponse::class.java)

    override suspend fun geocode(latitude: Double, longitude: Double): GeocodedAddress? {
        val id = CachedAddress.makeId(latitude, longitude)

        // 1. Memory cache
        memoryCache.get(id)?.let { return it }

        // 2. Disk cache
        val cached = cachedAddressDao.getById(id)
        if (cached != null) {
            val address = parseCachedAddress(cached)
            if (address != null) {
                // Put in memory cache for future calls
                memoryCache.put(id, address)
                return address
            }
        }

        // 3. Network (with rate‑limiting)
        // Nominatim requires at most 1 request per second.
        delay(1000L)

        val geocodingResponse: GeocodingResponse?
        try {
            geocodingResponse = geocodingService.reverseGeocode(latitude, longitude)
        } catch (e: Exception) {
            // Network error; return null (or fallback to last known address?)
            return null
        }

        if (geocodingResponse != null) {
            val address = geocodingResponse.toGeocodedAddress(latitude, longitude)
            // Store in disk cache
            val rawJson = geocodingResponseAdapter.toJson(geocodingResponse)
            val cachedAddress = CachedAddress(
                id = id,
                latitude = latitude,
                longitude = longitude,
                displayName = geocodingResponse.display_name ?: "",
                rawJson = rawJson,
                fetchedAt = System.currentTimeMillis()
            )
            cachedAddressDao.insert(cachedAddress)
            // Store in memory cache
            memoryCache.put(id, address)
            return address
        }

        return null
    }

    override suspend fun clearMemoryCache() {
        memoryCache.evictAll()
    }

    override suspend fun clearDiskCache() {
        cachedAddressDao.deleteAll()
    }

    // --- Helpers ---

    private fun parseCachedAddress(cached: CachedAddress): GeocodedAddress? {
        // Try to parse raw JSON first
        cached.rawJson?.let { json ->
            try {
                val response = geocodingResponseAdapter.fromJson(json)
                if (response != null) {
                    return response.toGeocodedAddress(cached.latitude, cached.longitude)
                }
            } catch (e: Exception) {
                // JSON malformed; fall back to display name
            }
        }

        // Fallback: create a minimal GeocodedAddress from display name only
        return GeocodedAddress(
            displayName = cached.displayName,
            road = null,
            suburb = null,
            city = null,
            county = null,
            state = null,
            postcode = null,
            country = null,
            countryCode = null,
            latitude = cached.latitude,
            longitude = cached.longitude
        )
    }

    private fun GeocodingResponse.toGeocodedAddress(lat: Double, lon: Double): GeocodedAddress =
        GeocodedAddress(
            displayName = this.display_name ?: "",
            road = this.address?.road,
            suburb = this.address?.suburb,
            city = this.address?.city,
            county = this.address?.county,
            state = this.address?.state,
            postcode = this.address?.postcode,
            country = this.address?.country,
            countryCode = this.address?.country_code,
            latitude = lat,
            longitude = lon
        )
}