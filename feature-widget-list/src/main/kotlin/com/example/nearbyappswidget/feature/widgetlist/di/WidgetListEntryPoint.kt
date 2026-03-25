package com.example.nearbyappswidget.feature.widgetlist.di

import com.example.nearbyappswidget.data.nearby.NearbyBranchFinder
import com.example.nearbyappswidget.data.repository.BusinessAppRepository
import com.example.nearbyappswidget.data.local.profiles.LocationProfileRepository
import com.example.nearbyappswidget.data.local.settings.SettingsRepository
import com.example.nearbyappswidget.feature.location.DistanceCalculator
import com.example.nearbyappswidget.feature.location.LocationProvider
import com.example.nearbyappswidget.feature.widgetlist.util.AppIconLoader
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