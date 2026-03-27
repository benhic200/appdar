package com.benhic.appdar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import com.benhic.appdar.data.local.settings.SettingsRepository
import com.benhic.appdar.data.local.settings.ThemeMode
import com.benhic.appdar.data.repository.BusinessAppRepository
import com.benhic.appdar.feature.settings.SettingsContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.draw.alpha
import com.benhic.appdar.feature.widget.WidgetUpdateScheduler
import com.benhic.appdar.feature.settings.SettingsViewModel
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import com.benhic.appdar.R
import com.benhic.appdar.data.local.profiles.ProfileId
import com.benhic.appdar.feature.geofencing.GeofenceManager
import com.benhic.appdar.core.util.BatteryOptimizationHelper
import com.benhic.appdar.billing.BillingManager
import com.benhic.appdar.billing.ProManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Companion app activity that guides users through setup:
 * - Checks location permission
 * - Seeds the database (if empty)
 * - Explains widget installation
 * - Provides links to system settings for battery optimization and autostart (MIUI)
 */
enum class PermissionState {
    UNKNOWN, GRANTED, DENIED
}



@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var repository: BusinessAppRepository

    @Inject
    lateinit var geofenceManager: GeofenceManager

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsGranted ->
        Log.d(TAG, "Location permissions granted: $permissionsGranted")
        // Check if all required permissions are granted
        val allGranted = permissionsGranted.values.all { it }
        _permissionState.value = if (allGranted) PermissionState.GRANTED else PermissionState.DENIED
        if (allGranted) {
            Toast.makeText(this@MainActivity, "Location permission granted", Toast.LENGTH_SHORT).show()
            startGeofencingIfReady()
        } else {
            Toast.makeText(this@MainActivity, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private val TAG = "MainActivity"

    companion object {
        /**
         * Returns the location permissions required for this app.
         * - ACCESS_FINE_LOCATION: Required for geofencing on Android 12+
         * - ACCESS_COARSE_LOCATION: Fallback for older devices (though geofencing may not work)
         * - ACCESS_BACKGROUND_LOCATION: Required for Android 10+ background geofencing
         */
        fun getRequiredPermissions(): Array<String> {
            val permissions = mutableListOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                permissions.add("android.permission.ACCESS_BACKGROUND_LOCATION")
            }
            return permissions.toTypedArray()
        }
    }

    private val _permissionState = mutableStateOf(PermissionState.UNKNOWN)
    private val _dbSeeded = mutableStateOf(false)
    private val _isPro = mutableStateOf(false)

    private lateinit var billingManager: BillingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Restore persisted Pro state, then verify with Play Store
        _isPro.value = ProManager.isPro(this)
        billingManager = BillingManager(
            context = this,
            onProUnlocked = {
                ProManager.setPro(this, true)
                _isPro.value = true
            },
            onPurchaseFailed = { message ->
                runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
            }
        )
        billingManager.startConnection()

        val appPrefs = getSharedPreferences("appdar_prefs", MODE_PRIVATE)
        val _onboardingComplete = mutableStateOf(appPrefs.getBoolean("onboarding_complete", false))

        setContent {
            val prefs by settingsRepository.userPreferences
                .collectAsState(initial = com.benhic.appdar.data.local.settings.UserPreferences())
            val darkTheme = when (prefs.themeMode) {
                ThemeMode.LIGHT  -> false
                ThemeMode.DARK   -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            val onboardingComplete by _onboardingComplete
            MaterialTheme(
                colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()
            ) {
                if (!onboardingComplete) {
                    OnboardingScreen(
                        permissionState = _permissionState.value,
                        onRequestPermission = { requestLocationPermission() },
                        onOpenAppSettings = { openAppSettings() },
                        onOpenBatterySettings = { openBatterySettings() },
                        onComplete = {
                            appPrefs.edit().putBoolean("onboarding_complete", true).apply()
                            _onboardingComplete.value = true
                            // Trigger database seed if not done yet
                            if (!_dbSeeded.value) seedDatabase()
                        }
                    )
                } else {
                    TabbedAppScreen(
                        repository = repository,
                        permissionState = _permissionState.value,
                        dbSeeded = _dbSeeded.value,
                        isPro = _isPro.value,
                        onUpgradeTapped = { billingManager.launchPurchaseFlow(this@MainActivity) },
                        onRestorePurchase = { billingManager.checkExistingPurchases() },
                        onRequestPermission = { requestLocationPermission() },
                        onSeedDatabase = { seedDatabase() },
                        onOpenAppSettings = { openAppSettings() },
                        onOpenBatterySettings = { openBatterySettings() },
                        onFinishSetup = { finish() }
                    )
                }
            }
        }
        // Check permission on create
        checkLocationPermission()
        // Check if database is already seeded
        lifecycleScope.launch {
            val count = repository.getMappingCount()
            _dbSeeded.value = count > 0
            Log.d(TAG, "Database mapping count: $count")
            startGeofencingIfReady()
        }
    }

    private fun checkLocationPermission() {
        val allGranted = getRequiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
        _permissionState.value = if (allGranted) PermissionState.GRANTED else PermissionState.DENIED
    }

    private fun requestLocationPermission() {
        Log.d(TAG, "Requesting location permission")
        // Check if any required permission is denied
        val deniedPermissions = getRequiredPermissions().filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }
        if (deniedPermissions.isEmpty()) {
            // All permissions already granted
            Log.d(TAG, "All location permissions already granted")
            Toast.makeText(this, "Location permission already granted", Toast.LENGTH_SHORT).show()
            _permissionState.value = PermissionState.GRANTED
            return
        }
        Log.d(TAG, "Denied permissions: $deniedPermissions")
        // Always open app settings when permission is denied (user expectation)
        Log.d(TAG, "Opening app settings for location permission")
        Toast.makeText(this, "Opening app settings for location permission", Toast.LENGTH_SHORT).show()
        openAppSettings()
    }

    private fun seedDatabase() {
        Toast.makeText(this, "Seeding database...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                // Clear and re‑seed database (calls DatabaseInitializer.forceReseed)
                repository.reseed()
                val count = repository.getMappingCount()
                _dbSeeded.value = true
                Log.d(TAG, "Database seeded with $count mappings")
                Toast.makeText(this@MainActivity, "Database seeded with $count businesses", Toast.LENGTH_SHORT).show()
                startGeofencingIfReady()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to seed database", e)
                Toast.makeText(this@MainActivity, "Failed to seed database", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startGeofencingIfReady() {
        if (_permissionState.value == PermissionState.GRANTED && _dbSeeded.value) {
            lifecycleScope.launch {
                try {
                    geofenceManager.startGeofencingForAllBusinesses()
                    Log.d(TAG, "Geofencing started for all businesses")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start geofencing", e)
                }
            }
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    private fun openBatterySettings() {
        BatteryOptimizationHelper.openBatteryOptimizationSettings(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        billingManager.destroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabbedAppScreen(
    repository: BusinessAppRepository,
    permissionState: PermissionState,
    dbSeeded: Boolean,
    isPro: Boolean,
    onUpgradeTapped: () -> Unit,
    onRestorePurchase: () -> Unit,
    onRequestPermission: () -> Unit,
    onSeedDatabase: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onFinishSetup: () -> Unit
) {
    val nearbyViewModel = hiltViewModel<NearbyAppsViewModel>()
    val dashboardViewModel = hiltViewModel<DashboardViewModel>()
    val settingsViewModel = hiltViewModel<SettingsViewModel>()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf("dashboard") }
    val context = LocalContext.current

    // Manage auto-refresh alarm when low power mode or interval changes
    val settingsPrefs by settingsViewModel.userPreferences.collectAsState()
    LaunchedEffect(settingsPrefs.lowPowerMode, settingsPrefs.refreshIntervalMinutes) {
        if (settingsPrefs.lowPowerMode) {
            WidgetUpdateScheduler.cancel(context)
        } else {
            WidgetUpdateScheduler.schedule(context, settingsPrefs.refreshIntervalMinutes)
        }
    }

    val screenTitle = when (currentScreen) {
        "dashboard" -> "Dashboard"
        "nearby"  -> "Nearby Apps"
        "businesses" -> "Places"
        "home"    -> "Home Apps"
        "work"    -> "Work Apps"
        "gym"     -> "Gym Apps"
        "custom1" -> "Custom Location 1"
        "custom2" -> "Custom Location 2"
        "settings" -> "Settings"
        "setup"   -> "Setup"
        "guide"   -> "User Guide"
        "upgrade" -> "Upgrade to Pro"
        else      -> "Dashboard"
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(modifier = Modifier.fillMaxHeight()) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // ── FREE section ──────────────────────────────────────────
                    Text(
                        text = "FREE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 28.dp, bottom = 4.dp)
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Filled.Dashboard, contentDescription = null) },
                        label = { Text("Dashboard") },
                        selected = currentScreen == "dashboard",
                        onClick = {
                            currentScreen = "dashboard"
                            coroutineScope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Filled.NearMe, contentDescription = null) },
                        label = { Text("Nearby Apps") },
                        selected = currentScreen == "nearby",
                        onClick = {
                            currentScreen = "nearby"
                            coroutineScope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Filled.List, contentDescription = null) },
                        label = { Text("Places") },
                        selected = currentScreen == "businesses",
                        onClick = {
                            currentScreen = "businesses"
                            coroutineScope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        label = { Text("Home Apps") },
                        selected = currentScreen == "home",
                        onClick = {
                            currentScreen = "home"
                            coroutineScope.launch { drawerState.close() }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // ── PRO section ───────────────────────────────────────────
                    Text(
                        text = "PRO",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 28.dp, bottom = 4.dp)
                    )
                    NavigationDrawerItem(
                        modifier = Modifier.alpha(if (isPro) 1f else 0.5f),
                        icon = { Icon(if (isPro) Icons.Filled.Work else Icons.Filled.Lock, contentDescription = null) },
                        label = { Text("Work Apps") },
                        badge = if (!isPro) ({ Text("Pro", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }) else null,
                        selected = currentScreen == "work",
                        onClick = {
                            if (isPro) { currentScreen = "work" } else { currentScreen = "upgrade" }
                            coroutineScope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        modifier = Modifier.alpha(if (isPro) 1f else 0.5f),
                        icon = { Icon(if (isPro) Icons.Filled.FitnessCenter else Icons.Filled.Lock, contentDescription = null) },
                        label = { Text("Gym Apps") },
                        badge = if (!isPro) ({ Text("Pro", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }) else null,
                        selected = currentScreen == "gym",
                        onClick = {
                            if (isPro) { currentScreen = "gym" } else { currentScreen = "upgrade" }
                            coroutineScope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        modifier = Modifier.alpha(if (isPro) 1f else 0.5f),
                        icon = { Icon(if (isPro) Icons.Filled.Apps else Icons.Filled.Lock, contentDescription = null) },
                        label = { Text("Custom Location 1") },
                        badge = if (!isPro) ({ Text("Pro", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }) else null,
                        selected = currentScreen == "custom1",
                        onClick = {
                            if (isPro) { currentScreen = "custom1" } else { currentScreen = "upgrade" }
                            coroutineScope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        modifier = Modifier.alpha(if (isPro) 1f else 0.5f),
                        icon = { Icon(if (isPro) Icons.Filled.Apps else Icons.Filled.Lock, contentDescription = null) },
                        label = { Text("Custom Location 2") },
                        badge = if (!isPro) ({ Text("Pro", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }) else null,
                        selected = currentScreen == "custom2",
                        onClick = {
                            if (isPro) { currentScreen = "custom2" } else { currentScreen = "upgrade" }
                            coroutineScope.launch { drawerState.close() }
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        label = { Text("Settings") },
                        selected = currentScreen == "settings",
                        onClick = {
                            currentScreen = "settings"
                            coroutineScope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Filled.Info, contentDescription = null) },
                        label = { Text("Setup") },
                        selected = currentScreen == "setup",
                        onClick = {
                            currentScreen = "setup"
                            coroutineScope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Filled.Help, contentDescription = null) },
                        label = { Text("Guide") },
                        selected = currentScreen == "guide",
                        onClick = {
                            currentScreen = "guide"
                            coroutineScope.launch { drawerState.close() }
                        }
                    )

                    // Push upgrade bar to bottom
                    Spacer(modifier = Modifier.weight(1f))

                    // ── Persistent upgrade bar (hidden once user is Pro) ───────
                    if (!isPro) {
                        Surface(
                            onClick = {
                                currentScreen = "upgrade"
                                coroutineScope.launch { drawerState.close() }
                            },
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Unlock Appdar Pro",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Text(
                                        "Work, Gym & custom locations",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                    )
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        "£1.67",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Open drawer")
                        }
                    },
                    title = { Text(screenTitle) },
                    actions = {
                        when (currentScreen) {
                            "dashboard" -> IconButton(onClick = { dashboardViewModel.refresh() }) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                            }
                            "nearby" -> IconButton(onClick = { nearbyViewModel.refresh() }) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                            }
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
                when (currentScreen) {
                    "dashboard" -> DashboardContent(viewModel = dashboardViewModel)
                    "nearby"      -> NearbyAppsContent(viewModel = nearbyViewModel, context = context)
                    "businesses"  -> AddBusinessScreen()
                    "home"    -> LocationProfileScreen(profileId = ProfileId.HOME)
                    "work"    -> LocationProfileScreen(profileId = ProfileId.WORK)
                    "gym"     -> LocationProfileScreen(profileId = ProfileId.GYM)
                    "custom1" -> LocationProfileScreen(profileId = ProfileId.CUSTOM1)
                    "custom2" -> LocationProfileScreen(profileId = ProfileId.CUSTOM2)
                    "settings" -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        SettingsContent()
                        HorizontalDivider()
                        SetupContent(
                            permissionState = permissionState,
                            dbSeeded = dbSeeded,
                            onRequestPermission = onRequestPermission,
                            onSeedDatabase = onSeedDatabase,
                            onOpenAppSettings = onOpenAppSettings,
                            onOpenBatterySettings = onOpenBatterySettings,
                            onRestorePurchase = onRestorePurchase,
                            onFinishSetup = { currentScreen = "dashboard" }
                        )
                    }
                    "setup"   -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        SetupContent(
                            permissionState = permissionState,
                            dbSeeded = dbSeeded,
                            onRequestPermission = onRequestPermission,
                            onSeedDatabase = onSeedDatabase,
                            onOpenAppSettings = onOpenAppSettings,
                            onOpenBatterySettings = onOpenBatterySettings,
                            onRestorePurchase = onRestorePurchase,
                            onFinishSetup = { currentScreen = "dashboard" }
                        )
                    }
                    "guide"   -> UserGuideScreen()
                    "upgrade" -> ProUpgradeScreen(onUpgradeTapped = onUpgradeTapped)
                }
            }
        }
    }
}

@Composable
fun SetupContent(
    permissionState: PermissionState,
    dbSeeded: Boolean,
    onRequestPermission: () -> Unit,
    onSeedDatabase: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onRestorePurchase: () -> Unit = {},
    onFinishSetup: () -> Unit
) {
    // Database status
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Database",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (dbSeeded) {
                Text("✅ Database seeded with UK & US businesses.")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onSeedDatabase,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Re‑seed Database")
                }
            } else {
                Text("❌ Database not yet seeded.")
                Button(
                    onClick = onSeedDatabase,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Seed Database Now")
                }
            }
        }
    }

    // Location permission
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Location Permission",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            when (permissionState) {
                PermissionState.GRANTED -> {
                    Text("✅ Location permission granted (fine + background).")
                }
                PermissionState.DENIED -> {
                    Text("❌ Location permission needed for distance calculation and geofencing.")
                    Button(
                        onClick = onRequestPermission,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Grant Location Permission")
                    }
                }
                PermissionState.UNKNOWN -> {
                    Text("Checking permission…")
                }
            }
        }
    }

    // Widget installation guide
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Install Widget",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("1. Go to your home screen")
            Text("2. Long‑press and select \"Widgets\"")
            Text("3. Find \"Appdar\"")
            Text("4. Drag it to your home screen")
        }
    }

    // System settings (MIUI / battery)
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "System Settings (MIUI)",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("On MIUI devices, ensure:")
            Text("• Autostart permission is enabled")
            Text("• Battery optimization is turned off")
            Text("• App is not restricted in background")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenAppSettings,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("App Settings")
                }
                OutlinedButton(
                    onClick = onOpenBatterySettings,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Battery Settings")
                }
            }
        }
    }

    // Restore purchase (new phone / reinstall)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Restore Purchase",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Already purchased Appdar Pro? Tap below to restore it — useful after reinstalling the app or switching to a new phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onRestorePurchase,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Restore Pro Purchase")
            }
        }
    }

    // Go to Dashboard
    Button(
        onClick = onFinishSetup,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Go to Dashboard")
    }
}

