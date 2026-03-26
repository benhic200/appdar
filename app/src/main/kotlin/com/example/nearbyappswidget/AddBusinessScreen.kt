package com.example.nearbyappswidget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Delete
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
import com.example.nearbyappswidget.data.local.BusinessAppMapping
import com.example.nearbyappswidget.data.repository.BusinessAppRepository
import com.example.nearbyappswidget.feature.location.LocationProvider
import com.example.nearbyappswidget.feature.widgetlist.util.AppIconLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AddBusinessViewModel @Inject constructor(
    private val repository: BusinessAppRepository,
    private val locationProvider: LocationProvider,
    private val appIconLoader: AppIconLoader,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val mappings: StateFlow<List<BusinessAppMapping>> = repository.getAllMappings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    init {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                pm.getInstalledPackages(0).mapNotNull { pkgInfo ->
                    if (pm.getLaunchIntentForPackage(pkgInfo.packageName) == null) return@mapNotNull null
                    val label = pkgInfo.applicationInfo.loadLabel(pm).toString()
                    val icon = appIconLoader.getIconBitmap(pkgInfo.packageName)
                    InstalledApp(pkgInfo.packageName, label, icon)
                }.sortedBy { it.label }
            }
            _installedApps.value = apps
        }
    }

    fun addBusiness(
        businessName: String,
        packageName: String,
        appName: String,
        category: String,
        useCurrentLocation: Boolean,
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
                    geofenceRadius = geofenceRadius
                    // isCustom is set to true inside addCustomMapping
                )
                repository.addCustomMapping(mapping)
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
}

@Composable
fun AddBusinessScreen(
    viewModel: AddBusinessViewModel = hiltViewModel()
) {
    val mappings by viewModel.mappings.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Add button at the top
        item {
            OutlinedButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add Place")
            }
        }

        if (mappings.isEmpty()) {
            item {
                Text(
                    text = "No places yet. Tap Add Place to get started.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
        }

        items(mappings, key = { it.id }) { mapping ->
            PlaceMappingCard(
                mapping = mapping,
                iconBitmap = installedApps.find { it.packageName == mapping.packageName }?.iconBitmap,
                onToggleEnabled = { viewModel.toggleEnabled(mapping) },
                onDelete = { viewModel.deleteBusiness(mapping) }
            )
        }
    }

    if (showAddDialog) {
        AddBusinessDialog(
            installedApps = installedApps,
            isSaving = viewModel.isSaving.collectAsState().value,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, pkg, appName, category, useLocation, radius ->
                viewModel.addBusiness(name, pkg, appName, category, useLocation, radius) {
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
                if (mapping.latitude != null && mapping.longitude != null) {
                    Text(
                        text = "%.4f, %.4f  •  ${mapping.geofenceRadius}m".format(
                            mapping.latitude, mapping.longitude
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (mapping.isCustom) {
                // User-added: show delete button
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                // Seeded place: show enabled/disabled toggle
                Switch(
                    checked = mapping.isEnabled,
                    onCheckedChange = { onToggleEnabled() }
                )
            }
        }
    }
}

@Composable
private fun AddBusinessDialog(
    installedApps: List<InstalledApp>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (name: String, pkg: String, appName: String, category: String, useLocation: Boolean, radius: Int) -> Unit
) {
    var businessName by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf<InstalledApp?>(null) }
    var useCurrentLocation by remember { mutableStateOf(true) }
    var geofenceRadius by remember { mutableStateOf(200f) }
    var searchQuery by remember { mutableStateOf("") }
    var showAppPicker by remember { mutableStateOf(false) }
    val isValid = businessName.isNotBlank() && selectedApp != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Place") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = businessName,
                    onValueChange = { businessName = it },
                    label = { Text("Place Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
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
                    onConfirm(businessName, app.packageName, app.label, category, useCurrentLocation, geofenceRadius.toInt())
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
