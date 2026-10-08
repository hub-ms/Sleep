package com.soundsleeper.app.data.remote.dto.request

import com.soundsleeper.app.domain.model.SleepStage
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SleepSessionRequest(
    @SerialName("sessionId") val sessionId: String,
    @SerialName("date") val date: LocalDate,

    @SerialName("awakeMinutes") val awakeMinutes: Double,
    @SerialName("lightSleepMinutes") val lightSleepMinutes: Double,
    @SerialName("deepSleepMinutes") val deepSleepMinutes: Double,
    @SerialName("remSleepMinutes") val remSleepMinutes: Double,
    @SerialName("sleepLatencyMinutes") val sleepLatencyMinutes: Double,

    @SerialName("sleepEfficiency") val sleepEfficiency: Int,
    @SerialName("wakeCount") val wakeCount: Int,

    @SerialName("updatedAt") val updatedAt: Long,

    @SerialName("stages")
    val stages: List<SleepStage>
)
