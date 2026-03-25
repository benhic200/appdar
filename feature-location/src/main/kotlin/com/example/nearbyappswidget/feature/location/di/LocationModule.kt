package com.example.nearbyappswidget.feature.location.di

import android.content.Context
import com.example.nearbyappswidget.feature.location.DistanceCalculator
import com.example.nearbyappswidget.feature.location.LocationProvider
import com.example.nearbyappswidget.feature.location.RealLocationProvider
import com.example.nearbyappswidget.feature.location.StubLocationProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LocationModule {

    @Binds
    @Singleton
    abstract fun bindLocationProvider(impl: RealLocationProvider): LocationProvider

    companion object {
        @Provides
        @Singleton
        fun provideDistanceCalculator(): DistanceCalculator = DistanceCalculator()

        @Provides
        @Singleton
        fun provideRealLocationProvider(@ApplicationContext context: Context): RealLocationProvider {
            return RealLocationProvider(context)
        }
    }
}