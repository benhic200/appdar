# Changelog

All notable changes to this project will be documented in this file.


## Version 110 (1.110) - 2026-03-31

1. StateFlow Refactor (Dashboard + Nearby tabs)
One Overpass API call now serves all screens simultaneously via a fetchMutex in NearbyBranchFinder
markLoading() signals loading state to all observers; second caller waits then hits cache
Used withTimeoutOrNull(35_000L) directly instead of launch + collect to avoid race conditions
Added private var refreshJob: Job? to both ViewModels — cancels stale in-flight loads on refresh
2. Fun Loading Messages
12 cycling messages (3s interval) shown while Overpass fetches, replacing a plain spinner
Custom AppdarRadarAnimation (pure Compose Canvas port of appdar_loading.html) used instead of CircularProgressIndicator in loading states and the onboarding screen
3. Empty Screen Fix — Location Denied
Added DashboardState.NoLocation sealed class entry and locationAvailable: Boolean to NearbyAppsUiState
Both tabs now show a LocationUnavailableContent composable: icon + explanation + "Open App Settings" button deep-linking to system app settings
4. "No Apps to Show" + Widget ANR Fix
Root cause: onUpdate blocked the main thread via runBlocking waiting on the Overpass mutex
Fixed with goAsync() + CoroutineScope(Dispatchers.IO) across all four widget entry points (onUpdate, onAppWidgetOptionsChanged, ACTION_SCHEDULED_UPDATE, ACTION_PAGE_CHANGE)
5. Widget Placement Dialog (MIUI compatibility)
Rewrote as a two-step flow: choose style → call requestPinAppWidget → show instructions
"Go to Home Screen" button calls moveTaskToBack(true)
Always shows manual fallback instructions (MIUI silently ignores requestPinAppWidget)
6. Dashboard Uninstalled Apps Banner
Small banner appears at top of Dashboard only when uninstalled apps are in the list
Text: directs user to Places tab to remove them (Info icon + tertiary container styling)
7. Geofencing Notification — Reduced Intrusiveness
Channel ID bumped to geofencing_foreground_v2 to force recreation on existing installs (Android ignores importance changes to existing channels)
IMPORTANCE_LOW → IMPORTANCE_MIN — removes status bar icon, no sound
PRIORITY_LOW → PRIORITY_MIN — collapses to bottom of notification shade
Removed setOngoing(true) — users on Android 13+ can now dismiss it
Renamed: "Nearby Apps Widget" → "Appdar", description updated to plain English
8. Buy Me a Coffee — Settings Card
New card in Settings, before the About card
Developer 3D avatar (animated WebP) with Compose bobbing animation (±8dp, 1200ms)
Message: "If this app has saved you time, you can buy me a coffee ☕"
BMC-branded yellow button opening https://buymeacoffee.com/benhic200
Added Coil (coil-compose + coil-gif) to handle animated WebP rendering


## Version 115 (1.115) - 2026-03-31

1. Empty results no longer cached (NearbyBranchFinder)
The root cause of Cache hit: 0 branches — Overpass was timing out or returning empty, that empty result was cached for 6 hours, so every call for the next 6 hours returned nothing. Now saveCache is only called when Overpass actually returns data.

2. Disabled brands filtered from Overpass query (NearbyBranchFinder + BusinessAppMappingDao)
If you disable Tesco, it no longer gets queried. Added getEnabledBusinessNames() to the DAO, and the query now only includes brands the user has enabled — smaller query, faster response.

3. Offline detection with fallback (NearbyBranchFinder)
Before hitting Overpass, connectivity is checked. If offline, the last persisted coordinates from the DB are returned immediately (no 30s+ timeout wait), and a "No internet — showing last known locations" amber banner appears at the top of both Dashboard and Nearby tabs.

4. Widget debounce (NearbyAppsWidgetProvider)
Widget updates are now skipped if the same widget was updated within the last 5 seconds. This stops the thundering herd seen in the logcat (5 updates in 400ms).

5. Background location monitoring (GeofencingForegroundService)
The foreground service was previously just a notification stub. It now uses PASSIVE_PROVIDER (zero extra battery — piggybacks on other apps' GPS requests) with a 0.1km threshold and 5-min minimum interval. When you move 2km, it sends an ACTION_SCHEDULED_UPDATE broadcast that triggers a widget refresh automatically.

6. OkHttp blocking call cancellation (NearbyBranchFinder)
call.execute() is blocking with no suspension points, causing ANR. Fixed by registering call.cancel() via invokeOnCompletion so the OS cancels the HTTP connection the moment the coroutine times out.

7. Reduced Overpass timeouts
Reduced to 8s connect / 20s read. Worst case with 2 retries: 8+20+2+8+20 = 58s — just inside the goAsync() limit, and in practice OkHttp's own timeout now kicks in instead of waiting for the server.

8. Tab‑switch refresh spam throttling
LaunchedEffect(Unit) triggers refresh() on every tab switch. With a cold cache, rapid switching kept cancelling and restarting Overpass calls. Now auto‑refreshes are throttled to once per 30 seconds. The manual refresh button bypasses this (force = true), as does retrying after an error. The NearbyAppsViewModel now has the same 30‑second throttle as DashboardViewModel.

9. Location‑profile rename cards (Home, Work, Gym)
The rename card now appears on Home, Work, and Gym exactly the same as Custom Location 1 & 2. The nameKey DataStore key was already defined for all ProfileId values, and updateDisplayName() already handles all of them — this was purely a UI gate being removed. The name entered saves on tap of the checkmark, and profileName initialises with the current name (e.g. "Home", "Work", "Gym").

## Version 132 (1.132) - 2026-04-05

v132 release

## Version 133 (1.133) - 2026-04-05

AppDar release

## Version 134 (1.134) - 2026-04-05

AppDar release

## Version 135 (1.135) - 2026-04-05

AppDar release

## Version 140 (1.140) - 2026-04-12

AppDar release

## Version 140 (1.140) - 2026-04-12

AppDar release
