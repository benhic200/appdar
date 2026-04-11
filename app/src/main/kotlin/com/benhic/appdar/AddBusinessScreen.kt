package com.benhic.appdar

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.benhic.appdar.data.local.BusinessAppMapping
import com.benhic.appdar.data.local.settings.RegionPreference
import com.benhic.appdar.data.local.settings.SettingsRepository
import com.benhic.appdar.data.nearby.NearbyBranchFinder
import com.benhic.appdar.data.repository.BusinessAppRepository
import com.benhic.appdar.feature.location.LocationProvider
import com.benhic.appdar.feature.widgetlist.util.AppIconLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface OsmValidationState {
    object Idle : OsmValidationState
    object Loading : OsmValidationState
    data class Found(val brandTag: String) : OsmValidationState
    object NotFound : OsmValidationState
}

@HiltViewModel
class AddBusinessViewModel @Inject constructor(
    private val repository: BusinessAppRepository,
    private val locationProvider: LocationProvider,
    private val appIconLoader: AppIconLoader,
    private val nearbyBranchFinder: NearbyBranchFinder,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val mappings: StateFlow<List<BusinessAppMapping>> = repository.getAllMappings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _osmState = MutableStateFlow<OsmValidationState>(OsmValidationState.Idle)
    val osmState: StateFlow<OsmValidationState> = _osmState.asStateFlow()

    /**
     * Effective region for the Places list — initialised immediately from the last known
     * GPS region so UK-only brands are filtered instantly (no blank/UNKNOWN flash).
     * Then updated from the user's regionPreference setting (or live GPS if AUTO).
     */
    private val _currentRegion = MutableStateFlow(nearbyBranchFinder.lastKnownRegion())
    val currentRegion: StateFlow<NearbyBranchFinder.Region> = _currentRegion.asStateFlow()

    private var osmValidationJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val pref = settingsRepository.getCurrentPreferences().regionPreference
            _currentRegion.value = when (pref) {
                RegionPreference.UK   -> NearbyBranchFinder.Region.UK
                RegionPreference.US   -> NearbyBranchFinder.Region.US
                RegionPreference.AU   -> NearbyBranchFinder.Region.AU
                RegionPreference.NZ   -> NearbyBranchFinder.Region.NZ
                RegionPreference.AUTO -> {
                    val location = runCatching { locationProvider.getCurrentLocation() }.getOrNull()
                    if (location != null) nearbyBranchFinder.detectRegion(location.latitude, location.longitude)
                    else nearbyBranchFinder.lastKnownRegion()
                }
            }
        }
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                pm.getInstalledPackages(0).mapNotNull { pkgInfo ->
                    if (pm.getLaunchIntentForPackage(pkgInfo.packageName) == null) return@mapNotNull null
                    val label = pkgInfo.applicationInfo?.loadLabel(pm)?.toString() ?: pkgInfo.packageName
                    val icon = appIconLoader.getIconBitmap(pkgInfo.packageName)
                    InstalledApp(pkgInfo.packageName, label, icon)
                }.sortedBy { it.label }
            }
            _installedApps.value = apps
        }
    }

    /** Debounce-validates [businessName] against OpenStreetMap after 1 second of inactivity. */
    fun onBusinessNameChanged(businessName: String) {
        osmValidationJob?.cancel()
        if (businessName.isBlank()) {
            _osmState.value = OsmValidationState.Idle
            return
        }
        _osmState.value = OsmValidationState.Loading
        osmValidationJob = viewModelScope.launch {
            delay(1_000)
            // Pass the user's current location so Overpass can scope its search to 100 km.
            // Built-in brands (Tesco, McDonald's, etc.) are resolved instantly without a network call.
            val location = runCatching { locationProvider.getCurrentLocation() }.getOrNull()
            val brandTag = nearbyBranchFinder.validateAndResolveBrandName(
                businessName.trim(),
                userLat = location?.latitude,
                userLon = location?.longitude
            )
            _osmState.value = if (brandTag != null) OsmValidationState.Found(brandTag)
                              else OsmValidationState.NotFound
        }
    }

    fun resetOsmState() {
        osmValidationJob?.cancel()
        _osmState.value = OsmValidationState.Idle
    }

    fun addBusiness(
        businessName: String,
        packageName: String,
        appName: String,
        category: String,
        useCurrentLocation: Boolean,
        osmBrandTag: String?,
        geofenceRadius: Int,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val location = if (useCurrentLocation) locationProvider.getCurrentLocation() else null
                val mapping = BusinessAppMapping(
                    businessName = businessName.trim(),
                    packageName = packageName,
                    appName = appName,
                    category = category.trim().ifBlank { "Custom" },
                    latitude = location?.latitude,
                    longitude = location?.longitude,
                    geofenceRadius = geofenceRadius,
                    osmBrandTag = osmBrandTag
                    // isCustom is set to true inside addCustomMapping
                )
                repository.addCustomMapping(mapping)
                // If OSM-validated, download that brand's UK branches immediately in the background
                // so the new place appears with correct distance on the next nearest-branch calculation.
                if (osmBrandTag != null) {
                    nearbyBranchFinder.downloadBrandAndStore(osmBrandTag)
                }
                onDone()
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun deleteBusiness(mapping: BusinessAppMapping) {
        viewModelScope.launch { repository.deleteMapping(mapping) }
    }

    fun toggleEnabled(mapping: BusinessAppMapping) {
        viewModelScope.launch { repository.toggleEnabled(mapping) }
    }

    fun disableUninstalled(uninstalledPackageNames: Set<String>) {
        viewModelScope.launch {
            repository.getAllMappings().first()
                .filter { it.isEnabled && it.packageName in uninstalledPackageNames && !isActuallyInstalled(it.packageName) }
                .forEach { repository.toggleEnabled(it) }
        }
    }

    fun enableUninstalled(installedPackageNames: Set<String>) {
        viewModelScope.launch {
            mappings.value
                .filter { !it.isEnabled && it.packageName !in installedPackageNames && !isActuallyInstalled(it.packageName) }
                .forEach { repository.toggleEnabled(it) }
        }
    }

    private fun isActuallyInstalled(packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, PackageManager.MATCH_ALL)
        true
    } catch (_: PackageManager.NameNotFoundException) { false }
}

