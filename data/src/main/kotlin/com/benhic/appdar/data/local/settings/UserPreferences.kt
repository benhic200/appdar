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
    val distanceUnit: DistanceUnit = DistanceUnit.KILOMETERS,
    val enableGeocoding: Boolean = false,
    val enableLocationHistory: Boolean = false,
    val refreshIntervalMinutes: Int = 5,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val lowPowerMode: Boolean = false,
    val widgetTheme: WidgetTheme = WidgetTheme.SYSTEM
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
 * DataStore preference keys for [UserPreferences].
 */
object PreferencesKeys {
    val SEARCH_RADIUS_METERS = intPreferencesKey("search_radius_meters")
    val DISTANCE_UNIT = stringPreferencesKey("distance_unit")
    val ENABLE_GEOCODING = booleanPreferencesKey("enable_geocoding")
    val ENABLE_LOCATION_HISTORY = booleanPreferencesKey("enable_location_history")
    val REFRESH_INTERVAL_MINUTES = intPreferencesKey("refresh_interval_minutes")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val LOW_POWER_MODE = booleanPreferencesKey("low_power_mode")
    val WIDGET_THEME = stringPreferencesKey("widget_theme")
}

/**
 * Converts [Preferences] to [UserPreferences].
 */
fun Preferences.toUserPreferences(): UserPreferences = UserPreferences(
    searchRadiusMeters = this[PreferencesKeys.SEARCH_RADIUS_METERS] ?: 5000,
    distanceUnit = this[PreferencesKeys.DISTANCE_UNIT]?.let { unitString ->
        DistanceUnit.values().find { it.name == unitString } ?: DistanceUnit.KILOMETERS
    } ?: DistanceUnit.KILOMETERS,
    enableGeocoding = this[PreferencesKeys.ENABLE_GEOCODING] ?: false,
    enableLocationHistory = this[PreferencesKeys.ENABLE_LOCATION_HISTORY] ?: false,
    refreshIntervalMinutes = this[PreferencesKeys.REFRESH_INTERVAL_MINUTES] ?: 5,
    themeMode = this[PreferencesKeys.THEME_MODE]?.let { modeString ->
        ThemeMode.values().find { it.name == modeString } ?: ThemeMode.SYSTEM
    } ?: ThemeMode.SYSTEM,
    lowPowerMode = this[PreferencesKeys.LOW_POWER_MODE] ?: false,
    widgetTheme = this[PreferencesKeys.WIDGET_THEME]?.let { s ->
        WidgetTheme.values().find { it.name == s } ?: WidgetTheme.SYSTEM
    } ?: WidgetTheme.SYSTEM
)