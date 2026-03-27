package com.benhic.appdar.data.local.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for user preferences stored in DataStore.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    /**
     * Stream of [UserPreferences] that emits whenever preferences change.
     */
    val userPreferences: Flow<UserPreferences> = dataStore.data.map { preferences ->
        preferences.toUserPreferences()
    }

    /**
     * Updates the search radius.
     *
     * @param meters Radius in meters (must be positive)
     */
    suspend fun updateSearchRadius(meters: Int) {
        require(meters > 0) { "Radius must be positive" }
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SEARCH_RADIUS_METERS] = meters
        }
    }

    /**
     * Updates the distance display unit.
     */
    suspend fun updateDistanceUnit(unit: DistanceUnit) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DISTANCE_UNIT] = unit.name
        }
    }

    /**
     * Enables or disables geocoding.
     */
    suspend fun updateGeocodingEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ENABLE_GEOCODING] = enabled
        }
    }

    /**
     * Enables or disables location history caching.
     */
    suspend fun updateLocationHistoryEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ENABLE_LOCATION_HISTORY] = enabled
        }
    }

    /**
     * Updates the widget refresh interval.
     *
     * @param minutes Interval in minutes (1–60)
     */
    suspend fun updateRefreshInterval(minutes: Int) {
        require(minutes in 1..60) { "Refresh interval must be between 1 and 60 minutes" }
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.REFRESH_INTERVAL_MINUTES] = minutes
        }
    }

    /**
     * Enables or disables low power mode (manual widget refresh only).
     */
    suspend fun updateLowPowerMode(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LOW_POWER_MODE] = enabled
        }
    }

    /**
     * Updates the app theme mode.
     */
    suspend fun updateThemeMode(mode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode.name
        }
    }

    /**
     * Updates the widget background theme.
     */
    suspend fun updateWidgetTheme(mode: WidgetTheme) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.WIDGET_THEME] = mode.name
        }
    }

    /**
     * Resets all preferences to defaults.
     */
    suspend fun resetToDefaults() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    /**
     * Gets the current preferences synchronously (blocking).
     * Use only in contexts where suspending isn't possible (e.g., widget updates).
     *
     * @return Current user preferences
     */
    suspend fun getCurrentPreferences(): UserPreferences {
        return dataStore.data.map { it.toUserPreferences() }.first()
    }
}