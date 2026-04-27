package com.benhic.appdar.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.benhic.appdar.data.local.settings.DistanceUnit
import com.benhic.appdar.data.local.settings.RegionPreference
import com.benhic.appdar.data.local.settings.ThemeMode
import com.benhic.appdar.data.local.settings.WidgetTheme
import com.benhic.appdar.data.local.settings.UserPreferences
import com.benhic.appdar.data.local.settings.SettingsRepository
import com.benhic.appdar.data.nearby.NearbyBranchFinder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val nearbyBranchFinder: NearbyBranchFinder
) : ViewModel() {

    val userPreferences: StateFlow<UserPreferences> = settingsRepository.userPreferences
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UserPreferences()
        )

    fun updateSearchRadius(radiusMeters: Int) {
        viewModelScope.launch {
            settingsRepository.updateSearchRadius(radiusMeters)
        }
    }

    fun updateDistanceUnit(unit: DistanceUnit) {
        viewModelScope.launch {
            settingsRepository.updateDistanceUnit(unit)
        }
    }

    fun toggleGeocoding(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateGeocodingEnabled(enabled)
        }
    }

    fun toggleLocationHistory(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateLocationHistoryEnabled(enabled)
        }
    }

    fun updateRefreshInterval(seconds: Int) {
        viewModelScope.launch {
            settingsRepository.updateRefreshInterval(seconds)
        }
    }

    fun updateThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.updateThemeMode(mode)
        }
    }

    fun toggleLowPowerMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateLowPowerMode(enabled)
        }
    }

    fun toggleScreenOnRefresh(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateScreenOnRefreshEnabled(enabled)
        }
    }

    fun updateWidgetTheme(mode: WidgetTheme) {
        viewModelScope.launch {
            settingsRepository.updateWidgetTheme(mode)
        }
    }

    fun updateRegionPreference(pref: RegionPreference) {
        viewModelScope.launch {
            settingsRepository.updateRegionPreference(pref)
            nearbyBranchFinder.clearCache()
        }
    }

    /** Saves the new region and wipes the branch DB so the Dashboard does a fresh foreground download. */
    fun confirmRegionChange(pref: RegionPreference) {
        viewModelScope.launch {
            settingsRepository.updateRegionPreference(pref)
            nearbyBranchFinder.clearCacheAndWipeDb()
        }
    }

    /** Wipes the local branch DB and TTL so the next Dashboard open does a full foreground re-download. */
    fun forceRedownloadBranchData() {
        viewModelScope.launch {
            nearbyBranchFinder.clearCacheAndWipeDb()
        }
    }
}