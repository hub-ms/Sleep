package com.example.sleep.data.local.database

import androidx.room.*
import com.example.sleep.data.local.entity.*
import com.example.sleep.data.local.dao.*

@Database(
    entities = [
        AccelerometerEntity::class,
        NoiseEntity::class,
        SleepGoalEntity::class,
        SleepInterruptionEntity::class,
        SleepScoreEntity::class,
        SleepSessionEntity::class,
        SleepStageEntity::class,
    ],
    version = 1,
    exportSchema = false
)
abstract class SleepDatabase: RoomDatabase() {
    abstract fun accelerometerDao(): AccelerometerDao
    abstract fun noiseDao(): NoiseDao
    abstract fun sleepGoalDao(): SleepGoalDao
    abstract fun sleepInterruptionDao(): SleepInterruptionDao
    abstract fun sleepScoreDao(): SleepScoreDao
    abstract fun sleepSessionDao(): SleepSessionDao
    abstract fun sleepStageDao(): SleepStageDao

}