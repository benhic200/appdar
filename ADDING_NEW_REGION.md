# Adding a New Country / Region

The region architecture supports an arbitrary number of regions. Adding one is a
~60-line change across 7 files, mostly data. Two validation scripts help you
verify brand tags and app packages before shipping.

---

## How the region model works

Each region has:
- A **`Region` enum entry** with a GPS bounding box (used by Overpass and `detectRegion`)
- A **brand map** (e.g. `AU_BRANDS`) mapping business name → OSM `brand=` tag
- An **exclusive brand name set** automatically computed as:
  ```
  AU_BRAND_NAMES = AU_BRANDS.keys − UK_BRANDS.keys − US_BRANDS.keys − NZ_BRANDS.keys
  ```
  Brands present in **multiple** regional maps are excluded from all exclusive sets
  so they show up wherever they have real locations (e.g. Bunnings is in both AU and NZ).

The Places card and Dashboard filter each show a brand if it is:
- A custom place (always shown), OR
- **Not** in the exclusive set of any other region

This means truly UK-only brands never appear for AU users, and a brand like
Costco (UK + US) appears for both.

---

## Currently supported regions

| Enum  | Preference | Display name | Bbox (minLat,minLon,maxLat,maxLon) |
|-------|-----------|--------------|-------------------------------------|
| UK    | UK        | UK & Ireland | 49.5,-11.0,61.0,2.0                 |
| US    | US        | United States | 24.0,-125.0,49.5,-66.0             |
| AU    | AU        | Australia    | -43.6,113.3,-10.0,153.6             |
| NZ    | NZ        | New Zealand  | -47.4,166.4,-34.4,178.6             |

---

## Files to change (example: adding Canada — `CA`)

### 1. `data/…/NearbyBranchFinder.kt`

**a) Add enum entry:**
```kotlin
CA("Canada", "41.7,-95.0,83.1,-52.6"),
```

**b) Extend `detectRegion()`:**
```kotlin
lat in 41.7..83.1 && lon in -95.0..-52.6 -> Region.CA
```

**c) Add brand map:**
```kotlin
private val CA_BRANDS = mapOf(
    "Tim Hortons"   to "Tim Hortons",
    "Canadian Tire" to "Canadian Tire",
    "Loblaws"       to "Loblaws",
    // ...
)
```

**d) Update `BRAND_TAGS` and exclusive sets:**
```kotlin
val BRAND_TAGS = UK_BRANDS + US_BRANDS + AU_BRANDS + NZ_BRANDS + CA_BRANDS + GLOBAL_BRANDS

val UK_BRAND_NAMES = UK_BRANDS.keys - US_BRANDS.keys - AU_BRANDS.keys - NZ_BRANDS.keys - CA_BRANDS.keys
val US_BRAND_NAMES = (US_BRANDS.keys - UK_BRANDS.keys - AU_BRANDS.keys - NZ_BRANDS.keys - CA_BRANDS.keys) + setOf(...)
val AU_BRAND_NAMES = AU_BRANDS.keys - UK_BRANDS.keys - US_BRANDS.keys - NZ_BRANDS.keys - CA_BRANDS.keys
val NZ_BRAND_NAMES = NZ_BRANDS.keys - UK_BRANDS.keys - US_BRANDS.keys - AU_BRANDS.keys - CA_BRANDS.keys
val CA_BRAND_NAMES = CA_BRANDS.keys - UK_BRANDS.keys - US_BRANDS.keys - AU_BRANDS.keys - NZ_BRANDS.keys
```

**e) Extend `resolveRegion()`:**
```kotlin
RegionPreference.CA -> Region.CA
```

**f) Bump `BRAND_DB_VERSION`** so existing users re-download:
```kotlin
private const val BRAND_DB_VERSION = 6  // was 5
```

---

### 2. `data/…/settings/UserPreferences.kt`

```kotlin
enum class RegionPreference { AUTO, UK, US, AU, NZ, CA }
```

---

### 3. `app/…/AddBusinessScreen.kt`

**a) ViewModel pref→Region mapping (in `init {}`):**
```kotlin
RegionPreference.CA -> NearbyBranchFinder.Region.CA
```

