package com.benhic.appdar

import androidx.compose.foundation.ExperimentalFoundationApi
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.location.Location
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import com.psoffritti.taptargetcompose.TapTargetCoordinator
import com.psoffritti.taptargetcompose.TapTargetDefinition
import com.psoffritti.taptargetcompose.TextDefinition
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.benhic.appdar.data.local.profiles.LocationProfile
import com.benhic.appdar.data.local.profiles.LocationProfileRepository
import com.benhic.appdar.data.local.profiles.PROFILE_GEOFENCE_METERS
import com.benhic.appdar.data.local.profiles.ProfileId
import com.benhic.appdar.data.local.settings.DistanceUnit
import com.benhic.appdar.data.local.settings.SettingsRepository
import com.benhic.appdar.data.nearby.NearbyBranchFinder
import com.benhic.appdar.data.repository.BusinessAppRepository
import com.benhic.appdar.feature.location.DistanceCalculator
import com.benhic.appdar.feature.location.LocationProvider
import com.benhic.appdar.feature.widgetlist.util.AppIconLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import com.benhic.appdar.WalkthroughState
import com.benhic.appdar.WalkthroughStep

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.math.roundToInt

// ── UI state ────────────────────────────────────────────────────────────────

data class DashboardItem(
    val label: String,
    val packageName: String,
    val subtitle: String,   // distance string or profile name
    val isInstalled: Boolean,
    val iconBitmap: android.graphics.Bitmap?,
    val branchLat: Double? = null,
    val branchLon: Double? = null
)

sealed class DashboardState {
    object Loading    : DashboardState()
    object NoLocation : DashboardState()
    data class AtProfile(val profileName: String, val apps: List<DashboardItem>, val location: Location?) : DashboardState()
    data class Nearby(val items: List<DashboardItem>, val location: Location?, val isOffline: Boolean = false) : DashboardState()
    data class Error(val message: String) : DashboardState()
}

