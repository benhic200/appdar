package com.example.nearbyappswidget.feature.widget.di

import com.example.nearbyappswidget.data.repository.BusinessAppRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Entry point for Hilt dependencies that need to be accessed from non‑Hilt classes
 * (e.g., [com.example.nearbyappswidget.feature.widget.NearbyAppsWidgetProvider]).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {

    fun businessAppRepository(): BusinessAppRepository
}