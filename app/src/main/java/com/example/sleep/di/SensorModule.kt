package com.example.sleep.di

import android.content.Context
import android.hardware.SensorManager
import dagger.*
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object SensorModule {
    @Provides
    fun provideSensorManager(
        @ApplicationContext context: Context
    ): SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
}