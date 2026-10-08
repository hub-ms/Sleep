package com.soundsleeper.app.data.local.mapper

import com.soundsleeper.app.data.local.SleepSessionEntity
import com.soundsleeper.app.domain.model.SleepSession
import kotlinx.datetime.LocalDateTime

fun SleepSessionEntity.toDomain() = SleepSession(
    sessionId = sessionId,
    date = date,
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
        targetMinutes = targetMinutes,
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
    ),
    wakeTime = LocalDateTime(1970, 1, 1, 0, 0)
)

fun SleepSession.toEntity() = SleepSessionEntity(
    sessionId = sessionId,
    date = date,
    environmentHistory = environment.history,
    environmentStats = environment.stats,
    environmentFlags = environment.flags,

    awakeMinutes = duration.awakeMinutes,
    lightMinutes = duration.lightMinutes,
    deepMinutes = duration.deepMinutes,
    remMinutes = duration.remMinutes,
    targetMinutes = duration.targetMinutes,
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