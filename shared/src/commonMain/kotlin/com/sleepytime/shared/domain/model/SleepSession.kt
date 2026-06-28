package com.sleepytime.shared.domain.model

import com.sleepytime.shared.enum_.SleepStageType
import com.sleepytime.shared.domain.model.SleepStage


data class SleepSession(
    val sessionId: String,
    val date: Long,
    val sleepMetrics: SleepMetrics,

    val environment: Environment,
    val duration: Duration,
    val stageTimeline: List<SleepStage>,
    val stagesDistribution: Map<SleepStageType, Float>,
    val sleepEfficiency: Int,
    val wakeCount: Int,

    val csvData: CsvData,
    val timestamp: Timestamp
) {
    data class Environment(
        val history: List<EnvironmentFeature.Snapshot>,
        val stats: EnvironmentFeature.Statistics,
        val flags: EnvironmentFeature.Flag,
    )
    data class Duration(
        val awakeMinutes: Double,
        val lightMinutes: Double,
        val deepMinutes: Double,
        val remMinutes: Double,
        val sleepLatencyMinutes: Double
    )
    data class CsvData(
        val sensorCsv: String,
        val environmentCsv: String
    )
    data class Timestamp(
        val createdAt: Long,
        val updatedAt: Long
    )
}

