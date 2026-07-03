package com.sleepytime.shared.util

import com.sleepytime.shared.domain.model.SleepAnalysis
import com.sleepytime.shared.domain.model.SleepMetrics
import com.sleepytime.shared.enum_.PredictionStageType
import io.github.aakira.napier.Napier
import kotlin.math.roundToInt

object SleepReportCalculator {

    fun findSleepOnsetIndex(
        analysisList: List<SleepAnalysis>,
        minConsecutiveSleepEpochs: Int = 3
    ): Int? {
        val sorted = analysisList.sortedBy { it.timestamp }

        fun isSleep(stage: PredictionStageType) = stage != PredictionStageType.AWAKE

        var consecutive = 0
        for ((index, item) in sorted.withIndex()) {
            if (isSleep(item.predictionStageType)) {
                consecutive++
                if (consecutive >= minConsecutiveSleepEpochs) return index
            } else {
                consecutive = 0
            }
        }
        return null
    }
    fun calculateSessionMetrics(
        analysisList: List<SleepAnalysis>,
        trackingStartTime: Long,
        sleepLatencyMinutes: Double = 0.0
    ): SleepMetrics {
        val startTime = analysisList.first().timestamp
        val endTime = analysisList.last().timestamp
        val durationMs = (endTime - startTime).coerceAtLeast(0L)

        val sampleInterval = if (analysisList.size > 1) durationMs / (analysisList.size - 1) else 0
        val msToMinutesFactor = sampleInterval / 60000.0

        val stageCounts = analysisList.groupingBy { it.predictionStageType }.eachCount()
        fun countToMins(count: Int?) = (count ?: 0) * msToMinutesFactor

        var wakeCount = 0
        var lastStage: PredictionStageType? = null
        analysisList.forEach {
            if (it.predictionStageType == PredictionStageType.AWAKE && lastStage != PredictionStageType.AWAKE && lastStage != null) wakeCount++
            lastStage = it.predictionStageType
        }
        if (wakeCount == 0 && (stageCounts[PredictionStageType.AWAKE]
                ?: 0) > 0
        ) wakeCount = 1

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
        val sleepLatencyMinutes = sleepOnsetTime?.let {
            ((it - trackingStartTime).coerceAtLeast(0L) / 60000.0)
        } ?: 0.0
        Napier.d("sleepLatencyMinutes: $sleepLatencyMinutes")

        val deepPct = if (totalSleepMinutes > 0) (n3Minutes / totalSleepMinutes) * 100.0 else 0.0
        val remPct  = if (totalSleepMinutes > 0) (remMinutes  / totalSleepMinutes) * 100.0 else 0.0

        val wakeCountScore  = (100.0 - ((wakeCount - 2).coerceAtLeast(0) * 8.0)).coerceIn(0.0, 100.0)
        val continuityScore = calculateContinuityScore(awakeMinutes, totalSleepMinutes, wakeCount)
        val deepScore = calculateBoundedScore(deepPct, 10.0, 25.0)
        val remScore = calculateBoundedScore(remPct,  15.0, 25.0)
        val latencyScore = when {
            sleepLatencyMinutes <= 10 -> 100.0
            sleepLatencyMinutes <= 20 -> 80.0
            sleepLatencyMinutes <= 30 -> 60.0
            sleepLatencyMinutes <= 45 -> 30.0
            else -> 0.0
        }

        return SleepMetrics(
            wakeCountScore = wakeCountScore,
            continuityScore = continuityScore,
            deepScore = deepScore,
            remScore = remScore,
            latencyScore = latencyScore,

            awakeMinutes = awakeMinutes,
            lightMinutes = n1Minutes + n2Minutes,
            deepMinutes = n3Minutes,
            remMinutes = remMinutes,
            sleepLatencyMinutes = sleepLatencyMinutes,
            wakeCount = wakeCount
        )
    }

    /**
     * 5가지 수면 핵심 지표와 4가지 침실 환경 오염/위험 요소를 모두 반영한 종합 수면 효율 점수 계산
     */
    fun SleepMetrics.toEfficiencyScore(
        isHeartRateAnomaly: Boolean,
        isNoiseDanger: Boolean,
    ): Int {
        // 1단계: 5가지 수면 핵심 지표 가중치 계산 (최대 100점)
        val w = object {
            val wakeCount  = 0.10  // 각성 횟수 (10%)
            val continuity = 0.30  // 수면 연속성/시간 내 효율 (30%)
            val deep       = 0.30  // 깊은 잠 비중 (30%)
            val rem        = 0.20  // 렘수면 비중 (20%)
            val latency    = 0.10  // 잠들기까지 걸린 시간 (10%)
        }

        val baseScore = (
                wakeCountScore * w.wakeCount +
                        continuityScore * w.continuity +
                        deepScore * w.deep +
                        remScore * w.rem +
                        latencyScore * w.latency
                )

        // 2단계: 환경 지표 위험 요소에 따른 페널티 감점 계산
        var environmentPenalty = 0.0

        if (isHeartRateAnomaly) {
            environmentPenalty += 8.0  // 심박수 이상 시 8점 감점
        }
        if (isNoiseDanger) {
            environmentPenalty += 10.0 // 침실 소음 과다 시 10점 감점
        }
        return (baseScore - environmentPenalty).roundToInt().coerceIn(0, 100)
    }
    fun calculateContinuityScore(awakeMinutes: Double, totalSleepMinutes: Double, wakeCount: Int): Double {
        if (totalSleepMinutes <= 0) return 0.0
        val totalTimeInBed = totalSleepMinutes + awakeMinutes
        val wakePercentage = (awakeMinutes / totalTimeInBed) * 100.0

        val wasoPenalty = if (wakePercentage > 10.0) (wakePercentage - 10.0) * 3.0 else 0.0
        val wakeCountPenalty = if (wakeCount > 2) (wakeCount - 2) * 5.0 else 0.0

        return (100.0 - wasoPenalty - wakeCountPenalty).coerceIn(0.0, 100.0)
    }

    fun calculateBoundedScore(value: Double, minIdeal: Double, maxIdeal: Double): Double {
        return when {
            value < minIdeal -> {
                // 최소 기준 미달 시 선형 감점 (예: 깊은 잠 0%면 0점)
                ((value / minIdeal) * 100.0).coerceIn(0.0, 100.0)
            }
            value in minIdeal..maxIdeal -> 100.0 // 최적 구간 (만점)
            else -> {
                // 최대 기준 초과 시 감점 (과다 수면 패턴 패널티)
                (100.0 - (value - maxIdeal) * 4.0).coerceIn(50.0, 100.0)
            }
        }
    }
}