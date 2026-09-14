package com.sleepytime.shared.platform

import com.sleepytime.shared.util.StatsUtil
import kotlinx.coroutines.CoroutineScope

expect class SensorBridge() {
    val latestNoiseStats: StatsUtil.RollingStats
    val latestGyroStats: StatsUtil.RollingStats

    // 센서 제어 함수들
    fun startNoiseSensor(scope: CoroutineScope)
    fun stopNoiseSensor()
    fun startGyroscopeSensor(scope: CoroutineScope)
    fun stopGyroscopeSensor()
}