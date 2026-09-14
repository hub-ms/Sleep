package com.sleepytime.shared.util

import com.sleepytime.shared.domain.model.SleepAnalysis
import com.sleepytime.shared.domain.model.SleepMetrics
import com.sleepytime.shared.enum_.PredictionStageType
import io.github.aakira.napier.Napier
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt

object SleepReportCalculator {

    fun findSleepOnsetIndex(
        analysisList: List<SleepAnalysis>,
        minConsecutiveSleepEpochs: Int = 3
    ): Int? {
        val sorted = analysisList.sortedBy { it.timestamp }

        var consecutive = 0
        var index = 0

        while (index < sorted.size) {
            val item = sorted[index]

            if (item.predictionStageType != PredictionStageType.AWAKE) {
                consecutive++
                if (consecutive >= minConsecutiveSleepEpochs) return index
            } else consecutive = 0
            index++
        }
        return null
    }
    fun calculateSessionMetrics(
        analysisList: List<SleepAnalysis>,
        trackingStartTime: Long,
        sleepLatencyMinutes: Double = 0.0
    ): SleepMetrics {
        val startTime = analysisList.first().timestamp
        Napier.d("startTime=$startTime")
        val endTime = analysisList.last().timestamp
        Napier.d("endTime=$endTime")
        val durationMs = (endTime - startTime).coerceAtLeast(0L)
        Napier.d("durationMs=$durationMs")

        val sampleInterval = if (analysisList.size > 1) durationMs / (analysisList.size - 1) else 0

        val stageCounts = analysisList.groupingBy { it.predictionStageType }.eachCount()
        Napier.d("stageCounts=$stageCounts")
        fun countToMins(count: Int?) = (count ?: 0) * (sampleInterval / 60000.0)

        var wakeCount = 0
        var lastStage: PredictionStageType? = null
        analysisList.forEach {
            if (it.predictionStageType == PredictionStageType.AWAKE && lastStage != PredictionStageType.AWAKE && lastStage != null) wakeCount++
            lastStage = it.predictionStageType
        }
        if (wakeCount == 0 && (stageCounts[PredictionStageType.AWAKE] ?: 0) > 0) wakeCount = 1

        val awakeMinutes = countToMins(stageCounts[PredictionStageType.AWAKE])
        val n1Minutes = countToMins(stageCounts[PredictionStageType.N1])
        val n2Minutes = countToMins(stageCounts[PredictionStageType.N2])
        val n3Minutes = countToMins(stageCounts[PredictionStageType.N3])
        val remMinutes = countToMins(stageCounts[PredictionStageType.REM])
        val totalSleepMinutes = n1Minutes + n2Minutes + n3Minutes + remMinutes

        val onsetIndex = findSleepOnsetIndex(analysisList)
        Napier.d("onsetIndex: $onsetIndex")
        val sleepOnsetTime = onsetIndex?.let { analysisList[it].timestamp }
        Napier.d("sleepOnsetTime: $sleepOnsetTime")
        val latencyMinutes = sleepOnsetTime?.let {
            ((it - trackingStartTime).coerceAtLeast(0L) / 60000.0)
        } ?: 0.0
        Napier.d("latencyMinutes: $latencyMinutes")

        val deepPct = if (totalSleepMinutes > 0) (n3Minutes / totalSleepMinutes) * 100.0 else 0.0
        val remPct  = if (totalSleepMinutes > 0) (remMinutes  / totalSleepMinutes) * 100.0 else 0.0

        return SleepMetrics(
            wakeCountScore = calculateWakeCountScore(wakeCount),
            continuityScore = calculateContinuityScore(awakeMinutes, totalSleepMinutes, latencyMinutes, wakeCount),
            deepScore = calculateBoundedScore(deepPct, 10.0, 25.0),
            remScore = calculateBoundedScore(remPct, 15.0, 25.0),
            latencyScore = calculateLatencyScore(latencyMinutes),

            awakeMinutes = awakeMinutes,
            lightMinutes = n1Minutes + n2Minutes,
            deepMinutes = n3Minutes,
            remMinutes = remMinutes,
            sleepLatencyMinutes = sleepLatencyMinutes,
            wakeCount = wakeCount
        )
    }
    fun SleepMetrics.toEfficiencyScore(
        isNoiseDanger: Boolean,
    ): Int {
        val w = object {
            val wakeCount = 0.10
            val continuity = 0.30
            val deep = 0.30
            val rem = 0.20
            val latency = 0.10
        }

        val baseScore = wakeCountScore * w.wakeCount + continuityScore * w.continuity + deepScore * w.deep + remScore * w.rem + latencyScore * w.latency
        var environmentPenalty = 0.0
        if (isNoiseDanger) environmentPenalty += 10.0
        return (baseScore - environmentPenalty).roundToInt().coerceIn(0, 100)
    }
    fun calculateWakeCountScore(wakeCount: Int): Double {
        return when (wakeCount) {
            0 -> 100.0
            1 -> 95.0
            2 -> 85.0
            3 -> 70.0
            4 -> 50.0
            5 -> 30.0
            else -> (30.0 - (wakeCount - 5) * 10.0).coerceIn(0.0, 100.0)
        }
    }
    fun calculateContinuityScore(awakeMinutes: Double, sleepMinutes: Double, latency: Double, wakeCount: Int): Double {
        if (sleepMinutes <= 0) return 0.0
        val timeInBed = sleepMinutes + awakeMinutes + latency
        val efficiencyPct = (sleepMinutes / timeInBed) * 100.0

        val wakePenalty = ((wakeCount - 2).coerceAtLeast(0) * 5.0)

        return (efficiencyPct - wakePenalty).coerceIn(0.0, 100.0)
    }

    fun calculateBoundedScore(currentPct: Double, minThreshold: Double, maxThreshold: Double): Double {
        return when {
            currentPct in minThreshold..maxThreshold -> 100.0 // 적정 범위 안이면 만점
            currentPct < minThreshold -> {
                val deficit = minThreshold - currentPct
                (100.0 - (deficit * 4.0)).coerceIn(0.0, 100.0)
            }
            else -> {
                val excess = currentPct - maxThreshold
                (100.0 - (excess * 2.0)).coerceIn(0.0, 100.0)
            }
        }
    }
    fun calculateLatencyScore(latencyMinutes: Double): Double {
        return when {
            latencyMinutes in 10.0..20.0 -> 100.0
            latencyMinutes < 10.0 -> {
                val deficit = 10.0 - latencyMinutes
                100.0 - (deficit.pow(1.5) * 1.5)
            }
            else -> {
                val excess = latencyMinutes - 20.0
                (100.0 * exp(-(excess / 18.0).pow(2))).coerceIn(0.0, 100.0)
            }
        }
    }
}