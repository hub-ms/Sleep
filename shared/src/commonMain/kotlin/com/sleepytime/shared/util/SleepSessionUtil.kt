package com.sleepytime.shared.util

import com.sleepytime.shared.domain.model.SleepMetrics
import com.sleepytime.shared.domain.model.SleepSession
import com.sleepytime.shared.ui.report.ReportContract
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.minutes

object SleepSessionUtil {
    fun SleepSession.toReportData(targetDate: LocalDate): ReportContract.ReportData {
        val tz = TimeZone.currentSystemDefault()
        val bedTime = Instant.fromEpochMilliseconds(date).toLocalDateTime(tz)
        val totalMinutes = duration.lightMinutes + duration.deepMinutes + duration.remMinutes
        val timeInBed = duration.awakeMinutes + totalMinutes
        val wakeTime = Instant.fromEpochMilliseconds(date)
            .plus(timeInBed.minutes)
            .toLocalDateTime(tz)

        return ReportContract.ReportData(
            environmentHistory = environment.history,
            bedTime = bedTime,
            wakeTime = wakeTime,
            totalMinutes = totalMinutes,
            awakeMinutes = duration.awakeMinutes,
            lightMinutes = duration.lightMinutes,
            deepMinutes = duration.deepMinutes,
            remMinutes = duration.remMinutes,
            sleepLatencyMinutes = duration.sleepLatencyMinutes,
            stageTimeline = stageTimeline,
            wakeCount = wakeCount,
            sleepScore = sleepEfficiency,
            avgHeartRate = environment.stats.heartRate.avg,
            avgNoise = environment.stats.noise.avg,
            avgTemperature = environment.stats.temperature.avg,
            avgHumidity = environment.stats.humidity.avg,
            isHeartRateAnomaly = environment.flags.isHeartRateAnomaly,
            isNoiseDanger = environment.flags.isNoiseDanger,
            isTempExtreme = environment.flags.isTempExtreme,
            isHumidityExtreme = environment.flags.isHumidityExtreme,
            dailyLatencyMinutes = mapOf(targetDate to duration.sleepLatencyMinutes),
            dailyBedTimes = mapOf(targetDate to bedTime),
            dailyWakeTimes = mapOf(targetDate to wakeTime),
            totalWakeCount = wakeCount,
            dailyScores = mapOf(targetDate to sleepEfficiency),
            dailyAvgHeartRates = mapOf(targetDate to environment.stats.heartRate.avg),
            dailyAvgNoises = mapOf(targetDate to environment.stats.noise.avg),
            dailyAvgTemps = mapOf(targetDate to environment.stats.temperature.avg),
            dailyAvgHumidities = mapOf(targetDate to environment.stats.humidity.avg),
            sleepMetrics = SleepMetrics(
                wakeCountScore = 0.0,
                continuityScore = 0.0,
                deepScore = 0.0,
                remScore = 0.0,
                latencyScore = 0.0,
                awakeMinutes = duration.awakeMinutes,
                lightMinutes = duration.lightMinutes,
                deepMinutes = duration.deepMinutes,
                remMinutes = duration.remMinutes,
                sleepLatencyMinutes = duration.sleepLatencyMinutes,
                wakeCount = wakeCount
            )
        )
    }
}
