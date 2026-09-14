package com.sleepytime.shared.platform

import com.sleepytime.shared.util.StatsUtil
import kotlinx.coroutines.CoroutineScope

actual class SensorBridge {
    actual val latestNoiseStats: StatsUtil.RollingStats get() = StatsUtil.RollingStats()
    actual val latestGyroStats: StatsUtil.RollingStats get() = StatsUtil.RollingStats()

    actual fun startGyroscopeSensor(scope: CoroutineScope) {}
    actual fun stopGyroscopeSensor() {}
    actual fun startNoiseSensor(scope: CoroutineScope) {}
    actual fun stopNoiseSensor() {}
}
