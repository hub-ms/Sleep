package com.sleepytime.shared.ui.tracking

import com.sleepytime.shared.enum_.SleepStageType

data class SleepAnalysisResult(
    val sleepStageType: SleepStageType,
    val confidence: Float,
    val timestamp: Long,
    val heartRate: Float? = null,
    val breathingRate: Float? = null
)


