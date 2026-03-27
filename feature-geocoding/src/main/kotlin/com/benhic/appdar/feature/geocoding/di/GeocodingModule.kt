package com.benhic.appdar.feature.geocoding.di

import com.benhic.appdar.feature.geocoding.GeocodingProvider
import com.benhic.appdar.feature.geocoding.StubGeocodingProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class GeocodingModule {

    @Binds
    @Singleton
    abstract fun bindGeocodingProvider(impl: StubGeocodingProvider): GeocodingProvider
}