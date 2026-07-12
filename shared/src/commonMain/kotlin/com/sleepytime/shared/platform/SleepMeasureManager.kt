package com.sleepytime.shared.platform

import com.sleepytime.shared.domain.model.EnvironmentFeature
import com.sleepytime.shared.domain.model.SleepMinuteAggregate

interface SleepMeasureManager {
    fun start()
    fun stop()
    fun getCapturedSensorData(): List<List<FloatArray>>
    fun getCapturedEnvironmentFeatures(): List<EnvironmentFeature>
    fun getCapturedTimestamps(): List<Long>
    fun submitHeartRate(bpm: Float, timestamp: Long)
    fun submitNoise(db: Float, timestamp: Long)
    var onWindowReady: ((List<FloatArray>) -> Unit)?
    var onEnvironmentReady: ((EnvironmentFeature) -> Unit)?
    var onMinuteAggregateReady: ((SleepMinuteAggregate) -> Unit)?
}