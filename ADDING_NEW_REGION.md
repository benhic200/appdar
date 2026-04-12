# Adding a New Country / Region

The region architecture supports an arbitrary number of regions. This document
covers both how to add a region **today** and the one-time migration that will
make adding future regions a much smaller change.

---

## How the filtering model works

There are currently **two parallel filter mechanisms**. Understanding both is
important before making changes.

### 1. `regionHint` (the scalable path)

Every `BusinessAppMapping` row has a nullable `regionHint` column:

| Value | Meaning |
|-------|---------|
| `null` | Visible in all regions |
| `"UK"` | UK only |
| `"US,AU,NZ"` | Visible in US, AU and NZ |

The filter is a single line that works for **any number of regions, forever**:

```kotlin
val effectiveRegionName = if (region == UNKNOWN) "UK" else region.name
.filter { m -> m.isCustom || m.regionHint == null || effectiveRegionName in m.regionHint.split(",") }
```

This is currently used to select the correct app variant for global brands that
have different packages per region (e.g. McDonald's has `com.mcdonalds.app.uk`
for UK and `com.mcdonalds.app` for US/AU/NZ).

### 2. Exclusive brand-name sets (the legacy path)

`NearbyBranchFinder` also computes sets like:

```kotlin
val UK_BRAND_NAMES = UK_BRANDS.keys - US_BRANDS.keys - AU_BRANDS.keys - NZ_BRANDS.keys
```

These are used in `when(region)` blocks in four places to hide, for example,
Tesco from US users. This mechanism **does not scale**: adding a new region
requires editing every existing `when` branch in four files (DashboardTab ×2,
NearbyAppsTab, NearbyAppsWidgetListFactory, AddBusinessScreen).

The long-term plan is to eliminate these blocks by migrating all region-specific
brands to use `regionHint` instead (see [Migration path](#migration-path-to-regionhint-only-filtering) below).

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

**c) Add brand map** (used for Overpass queries — keep separate from the UI filter):
```kotlin
private val CA_BRANDS = mapOf(
    "Tim Hortons"   to "Tim Hortons",
    "Canadian Tire" to "Canadian Tire",
    "Loblaws"       to "Loblaws",
    // ...
)
```

**d) Update `BRAND_TAGS` and exclusive name sets:**
```kotlin
val BRAND_TAGS = UK_BRANDS + US_BRANDS + AU_BRANDS + NZ_BRANDS + CA_BRANDS + GLOBAL_BRANDS

// Subtract CA from every existing set, and add the new CA set:
val UK_BRAND_NAMES = UK_BRANDS.keys - US_BRANDS.keys - AU_BRANDS.keys - NZ_BRANDS.keys - CA_BRANDS.keys
val US_BRAND_NAMES = US_BRANDS.keys - UK_BRANDS.keys - AU_BRANDS.keys - NZ_BRANDS.keys - CA_BRANDS.keys
val AU_BRAND_NAMES = AU_BRANDS.keys - UK_BRANDS.keys - US_BRANDS.keys - NZ_BRANDS.keys - CA_BRANDS.keys
val NZ_BRAND_NAMES = NZ_BRANDS.keys - UK_BRANDS.keys - US_BRANDS.keys - AU_BRANDS.keys - CA_BRANDS.keys
val CA_BRAND_NAMES = CA_BRANDS.keys - UK_BRANDS.keys - US_BRANDS.keys - AU_BRANDS.keys - NZ_BRANDS.keys
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

### 3. `app/…/DashboardTab.kt` (two filter blocks — `refresh()` and `silentRefresh()`)

Add a `Region.CA` branch **and** append `&& m.businessName !in NearbyBranchFinder.CA_BRAND_NAMES`
to every other existing branch (UK, US, AU, NZ, UNKNOWN):

```kotlin
NearbyBranchFinder.Region.CA ->
    m.isCustom || (m.businessName !in NearbyBranchFinder.UK_BRAND_NAMES
               && m.businessName !in NearbyBranchFinder.US_BRAND_NAMES
               && m.businessName !in NearbyBranchFinder.AU_BRAND_NAMES
               && m.businessName !in NearbyBranchFinder.NZ_BRAND_NAMES)
