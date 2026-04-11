package com.benhic.appdar.feature.settings

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import coil.compose.AsyncImage
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.benhic.appdar.feature.settings.R
import com.benhic.appdar.data.local.settings.DistanceUnit
import com.benhic.appdar.data.local.settings.RegionPreference
import com.benhic.appdar.data.local.settings.ThemeMode
import com.benhic.appdar.data.local.settings.WidgetTheme

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
    userPreferences: com.benhic.appdar.data.local.settings.UserPreferences,
    viewModel: SettingsViewModel
) {
    // Detection Radius
    val useKm = userPreferences.distanceUnit == DistanceUnit.KILOMETERS
    val metersPerUnit = if (useKm) 1000f else 1609.344f
    val unitLabel = if (useKm) "km" else "miles"
    // Slider operates in the current unit (0.5 – 10), stored internally as meters
    val sliderValue = (userPreferences.searchRadiusMeters / metersPerUnit).coerceIn(0.5f, 10f)
    val displayValue = userPreferences.searchRadiusMeters / metersPerUnit
    val displayText = if (displayValue % 1f == 0f) "${displayValue.toInt()} $unitLabel"
                      else "%.1f $unitLabel".format(displayValue)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Detection Radius",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.padding(4.dp))
            Text(
                text = "Show businesses within $displayText of your location",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = sliderValue,
                onValueChange = { viewModel.updateSearchRadius((it * metersPerUnit).toInt()) },
                valueRange = 0.5f..10f,
                // 0.5 → 10 in 0.5 steps = 20 positions → 18 intermediate steps
                steps = 18,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "0.5 $unitLabel – 10 $unitLabel  (default ${if (useKm) "5 km" else "~3 miles"})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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

    // Region
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Region",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.padding(4.dp))
            Text(
                text = "Controls which brands appear in Places and which branch data is downloaded. " +
                    "Auto-detect switches automatically when you travel between UK and US.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.padding(6.dp))
            RegionDropdown(
                selected = userPreferences.regionPreference,
                onSelect = { viewModel.updateRegionPreference(it) }
            )
            Spacer(modifier = Modifier.padding(4.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Text(
                    text = "Currently available in UK+Ireland and US only. More regions coming soon.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }

    Spacer(modifier = Modifier.padding(8.dp))

    // Background Refresh
    val refreshPresets = listOf(1, 2, 5, 10, 15, 30, 45, 60, 90, 120, 300, 600, 900, 1800, 3600)
    val currentSeconds = userPreferences.refreshIntervalSeconds
    val currentPresetIndex = refreshPresets.indexOfFirst { it >= currentSeconds }
        .let { if (it < 0) refreshPresets.lastIndex else it }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Background Refresh",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.padding(4.dp))
            Text(
                text = "Every ${formatRefreshInterval(currentSeconds)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = currentPresetIndex.toFloat(),
                onValueChange = { viewModel.updateRefreshInterval(refreshPresets[it.toInt()]) },
                valueRange = 0f..(refreshPresets.lastIndex.toFloat()),
                steps = refreshPresets.lastIndex - 1,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Controls how often the Dashboard, Nearby Apps tab, and home screen widget " +
                    "check your location (1 second – 1 hour).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (currentSeconds < 60) {
                Spacer(modifier = Modifier.padding(4.dp))
                Text(
                    text = if (userPreferences.screenOnRefreshEnabled)
                        "The background schedule is limited to 60 seconds minimum (Android system constraint), " +
                        "but Screen-on Refresh is enabled — the widget also updates on every screen unlock, " +
                        "so your ${formatRefreshInterval(currentSeconds)} rate is effective while the screen is on."
                    else
                        "Widget refresh is limited to 60 seconds minimum (Android system constraint). " +
                        "Enable Screen-on Refresh below to get sub-60 second updates while the screen is on. " +
                        "The Dashboard and Nearby Apps tabs will still refresh at ${formatRefreshInterval(currentSeconds)}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (userPreferences.screenOnRefreshEnabled)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }

    Spacer(modifier = Modifier.padding(8.dp))

    // Screen-on Refresh
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
                    text = "Screen-on Refresh",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = if (userPreferences.screenOnRefreshEnabled)
                        "Widget refreshes at your set rate while screen is on, and instantly on unlock"
                    else
                        "Widget refreshes at your set rate on a background schedule only",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = userPreferences.screenOnRefreshEnabled,
                onCheckedChange = { viewModel.toggleScreenOnRefresh(it) }
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
                        "Disables auto-refresh (tap widget to refresh manually)",
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

    // Branch Data
    var branchDataCleared by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Branch Data",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.padding(4.dp))
            Text(
                text = "Branch locations are downloaded once and automatically refreshed every 30 days. " +
                    "Tap below to force a fresh download next time you open the app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.padding(8.dp))
            if (branchDataCleared) {
                Text(
                    text = "Done — branch data will re-download when you next open the Dashboard or Nearby Apps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                OutlinedButton(
                    onClick = {
                        viewModel.forceRedownloadBranchData()
                        branchDataCleared = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Force Re-download Branch Data")
                }
            }
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
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false) }
                    } else {
                        OutlinedButton(
                            onClick = { viewModel.updateThemeMode(mode) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false) }
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
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false) }
                    } else {
                        OutlinedButton(
                            onClick = { viewModel.updateWidgetTheme(mode) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false) }
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.padding(8.dp))

    // Buy Me a Coffee
    val bmcContext = LocalContext.current
    val bmcYellow = Color(0xFFFFDD00)
    val bmcBrown  = Color(0xFF191919)
    val infiniteTransition = rememberInfiniteTransition(label = "avatar_bob")
    val bobOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue  = -8f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bobOffset"
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = ImageRequest.Builder(bmcContext)
                    .data(R.drawable.avatar_developer)
                    .decoderFactory(ImageDecoderDecoder.Factory())
                    .build(),
                contentDescription = "Developer avatar",
                modifier = Modifier
                    .size(96.dp)
                    .graphicsLayer { translationY = bobOffset * density }
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "If this app has saved you time,\nyou can buy me a coffee ☕",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    bmcContext.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://buymeacoffee.com/benhic200"))
                            .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = bmcYellow,
                    contentColor   = bmcBrown
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Buy me a coffee ☕", style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    Spacer(modifier = Modifier.padding(8.dp))

    // About
    val context = LocalContext.current
    val packageInfo = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("About", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.padding(4.dp))
            Text(
                text = "Appdar  v${packageInfo?.versionName ?: "—"}  (build ${packageInfo?.longVersionCode ?: "—"})",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.padding(6.dp))
            OutlinedButton(
                onClick = {
                    val version  = packageInfo?.versionName ?: "unknown"
                    val build    = packageInfo?.longVersionCode?.toString() ?: "unknown"
                    val device   = "${Build.MANUFACTURER} ${Build.MODEL}"
                    val android  = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
                    val body = """
                        **App version:** $version (build $build)
                        **Device:** $device
                        **OS:** $android

                        **Steps to reproduce:**
                        1.

                        **Expected behaviour:**

                        **Actual behaviour:**
                    """.trimIndent()
                    val url = Uri.Builder()
                        .scheme("https")
                        .authority("github.com")
                        .appendPath("benhic200").appendPath("appdar")
                        .appendPath("issues").appendPath("new")
                        .appendQueryParameter("title", "Bug report")
                        .appendQueryParameter("body", body)
                        .build()
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, url)
                            .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Report a bug on GitHub")
            }
        }
    }

    Spacer(modifier = Modifier.padding(8.dp))

    // OpenStreetMap attribution
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Powered by OpenStreetMap", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.padding(4.dp))
            Text(
                text = "A big thank you to OpenStreetMap and its contributors for making " +
                       "branch location data freely available. OSM is a free, editable map " +
                       "of the world — if a location is wrong or missing, anyone can fix it.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.padding(6.dp))
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org"))
                            .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Visit OpenStreetMap.org")
            }
        }
    }
}

private fun formatRefreshInterval(seconds: Int): String = when {
    seconds < 60   -> if (seconds == 1) "1 second" else "$seconds seconds"
    seconds < 3600 -> {
        val mins = seconds / 60
        if (mins == 1) "1 minute" else "$mins minutes"
    }
    else -> "1 hour"
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegionDropdown(
    selected: com.benhic.appdar.data.local.settings.RegionPreference,
    onSelect: (com.benhic.appdar.data.local.settings.RegionPreference) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        com.benhic.appdar.data.local.settings.RegionPreference.AUTO to "Auto-detect from GPS",
        com.benhic.appdar.data.local.settings.RegionPreference.UK   to "UK & Ireland",
        com.benhic.appdar.data.local.settings.RegionPreference.US   to "United States"
    )
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second ?: "Auto-detect from GPS"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (pref, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelect(pref)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}