**b) `regionVisible` filter — add a `Region.CA` branch and update all others to also
exclude `NZ_BRAND_NAMES`:**
```kotlin
NearbyBranchFinder.Region.CA ->
    m.isCustom || (m.businessName !in NearbyBranchFinder.UK_BRAND_NAMES
               && m.businessName !in NearbyBranchFinder.US_BRAND_NAMES
               && m.businessName !in NearbyBranchFinder.AU_BRAND_NAMES
               && m.businessName !in NearbyBranchFinder.NZ_BRAND_NAMES)
```
Also add `&& m.businessName !in NearbyBranchFinder.CA_BRAND_NAMES` to every other
`Region.XX` branch (UK, US, AU, NZ, UNKNOWN).

---

### 4. `app/…/DashboardTab.kt`

Identical filter pattern as step 3, applied in **both** `refresh()` and
`silentRefresh()`. Add `Region.CA` case and extend all others.

---

### 5. `feature-settings/…/SettingsScreen.kt`

**a) `RegionDropdown` options list:**
```kotlin
RegionPreference.CA to "Canada",
```

**b) Update the availability notice** (search for "coming soon"):
```kotlin
"Currently available in UK+Ireland, US, Australia, New Zealand, and Canada. ..."
```

---

### 6. `app/…/OnboardingScreen.kt`

**a) Auto-detect in `RegionStepContent`:**
```kotlin
loc.latitude in 41.7..83.1 && loc.longitude in -95.0..-52.6 -> RegionPreference.CA
```

**b) Update the availability notice** (same string as step 5b).

---

### 7. `data/…/InitialDataset.kt`

Add `createMapping(...)` entries for each CA brand with `isEnabled = false`
(so UK/US users don't see them until they switch region or are auto-detected):
```kotlin
// ── Canada ──────────────────────────────────────────────────────────────────
createMapping("Tim Hortons",   "com.timhortons.timapp",        "Tim Hortons",   "coffee", isEnabled = false),
createMapping("Canadian Tire", "com.canadiantire.consumer.android","Canadian Tire","retail",isEnabled = false),
```

---

## What works automatically (no changes needed)

- Overpass download with regional bbox — only returns branches in that area
- Per-region DB TTL and 30-day background refresh
- Border-crossing detection and background re-download
- `clearCache()` force re-download from Settings
- `UNKNOWN` region fallback (defaults to UK behaviour)

---

## Verification scripts

Two scripts in `scripts/` help validate brand data before shipping.
Both scripts auto-discover data from the source files — no manual list needed.

### Check app package names against Play Store
```bash
cd /path/to/phase1
./scripts/check_packages.sh
```
Reads `InitialDataset.kt`, hits each Play Store URL, and reports which
packages return 404. Note: region-locked apps (e.g. AU-only) may return
404 when checked from a non-AU server — this is expected.

### Check OSM brand tags via Overpass
```bash
cd /path/to/phase1
./scripts/check_osm_brands.sh [REGION]   # e.g. AU or NZ; omit for all
```
Reads `NearbyBranchFinder.kt`, queries Overpass for each brand tag within
its region bbox, and reports any that return 0 nodes (likely wrong tag).

---

## Finding correct OSM brand tags

1. Open [openstreetmap.org](https://www.openstreetmap.org) and search for a
   known branch (e.g. "Woolworths Sydney")
2. Click the node/way → inspect the `brand=` tag value
3. Use that **exact string** (case-sensitive) in the brand map

The `validateAndResolveBrandName()` function in `NearbyBranchFinder` also
resolves tags interactively via the Add Places screen.

---

## Checklist for a new region

- [ ] `Region` enum entry + bbox
- [ ] `detectRegion()` GPS check
- [ ] `resolveRegion()` preference branch
- [ ] `XX_BRANDS` map (verify OSM tags with `check_osm_brands.sh`)
- [ ] `BRAND_TAGS` updated
- [ ] All `XX_BRAND_NAMES` sets updated (subtract new region from each existing one too)
- [ ] `BRAND_DB_VERSION` bumped
- [ ] `RegionPreference` enum entry
- [ ] `AddBusinessScreen` ViewModel mapping + `regionVisible` filter updated
- [ ] `DashboardTab` both filter blocks updated
- [ ] `RegionDropdown` options updated
- [ ] Onboarding auto-detect updated
- [ ] Availability notice updated (both `SettingsScreen` and `OnboardingScreen`)
- [ ] `InitialDataset` entries added with `isEnabled = false`
- [ ] Package names verified with `check_packages.sh`
- [ ] OSM tags verified with `check_osm_brands.sh`
