package com.benhic.appdar.data.remote.geocoding

interface GeocodingRepository {
    /**
     * Returns a geocoded address for the given coordinates, or null if geocoding fails.
     * Implements caching (memory → disk → network) and respects API quotas.
     */
    suspend fun geocode(latitude: Double, longitude: Double): GeocodedAddress?

    /**
     * Clears the in‑memory cache (useful for testing or low‑memory scenarios).
     */
    suspend fun clearMemoryCache()

    /**
     * Clears the disk cache (Room table).
     */
    suspend fun clearDiskCache()
}