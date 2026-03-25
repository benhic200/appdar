package com.example.nearbyappswidget

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nearbyappswidget.data.local.profiles.LocationProfile
import com.example.nearbyappswidget.data.local.profiles.LocationProfileRepository
import com.example.nearbyappswidget.data.local.profiles.ProfileId
import com.example.nearbyappswidget.data.local.settings.DistanceUnit
import com.example.nearbyappswidget.data.local.settings.SettingsRepository
import com.example.nearbyappswidget.data.nearby.NearbyBranchFinder
import com.example.nearbyappswidget.data.repository.BusinessAppRepository
import com.example.nearbyappswidget.feature.location.DistanceCalculator
import com.example.nearbyappswidget.feature.location.LocationProvider
import com.example.nearbyappswidget.feature.widgetlist.util.AppIconLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

// ── UI state ────────────────────────────────────────────────────────────────

data class DashboardItem(
    val label: String,
    val packageName: String,
    val subtitle: String,   // distance string or profile name
    val isInstalled: Boolean,
    val iconBitmap: android.graphics.Bitmap?
)

sealed class DashboardState {
    object Loading : DashboardState()
    data class AtProfile(val profileName: String, val apps: List<DashboardItem>) : DashboardState()
    data class Nearby(val items: List<DashboardItem>) : DashboardState()
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

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
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
                            ) <= prefs.searchRadiusMeters
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
                    _state.value = DashboardState.AtProfile(matched.displayName, items)
                    return@launch
                }

                // Not at any profile — show nearby businesses
                businessAppRepository.initialize()
                val mappings = businessAppRepository.getAllMappings().first()
                val branches = if (location != null) {
                    withTimeoutOrNull(10_000L) {
                        nearbyBranchFinder.findNearestBranches(location.latitude, location.longitude)
                    } ?: emptyMap()
                } else emptyMap()

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
                        iconBitmap = appIconLoader.getIconBitmap(m.packageName)
                    )
                }.sortedBy { item ->
                    val (lat, lon) = branches[item.label] ?: return@sortedBy Int.MAX_VALUE
                    if (location != null)
                        distanceCalculator.calculateDistanceMeters(
                            location.latitude, location.longitude, lat, lon
                        ).toInt()
                    else Int.MAX_VALUE
                }
                _state.value = DashboardState.Nearby(items)
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
fun DashboardContent(viewModel: DashboardViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    when (val s = state) {
        is DashboardState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is DashboardState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Error", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                Text(s.message, style = MaterialTheme.typography.bodySmall)
                Button(onClick = { viewModel.refresh() }) { Text("Retry") }
            }
        }
        is DashboardState.AtProfile -> DashboardList(
            bannerText = "At ${s.profileName.substringBefore(" Apps")}",
            items = s.apps,
            context = context
        )
        is DashboardState.Nearby -> DashboardList(
            bannerText = "Nearby",
            items = s.items,
            context = context
        )
    }
}

@Composable
private fun DashboardList(bannerText: String, items: List<DashboardItem>, context: Context) {
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
                items(items, key = { "${it.packageName}:${it.label}" }) { item ->
                    DashboardItemCard(item, context)
                }
            }
        }
    }
}

@Composable
private fun DashboardItemCard(item: DashboardItem, context: Context) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val intent = context.packageManager.getLaunchIntentForPackage(item.packageName)
                    ?: Intent(Intent.ACTION_VIEW).apply {
                        data = android.net.Uri.parse("https://play.google.com/store/apps/details?id=${item.packageName}")
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
        }
    }
}
