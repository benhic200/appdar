# Adding a New Country/Region

This documents the exact steps required to add support for a new country (e.g. Australia, Canada, Germany).
The architecture is already designed for this — it is a ~50 line change, mostly data.

---

## Files to change

### 1. `data/src/main/kotlin/com/benhic/appdar/data/nearby/NearbyBranchFinder.kt`

**a) Add a new `Region` enum entry** with an Overpass bounding box (format: `minLat,minLon,maxLat,maxLon`):

```kotlin
enum class Region(val displayName: String, val bbox: String) {
    UK("UK",          "49.5,-11.0,61.0,2.0"),
    US("US",          "24.0,-125.0,49.5,-66.0"),
    AU("Australia",   "-43.6,113.3,-10.0,153.6"),  // ← new
    UNKNOWN("Unknown", "49.5,-11.0,61.0,2.0")
}
```

**b) Extend `detectRegion()`** with a GPS coordinate check for the new region:

```kotlin
fun detectRegion(lat: Double, lon: Double): Region = when {
    lat in 49.5..61.0  && lon in -11.0..2.0    -> Region.UK
    lat in 24.0..49.5  && lon in -125.0..-66.0  -> Region.US
    lat in -43.6..-10.0 && lon in 113.3..153.6  -> Region.AU  // ← new
    else -> Region.UNKNOWN
}
```

**c) Add a brand map** for the new region and merge it into `BRAND_TAGS`:

```kotlin
private val AU_BRANDS = mapOf(
    "Woolworths" to "Woolworths",
    "Coles"      to "Coles",
    // ... add more
)

val BRAND_TAGS: Map<String, String> = UK_BRANDS + US_BRANDS + AU_BRANDS + GLOBAL_BRANDS
val AU_BRAND_NAMES: Set<String> = AU_BRANDS.keys
```

**d) Bump `BRAND_DB_VERSION`** so existing users automatically re-download on the next app update:

```kotlin
private const val BRAND_DB_VERSION = 3  // was 2
```

---

### 2. `data/src/main/kotlin/com/benhic/appdar/data/local/settings/UserPreferences.kt`

Add the new option to `RegionPreference`:

```kotlin
enum class RegionPreference {
    AUTO,
    UK,
    US,
    AU   // ← new
}
```

---

### 3. `data/src/main/kotlin/com/benhic/appdar/data/nearby/NearbyBranchFinder.kt` — `resolveRegion()`

Add a `when` branch for the new preference:

```kotlin
suspend fun resolveRegion(userLat: Double, userLon: Double): Region {
    return when (settingsRepository.getCurrentPreferences().regionPreference) {
        RegionPreference.UK   -> Region.UK
        RegionPreference.US   -> Region.US
        RegionPreference.AU   -> Region.AU   // ← new
        RegionPreference.AUTO -> detectRegion(userLat, userLon)
    }
}
```

---

### 4. `feature-settings/src/main/kotlin/com/benhic/appdar/feature/settings/SettingsScreen.kt`

Add a new button to the Region card (the three-button row becomes four):

```kotlin
listOf(
    RegionPreference.AUTO to "Auto",
    RegionPreference.UK   to "UK & IE",
    RegionPreference.US   to "US",
    RegionPreference.AU   to "AU"    // ← new
).forEach { ... }
```

---

### 5. `feature-settings/src/main/kotlin/com/benhic/appdar/feature/settings/SettingsViewModel.kt`

`updateRegionPreference()` requires no changes — it already handles any `RegionPreference` value generically.

---

### 6. `app/src/main/kotlin/com/benhic/appdar/AddBusinessScreen.kt` (Places list)

Add the new region's brand names to the Places filter so they show up when the user is in that region.
Follow the same pattern as `UK_BRAND_NAMES` / `US_BRAND_NAMES` filtering that is already in place.

---

## What works automatically (no changes needed)

- Overpass download, chunked group queries, and retry logic
- Per-region DB TTL and 30-day background refresh
- Border-crossing detection and background re-download
- `clearCache()` and force re-download from Settings
- The `UNKNOWN` region fallback (currently falls back to UK bbox — acceptable)

---

## Optional follow-up (non-blocking)

- Update `UserGuideScreen.kt` Settings Reference to mention the new region option
- If the new region has brands that overlap with GLOBAL_BRANDS, no action needed —
  global brands already download into every region's bbox automatically

---

## Finding OSM brand tags for a new region

Before adding a brand, verify its exact `brand=` tag on OpenStreetMap:
1. Search for a known location on openstreetmap.org
2. Click the node/way → check the `brand` tag value
3. Use that exact string (case-sensitive) as the map value in the brand map

The `validateAndResolveBrandName()` function in `NearbyBranchFinder` can also be used
interactively via the Add Places screen to test whether a tag resolves correctly.
