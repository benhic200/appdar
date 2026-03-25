package com.example.nearbyappswidget.data.di

import com.example.nearbyappswidget.data.repository.BusinessAppRepository
import com.example.nearbyappswidget.data.repository.BusinessAppRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindBusinessAppRepository(
        impl: BusinessAppRepositoryImpl
    ): BusinessAppRepository
}