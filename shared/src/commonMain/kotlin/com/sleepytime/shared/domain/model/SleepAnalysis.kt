package com.sleepytime.shared.domain.model

import com.sleepytime.shared.enum_.PredictionStageType
import kotlinx.datetime.LocalDateTime

data class SleepAnalysis(
    val timestamp: Long,
    val predictionStageType: PredictionStageType,
    val windowDurationMs: Long = 0L,
    val confidence: Float? = null,
    val isSleepOnsetCandidate: Boolean = false,
    val environmentFeature: EnvironmentFeature? = null
)