// ── ViewModel ───────────────────────────────────────────────────────────────

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val businessAppRepository: BusinessAppRepository,
    private val settingsRepository: SettingsRepository,
    private val locationProfileRepository: LocationProfileRepository,
    private val distanceCalculator: DistanceCalculator,
    private val locationProvider: LocationProvider,
    private val appIconLoader: AppIconLoader,
    private val nearbyBranchFinder: NearbyBranchFinder,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow<DashboardState>(DashboardState.Loading)
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    /** Exposes the Overpass download status message (non-null only during first-time UK data download). */
    val branchStatusMessage: StateFlow<String?> = nearbyBranchFinder.fetchState
        .map { it.statusMessage }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Exposes chunked download progress as (completedGroups, totalGroups), or null when not downloading. */
    val branchDownloadProgress: StateFlow<Pair<Int, Int>?> = nearbyBranchFinder.fetchState
        .map { it.downloadProgress }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var refreshJob: Job? = null
    private var lastRefreshMs = 0L

    init {
        refresh()
        startPeriodicDistanceUpdate()
        startPeriodicFullRefresh()
    }

    /**
     * Every 10 seconds, re-calculates distances from the cached nearest-branch positions
     * using the latest device location. No network call, no DB read — pure local maths.
     */
    private fun startPeriodicDistanceUpdate() {
        viewModelScope.launch {
            while (isActive) {
                val intervalMs = settingsRepository.getCurrentPreferences().refreshIntervalSeconds * 1000L
                delay(intervalMs)
                val current = _state.value as? DashboardState.Nearby ?: continue
                val location = withTimeoutOrNull(3_000L) {
                    locationProvider.getCurrentLocation()
                } ?: continue
                val prefs = settingsRepository.getCurrentPreferences()
                val branches = nearbyBranchFinder.fetchState.value.branches
                val updated = current.items.map { item ->
                    val (lat, lon) = branches[item.label] ?: return@map item
                    val dist = distanceCalculator.calculateDistanceMeters(
                        location.latitude, location.longitude, lat, lon
                    ).toInt()
                    item.copy(subtitle = formatDist(dist, prefs.distanceUnit),
                              branchLat = lat, branchLon = lon)
                }.sortedBy { item ->
                    val (lat, lon) = branches[item.label] ?: return@sortedBy Int.MAX_VALUE
                    distanceCalculator.calculateDistanceMeters(
                        location.latitude, location.longitude, lat, lon
                    ).toInt()
                }
                _state.value = DashboardState.Nearby(updated, location, current.isOffline)
            }
        }
    }

    /**
     * Periodically rebuilds the full list at the widget refresh interval — silently,
     * with no loading flash. This is what adds new businesses that have come into range.
     * The 10-second [startPeriodicDistanceUpdate] handles the lightweight distance-only updates.
     */
    private fun startPeriodicFullRefresh() {
        viewModelScope.launch {
            while (isActive) {
                val prefs = settingsRepository.getCurrentPreferences()
                delay(prefs.refreshIntervalSeconds * 1000L)
                if (!settingsRepository.getCurrentPreferences().lowPowerMode) {
                    silentRefresh()
                }
            }
        }
    }

    /**
     * Rebuilds the nearest-branch list silently — no [DashboardState.Loading] transition.
     * On failure, keeps the current list intact. Used by the periodic auto-refresh so
     * there's no loading flash; items reorder and new entries animate in via animateItem().
     */
    private fun silentRefresh() {
        // Only meaningful when already showing a Nearby list — skip if Loading/Error/NoLocation
        if (_state.value !is DashboardState.Nearby) return
        viewModelScope.launch {
            try {
                val prefs = settingsRepository.getCurrentPreferences()
                val location = withTimeoutOrNull(5_000L) { locationProvider.getCurrentLocation() }
                    ?: return@launch

                // If user has moved to a profile location, hand off to the full refresh path
                val profiles = ProfileId.values().map { id ->
                    locationProfileRepository.getProfile(id).first()
                }
                val matched = profiles.firstOrNull { p ->
                    val lat = p.latitude ?: return@firstOrNull false
                    val lon = p.longitude ?: return@firstOrNull false
                    p.selectedApps.isNotEmpty() &&
                        distanceCalculator.calculateDistanceMeters(
                            location.latitude, location.longitude, lat, lon
                        ) <= PROFILE_GEOFENCE_METERS
                }
                if (matched != null) {
                    // Transition to profile silently — no loading state needed
                    val items = matched.selectedApps.map { pkg ->
                        DashboardItem(
                            label = appLabel(pkg),
                            packageName = pkg,
                            subtitle = "@ ${matched.displayName.substringBefore(" Apps")}",
                            isInstalled = isInstalled(pkg),
                            iconBitmap = appIconLoader.getIconBitmap(pkg)
                        )
                    }
                    _state.value = DashboardState.AtProfile(matched.displayName, items, location)
                    return@launch
                }

                // Rebuild from branch cache — findNearestBranches hits the local DB (no network
                // call unless the 30-day TTL has expired), so this is effectively instant.
                businessAppRepository.initialize()
                val region = nearbyBranchFinder.resolveRegion(location.latitude, location.longitude)
                val effectiveRegionName = if (region == NearbyBranchFinder.Region.UNKNOWN) "UK" else region.name
                val mappings = businessAppRepository.getAllMappings().first()
                    .filter { it.isEnabled }
                    .filter { m -> m.isCustom || m.regionHint?.split(",")?.contains(effectiveRegionName) ?: true }
                val branches = nearbyBranchFinder.findNearestBranches(location.latitude, location.longitude)
                if (branches.isEmpty()) return@launch  // offline — keep showing current list

                val items = mappings.mapNotNull { m ->
                    val (lat, lon) = branches[m.businessName]
                        ?: ((m.latitude ?: return@mapNotNull null) to (m.longitude ?: return@mapNotNull null))
                    val dist = distanceCalculator.calculateDistanceMeters(
                        location.latitude, location.longitude, lat, lon
                    ).toInt()
                    DashboardItem(
                        label = m.businessName,
                        packageName = m.packageName,
                        subtitle = formatDist(dist, prefs.distanceUnit),
                        isInstalled = isInstalled(m.packageName),
                        iconBitmap = appIconLoader.getIconBitmap(m.packageName),
                        branchLat = lat,
                        branchLon = lon
                    )
                }.sortedBy { item ->
                    val (lat, lon) = branches[item.label] ?: return@sortedBy Int.MAX_VALUE
                    distanceCalculator.calculateDistanceMeters(
                        location.latitude, location.longitude, lat, lon
                    ).toInt()
                }
                val wasOffline = (_state.value as? DashboardState.Nearby)?.isOffline ?: false
                _state.value = DashboardState.Nearby(items, location, wasOffline)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                // Silent failure — keep the current list as-is
            }
        }
    }

    fun refresh(force: Boolean = false) {
        // Avoid hammering Overpass when the user switches tabs rapidly.
        // Auto-refreshes (LaunchedEffect) are throttled; manual taps always proceed.
        val now = System.currentTimeMillis()
        if (!force && now - lastRefreshMs < 30_000L && _state.value !is DashboardState.Error) return
        lastRefreshMs = now
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.value = DashboardState.Loading
            try {
                val prefs = settingsRepository.getCurrentPreferences()
                val location = withTimeoutOrNull(5_000L) { locationProvider.getCurrentLocation() }

                // Check if user is at a saved profile location
                val profiles = ProfileId.values().map { id ->
                    locationProfileRepository.getProfile(id).first()
                }
                val matched: LocationProfile? = if (location != null) {
                    profiles.firstOrNull { p ->
                        val lat = p.latitude ?: return@firstOrNull false
                        val lon = p.longitude ?: return@firstOrNull false
                        p.selectedApps.isNotEmpty() &&
                            distanceCalculator.calculateDistanceMeters(
                                location.latitude, location.longitude, lat, lon
                            ) <= PROFILE_GEOFENCE_METERS
                    }
                } else null

                if (matched != null) {
                    val items = matched.selectedApps.map { pkg ->
                        DashboardItem(
                            label = appLabel(pkg),
                            packageName = pkg,
                            subtitle = "@ ${matched.displayName.substringBefore(" Apps")}",
                            isInstalled = isInstalled(pkg),
                            iconBitmap = appIconLoader.getIconBitmap(pkg)
                        )
                    }
                    _state.value = DashboardState.AtProfile(matched.displayName, items, location)
                    return@launch
                }

                // Not at any profile — need location for nearby search.
                if (location == null) {
                    _state.value = DashboardState.NoLocation
                    return@launch
                }

                // The mutex inside findNearestBranches ensures only one Overpass call fires
                // even when Dashboard, Nearby, and the widget all start simultaneously.
                // If another coroutine is already fetching, this call waits for the mutex
                // and then cache-hits — no duplicate network call.
                businessAppRepository.initialize()
                val region = nearbyBranchFinder.resolveRegion(location.latitude, location.longitude)
                val effectiveRegionName = if (region == NearbyBranchFinder.Region.UNKNOWN) "UK" else region.name
                val mappings = businessAppRepository.getAllMappings().first()
                    .filter { it.isEnabled }
                    .filter { m -> m.isCustom || m.regionHint?.split(",")?.contains(effectiveRegionName) ?: true }

                nearbyBranchFinder.markLoading()
                val branches = nearbyBranchFinder.findNearestBranches(location.latitude, location.longitude)
                val isOffline = nearbyBranchFinder.fetchState.value.isOffline

                // DB still empty after download attempt — Overpass unreachable
                if (branches.isEmpty() && isOffline) {
                    _state.value = DashboardState.Error(
                        "Could not download location data.\nCheck your connection and tap Refresh."
                    )
                    return@launch
                }

                val items = mappings.mapNotNull { m ->
                    val (lat, lon) = branches[m.businessName]
                        ?: ((m.latitude ?: return@mapNotNull null) to (m.longitude ?: return@mapNotNull null))
                    val dist = if (location != null)
                        distanceCalculator.calculateDistanceMeters(
                            location.latitude, location.longitude, lat, lon
                        ).toInt()
                    else m.geofenceRadius
                    DashboardItem(
                        label = m.businessName,
                        packageName = m.packageName,
                        subtitle = formatDist(dist, prefs.distanceUnit),
                        isInstalled = isInstalled(m.packageName),
                        iconBitmap = appIconLoader.getIconBitmap(m.packageName),
                        branchLat = lat,
                        branchLon = lon
                    )
                }.sortedBy { item ->
                    val (lat, lon) = branches[item.label] ?: return@sortedBy Int.MAX_VALUE
                    if (location != null)
                        distanceCalculator.calculateDistanceMeters(
                            location.latitude, location.longitude, lat, lon
                        ).toInt()
                    else Int.MAX_VALUE
                }
                _state.value = DashboardState.Nearby(items, location, isOffline)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = DashboardState.Error(e.localizedMessage ?: "Unknown error")
            }
        }
    }

    private fun isInstalled(pkg: String) = try {
        context.packageManager.getPackageInfo(pkg, PackageManager.MATCH_ALL)
        true
    } catch (_: PackageManager.NameNotFoundException) { false }

    private fun appLabel(pkg: String) = try {
        val info = context.packageManager.getApplicationInfo(pkg, 0)
        context.packageManager.getApplicationLabel(info).toString()
    } catch (_: Exception) { pkg }

    private fun formatDist(meters: Int, unit: DistanceUnit) = when (unit) {
        DistanceUnit.MILES -> String.format("%.1f mi", meters * 0.000621371)
        DistanceUnit.KILOMETERS -> if (meters < 1000) "$meters m" else String.format("%.1f km", meters * 0.001)
        DistanceUnit.METERS -> "$meters m"
        DistanceUnit.FEET -> String.format("%.0f ft", meters * 3.28084)
    }
}

