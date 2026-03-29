package com.benhic.appdar.data.local.profiles

import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/** Radius used to determine whether the user is "at" a saved profile location. */
const val PROFILE_GEOFENCE_METERS = 300.0

data class LocationProfile(
    val id: String,
    val displayName: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationLabel: String? = null,
    val selectedApps: List<String> = emptyList()
)

enum class ProfileId(val key: String, val displayName: String) {
    HOME("home", "Home Apps"),
    WORK("work", "Work Apps"),
    GYM("gym", "Gym Apps"),
    CUSTOM1("custom1", "Custom Location 1"),
    CUSTOM2("custom2", "Custom Location 2");

    val latKey get() = doublePreferencesKey("profile_${key}_lat")
    val lonKey get() = doublePreferencesKey("profile_${key}_lon")
    val labelKey get() = stringPreferencesKey("profile_${key}_label")
    val appsKey get() = stringPreferencesKey("profile_${key}_apps")
    val nameKey get() = stringPreferencesKey("profile_${key}_display_name")
}
