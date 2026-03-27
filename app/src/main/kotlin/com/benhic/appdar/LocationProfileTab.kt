package com.benhic.appdar

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.benhic.appdar.data.local.profiles.LocationProfile
import com.benhic.appdar.data.local.profiles.LocationProfileRepository
import com.benhic.appdar.data.local.profiles.ProfileId
import com.benhic.appdar.feature.location.LocationProvider
import com.benhic.appdar.feature.widgetlist.util.AppIconLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class InstalledApp(
    val packageName: String,
    val label: String,
    val iconBitmap: Bitmap? = null
)

@HiltViewModel
class LocationProfileViewModel @Inject constructor(
    private val locationProfileRepository: LocationProfileRepository,
    private val locationProvider: LocationProvider,
    private val appIconLoader: AppIconLoader,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _profileStates = mutableMapOf<ProfileId, MutableStateFlow<LocationProfile>>()

    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps.asStateFlow()

    private val _isSettingLocation = MutableStateFlow<ProfileId?>(null)
    val isSettingLocation: StateFlow<ProfileId?> = _isSettingLocation.asStateFlow()

    init {
        // Load installed apps + icons on IO thread
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                pm.getInstalledPackages(0).mapNotNull { pkgInfo ->
                    if (pm.getLaunchIntentForPackage(pkgInfo.packageName) == null) return@mapNotNull null
                    val label = pkgInfo.applicationInfo?.loadLabel(pm)?.toString() ?: pkgInfo.packageName
                    val icon = appIconLoader.getIconBitmap(pkgInfo.packageName)
                    InstalledApp(packageName = pkgInfo.packageName, label = label, iconBitmap = icon)
                }.sortedBy { it.label }
            }
            _installedApps.value = apps
        }

        // Start collecting all profile flows
        ProfileId.values().forEach { profileId ->
            val flow = MutableStateFlow(
                LocationProfile(id = profileId.key, displayName = profileId.displayName)
            )
            _profileStates[profileId] = flow
            viewModelScope.launch {
                locationProfileRepository.getProfile(profileId).collect { profile ->
                    flow.value = profile
                }
            }
        }
    }

    fun profileState(profileId: ProfileId): StateFlow<LocationProfile> {
        return _profileStates.getOrPut(profileId) {
            MutableStateFlow(
                LocationProfile(id = profileId.key, displayName = profileId.displayName)
            )
        }
    }

    fun setCurrentLocation(profileId: ProfileId) {
        viewModelScope.launch {
            _isSettingLocation.value = profileId
            try {
                val location = locationProvider.getCurrentLocation()
                if (location != null) {
                    val label = "${String.format("%.4f", location.latitude)}, ${String.format("%.4f", location.longitude)}"
                    locationProfileRepository.updateLocation(profileId, location.latitude, location.longitude, label)
                }
            } finally {
                _isSettingLocation.value = null
            }
        }
    }

    fun clearLocation(profileId: ProfileId) {
        viewModelScope.launch {
            locationProfileRepository.clearLocation(profileId)
        }
    }

    fun updateSelectedApps(profileId: ProfileId, packageNames: List<String>) {
        viewModelScope.launch {
            locationProfileRepository.updateSelectedApps(profileId, packageNames)
        }
    }
}

