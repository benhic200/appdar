package com.benhic.appdar.data.di

import android.content.Context
import androidx.room.Room
import com.benhic.appdar.data.database.AppDatabase
import com.benhic.appdar.data.local.BranchLocationDao
import com.benhic.appdar.data.local.geocoding.CachedAddressDao
import com.benhic.appdar.data.local.location.LocationHistoryDao
import com.benhic.appdar.data.local.location.LocationHistoryRepository
import com.benhic.appdar.data.local.location.LocationHistoryRepositoryImpl
import com.benhic.appdar.data.local.settings.SettingsRepository
import com.benhic.appdar.data.remote.geocoding.GeocodingRepository
import com.benhic.appdar.data.remote.geocoding.GeocodingRepositoryImpl
import com.benhic.appdar.data.remote.geocoding.GeocodingService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "nearby-apps.db"
        ).addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_9_10, AppDatabase.MIGRATION_10_11)
         .fallbackToDestructiveMigration()
         .build()
    }

    @Provides
    fun provideBusinessAppMappingDao(database: AppDatabase) = database.businessAppMappingDao()

    @Provides
    fun provideBranchLocationDao(database: AppDatabase) = database.branchLocationDao()

    @Provides
    fun provideCachedAddressDao(database: AppDatabase) = database.cachedAddressDao()

    @Provides
    fun provideLocationHistoryDao(database: AppDatabase) = database.locationHistoryDao()

    // --- Networking ---

    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient {
        val cacheDir = File(context.cacheDir, "http_cache")
        val cacheSize = 10L * 1024 * 1024 // 10 MB
        val cache = Cache(cacheDir, cacheSize)

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val userAgentInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Appdar/1.0")
                .build()
            chain.proceed(request)
        }

        return OkHttpClient.Builder()
            .cache(cache)
            .addInterceptor(userAgentInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://nominatim.openstreetmap.org/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    fun provideGeocodingService(retrofit: Retrofit): GeocodingService {
        return retrofit.create(GeocodingService::class.java)
    }

    @Provides
    @Singleton
    fun provideGeocodingRepository(
        geocodingService: GeocodingService,
        cachedAddressDao: CachedAddressDao
    ): GeocodingRepository {
        return GeocodingRepositoryImpl(geocodingService, cachedAddressDao)
    }

    // Temporarily disabled due to DI resolution issues
    // @Provides
    // @Singleton
    // fun provideLocationHistoryRepository(
    //     locationHistoryDao: LocationHistoryDao,
    //     settingsRepository: SettingsRepository
    // ): LocationHistoryRepository {
    //     return LocationHistoryRepositoryImpl(locationHistoryDao, settingsRepository)
    // }
}