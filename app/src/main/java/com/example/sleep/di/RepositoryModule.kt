package com.example.sleep.di

import dagger.*
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindSleepRepository(
        implements: SleepRepositoryImpl
    ): SleepRepository
}