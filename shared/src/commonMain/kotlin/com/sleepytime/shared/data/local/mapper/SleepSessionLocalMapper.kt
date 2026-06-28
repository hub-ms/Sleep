package com.sleepytime.shared.data.local.mapper

import com.sleepytime.shared.data.local.SleepSessionEntity
import com.sleepytime.shared.domain.model.SleepSession

fun SleepSessionEntity.toDomain() = SleepSession(
    sessionId = sessionId,
    date = date,
    sleepMetrics = sleepMetrics,
    environment = SleepSession.Environment(
        history = environmentHistory,
        stats = environmentStats,
        flags = environmentFlags,
    ),
    duration = SleepSession.Duration(
        awakeMinutes = awakeMinutes,
        lightMinutes = lightMinutes,
        deepMinutes = deepMinutes,
        remMinutes = remMinutes,
        sleepLatencyMinutes = sleepLatencyMinutes
    ),
    stageTimeline = stageTimeline,
    stagesDistribution = stagesDistribution,
    sleepEfficiency = sleepEfficiency,
    wakeCount = wakeCount,
    csvData = SleepSession.CsvData(
        sensorCsv = sensorCsv,
        environmentCsv = environmentCsv
    ),
    timestamp = SleepSession.Timestamp(
        createdAt = createdAt,
        updatedAt = updatedAt
    )
)

fun SleepSession.toEntity() = SleepSessionEntity(
    sessionId = sessionId,
    date = date,
    sleepMetrics = sleepMetrics,

    environmentHistory = environment.history,
    environmentStats = environment.stats,
    environmentFlags = environment.flags,

    awakeMinutes = duration.awakeMinutes,
    lightMinutes = duration.lightMinutes,
    deepMinutes = duration.deepMinutes,
    remMinutes = duration.remMinutes,
    sleepLatencyMinutes = duration.sleepLatencyMinutes,

    stageTimeline = stageTimeline,
    stagesDistribution = stagesDistribution,
    wakeCount = wakeCount,
    sleepEfficiency = sleepEfficiency,

    sensorCsv = csvData.sensorCsv,
    environmentCsv = csvData.environmentCsv,
    createdAt = timestamp.createdAt,
    updatedAt = timestamp.updatedAt
)