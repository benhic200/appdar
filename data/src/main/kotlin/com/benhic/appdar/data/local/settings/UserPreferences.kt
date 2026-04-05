package com.benhic.appdar.data.local.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * User preferences for Nearby Apps Widget, stored via DataStore.
 *
 * @property searchRadiusMeters Detection radius in meters (default: 5000)
 * @property distanceUnit Display unit for distances (default: METERS)
 * @property enableGeocoding Whether to show addresses via geocoding API (default: false)
 * @property enableLocationHistory Whether to cache location history locally (default: false)
 * @property refreshIntervalMinutes How often to refresh widget data in minutes (default: 5)
 */
data class UserPreferences(
    val searchRadiusMeters: Int = 5000,
    val distanceUnit: DistanceUnit = DistanceUnit.MILES,
    val enableGeocoding: Boolean = false,
    val enableLocationHistory: Boolean = true,
    /** Refresh interval in seconds. Dashboard uses exact value; widget clamps to ≥60s. */
    val refreshIntervalSeconds: Int = 1,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val lowPowerMode: Boolean = false,
    val widgetTheme: WidgetTheme = WidgetTheme.SYSTEM,
    val regionPreference: RegionPreference = RegionPreference.AUTO,
    /** When true, widget self-schedules at the user's interval while the screen is on,
     *  and immediately refreshes when the user unlocks their device. */
    val screenOnRefreshEnabled: Boolean = true
)

/**
 * Distance display unit.
 */
enum class DistanceUnit {
    METERS,
    KILOMETERS,
    MILES,
    FEET
}

/**
 * App theme mode.
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

/**
 * Widget background theme (independent of the app theme).
 */
enum class WidgetTheme {
    SYSTEM,
    LIGHT,
    DARK
}

/**
 * Region/country preference for branch data and Places list filtering.
 * AUTO = detect from GPS on each lookup.
 */
enum class RegionPreference {
    AUTO,
    UK,
    US
}

/**
 * DataStore preference keys for [UserPreferences].
 */
object PreferencesKeys {
    val SEARCH_RADIUS_METERS = intPreferencesKey("search_radius_meters")
    val DISTANCE_UNIT = stringPreferencesKey("distance_unit")
    val ENABLE_GEOCODING = booleanPreferencesKey("enable_geocoding")
    val ENABLE_LOCATION_HISTORY = booleanPreferencesKey("enable_location_history")
    val REFRESH_INTERVAL_MINUTES = intPreferencesKey("refresh_interval_minutes")  // legacy key — minutes
    val REFRESH_INTERVAL_SECONDS = intPreferencesKey("refresh_interval_seconds")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val LOW_POWER_MODE = booleanPreferencesKey("low_power_mode")
    val WIDGET_THEME = stringPreferencesKey("widget_theme")
    val REGION_PREFERENCE = stringPreferencesKey("region_preference")
    val SCREEN_ON_REFRESH_ENABLED = booleanPreferencesKey("screen_on_refresh_enabled")
}

/**
 * Converts [Preferences] to [UserPreferences].
 */
fun Preferences.toUserPreferences(): UserPreferences = UserPreferences(
    searchRadiusMeters = this[PreferencesKeys.SEARCH_RADIUS_METERS] ?: 5000,
    distanceUnit = this[PreferencesKeys.DISTANCE_UNIT]?.let { unitString ->
        DistanceUnit.values().find { it.name == unitString } ?: DistanceUnit.MILES
    } ?: DistanceUnit.MILES,
    enableGeocoding = this[PreferencesKeys.ENABLE_GEOCODING] ?: false,
    enableLocationHistory = this[PreferencesKeys.ENABLE_LOCATION_HISTORY] ?: true,
    // Migrate: old users have refreshIntervalMinutes (1–60), multiply by 60 to get seconds.
    // New users (or after first write) use REFRESH_INTERVAL_SECONDS directly.
    refreshIntervalSeconds = this[PreferencesKeys.REFRESH_INTERVAL_SECONDS]
        ?: ((this[PreferencesKeys.REFRESH_INTERVAL_MINUTES] ?: 5) * 60).let { if (it == 300) 1 else it },
    themeMode = this[PreferencesKeys.THEME_MODE]?.let { modeString ->
        ThemeMode.values().find { it.name == modeString } ?: ThemeMode.SYSTEM
    } ?: ThemeMode.SYSTEM,
    lowPowerMode = this[PreferencesKeys.LOW_POWER_MODE] ?: false,
    widgetTheme = this[PreferencesKeys.WIDGET_THEME]?.let { s ->
        WidgetTheme.values().find { it.name == s } ?: WidgetTheme.SYSTEM
    } ?: WidgetTheme.SYSTEM,
    regionPreference = this[PreferencesKeys.REGION_PREFERENCE]?.let { s ->
        RegionPreference.values().find { it.name == s } ?: RegionPreference.AUTO
    } ?: RegionPreference.AUTO,
    screenOnRefreshEnabled = this[PreferencesKeys.SCREEN_ON_REFRESH_ENABLED] ?: true
)