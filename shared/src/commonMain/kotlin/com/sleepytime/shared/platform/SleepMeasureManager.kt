package com.sleepytime.shared.platform

import com.sleepytime.shared.domain.model.EnvironmentFeature

interface SleepMeasureManager {
    fun start()
    fun stop()
    fun getCapturedSensorData(): List<List<FloatArray>>
    fun getCapturedEnvironmentFeatures(): List<EnvironmentFeature>
    fun getCapturedTimestamps(): List<Long>
    var onWindowReady: ((List<FloatArray>) -> Unit)?
    var onEnvironmentReady: ((EnvironmentFeature) -> Unit)?
}