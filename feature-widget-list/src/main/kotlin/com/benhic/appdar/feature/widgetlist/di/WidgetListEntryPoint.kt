package com.benhic.appdar.feature.widgetlist.di

import com.benhic.appdar.data.nearby.NearbyBranchFinder
import com.benhic.appdar.data.repository.BusinessAppRepository
import com.benhic.appdar.data.local.profiles.LocationProfileRepository
import com.benhic.appdar.data.local.settings.SettingsRepository
import com.benhic.appdar.feature.location.DistanceCalculator
import com.benhic.appdar.feature.location.LocationProvider
import com.benhic.appdar.feature.widgetlist.util.AppIconLoader
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt entry point for the scrollable widget list.
 *
 * Used by [NearbyAppsWidgetListFactory] to obtain repository instances
 * without direct dependency injection (the factory is instantiated by the system).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetListEntryPoint {

    fun businessAppRepository(): BusinessAppRepository

    fun settingsRepository(): SettingsRepository

    fun distanceCalculator(): DistanceCalculator

    fun locationProvider(): LocationProvider

    fun appIconLoader(): AppIconLoader

    fun nearbyBranchFinder(): NearbyBranchFinder

    fun locationProfileRepository(): LocationProfileRepository
}