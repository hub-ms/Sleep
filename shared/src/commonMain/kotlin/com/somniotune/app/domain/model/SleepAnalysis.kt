package com.somniotune.app.domain.model

import com.somniotune.app.enum_.PredictionStageType

data class SleepAnalysis(
    val timestamp: Long,
    val predictionStageType: PredictionStageType,
    val windowDurationMs: Long = 0L,
    val confidence: Float? = null,
    val isSleepOnsetCandidate: Boolean = false,
    val environmentFeature: EnvironmentFeature? = null
)