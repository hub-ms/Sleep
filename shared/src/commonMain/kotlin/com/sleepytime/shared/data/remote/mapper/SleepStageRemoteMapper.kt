package com.sleepytime.shared.data.remote.mapper

import com.sleepytime.shared.data.remote.dto.request.SleepStageRequest
import com.sleepytime.shared.data.remote.dto.response.SleepStageResponse
import com.sleepytime.shared.domain.model.SleepStage

fun SleepStageResponse.toDomain() = SleepStage(
    id = id,
    sessionId = sessionId,
    type = type,
    startTime = startTime,
    duration = duration
)
fun SleepStage.toRequest(): SleepStageRequest {
    return SleepStageRequest(
        sessionId = sessionId,
        type = type,
        startTime = startTime,
        endTime = endTime,
        duration = duration
    )
}
