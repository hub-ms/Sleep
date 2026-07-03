package com.sleepytime.shared.platform

import com.sleepytime.shared.util.StatsUtil
import kotlinx.coroutines.CoroutineScope

expect class SensorBridge() {
    val latestHeartRateStats: StatsUtil.RollingStats
    val latestNoiseStats: StatsUtil.RollingStats

    // 센서 제어 함수들
    fun startHeartRateSensor(scope: CoroutineScope)
    fun stopHeartRateSensor()
    fun startNoiseSensor(scope: CoroutineScope)
    fun stopNoiseSensor()
}