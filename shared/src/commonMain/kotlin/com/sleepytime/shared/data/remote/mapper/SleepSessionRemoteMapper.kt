package com.sleepytime.shared.data.remote.mapper

import com.sleepytime.shared.data.remote.dto.request.SleepSessionRequest
import com.sleepytime.shared.data.remote.dto.response.SleepSessionResponse
import com.sleepytime.shared.domain.model.SleepMetrics
import com.sleepytime.shared.domain.model.EnvironmentFeature
import com.sleepytime.shared.domain.model.SleepSession
import com.sleepytime.shared.domain.model.Stats
import kotlinx.datetime.LocalDateTime

fun SleepSessionResponse.toDomain() = SleepSession(
    sessionId = sessionId,
    date = date,
    wakeTime = LocalDateTime(1970, 1, 1, 0, 0),
    sleepMetrics = SleepMetrics(),
    environment = SleepSession.Environment(
        history = emptyList(),
        stats = EnvironmentFeature.Statistics(
            noise = Stats(),
        ),
        flags = EnvironmentFeature.Flag(
            isNoiseDanger = false,
        ),
    ),
    duration = SleepSession.Duration(
        awakeMinutes = awakeMinutes,
        lightMinutes = lightMinutes,
        deepMinutes = deepMinutes,
        remMinutes = remMinutes,
        targetMinutes = 480.0,
        sleepLatencyMinutes = sleepLatencyMinutes
    ),
    stageTimeline = emptyList(),
    stagesDistribution = emptyMap(),

    sleepEfficiency = sleepEfficiency,
    wakeCount = wakeCount,

    csvData = SleepSession.CsvData(
        sensorCsv = "",
        environmentCsv = ""
    ),
    timestamp = SleepSession.Timestamp(
        createdAt = createdAt,
        updatedAt = updatedAt
    )
)

fun SleepSession.toRequest() = SleepSessionRequest(
    sessionId = sessionId,
    date = date,
    awakeMinutes = duration.awakeMinutes,
    lightSleepMinutes = duration.lightMinutes,
    deepSleepMinutes = duration.deepMinutes,
    remSleepMinutes = duration.remMinutes,
    sleepLatencyMinutes = duration.sleepLatencyMinutes,
    sleepEfficiency = sleepEfficiency,
    wakeCount = wakeCount,
    updatedAt = timestamp.updatedAt
)