@Composable
fun AddBusinessScreen(
    viewModel: AddBusinessViewModel = hiltViewModel()
) {
    val mappings by viewModel.mappings.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    val currentRegion by viewModel.currentRegion.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val installedPackageNames = remember(installedApps) {
        installedApps.map { it.packageName }.toHashSet()
    }

    // Brands hidden due to region mismatch (e.g. US-only brands hidden when in UK).
    // Custom brands (isCustom = true) are always shown regardless of region.
    val regionVisible = remember(mappings, currentRegion) {
        mappings.filter { m ->
            when (currentRegion) {
                NearbyBranchFinder.Region.UK ->
                    m.isCustom || (m.businessName !in NearbyBranchFinder.US_BRAND_NAMES
                               && m.businessName !in NearbyBranchFinder.AU_BRAND_NAMES
                               && m.businessName !in NearbyBranchFinder.NZ_BRAND_NAMES)
                NearbyBranchFinder.Region.US ->
                    m.isCustom || (m.businessName !in NearbyBranchFinder.UK_BRAND_NAMES
                               && m.businessName !in NearbyBranchFinder.AU_BRAND_NAMES
                               && m.businessName !in NearbyBranchFinder.NZ_BRAND_NAMES)
                NearbyBranchFinder.Region.AU ->
                    m.isCustom || (m.businessName !in NearbyBranchFinder.UK_BRAND_NAMES
                               && m.businessName !in NearbyBranchFinder.US_BRAND_NAMES
                               && m.businessName !in NearbyBranchFinder.NZ_BRAND_NAMES)
                NearbyBranchFinder.Region.NZ ->
                    m.isCustom || (m.businessName !in NearbyBranchFinder.UK_BRAND_NAMES
                               && m.businessName !in NearbyBranchFinder.US_BRAND_NAMES
                               && m.businessName !in NearbyBranchFinder.AU_BRAND_NAMES)
                NearbyBranchFinder.Region.UNKNOWN ->
                    m.isCustom || (m.businessName !in NearbyBranchFinder.US_BRAND_NAMES
                               && m.businessName !in NearbyBranchFinder.AU_BRAND_NAMES
                               && m.businessName !in NearbyBranchFinder.NZ_BRAND_NAMES)
            }
        }
    }

    // Counts drive the toggle label/action — base on region-visible list
    val uninstalledEnabledCount = remember(regionVisible, installedPackageNames) {
        regionVisible.count { it.isEnabled && it.packageName !in installedPackageNames }
    }
    val uninstalledDisabledCount = remember(regionVisible, installedPackageNames) {
        regionVisible.count { !it.isEnabled && it.packageName !in installedPackageNames }
    }
    val uninstalledMappingPackageNames = remember(regionVisible, installedPackageNames) {
        regionVisible.filter { it.packageName !in installedPackageNames }.map { it.packageName }.toHashSet()
    }
    val filtered = remember(regionVisible, searchQuery) {
        if (searchQuery.isBlank()) regionVisible
        else regionVisible.filter {
            it.businessName.contains(searchQuery, ignoreCase = true) ||
            it.appName.contains(searchQuery, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── Search bar ────────────────────────────────────────────────────
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search places…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ── Action row ────────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Place")
                }
                // Toggle: hide uninstalled apps (or show them again if already hidden)
                val hidingMode = uninstalledEnabledCount > 0
                OutlinedButton(
                    onClick = {
                        if (hidingMode) viewModel.disableUninstalled(uninstalledMappingPackageNames)
                        else viewModel.enableUninstalled(installedPackageNames)
                    },
                    enabled = hidingMode || uninstalledDisabledCount > 0,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.VisibilityOff, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        when {
                            hidingMode -> "Hide uninstalled ($uninstalledEnabledCount)"
                            uninstalledDisabledCount > 0 -> "Show uninstalled ($uninstalledDisabledCount)"
                            else -> "Hide uninstalled"
                        }
                    )
                }
            }
        }

        // ── Empty states ──────────────────────────────────────────────────
        if (filtered.isEmpty()) {
            item {
                Text(
                    text = if (searchQuery.isNotBlank()) "No places match \"$searchQuery\"."
                           else "No places yet. Tap Add Place to get started.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
        }

        items(filtered, key = { it.id }) { mapping ->
            PlaceMappingCard(
                mapping = mapping,
                iconBitmap = installedApps.find { it.packageName == mapping.packageName }?.iconBitmap,
                onToggleEnabled = { viewModel.toggleEnabled(mapping) },
                onDelete = { viewModel.deleteBusiness(mapping) }
            )
        }
    }

    if (showAddDialog) {
        val osmState by viewModel.osmState.collectAsState()
        AddBusinessDialog(
            installedApps = installedApps,
            isSaving = viewModel.isSaving.collectAsState().value,
            osmState = osmState,
            onBusinessNameChanged = viewModel::onBusinessNameChanged,
            onDismiss = {
                viewModel.resetOsmState()
                showAddDialog = false
            },
            onConfirm = { name, pkg, appName, category, useLocation, osmBrandTag, radius ->
                viewModel.addBusiness(name, pkg, appName, category, useLocation, osmBrandTag, radius) {
                    showAddDialog = false
                }
            }
        )
    }
}

