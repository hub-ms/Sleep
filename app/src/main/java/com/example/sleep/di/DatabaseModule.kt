package com.example.sleep.di

import android.content.Context
import androidx.room.Room
import com.example.sleep.data.local.dao.AccelerometerDao
import com.example.sleep.data.local.dao.NoiseDao
import com.example.sleep.data.local.dao.SleepGoalDao
import com.example.sleep.data.local.dao.SleepInterruptionDao
import com.example.sleep.data.local.dao.SleepScoreDao
import com.example.sleep.data.local.dao.SleepSessionDao
import com.example.sleep.data.local.dao.SleepStageDao
import com.example.sleep.data.local.database.SleepDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SleepDatabase =
        Room.databaseBuilder(context, SleepDatabase::class.java, "sleep.db").build()

    @Provides
    fun provideAccelerometerDao(database: SleepDatabase): AccelerometerDao =
        database.accelerometerDao()

    @Provides
    fun provideNoiseDao(database: SleepDatabase): NoiseDao =
        database.noiseDao()

    @Provides
    fun provideSleepGoalDao(database: SleepDatabase): SleepGoalDao =
        database.sleepGoalDao()

    @Provides
    fun provideSleepInterruptionDao(database: SleepDatabase): SleepInterruptionDao =
        database.sleepInterruptionDao()

    @Provides
    fun provideSleepScoreDao(database: SleepDatabase): SleepScoreDao =
        database.sleepScoreDao()

    @Provides
    fun provideSleepSessionDao(database: SleepDatabase): SleepSessionDao =
        database.sleepSessionDao()

    @Provides
    fun provideSleepStageDao(database: SleepDatabase): SleepStageDao =
        database.sleepStageDao()
}