package com.benhic.appdar

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.List
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.benhic.appdar.data.nearby.NearbyBranchFinder
import com.benhic.appdar.data.repository.BusinessAppRepository
import com.benhic.appdar.data.local.settings.SettingsRepository
import com.benhic.appdar.data.local.settings.DistanceUnit
import com.benhic.appdar.feature.widgetlist.util.AppIconLoader
import com.benhic.appdar.feature.location.DistanceCalculator
import com.benhic.appdar.feature.location.LocationProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * UI state for the Nearby Apps tab.
 */
data class NearbyAppsUiState(
    val businesses: List<BusinessItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val distanceUnit: DistanceUnit = DistanceUnit.METERS
)

/**
 * Item representing a business‑app mapping in the UI.
 */
data class BusinessItem(
    val name: String,
    val packageName: String,
    val distanceMeters: Int,
    val isInstalled: Boolean,
    val iconBitmap: android.graphics.Bitmap? = null
)

@HiltViewModel
class NearbyAppsViewModel @Inject constructor(
    private val businessAppRepository: BusinessAppRepository,
    private val settingsRepository: SettingsRepository,
    private val distanceCalculator: DistanceCalculator,
    private val locationProvider: LocationProvider,
    private val appIconLoader: AppIconLoader,
    private val nearbyBranchFinder: NearbyBranchFinder,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(NearbyAppsUiState(isLoading = true))
    val uiState: StateFlow<NearbyAppsUiState> = _uiState.asStateFlow()

    init {
        loadBusinesses()
        // Keep distanceUnit in sync so BusinessCard re-renders immediately on settings change.
        viewModelScope.launch {
            settingsRepository.userPreferences.collect { prefs ->
                _uiState.update { it.copy(distanceUnit = prefs.distanceUnit) }
            }
        }
    }

    fun refresh() {
        loadBusinesses()
    }

    private fun loadBusinesses() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                businessAppRepository.initialize()
                val mappings = businessAppRepository.getAllMappings().first()
                val preferences = settingsRepository.getCurrentPreferences()
                val currentLocation = locationProvider.getCurrentLocation()

                // Resolve nearest real branch coordinates from Overpass (cached after first call).
                val nearestBranches = if (currentLocation != null) {
                    withTimeoutOrNull(10_000L) {
                        nearbyBranchFinder.findNearestBranches(
                            currentLocation.latitude,
                            currentLocation.longitude
                        )
                    } ?: emptyMap()
                } else emptyMap()

                val businessItems = mappings.mapNotNull { mapping ->
                    val (branchLat, branchLon) = nearestBranches[mapping.businessName]
                        ?: ((mapping.latitude ?: return@mapNotNull null) to
                            (mapping.longitude ?: return@mapNotNull null))
                    val distance = if (currentLocation != null) {
                        distanceCalculator.calculateDistanceMeters(
                            currentLocation.latitude, currentLocation.longitude,
                            branchLat, branchLon
                        ).toInt()
                    } else {
                        mapping.geofenceRadius
                    }
                    val isInstalled = isAppInstalled(mapping.packageName)
                    val iconBitmap = if (isInstalled) appIconLoader.getIconBitmap(mapping.packageName) else null
                    BusinessItem(
                        name = mapping.businessName,
                        packageName = mapping.packageName,
                        distanceMeters = distance,
                        isInstalled = isInstalled,
                        iconBitmap = iconBitmap
                    )
                }.sortedBy { it.distanceMeters }

                _uiState.update {
                    it.copy(
                        businesses = businessItems,
                        isLoading = false,
                        distanceUnit = preferences.distanceUnit
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to load businesses: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, PackageManager.MATCH_ALL)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyAppsTab() {
    val viewModel = hiltViewModel<NearbyAppsViewModel>()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nearby_apps)) }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.refresh() }
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Refresh"
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            NearbyAppsContent(viewModel = viewModel, context = context)
        }
    }
}

@Composable
fun NearbyAppsContent(viewModel: NearbyAppsViewModel, context: Context) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Error",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = uiState.error!!,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = { viewModel.refresh() }
                    ) {
                        Text("Retry")
                    }
                }
            }
        } else if (uiState.businesses.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No businesses found. Seed database first.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(uiState.businesses, key = { it.packageName }) { business ->
                    BusinessCard(business, uiState.distanceUnit, context)
                }
            }
        }
    }
}

@Composable
private fun BusinessCard(business: BusinessItem, distanceUnit: DistanceUnit, context: Context) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (business.isInstalled) {
                    val intent = context.packageManager.getLaunchIntentForPackage(business.packageName)
                        ?: Intent(Intent.ACTION_VIEW).apply {
                            data = android.net.Uri.parse("https://play.google.com/store/apps/details?id=${business.packageName}")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                    context.startActivity(intent)
                } else {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = android.net.Uri.parse("https://play.google.com/store/apps/details?id=${business.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // App icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (business.iconBitmap != null) {
                    Image(
                        bitmap = business.iconBitmap.asImageBitmap(),
                        contentDescription = "App icon",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.List,
                        contentDescription = "App icon",
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // Business info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = business.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    // Installation status dot inline with business name
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                color = if (business.isInstalled) Color.Green else Color.Gray
                            )
                    )
                }
                Text(
                    text = business.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatDistance(business.distanceMeters, distanceUnit),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun formatDistance(meters: Int, unit: DistanceUnit): String = when (unit) {
    DistanceUnit.MILES      -> String.format("%.1f mi", meters * 0.000621371)
    DistanceUnit.KILOMETERS -> if (meters < 1000) "$meters m" else String.format("%.1f km", meters * 0.001)
    DistanceUnit.METERS     -> "$meters m"
    DistanceUnit.FEET       -> String.format("%.0f ft", meters * 3.28084)
}
