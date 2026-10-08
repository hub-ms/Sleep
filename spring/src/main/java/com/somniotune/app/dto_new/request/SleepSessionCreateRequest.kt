package com.somniotune.app.dto_new.request

import com.somniotune.app.domain.model.SleepAnalysis
import com.somniotune.app.domain.model.EnvironmentFeature
data class SleepSessionCreateRequest(
    val userId: Long,
    val sessionId: String,
    val startTime: Long,
    val endTime: Long,
    val analysisList: List<SleepAnalysis>,
    val environmentFeatures: List<EnvironmentFeature>
)