// ── Composables ─────────────────────────────────────────────────────────────

@Composable
fun DashboardContent(
    viewModel: DashboardViewModel = hiltViewModel(),
    walkthroughState: WalkthroughState = WalkthroughState(),
    walkthroughCompleted: Boolean = true,
    onWalkthroughNext: () -> Unit = {},
    onWalkthroughSkip: () -> Unit = {},
    onChangeScreen: (String) -> Unit = {},
) {
    val addBusinessViewModel: AddBusinessViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val branchStatusMessage by viewModel.branchStatusMessage.collectAsState()
    val branchDownloadProgress by viewModel.branchDownloadProgress.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.refresh() }

    // Determine if tap targets should be shown on this screen
    val showTapTargets = !walkthroughCompleted && walkthroughState.currentStep in setOf(
        WalkthroughStep.WELCOME,
        WalkthroughStep.DASHBOARD_APPS_CARD,
        WalkthroughStep.DASHBOARD_HIDE_UNINSTALLED
    )
    val currentStep = walkthroughState.currentStep

    key(walkthroughState.currentStep) {
        TapTargetCoordinator(
            showTapTargets = showTapTargets,
            onComplete = { onWalkthroughNext() },
            contentAlignment = if (currentStep == WalkthroughStep.DASHBOARD_HIDE_UNINSTALLED)
                Alignment.BottomCenter else Alignment.Center
        ) {
            // Modifier for the Hide button when targeting DASHBOARD_HIDE_UNINSTALLED
            val hideButtonModifier = if (showTapTargets && currentStep == WalkthroughStep.DASHBOARD_HIDE_UNINSTALLED) {
                Modifier.tapTarget(
                    TapTargetDefinition(
                        precedence = WalkthroughTarget.precedence(currentStep),
                        title = TextDefinition(
                            text = WalkthroughTarget.message(currentStep),
                            textStyle = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        description = TextDefinition(
                            text = "",
                            textStyle = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        tapTargetStyle = WalkthroughTarget.style(currentStep)
                    )
                )
            } else Modifier

            // Modifier for centered tap targets (WELCOME, DASHBOARD_APPS_CARD)
            val centeredTapTargetModifier = if (showTapTargets && currentStep in setOf(
                    WalkthroughStep.WELCOME,
                    WalkthroughStep.DASHBOARD_APPS_CARD
                )) {
                Modifier.tapTarget(
                    TapTargetDefinition(
                        precedence = WalkthroughTarget.precedence(currentStep),
                        title = TextDefinition(
                            text = WalkthroughTarget.message(currentStep),
                            textStyle = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        description = TextDefinition(
                            text = "",
                            textStyle = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        tapTargetStyle = WalkthroughTarget.style(currentStep)
                    )
                )
            } else {
                Modifier
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (val s = state) {
                    is DashboardState.Loading    -> LoadingContent(branchStatusMessage, branchDownloadProgress)
                    is DashboardState.NoLocation -> LocationUnavailableContent(context)
                    is DashboardState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Error", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                            Text(s.message, style = MaterialTheme.typography.bodySmall)
                            Button(onClick = { viewModel.refresh() }) { Text("Retry") }
                        }
                    }
                    is DashboardState.AtProfile -> DashboardList(
                        bannerText = "At ${s.profileName.substringBefore(" Apps")} (${s.apps.size})",
                        items = s.apps,
                        context = context,
                        location = s.location,
                        onHideUninstalled = { installed -> addBusinessViewModel.disableUninstalled(installed) },
                        hideButtonModifier = hideButtonModifier,
                        onHideButtonPosition = null,
                        forceShowHide = showTapTargets && currentStep == WalkthroughStep.DASHBOARD_HIDE_UNINSTALLED
                    )
                    is DashboardState.Nearby -> Column {
                        if (s.isOffline) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "No internet — showing last known locations",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        DashboardList(
                            bannerText = "Nearby (${s.items.size})",
                            items = s.items,
                            context = context,
                            location = s.location,
                            onHideUninstalled = { installed -> addBusinessViewModel.disableUninstalled(installed) },
                            hideButtonModifier = hideButtonModifier,
                            onHideButtonPosition = null,
                            forceShowHide = showTapTargets && currentStep == WalkthroughStep.DASHBOARD_HIDE_UNINSTALLED
                        )
                    }
                }

                // Centered tap target overlay — small box so the spotlight circle is visible
                if (centeredTapTargetModifier != Modifier) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .then(centeredTapTargetModifier)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Full-screen loading indicator. Two modes:
 *
 *  - **First-boot** ([statusMessage] non-null): large logo, indeterminate progress bar,
 *    the live status from [NearbyBranchFinder], plus a crossfading carousel of messages
 *    that explain what's happening and set expectations.
 *
 *  - **Normal** ([statusMessage] null): compact radar + cycling humorous messages.
 */
@Composable
fun LoadingContent(statusMessage: String? = null, downloadProgress: Pair<Int, Int>? = null) {
    val isFirstBoot = statusMessage != null

    val firstBootMessages = remember {
        listOf(
            "This only happens once — updates silently every 30 days after that",
            "Downloading every KFC, McDonald's and Greggs in the UK...",
            "Once saved, your nearest branch loads instantly — no waiting",
            "Mapping Tesco, Boots, Costa Coffee and more...",
            "Finding every Nando's, Wetherspoons and Wagamama...",
            "Storing branch locations locally for offline use too",
            "No need to stay on this screen — it'll open automatically",
            "After this, moving 50 miles updates results in under a second",
            "Asking Overpass API very nicely for all of this...",
            "Your nearest branch will always be at the top of the list"
        )
    }
    val normalMessages = remember {
        listOf(
            "Asking OpenStreetMap nicely...",
            "Bribing Overpass with imaginary coffee...",
            "Triangulating your nearest Costa Coffee...",
            "Checking if there's a Greggs nearby (there is)",
            "Calculating distances... maths is hard",
            "Your location: confirmed. Nearest IKEA: pending...",
            "Overpass API is doing its best, promise...",
            "Rummaging through map data...",
            "Finding the closest Nando's (mission critical)",
            "Almost there... probably",
            "Please don't refresh. Please.",
            "Pinging satellites (not really, but it sounds impressive)"
        )
    }

    val messages = if (isFirstBoot) firstBootMessages else normalMessages
    var index by remember(isFirstBoot) { mutableIntStateOf(0) }
    LaunchedEffect(isFirstBoot) {
        while (true) {
            delay(if (isFirstBoot) 4_000L else 3_000L)
            index = (index + 1) % messages.size
        }
    }

    if (isFirstBoot) {
        // ── First-boot splash ────────────────────────────────────────────────
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(horizontal = 40.dp)
            ) {
                Text(
                    text = "Setting up for the first time.\nThis may take between 5 to 10 minutes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Our servers are doing the heavy lifting — only a small amount of location data is saved to your device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                AppdarRadarAnimation(Modifier.size(160.dp))

                Text(
                    text = "Appdar",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(Modifier.height(8.dp))

                val dpCurrent = downloadProgress?.first ?: 0
                val dpTotal   = downloadProgress?.second ?: 0
                if (dpTotal > 0) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Step dots — same style as onboarding page indicator
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(dpTotal) { i ->
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 4.dp)
                                        .size(if (i == dpCurrent - 1) 12.dp else 8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (i < dpCurrent) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outlineVariant
                                        )
                                )
                            }
                        }
                        Text(
                            text = "$dpCurrent of $dpTotal",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LinearProgressIndicator(
                            progress = { dpCurrent.toFloat() / dpTotal },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }

                // Live technical status ("Downloading UK locations…" / "Saving 4 312 locations…")
                Text(
                    text = statusMessage ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                // Cycling explanation messages with crossfade
                AnimatedContent(
                    targetState = index,
                    transitionSpec = {
                        fadeIn(tween(600)) togetherWith fadeOut(tween(600))
                    },
                    label = "first_boot_msg"
                ) { idx ->
                    Text(
                        text = messages[idx],
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    } else {
        // ── Normal loading: compact radar + cycling message ──────────────────
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                AppdarRadarAnimation(Modifier.size(96.dp))
                Text(
                    text = messages[index],
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Shown when location permission is denied or timed out.
 * Gives the user a clear explanation and a direct route to app settings.
 * Used by both Dashboard and Nearby Apps (same package, no import needed).
 */
@Composable
fun LocationUnavailableContent(context: Context) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 40.dp)
        ) {
            Icon(
                Icons.Filled.LocationOff,
                contentDescription = null,
                modifier = Modifier.size(52.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Location unavailable",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Appdar needs your location to find nearby branches. " +
                "Grant location permission and tap refresh.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Button(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data  = Uri.fromParts("package", context.packageName, null)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                )
            }) {
                Text("Open App Settings")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DashboardList(
    bannerText: String,
    items: List<DashboardItem>,
    context: Context,
    location: Location?,
    onHideUninstalled: (installedPackageNames: Set<String>) -> Unit = {},
    hideButtonModifier: Modifier = Modifier,
    onHideButtonPosition: ((Offset, IntSize) -> Unit)? = null,
    forceShowHide: Boolean = false
) {
    Column(Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = bannerText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        // Location display — show current coordinates and accuracy
        if (location != null) {
            val ageMs = System.currentTimeMillis() - location.time
            val ageText = when {
                ageMs < 60_000 -> "${ageMs / 1000}s ago"
                ageMs < 3_600_000 -> "${ageMs / 60_000}m ago"
                else -> "${ageMs / 3_600_000}h ago"
            }
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format("%.6f, %.6f", location.latitude, location.longitude),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = if (location.hasAccuracy()) "±${location.accuracy.toInt()}m • $ageText" else "$ageText",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
        // Uninstalled-apps hint — only shown when at least one listed app isn't on the device
        val uninstalledCount = items.count { !it.isInstalled }
        val uninstalledPackageNames = remember(items) {
            items.filter { !it.isInstalled }.map { it.packageName }.toHashSet()
        }
        if (uninstalledCount > 0 || forceShowHide) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = if (uninstalledCount > 0)
                                "$uninstalledCount app${if (uninstalledCount > 1) "s" else ""} shown " +
                                    "here ${if (uninstalledCount > 1) "aren't" else "isn't"} installed. " +
                                    "Go to Places to remove ${if (uninstalledCount > 1) "them" else "it"}."
                            else
                                "Tap Hide to remove apps not installed on this device.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    TextButton(
                        onClick = { onHideUninstalled(uninstalledPackageNames) },
                        modifier = hideButtonModifier
                    ) {
                        Text(
                            text = "Hide",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
        }

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No apps to show.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                itemsIndexed(items, key = { _, item -> "${item.packageName}:${item.label}" }) { index, item ->
                    DashboardItemCard(item, index, context, modifier = Modifier.animateItemPlacement())
                }
            }
        }
    }
}

@Composable
private fun DashboardItemCard(item: DashboardItem, index: Int, context: Context, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                val intent = context.packageManager.getLaunchIntentForPackage(item.packageName)
                    ?: Intent(Intent.ACTION_VIEW).apply {
                        data = android.net.Uri.parse("market://details?id=${item.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                context.startActivity(intent)
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (item.iconBitmap != null) {
                    Image(
                        bitmap = item.iconBitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Filled.List, contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Box(
                        modifier = Modifier.size(10.dp).clip(CircleShape)
                            .background(if (item.isInstalled) Color.Green else Color.Gray)
                    )
                }
                Text(item.subtitle, style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
            }
            if (item.branchLat != null && item.branchLon != null) {
                IconButton(onClick = {
                    val encodedName = android.net.Uri.encode(item.label)
                    val uri = android.net.Uri.parse(
                        "geo:${item.branchLat},${item.branchLon}?q=${item.branchLat},${item.branchLon}($encodedName)"
                    )
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, uri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                    )
                }) {
                    Icon(
                        Icons.Filled.Directions,
                        contentDescription = "Navigate to ${item.label}",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