@Composable
private fun PlaceMappingCard(
    mapping: BusinessAppMapping,
    iconBitmap: Bitmap?,
    onToggleEnabled: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (mapping.isEnabled) MaterialTheme.colorScheme.surface
                             else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Icon(Icons.Filled.Android, contentDescription = null, modifier = Modifier.size(40.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mapping.businessName,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (mapping.isEnabled) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = mapping.appName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Custom places get a delete button to the left of the toggle.
            if (mapping.isCustom) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            // All places get a toggle.
            Switch(
                checked = mapping.isEnabled,
                onCheckedChange = { onToggleEnabled() }
            )
        }
    }
}

@Composable
private fun AddBusinessDialog(
    installedApps: List<InstalledApp>,
    isSaving: Boolean,
    osmState: OsmValidationState,
    onBusinessNameChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (name: String, pkg: String, appName: String, category: String, useLocation: Boolean, osmBrandTag: String?, radius: Int) -> Unit
) {
    var businessName by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf<InstalledApp?>(null) }
    var useCurrentLocation by remember { mutableStateOf(false) }
    var geofenceRadius by remember { mutableStateOf(200f) }
    var searchQuery by remember { mutableStateOf("") }
    var showAppPicker by remember { mutableStateOf(false) }

    // Can save if: has a name + selected app + (using current location OR OSM validated)
    val osmFound = osmState is OsmValidationState.Found
    val isValid = businessName.isNotBlank() && selectedApp != null && (useCurrentLocation || osmFound)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Place") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Business name + OSM validation indicator
                OutlinedTextField(
                    value = businessName,
                    onValueChange = {
                        businessName = it
                        onBusinessNameChanged(it)
                    },
                    label = { Text("Place Name") },
                    singleLine = true,
                    trailingIcon = {
                        when (osmState) {
                            is OsmValidationState.Loading ->
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            is OsmValidationState.Found ->
                                Icon(Icons.Filled.CheckCircle, contentDescription = "Found on OpenStreetMap",
                                    tint = MaterialTheme.colorScheme.primary)
                            is OsmValidationState.NotFound ->
                                Icon(Icons.Filled.Error, contentDescription = "Not found on OpenStreetMap",
                                    tint = MaterialTheme.colorScheme.error)
                            else -> {}
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // OSM status message
                when (osmState) {
                    is OsmValidationState.Found ->
                        Text(
                            "Found on OpenStreetMap — nearest branch will be shown automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    is OsmValidationState.NotFound ->
                        Text(
                            "Not found on OpenStreetMap — turn on \"Use current location\" below to save your position manually.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    else -> {}
                }

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                // App picker button
                OutlinedButton(
                    onClick = { showAppPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (selectedApp != null) {
                        if (selectedApp!!.iconBitmap != null) {
                            Image(
                                bitmap = selectedApp!!.iconBitmap!!.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp).clip(RoundedCornerShape(4.dp))
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(selectedApp!!.label)
                    } else {
                        Text("Select App…")
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Use current location", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = useCurrentLocation, onCheckedChange = { useCurrentLocation = it })
                }
                if (!useCurrentLocation && !osmFound) {
                    Text(
                        text = "Without a location and no OpenStreetMap match, this place won't appear in the widget. Turn on \"Use current location\" while you're at the place to save your position.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Column {
                    Text(
                        "Detection radius: ${geofenceRadius.toInt()}m",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = geofenceRadius,
                        onValueChange = { geofenceRadius = it },
                        valueRange = 50f..2000f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val app = selectedApp ?: return@TextButton
                    val brandTag = (osmState as? OsmValidationState.Found)?.brandTag
                    onConfirm(businessName, app.packageName, app.label, category, useCurrentLocation, brandTag, geofenceRadius.toInt())
                },
                enabled = isValid && !isSaving
            ) {
                if (isSaving) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showAppPicker) {
        val filtered = if (searchQuery.isBlank()) installedApps
        else installedApps.filter { it.label.contains(searchQuery, ignoreCase = true) }

        AlertDialog(
            onDismissRequest = { showAppPicker = false },
            title = { Text("Select App") },
            text = {
                Column {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                    LazyColumn {
                        items(filtered, key = { it.packageName }) { app ->
                            TextButton(
                                onClick = {
                                    selectedApp = app
                                    showAppPicker = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (app.iconBitmap != null) {
                                        Image(
                                            bitmap = app.iconBitmap.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(6.dp))
                                        )
                                    } else {
                                        Icon(Icons.Filled.Android, contentDescription = null, modifier = Modifier.size(32.dp))
                                    }
                                    Text(app.label, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAppPicker = false }) { Text("Cancel") }
            }
        )
    }
}
