package com.example.nearbyappswidget.data.local.profiles

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationProfileRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    fun getProfile(profileId: ProfileId): Flow<LocationProfile> = dataStore.data.map { prefs ->
        LocationProfile(
            id = profileId.key,
            displayName = profileId.displayName,
            latitude = prefs[profileId.latKey],
            longitude = prefs[profileId.lonKey],
            locationLabel = prefs[profileId.labelKey],
            selectedApps = prefs[profileId.appsKey]
                ?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        )
    }

    suspend fun updateLocation(profileId: ProfileId, lat: Double, lon: Double, label: String) {
        dataStore.edit { prefs ->
            prefs[profileId.latKey] = lat
            prefs[profileId.lonKey] = lon
            prefs[profileId.labelKey] = label
        }
    }

    suspend fun clearLocation(profileId: ProfileId) {
        dataStore.edit { prefs ->
            prefs.remove(profileId.latKey)
            prefs.remove(profileId.lonKey)
            prefs.remove(profileId.labelKey)
        }
    }

    suspend fun updateSelectedApps(profileId: ProfileId, packageNames: List<String>) {
        dataStore.edit { prefs ->
            prefs[profileId.appsKey] = packageNames.joinToString(",")
        }
    }
}
