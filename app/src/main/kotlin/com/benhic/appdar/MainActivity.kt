package com.benhic.appdar

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.benhic.appdar.feature.widget.NearbyAppsWidgetProvider
import com.benhic.appdar.feature.widget.NearbyAppsWidgetProviderGrid
import com.benhic.appdar.feature.widget.NearbyAppsWidgetProviderNano
import com.benhic.appdar.feature.widget.NearbyAppsWidgetProviderNarrow
import com.benhic.appdar.feature.widget.NearbyAppsWidgetProviderStrip
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.benhic.appdar.data.nearby.NearbyBranchFinder
import com.benhic.appdar.WalkthroughState
import com.benhic.appdar.WalkthroughStep
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
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
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

    @Inject
    lateinit var nearbyBranchFinder: NearbyBranchFinder

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
    private val _isPro = mutableStateOf(false)
    private val _walkthroughCompleted = mutableStateOf(false)
    private val _walkthroughState = mutableStateOf(WalkthroughState())
    private val _currentScreen = mutableStateOf("dashboard")

    /** Advances the walkthrough to the next step, or marks as complete if on the last step. */
    private fun advanceWalkthroughStep() {
        val current = _walkthroughState.value.currentStep
        Log.d(TAG, "advanceWalkthroughStep: current=$current")
        val nextStep = when (current) {
            WalkthroughStep.WELCOME -> WalkthroughStep.DASHBOARD_HIDE_UNINSTALLED
            WalkthroughStep.DASHBOARD_HIDE_UNINSTALLED -> WalkthroughStep.PLACES_HIDE_UNINSTALLED
            WalkthroughStep.PLACES_HIDE_UNINSTALLED -> WalkthroughStep.WIDGET_EXPLANATION
            WalkthroughStep.WIDGET_EXPLANATION -> WalkthroughStep.COMPLETE
            WalkthroughStep.COMPLETE -> WalkthroughStep.COMPLETE
        }
        // Automatically navigate to appropriate screen for the next step
        when (nextStep) {
            WalkthroughStep.PLACES_HIDE_UNINSTALLED -> _currentScreen.value = "businesses"
            WalkthroughStep.WIDGET_EXPLANATION -> _currentScreen.value = "dashboard"
            else -> {}
        }
        if (nextStep == WalkthroughStep.COMPLETE) {
            // Walkthrough finished, persist completion
            val prefs = getSharedPreferences("appdar_prefs", MODE_PRIVATE)
            prefs.edit().putBoolean("walkthrough_completed", true).apply()
            _walkthroughCompleted.value = true
            Log.d(TAG, "walkthrough completed, pref set to true")
        }
        _walkthroughState.value = _walkthroughState.value.copy(currentStep = nextStep)
        Log.d(TAG, "walkthrough state updated: currentStep=$nextStep")
    }

    /** Skips the entire walkthrough, marking it as completed. */
    private fun skipWalkthrough() {
        Log.d(TAG, "skipWalkthrough called")
        val prefs = getSharedPreferences("appdar_prefs", MODE_PRIVATE)
        prefs.edit().putBoolean("walkthrough_completed", true).apply()
        _walkthroughCompleted.value = true
        _walkthroughState.value = WalkthroughState(currentStep = WalkthroughStep.COMPLETE)
        Log.d(TAG, "walkthrough skipped, pref set to true, state set to COMPLETE")
    }

    /** Restarts the walkthrough from the beginning (used from settings). */
    private fun restartWalkthrough() {
        Log.d(TAG, "restartWalkthrough called, setting pref to false and resetting state")
        val prefs = getSharedPreferences("appdar_prefs", MODE_PRIVATE)
        prefs.edit().putBoolean("walkthrough_completed", false).apply()
        _walkthroughCompleted.value = false
        _walkthroughState.value = WalkthroughState()
        Log.d(TAG, "walkthrough restarted, pref=false, state=WELCOME")
    }

    private lateinit var billingManager: BillingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
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
        // Initialize walkthrough state from preferences
        _walkthroughCompleted.value = appPrefs.getBoolean("walkthrough_completed", false)
        Log.d(TAG, "onCreate: walkthrough_completed pref=${appPrefs.getBoolean("walkthrough_completed", false)}")

        setContent {
            val prefs by settingsRepository.userPreferences
                .collectAsState(initial = com.benhic.appdar.data.local.settings.UserPreferences())
            val darkTheme = when (prefs.themeMode) {
                ThemeMode.LIGHT  -> false
                ThemeMode.DARK   -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            val onboardingComplete by _onboardingComplete
            val walkthroughCompleted by _walkthroughCompleted
            val walkthroughState by _walkthroughState

            MaterialTheme(
                colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize().safeDrawingPadding(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Observe OpenStreetMap download completion to trigger walkthrough
                    LaunchedEffect(onboardingComplete, walkthroughCompleted) {
                        Log.d(TAG, "LaunchedEffect: onboardingComplete=$onboardingComplete, walkthroughCompleted=$walkthroughCompleted")
                        if (onboardingComplete && !walkthroughCompleted) {
                            Log.d(TAG, "Waiting for OSM download completion...")
                            nearbyBranchFinder.fetchState.collect { fetchState ->
                                Log.d(TAG, "fetchState: isLoading=${fetchState.isLoading}, branches.size=${fetchState.branches.size}, isOffline=${fetchState.isOffline}, status=${fetchState.statusMessage}")
                                if (!fetchState.isLoading) {
                                    Log.d(TAG, "OSM download finished (isLoading=false), resetting walkthrough state. branches.size=${fetchState.branches.size}, isOffline=${fetchState.isOffline}")
                                    // Download complete (or failed), start walkthrough
                                    // Reset walkthrough state to first step
                                    _walkthroughState.value = WalkthroughState()
                                }
                            }
                        }
                    }

                    if (!onboardingComplete) {
                        OnboardingScreen(
                            permissionState = _permissionState.value,
                            onRequestPermission = { requestLocationPermission() },
                            onOpenAppSettings = { openAppSettings() },
                            onOpenBatterySettings = { openBatterySettings() },
                            onComplete = {
                                appPrefs.edit().putBoolean("onboarding_complete", true).apply()
                                _onboardingComplete.value = true
                            }
                        )
                    } else {
                        TabbedAppScreen(
                            repository = repository,
                            permissionState = _permissionState.value,
                            isPro = _isPro.value,
                            onUpgradeTapped = { billingManager.launchPurchaseFlow(this@MainActivity) },
                            onRestorePurchase = { billingManager.checkExistingPurchases() },
                            onRequestPermission = { requestLocationPermission() },
                            onOpenAppSettings = { openAppSettings() },
                            onOpenBatterySettings = { openBatterySettings() },
                            onFinishSetup = { finish() },
                            currentScreen = _currentScreen.value,
                            onScreenChange = { screen -> _currentScreen.value = screen },
                            walkthroughState = walkthroughState,
                            walkthroughCompleted = walkthroughCompleted,
                            onWalkthroughNext = { advanceWalkthroughStep() },
                            onWalkthroughSkip = { skipWalkthrough() },
                            onRestartWalkthrough = { restartWalkthrough() },
                            onChangeScreen = { screen -> _currentScreen.value = screen }
                        )
                    }
                }
            }
        }
        // Check permission on create
        checkLocationPermission()
        startGeofencingIfReady()
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

    private fun startGeofencingIfReady() {
        if (_permissionState.value == PermissionState.GRANTED) {
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
    isPro: Boolean,
    onUpgradeTapped: () -> Unit,
    onRestorePurchase: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onFinishSetup: () -> Unit,
    currentScreen: String = "dashboard",
    onScreenChange: (String) -> Unit = {},
    walkthroughState: WalkthroughState,
    walkthroughCompleted: Boolean,
    onWalkthroughNext: () -> Unit,
    onWalkthroughSkip: () -> Unit,
    onRestartWalkthrough: () -> Unit = {},
    onChangeScreen: (String) -> Unit
) {
    val nearbyViewModel = hiltViewModel<NearbyAppsViewModel>()
    val dashboardViewModel = hiltViewModel<DashboardViewModel>()
    val settingsViewModel = hiltViewModel<SettingsViewModel>()
    val profileViewModel = hiltViewModel<LocationProfileViewModel>()
    val homeProfile   by profileViewModel.profileState(ProfileId.HOME).collectAsState()
    val workProfile   by profileViewModel.profileState(ProfileId.WORK).collectAsState()
    val gymProfile    by profileViewModel.profileState(ProfileId.GYM).collectAsState()
    val custom1Profile by profileViewModel.profileState(ProfileId.CUSTOM1).collectAsState()
    val custom2Profile by profileViewModel.profileState(ProfileId.CUSTOM2).collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    var currentScreenState by remember { mutableStateOf(currentScreen) }
    val context = LocalContext.current

    // Sync external currentScreen changes into local state
    LaunchedEffect(currentScreen) {
        currentScreenState = currentScreen
    }

    // When local state changes, notify parent via onScreenChange
    LaunchedEffect(currentScreenState) {
        onScreenChange(currentScreenState)
    }

    // Manage auto-refresh alarm when low power mode or interval changes
    val settingsPrefs by settingsViewModel.userPreferences.collectAsState()
    LaunchedEffect(settingsPrefs.lowPowerMode, settingsPrefs.refreshIntervalSeconds) {
        if (settingsPrefs.lowPowerMode) {
            WidgetUpdateScheduler.cancel(context)
        } else {
            WidgetUpdateScheduler.schedule(context, settingsPrefs.refreshIntervalSeconds)
        }
    }

    val screenTitle = when (currentScreenState) {
        "dashboard" -> "Dashboard"
        "nearby"  -> "Nearby Apps"
        "businesses" -> "Places"
        "home"    -> homeProfile.displayName
        "work"    -> workProfile.displayName
        "gym"     -> gymProfile.displayName
        "custom1" -> custom1Profile.displayName
        "custom2" -> custom2Profile.displayName
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
                    // Scrollable nav items — weight(1f) so Pro button is always visible below
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // ── FREE section ──────────────────────────────────────────
                    Text(
                        text = "FREE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 28.dp, bottom = 4.dp)
                    )
                    NavigationDrawerItem(
                        icon = { AnimatedNavIcon(selected = currentScreenState == "dashboard") { Icon(Icons.Filled.Dashboard, contentDescription = null) } },
                        label = { Text("Dashboard") },
                        selected = currentScreenState == "dashboard",
                        onClick = {
                            currentScreenState = "dashboard"
                            coroutineScope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        icon = { AnimatedNavIcon(selected = currentScreenState == "nearby") { Icon(Icons.Filled.NearMe, contentDescription = null) } },
                        label = { Text("Nearby Apps") },
                        selected = currentScreenState == "nearby",
                        onClick = {
                            currentScreenState = "nearby"
                            coroutineScope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        icon = { AnimatedNavIcon(selected = currentScreenState == "businesses") { Icon(Icons.Filled.List, contentDescription = null) } },
                        label = { Text("Places") },
                        selected = currentScreenState == "businesses",
                        onClick = {
                            currentScreenState = "businesses"
                            coroutineScope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        icon = { AnimatedNavIcon(selected = currentScreenState == "home") { Icon(Icons.Filled.Home, contentDescription = null) } },
                        label = { Text(homeProfile.displayName) },
                        selected = currentScreenState == "home",
                        onClick = {
                            currentScreenState = "home"
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
                        icon = { AnimatedNavIcon(selected = currentScreenState == "work") { Icon(if (isPro) Icons.Filled.Work else Icons.Filled.Lock, contentDescription = null) } },
                        label = { Text(workProfile.displayName) },
                        badge = if (!isPro) ({ Text("Pro", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }) else null,
                        selected = currentScreenState == "work",
                        onClick = {
                            if (isPro) { currentScreenState = "work" } else { currentScreenState = "upgrade" }
                            coroutineScope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        modifier = Modifier.alpha(if (isPro) 1f else 0.5f),
                        icon = { AnimatedNavIcon(selected = currentScreenState == "gym") { Icon(if (isPro) Icons.Filled.FitnessCenter else Icons.Filled.Lock, contentDescription = null) } },
                        label = { Text(gymProfile.displayName) },
                        badge = if (!isPro) ({ Text("Pro", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }) else null,
                        selected = currentScreenState == "gym",
                        onClick = {
                            if (isPro) { currentScreenState = "gym" } else { currentScreenState = "upgrade" }
                            coroutineScope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        modifier = Modifier.alpha(if (isPro) 1f else 0.5f),
                        icon = { AnimatedNavIcon(selected = currentScreenState == "custom1") { Icon(if (isPro) Icons.Filled.Apps else Icons.Filled.Lock, contentDescription = null) } },
                        label = { Text(custom1Profile.displayName) },
                        badge = if (!isPro) ({ Text("Pro", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }) else null,
                        selected = currentScreenState == "custom1",
                        onClick = {
                            if (isPro) { currentScreenState = "custom1" } else { currentScreenState = "upgrade" }
                            coroutineScope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        modifier = Modifier.alpha(if (isPro) 1f else 0.5f),
                        icon = { AnimatedNavIcon(selected = currentScreenState == "custom2") { Icon(if (isPro) Icons.Filled.Apps else Icons.Filled.Lock, contentDescription = null) } },
                        label = { Text(custom2Profile.displayName) },
                        badge = if (!isPro) ({ Text("Pro", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }) else null,
                        selected = currentScreenState == "custom2",
                        onClick = {
                            if (isPro) { currentScreenState = "custom2" } else { currentScreenState = "upgrade" }
                            coroutineScope.launch { drawerState.close() }
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    NavigationDrawerItem(
                        icon = { AnimatedNavIcon(selected = currentScreenState == "settings") { Icon(Icons.Filled.Settings, contentDescription = null) } },
                        label = { Text("Settings") },
                        selected = currentScreenState == "settings",
                        onClick = {
                            currentScreenState = "settings"
                            coroutineScope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        icon = { AnimatedNavIcon(selected = currentScreenState == "setup") { Icon(Icons.Filled.Info, contentDescription = null) } },
                        label = { Text("Setup") },
                        selected = currentScreenState == "setup",
                        onClick = {
                            currentScreenState = "setup"
                            coroutineScope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        icon = { AnimatedNavIcon(selected = currentScreenState == "guide") { Icon(Icons.Filled.Help, contentDescription = null) } },
                        label = { Text("Guide") },
                        selected = currentScreenState == "guide",
                        onClick = {
                            currentScreenState = "guide"
                            coroutineScope.launch { drawerState.close() }
                        }
                    )
                    } // end scrollable Column

                    // ── Persistent upgrade bar (hidden once user is Pro) ───────
                    if (!isPro) {
                        Surface(
                            onClick = {
                                currentScreenState = "upgrade"
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
                        when (currentScreenState) {
                            "dashboard" -> {
                                var showWidgetPicker by remember { mutableStateOf(false) }
                                val awm = AppWidgetManager.getInstance(context)

                                IconButton(onClick = { showWidgetPicker = true }) {
                                    Icon(Icons.Filled.Widgets, contentDescription = "Add widget")
                                }
                                IconButton(onClick = { dashboardViewModel.refresh(force = true) }) {
                                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                                }

                                if (showWidgetPicker) {
                                    val supported = awm.isRequestPinAppWidgetSupported
                                    // Tracks which widget class the user chose so we can show
                                    // the "go to home screen" step before backgrounding the app.
                                    var pendingCls by remember {
                                        mutableStateOf<Class<*>?>(null)
                                    }
                                    AlertDialog(
                                        onDismissRequest = {
                                            showWidgetPicker = false
                                            pendingCls = null
                                        },
                                        title = { Text(if (pendingCls != null) "Place your widget" else "Add widget") },
                                        text = {
                                            if (pendingCls != null) {
                                                // Step 2: user chose a style — tell them what to do next.
                                                // This message covers both well-behaved launchers
                                                // (placement prompt appears automatically) and MIUI /
                                                // launchers that ignore the API (manual fallback).
                                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                    Text("1. Tap \"Go to Home Screen\" below.")
                                                    Text("2. Your launcher should show a widget placement prompt — tap to confirm.")
                                                    Text(
                                                        "Nothing appeared? Long-press your home screen → Widgets → Appdar to add manually.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            } else if (!supported) {
                                                Text("Your launcher doesn't support quick-add.\n\nLong-press your home screen → Widgets → Appdar to add one manually.")
                                            } else {
                                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Text("Choose a widget style:", style = MaterialTheme.typography.bodyMedium)
                                                    listOf(
                                                        "Grid / List"     to NearbyAppsWidgetProviderGrid::class.java,
                                                        "Strip"           to NearbyAppsWidgetProviderStrip::class.java,
                                                        "Nano (tiny)"     to NearbyAppsWidgetProviderNano::class.java,
                                                        "Narrow column"   to NearbyAppsWidgetProviderNarrow::class.java,
                                                        "Scrollable list" to NearbyAppsWidgetProvider::class.java
                                                    ).forEach { (label, cls) ->
                                                        OutlinedButton(
                                                            onClick = {
                                                                awm.requestPinAppWidget(
                                                                    ComponentName(context, cls), null, null
                                                                )
                                                                // Show step-2 instructions regardless of
                                                                // the return value — some launchers (MIUI)
                                                                // return true but handle it silently, so
                                                                // the manual fallback text is always shown.
                                                                pendingCls = cls
                                                            },
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) { Text(label) }
                                                    }
                                                }
                                            }
                                        },
                                        confirmButton = {
                                            if (pendingCls != null) {
                                                Button(onClick = {
                                                    showWidgetPicker = false
                                                    pendingCls = null
                                                    (context as? android.app.Activity)?.moveTaskToBack(true)
                                                }) { Text("Go to Home Screen") }
                                            }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = {
                                                showWidgetPicker = false
                                                pendingCls = null
                                            }) { Text(if (pendingCls != null) "Close" else "Cancel") }
                                        }
                                    )
                                }
                            }
                            "nearby" -> IconButton(onClick = { nearbyViewModel.refresh(force = true) }) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                            }
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
            AnimatedContent(
                targetState = currentScreenState,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "screen_transition"
            ) { screen ->
                when (screen) {
                    "dashboard" -> DashboardContent(
                        viewModel = dashboardViewModel,
                        walkthroughState = walkthroughState,
                        walkthroughCompleted = walkthroughCompleted,
                        onWalkthroughNext = onWalkthroughNext,
                        onWalkthroughSkip = onWalkthroughSkip,
                        onChangeScreen = onChangeScreen,
                    )
                    "nearby"      -> NearbyAppsContent(viewModel = nearbyViewModel, context = context)
                    "businesses"  -> AddBusinessScreen(
                        walkthroughState = walkthroughState,
                        walkthroughCompleted = walkthroughCompleted,
                        onWalkthroughNext = onWalkthroughNext,
                        onWalkthroughSkip = onWalkthroughSkip,
                        onChangeScreen = onChangeScreen,
                    )
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
                        SettingsContent(
                            onNavigateToDashboard = {
                                currentScreenState = "dashboard"
                                dashboardViewModel.refresh(force = true)
                            },
                            onRestartWalkthrough = onRestartWalkthrough
                        )
                        HorizontalDivider()
                        SetupContent(
                            permissionState = permissionState,
                            onRequestPermission = onRequestPermission,
                            onOpenAppSettings = onOpenAppSettings,
                            onOpenBatterySettings = onOpenBatterySettings,
                            onRestorePurchase = onRestorePurchase,
                            onFinishSetup = { currentScreenState = "dashboard" }
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
                            onRequestPermission = onRequestPermission,
                            onOpenAppSettings = onOpenAppSettings,
                            onOpenBatterySettings = onOpenBatterySettings,
                            onRestorePurchase = onRestorePurchase,
                            onFinishSetup = { currentScreenState = "dashboard" }
                        )
                    }
                    "guide"   -> UserGuideScreen()
                    "upgrade" -> ProUpgradeScreen(onUpgradeTapped = onUpgradeTapped)
                    else -> DashboardContent(
                        viewModel = dashboardViewModel,
                        walkthroughState = walkthroughState,
                        walkthroughCompleted = walkthroughCompleted,
                        onWalkthroughNext = onWalkthroughNext,
                        onWalkthroughSkip = onWalkthroughSkip,
                        onChangeScreen = onChangeScreen,
                    )
                }
            } // AnimatedContent
            }
        }
    }
}

@Composable
private fun AnimatedNavIcon(selected: Boolean, content: @Composable () -> Unit) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.22f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "nav_icon_scale"
    )
    Box(modifier = Modifier.scale(scale)) { content() }
}

@Composable
fun SetupContent(
    permissionState: PermissionState,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onRestorePurchase: () -> Unit = {},
    onFinishSetup: () -> Unit
) {
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
