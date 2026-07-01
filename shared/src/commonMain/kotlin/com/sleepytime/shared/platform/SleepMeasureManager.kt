package com.sleepytime.shared.platform

import com.sleepytime.shared.domain.model.EnvironmentFeature
import com.sleepytime.shared.domain.model.SleepMinuteAggregate

interface SleepMeasureManager {
    fun start()
    fun stop()
    fun getCapturedAggregates(): List<SleepMinuteAggregate>
    fun getCapturedSensorData(): List<List<FloatArray>>
    fun getCapturedEnvironmentFeatures(): List<EnvironmentFeature>
    fun getCapturedTimestamps(): List<Long>
    var onWindowReady: ((List<FloatArray>) -> Unit)?
    var onEnvironmentReady: ((EnvironmentFeature) -> Unit)?
}