@Composable
fun LocationProfileScreen(
    profileId: ProfileId,
    viewModel: LocationProfileViewModel = hiltViewModel()
) {
    val profile by viewModel.profileState(profileId).collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    val isSettingLocation by viewModel.isSettingLocation.collectAsState()
    var showAppPicker by remember { mutableStateOf(false) }

    val profileName = profileId.displayName.substringBefore(" Apps")
    val locationSet = profile.latitude != null && profile.longitude != null
    val appsSet = profile.selectedApps.isNotEmpty()
    val isReady = locationSet && appsSet

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // ── How it works banner (shown until fully configured) ────────────────
        if (!isReady) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "How to set up $profileName Apps",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            "Step 1 — Go to $profileName and tap \"Set to Current Location\". " +
                            "Appdar will activate this profile whenever you're within 300 m of that spot.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            "Step 2 — Tap \"Choose Apps\" to pick the apps you want to appear in the " +
                            "widget and Dashboard when you arrive at that location.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }

        // ── Step 1: Activation Location ───────────────────────────────────────
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (locationSet) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                "1",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (locationSet) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Text("Activation Location", style = MaterialTheme.typography.titleMedium)
                    }

                    if (locationSet) {
                        Text(
                            "Saved: ${profile.locationLabel ?: "${profile.latitude}, ${profile.longitude}"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Appdar will switch to $profileName Apps automatically when you're within 300 m of this location.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.setCurrentLocation(profileId) },
                                enabled = isSettingLocation == null
                            ) {
                                if (isSettingLocation == profileId) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Update Location")
                                }
                            }
                            OutlinedButton(
                                onClick = { viewModel.clearLocation(profileId) },
                                enabled = isSettingLocation == null
                            ) {
                                Text("Clear")
                            }
                        }
                    } else {
                        Text(
                            "Go to your $profileName now, then tap the button below to save that spot as your $profileName location.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { viewModel.setCurrentLocation(profileId) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = isSettingLocation == null
                        ) {
                            if (isSettingLocation == profileId) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Set to Current Location")
                            }
                        }
                    }
                }
            }
        }

        // ── Step 2: Apps ──────────────────────────────────────────────────────
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = if (appsSet) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    "2",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (appsSet) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Text("Apps to Show", style = MaterialTheme.typography.titleMedium)
                        }
                        TextButton(onClick = { showAppPicker = true }) {
                            Text(if (appsSet) "Edit" else "Choose Apps")
                        }
                    }

                    if (appsSet) {
                        Text(
                            "These apps appear in the widget and Dashboard when you arrive at your $profileName location:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        profile.selectedApps.forEach { packageName ->
                            val appLabel = installedApps.find { it.packageName == packageName }?.label ?: packageName
                            Text("• $appLabel", style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        Text(
                            "Choose which apps to show in the widget and Dashboard when Appdar detects you're at your $profileName location.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { showAppPicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Choose Apps")
                        }
                    }
                }
            }
        }

        // ── Ready banner ──────────────────────────────────────────────────────
        if (isReady) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("✓", style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary)
                        Column {
                            Text(
                                "$profileName Apps is active",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "The widget and Dashboard will automatically show your ${profile.selectedApps.size} selected app${if (profile.selectedApps.size != 1) "s" else ""} when you're near ${profile.locationLabel ?: "your saved location"}.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAppPicker) {
        AppPickerDialog(
            installedApps = installedApps,
            selectedApps = profile.selectedApps,
            onDismiss = { showAppPicker = false },
            onConfirm = { selected ->
                viewModel.updateSelectedApps(profileId, selected)
                showAppPicker = false
            }
        )
    }
}

@Composable
private fun AppPickerDialog(
    installedApps: List<InstalledApp>,
    selectedApps: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    val checkedPackages = remember { mutableStateListOf<String>().also { it.addAll(selectedApps) } }
    var searchQuery by remember { mutableStateOf("") }
    val filtered = if (searchQuery.isBlank()) installedApps
    else installedApps.filter { it.label.contains(searchQuery, ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Apps") },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search apps…") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
                LazyColumn {
                    items(filtered, key = { it.packageName }) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Checkbox(
                                checked = checkedPackages.contains(app.packageName),
                                onCheckedChange = { checked ->
                                    if (checked) checkedPackages.add(app.packageName)
                                    else checkedPackages.remove(app.packageName)
                                }
                            )
                            if (app.iconBitmap != null) {
                                Image(
                                    bitmap = app.iconBitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Android,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(checkedPackages.toList()) }) {
                Text("Apply")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { checkedPackages.clear() }) {
                    Text("Clear All")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
