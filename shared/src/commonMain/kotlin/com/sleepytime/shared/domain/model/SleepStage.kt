package com.sleepytime.shared.domain.model

import com.sleepytime.shared.enum_.SleepStageType
import com.sleepytime.shared.util.IdGenerator
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.time.Duration

@Serializable
data class SleepStage(
    val id: String = IdGenerator.randomUuidString(),
    val sessionId: String,
    val type: SleepStageType,
    val startTime: LocalDateTime,
    val duration: Duration
) {
    val endTime: LocalDateTime
        get() {
            val tz = TimeZone.currentSystemDefault()
            val startInstant = startTime.toInstant(tz)
            val endInstant = startInstant + duration
            return endInstant.toLocalDateTime(tz)
        }
}

