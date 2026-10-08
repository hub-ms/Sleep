package com.soundsleeper.app.dto_new.request

import com.soundsleeper.app.domain.model.SleepAnalysis
import com.soundsleeper.app.domain.model.EnvironmentFeature
data class SleepSessionCreateRequest(
    val userId: Long,
    val sessionId: String,
    val startTime: Long,
    val endTime: Long,
    val analysisList: List<SleepAnalysis>,
    val environmentFeatures: List<EnvironmentFeature>
)
