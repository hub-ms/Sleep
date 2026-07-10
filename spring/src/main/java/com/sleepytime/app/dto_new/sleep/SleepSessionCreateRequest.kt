package com.sleepytime.app.dto_new.sleep

import com.sleepytime.shared.domain.model.SleepAnalysis
import com.sleepytime.shared.domain.model.EnvironmentFeature
data class SleepSessionCreateRequest(
    val userId: Long,
    val sessionId: String,
    val startTime: Long,
    val endTime: Long,
    val analysisList: List<SleepAnalysis>,
    val environmentFeatures: List<EnvironmentFeature>
)