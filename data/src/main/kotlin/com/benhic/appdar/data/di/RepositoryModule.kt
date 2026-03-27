package com.benhic.appdar.data.di

import com.benhic.appdar.data.repository.BusinessAppRepository
import com.benhic.appdar.data.repository.BusinessAppRepositoryImpl
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