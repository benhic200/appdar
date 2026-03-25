package com.example.nearbyappswidget.feature.geofencing

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GeofencingModule {

    @Provides
    @Singleton
    fun provideGeofenceManager(
        @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context,
        repository: com.example.nearbyappswidget.data.repository.BusinessAppRepository
    ): GeofenceManager {
        return GeofenceManager(context, repository)
    }
}