@Composable
fun ProUpgradeScreen(onUpgradeTapped: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Appdar Pro",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "The right apps for wherever you are.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    onClick = onUpgradeTapped,
                    color = MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        "One-time purchase — £1.67",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // Feature list
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("What you get", style = MaterialTheme.typography.titleMedium)
                listOf(
                    Triple(Icons.Filled.Work,         "Work Apps",         "Pin the apps you use at work — they appear automatically when you arrive"),
                    Triple(Icons.Filled.FitnessCenter, "Gym Apps",          "Your gym apps front and centre when you reach the gym"),
                    Triple(Icons.Filled.Apps,          "Custom Location 1", "Any place you choose — coffee shop, library, anywhere"),
                    Triple(Icons.Filled.Apps,          "Custom Location 2", "A second fully custom location profile")
                ).forEach { (icon, title, desc) ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(title, style = MaterialTheme.typography.titleSmall)
                            Text(desc, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // How it works
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("How it works", style = MaterialTheme.typography.titleMedium)
                listOf(
                    "1. Set a location for Work, Gym, or a custom place",
                    "2. Pin the apps you want to see there",
                    "3. Appdar automatically shows them when you arrive"
                ).forEach { step ->
                    Text(step, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Button(
            onClick = onUpgradeTapped,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Unlock Pro — £1.67")
        }

        Text(
            "One-time payment. No subscription.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}
