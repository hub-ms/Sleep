package com.sleepytime.shared.platform

import com.sleepytime.shared.util.CircularFloatBuffer
import com.sleepytime.shared.util.StatsUtil
import com.sleepytime.shared.util.StatsUtil.RollingStats
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
class NoiseDetector {
    private val buffer = CircularFloatBuffer(60)
    private val _latestStats = AtomicReference(RollingStats())
    val latestStats: RollingStats get() = _latestStats.load()

    private val _sampleFlow = MutableSharedFlow<Float>(0)
    val sampleFlow = _sampleFlow.asSharedFlow()

    private var job: Job? = null

    fun onNewNoiseSample(db: Float) {
        buffer.add(db)
        _sampleFlow.tryEmit(db)
    }

    fun startMonitoring(scope: CoroutineScope) {
        if (job?.isActive == true) return
        Napier.d("startMonitoring()")
        job = scope.launch(Dispatchers.Default) {
            while (isActive) {
                val samples = buffer.toList()
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
        _latestStats.store(RollingStats())
    }

    fun getRollingStats(windowSec: Int = 5): RollingStats = _latestStats.load()
    fun getCurrentNoise(): Float = _latestStats.load().last
}
