package com.soundsleeper.app.data.remote.dto.request

import kotlinx.serialization.Serializable

@Serializable
data class SleepAdviceRequest(
    val sessionId: String,
    val findings: List<SleepFindingDto>,
)

@Serializable
data class SleepFindingDto(
    /** SleepAdviceAnalyzer.FindingCode 이름. */
    val code: String,
    /** INFO / WARNING / CRITICAL */
    val severity: String,
    val measured: Double,
    val threshold: Double,
)