```

Do this in both the `refresh()` block (around line 215) and the `silentRefresh()`
block (around line 330).

---

### 4. `app/…/NearbyAppsTab.kt`

Identical filter pattern as step 3 — add `Region.CA` case and extend all others.

---

### 5. `feature-widget-list/…/NearbyAppsWidgetListFactory.kt`

Identical filter pattern as step 3 — add `Region.CA` case and extend all others.

---

### 6. `app/…/AddBusinessScreen.kt`

**a) ViewModel pref→Region mapping (in `init {}`):**
```kotlin
RegionPreference.CA -> NearbyBranchFinder.Region.CA
```

**b) `regionVisible` filter** — add `Region.CA` case and extend all others with
`&& m.businessName !in NearbyBranchFinder.CA_BRAND_NAMES`.

---

### 7. `feature-settings/…/SettingsScreen.kt`

**a) `RegionDropdown` options list:**
```kotlin
RegionPreference.CA to "Canada",
```

**b) Update the availability notice:**
```kotlin
"Currently available in UK+Ireland, US, Australia, New Zealand, and Canada. ..."
```

---

### 8. `app/…/OnboardingScreen.kt`

**a) Auto-detect in `RegionStepContent`:**
```kotlin
loc.latitude in 41.7..83.1 && loc.longitude in -95.0..-52.6 -> RegionPreference.CA
```

**b) Update the availability notice** (same string as step 7b).

---

### 9. `data/…/InitialDataset.kt`

Add `createMapping(...)` entries for each CA-specific brand. Use `isEnabled = false`
for brands that are exclusive to CA (users outside CA won't see them, and CA users
can enable what they want). For global brands that have a CA-specific app variant,
use `regionHint = "CA"` on the CA entry and add `"CA"` to the `regionHint` of
any existing non-UK variant that also covers CA (e.g. `"US,AU,NZ"` → `"US,AU,NZ,CA"`).

```kotlin
// ── Canada ─────────────────────────────────────────────────────────────────
createMapping("Tim Hortons",   "com.timhortons.timapp",             "Tim Hortons",   "coffee", isEnabled = false, regionHint = "CA"),
createMapping("Canadian Tire", "com.canadiantire.consumer.android", "Canadian Tire", "retail", isEnabled = false, regionHint = "CA"),
```

> **Note:** Set `regionHint` on CA-specific brands so that when the `when(region)` blocks
> are eventually removed, these entries are already correctly tagged.

---

## Migration path to `regionHint`-only filtering

Once all region-specific brands have a `regionHint` set, the entire `when(region)`
block in steps 3–6 above can be deleted and replaced with the single line already
present in each file:

```kotlin
.filter { m -> m.isCustom || m.regionHint == null || effectiveRegionName in m.regionHint.split(",") }
```

This eliminates the O(N²) maintenance cost. After this migration, **adding a new
region requires zero changes to the four filter files** — only the data layer
(NearbyBranchFinder brand maps, InitialDataset entries) and the UI layer
(settings dropdown, onboarding) need updating.

### What needs to happen for the migration

Set `regionHint` on every brand that is currently hidden by the exclusive-name-set
filter, then remove the `when(region)` blocks:

| Brands | `regionHint` to set |
|--------|---------------------|
| Tesco, Greggs, Boots, Argos, Next, Odeon, Vue, Cineworld, Premier Inn, Travelodge, Wetherspoons, Pizza Express, Zizzi, Yo! Sushi, TGI Fridays, Nando's, Wagamama, Papa John's, Co-op, Iceland, WHSmith, Superdrug, M&S, Waitrose, Morrisons, Asda, Sainsbury's, Aldi, Lidl, Costa Coffee, Caffè Nero, Pret A Manger, Five Guys | `"UK"` |
| Walmart, Target, Whole Foods, Walgreens, CVS, Panera Bread | `"US"` |
| Woolworths, Coles, Hungry Jack's, Chemist Warehouse, Dan Murphy's, JB Hi-Fi, Bunnings, Officeworks, Myer, Event Cinemas, Hoyts | `"AU"` |
| Countdown, New World, The Warehouse, Z, Mitre 10 | `"NZ"` |
| Global brands with no region-specific app (Subway, Starbucks, IKEA, Hilton, Marriott, Holiday Inn, BP, Shell, Costco, Taco Bell, Chipotle, Chick-fil-A, Dunkin', Shake Shack) | leave as `null` |

After setting those `regionHint` values, the `XX_BRAND_NAMES` sets in
`NearbyBranchFinder` can be removed from the filter code (the regional brand maps
`UK_BRANDS`, `US_BRANDS` etc. must be kept — they drive the Overpass download
queries, not the UI filter).

This migration also requires a `Room` database migration bump to add the new
`regionHint` values to existing rows — use the seeder's metadata-sync path
(`metaChanged` check in `DatabaseInitializer`) to propagate them automatically.

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

### Data layer
- [ ] `Region` enum entry + bbox in `NearbyBranchFinder`
- [ ] `detectRegion()` GPS check
- [ ] `resolveRegion()` preference branch
- [ ] `XX_BRANDS` map (verify OSM tags with `check_osm_brands.sh`)
- [ ] `BRAND_TAGS` updated
- [ ] All `XX_BRAND_NAMES` sets updated (subtract new region from every existing set)
- [ ] `BRAND_DB_VERSION` bumped
- [ ] `RegionPreference` enum entry

### Filter sites *(skip these once the `regionHint` migration is complete)*
- [ ] `DashboardTab` — `refresh()` filter block updated
- [ ] `DashboardTab` — `silentRefresh()` filter block updated
- [ ] `NearbyAppsTab` — filter block updated
- [ ] `NearbyAppsWidgetListFactory` — filter block updated
- [ ] `AddBusinessScreen` ViewModel mapping + `regionVisible` filter updated

### Dataset
- [ ] `InitialDataset` entries added with `regionHint` set (and `isEnabled = false` for region-exclusive brands)
- [ ] Global brand variants with a region-specific app added with correct `regionHint`
- [ ] Package names verified with `check_packages.sh`
- [ ] OSM tags verified with `check_osm_brands.sh`

### UI
- [ ] `RegionDropdown` options updated in `SettingsScreen`
- [ ] Onboarding auto-detect updated
- [ ] Availability notice updated (both `SettingsScreen` and `OnboardingScreen`)
