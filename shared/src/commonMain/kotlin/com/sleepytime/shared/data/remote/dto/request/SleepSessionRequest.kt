package com.sleepytime.shared.data.remote.dto.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SleepSessionRequest(
    @SerialName("sessionId") val sessionId: String,
    @SerialName("date") val date: Long,

    @SerialName("awakeMinutes") val awakeMinutes: Double,
    @SerialName("lightSleepMinutes") val lightSleepMinutes: Double,
    @SerialName("deepSleepMinutes") val deepSleepMinutes: Double,
    @SerialName("remSleepMinutes") val remSleepMinutes: Double,
    @SerialName("sleepLatencyMinutes") val sleepLatencyMinutes: Double,

    @SerialName("sleepEfficiency") val sleepEfficiency: Int,
    @SerialName("wakeCount") val wakeCount: Int,

    @SerialName("updatedAt") val updatedAt: Long
)
