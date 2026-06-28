package com.sleepytime.shared.platform

import com.sleepytime.shared.util.CircularFloatBuffer
import com.sleepytime.shared.util.StatsUtil
import com.sleepytime.shared.util.StatsUtil.computeStats
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
class HeartRateMonitor {
    private val buffer = CircularFloatBuffer(60)
    private val _latestStats = AtomicReference<StatsUtil.RollingStats>(StatsUtil.RollingStats())
    val latestStats: StatsUtil.RollingStats get() = _latestStats.load()

    private val _sampleFlow = MutableSharedFlow<Float>(0)
    val sampleFlow = _sampleFlow.asSharedFlow()

    private var job: Job? = null

    fun onNewHeartRateSample(sample: Float) {
        buffer.add(sample)
        _sampleFlow.tryEmit(sample)
    }

    fun startMonitoring(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.Default) {
            while (isActive) {
                val samples = buffer.toList()
                Napier.d("samples=$samples")
                val stats = computeStats(samples)
                _latestStats.store(stats)
                delay(1000L)
            }
        }
    }

    fun stopMonitoring() {
        job?.cancel()
        job = null
        buffer.clear()
        _latestStats.store(StatsUtil.RollingStats())
    }

    fun getRollingStats(windowSec: Int = 5): StatsUtil.RollingStats = _latestStats.load()
    fun getCurrentHeartRate(): Float = _latestStats.load().last
}