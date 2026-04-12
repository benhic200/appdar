# Adding a New Country / Region

Adding a new region is a **~40-line change across 5 files** — all data, no filter
logic. The filter sites never need touching again.

---

## How the filter works

All four filter sites (Dashboard, Nearby Apps, widget, Places) use a single line:

```kotlin
val effectiveRegionName = if (region == NearbyBranchFinder.Region.UNKNOWN) "UK" else region.name
.filter { m -> m.isCustom || m.regionHint?.split(",")?.contains(effectiveRegionName) ?: true }
```

`regionHint = null` → visible everywhere (truly global brands like Starbucks, Subway, IKEA).  
`regionHint = "UK"` → UK only.  
`regionHint = "AU,NZ"` → Australia and New Zealand.  
`regionHint = "US,AU,NZ"` → all non-UK regions (used for global-brand non-UK app variants).

When a new `Region.XX` enum entry is added, the filter picks it up automatically
because `region.name` matches the string in `regionHint`. **No changes to the
filter files are needed.**

---

## Currently supported regions

| Enum    | Display name   | Bbox (minLat,minLon,maxLat,maxLon) |
|---------|---------------|--------------------------------------|
| `UK`    | UK & Ireland  | 49.5,-11.0,61.0,2.0                  |
| `US`    | United States | 24.0,-125.0,49.5,-66.0               |
| `AU`    | Australia     | -43.6,113.3,-10.0,153.6              |
| `NZ`    | New Zealand   | -47.4,166.4,-34.4,178.6              |

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

**c) Add brand map** (used for Overpass queries — separate from the UI filter):
```kotlin
private val CA_BRANDS = mapOf(
    "Tim Hortons"   to "Tim Hortons",
    "Canadian Tire" to "Canadian Tire",
    "Loblaws"       to "Loblaws",
    // ...
)
```

**d) Update `BRAND_TAGS`:**
```kotlin
val BRAND_TAGS = UK_BRANDS + US_BRANDS + AU_BRANDS + NZ_BRANDS + CA_BRANDS + GLOBAL_BRANDS
```

**e) Extend `resolveRegion()`:**
```kotlin
RegionPreference.CA -> Region.CA
```

**f) Bump `BRAND_DB_VERSION`** so existing users re-download branch data:
```kotlin
private const val BRAND_DB_VERSION = 6  // was 5
```

---

### 2. `data/…/settings/UserPreferences.kt`

```kotlin
enum class RegionPreference { AUTO, UK, US, AU, NZ, CA }
```

---

### 3. `data/…/InitialDataset.kt`

Add `createMapping(...)` entries for each CA-specific brand. Use `isEnabled = false`
for brands that are exclusive to CA (users outside CA won't see them due to
`regionHint`, and CA users can enable what they want).

For global brands that have a CA-specific app variant, add a CA entry with
`regionHint = "CA"` and update the existing non-UK variant's `regionHint` to
include CA (e.g. `"US,AU,NZ"` → `"US,AU,NZ,CA"`).

```kotlin
// ── Canada ─────────────────────────────────────────────────────────────────
createMapping("Tim Hortons",   "com.timhortons.timapp",             "Tim Hortons",   "coffee", isEnabled = false, regionHint = "CA"),
createMapping("Canadian Tire", "com.canadiantire.consumer.android", "Canadian Tire", "retail", isEnabled = false, regionHint = "CA"),
```

---

### 4. `feature-settings/…/SettingsScreen.kt`

**a) `RegionDropdown` options list:**
```kotlin
RegionPreference.CA to "Canada",
```

**b) Update the availability notice:**
```kotlin
"Currently available in UK+Ireland, US, Australia, New Zealand, and Canada. ..."
```

---

### 5. `app/…/OnboardingScreen.kt`

**a) Auto-detect in `RegionStepContent`:**
```kotlin
loc.latitude in 41.7..83.1 && loc.longitude in -95.0..-52.6 -> RegionPreference.CA
```

**b) Update the availability notice** (same string as step 4b).

---

## What works automatically (no changes needed)

- All four filter sites (Dashboard, Nearby Apps, widget, Places) — handled by `regionHint`
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
   known branch (e.g. "Tim Hortons Ottawa")
2. Click the node/way → inspect the `brand=` tag value
3. Use that **exact string** (case-sensitive) in the brand map

The `validateAndResolveBrandName()` function in `NearbyBranchFinder` also
resolves tags interactively via the Add Places screen.

---

## Checklist for a new region

### Data layer
- [ ] `Region` enum entry + bbox in `NearbyBranchFinder`
- [ ] `detectRegion()` GPS check
- [ ] `resolveRegion()` preference branch
- [ ] `XX_BRANDS` map added (verify OSM tags with `check_osm_brands.sh`)
- [ ] `BRAND_TAGS` updated
- [ ] `BRAND_DB_VERSION` bumped
- [ ] `RegionPreference` enum entry

### Dataset
- [ ] `InitialDataset` entries added with `regionHint = "XX"` (and `isEnabled = false`)
- [ ] Global brand variants with a region-specific app added with correct `regionHint`
- [ ] Existing non-UK global variants updated if CA needs adding to their `regionHint`
- [ ] Package names verified with `check_packages.sh`
- [ ] OSM tags verified with `check_osm_brands.sh`

### UI
- [ ] `RegionDropdown` options updated in `SettingsScreen`
- [ ] Onboarding auto-detect updated
- [ ] Availability notice updated (both `SettingsScreen` and `OnboardingScreen`)
