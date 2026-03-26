package com.example.nearbyappswidget.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Update
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nearbyappswidget.data.local.settings.DistanceUnit
import com.example.nearbyappswidget.data.local.settings.ThemeMode
import com.example.nearbyappswidget.data.local.settings.WidgetTheme

/**
 * Embeddable settings cards — no Scaffold, no scroll. The caller is responsible for scroll and padding.
 */
@Composable
fun SettingsContent(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    SettingsCards(userPreferences = userPreferences, viewModel = viewModel)
}

@Composable
private fun SettingsCards(
    userPreferences: com.example.nearbyappswidget.data.local.settings.UserPreferences,
    viewModel: SettingsViewModel
) {
            // Detection Radius
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Detection Radius",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.padding(4.dp))
                    Text(
                        text = "Search for businesses within ${userPreferences.searchRadiusMeters} m",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = userPreferences.searchRadiusMeters.toFloat(),
                        onValueChange = { viewModel.updateSearchRadius(it.toInt()) },
                        valueRange = 100f..2000f,
                        steps = 19, // 100 m increments
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.padding(8.dp))

            // Distance Units
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Distance Units",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (userPreferences.distanceUnit == DistanceUnit.MILES) "Miles" else "Kilometres",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = userPreferences.distanceUnit == DistanceUnit.MILES,
                        onCheckedChange = { useMiles ->
                            viewModel.updateDistanceUnit(
                                if (useMiles) DistanceUnit.MILES else DistanceUnit.KILOMETERS
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.padding(8.dp))

            // Geocoding Toggle
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Geocoding (Beta)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.padding(4.dp))
                    Text(
                        text = "Convert coordinates to street addresses using Google Maps Geocoding API. Requires internet.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Switch(
                        checked = userPreferences.enableGeocoding,
                        onCheckedChange = { viewModel.toggleGeocoding(it) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.padding(8.dp))

            // Location History Toggle
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Location History Cache",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.padding(4.dp))
                    Text(
                        text = "Temporarily cache your recent locations (up to 24 h) to improve widget responsiveness. Data stays on‑device.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Switch(
                        checked = userPreferences.enableLocationHistory,
                        onCheckedChange = { viewModel.toggleLocationHistory(it) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.padding(8.dp))

            // Refresh Interval
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Background Refresh",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.padding(4.dp))
                    Text(
                        text = "Update widget data every ${userPreferences.refreshIntervalHours} h",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = userPreferences.refreshIntervalHours.toFloat(),
                        onValueChange = { viewModel.updateRefreshInterval(it.toInt()) },
                        valueRange = 1f..24f,
                        steps = 23,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.padding(8.dp))

            // Low Power Mode
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Low Power Mode",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (userPreferences.lowPowerMode)
                                "Widget updates only when you tap refresh"
                            else
                                "Widget auto-refreshes every 5 minutes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = userPreferences.lowPowerMode,
                        onCheckedChange = { viewModel.toggleLowPowerMode(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.padding(8.dp))

            // App Theme
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "App Theme",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.padding(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            ThemeMode.SYSTEM to "System",
                            ThemeMode.LIGHT  to "Light",
                            ThemeMode.DARK   to "Dark"
                        ).forEach { (mode, label) ->
                            val selected = userPreferences.themeMode == mode
                            if (selected) {
                                Button(
                                    onClick = { viewModel.updateThemeMode(mode) },
                                    modifier = Modifier.weight(1f)
                                ) { Text(label) }
                            } else {
                                OutlinedButton(
                                    onClick = { viewModel.updateThemeMode(mode) },
                                    modifier = Modifier.weight(1f)
                                ) { Text(label) }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.padding(8.dp))

            // Widget Theme
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Widget Theme",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.padding(4.dp))
                    Text(
                        text = "Controls the widget background — set to Dark if your launcher uses a light background.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.padding(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            WidgetTheme.SYSTEM to "System",
                            WidgetTheme.LIGHT  to "Light",
                            WidgetTheme.DARK   to "Dark"
                        ).forEach { (mode, label) ->
                            val selected = userPreferences.widgetTheme == mode
                            if (selected) {
                                Button(
                                    onClick = { viewModel.updateWidgetTheme(mode) },
                                    modifier = Modifier.weight(1f)
                                ) { Text(label) }
                            } else {
                                OutlinedButton(
                                    onClick = { viewModel.updateWidgetTheme(mode) },
                                    modifier = Modifier.weight(1f)
                                ) { Text(label) }
                            }
                        }
                    }
                }
            }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBackPressed: () -> Unit = {}
) {
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Widget Settings") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            SettingsCards(userPreferences = userPreferences, viewModel = viewModel)
        }
    }
}
