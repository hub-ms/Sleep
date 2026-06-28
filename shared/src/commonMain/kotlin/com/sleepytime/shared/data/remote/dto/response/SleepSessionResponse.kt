package com.sleepytime.shared.data.remote.dto.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SleepSessionResponse(
    @SerialName("sessionId") val sessionId: String,
    @SerialName("date") val date: Long,

    @SerialName("awakeMinutes") val awakeMinutes: Double,
    @SerialName("lightMinutes") val lightMinutes: Double,
    @SerialName("deepMinutes") val deepMinutes: Double,
    @SerialName("remMinutes") val remMinutes: Double,
    @SerialName("sleepLatencyMinutes") val sleepLatencyMinutes: Double,

    @SerialName("sleepEfficiency") val sleepEfficiency: Int,
    @SerialName("wakeCount") val wakeCount: Int,

    @SerialName("createdAt") val createdAt: Long,
    @SerialName("updatedAt") val updatedAt: Long
)