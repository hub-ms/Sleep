package com.sleepytime.shared.data.remote.dto.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PeriodSummaryResponse(
    @SerialName("averageSleepMinutes") val averageSleepMinutes: Double,
    @SerialName("averageSleepEfficiency") val averageSleepEfficiency: Double,
    @SerialName("totalSleepMinutes") val totalSleepMinutes: Double,
    @SerialName("totalSessionCount") val totalSessionCount: Int,

    @SerialName("sleepMinutesDelta") val sleepMinutesDelta: Double,
    @SerialName("sleepEfficiencyDelta") val sleepEfficiencyDelta: Double
)
