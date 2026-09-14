package com.sleepytime.shared.util

import com.sleepytime.shared.domain.model.SleepMetrics
import com.sleepytime.shared.domain.model.SleepSession
import com.sleepytime.shared.ui.report.ReportContract
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.minutes

object SleepSessionUtil {
    fun SleepSession.toReportData(targetDate: LocalDate): ReportContract.ReportData {
        val tz = TimeZone.currentSystemDefault()
        
        // 💡 실제 저장된 날짜(this.date)를 우선 사용하도록 수정하여 데이터 불일치 방지
        val actualDate = this.date 

        val firstStage = stageTimeline.firstOrNull()
        val bedTime = firstStage?.startTime ?: date.atTime(22, 0)

        val sleepMinutes = duration.lightMinutes + duration.deepMinutes + duration.remMinutes
        val timeInBed = duration.awakeMinutes + duration.sleepLatencyMinutes + sleepMinutes
        val wakeTime = bedTime
            .toInstant(tz)
            .plus(timeInBed.minutes)
            .toLocalDateTime(tz)


        return ReportContract.ReportData(
            sessionId = sessionId,
            environmentHistory = environment.history,
            bedTime = bedTime,
            wakeTime = wakeTime,
            awakeMinutes = duration.awakeMinutes,
            lightMinutes = duration.lightMinutes,
            deepMinutes = duration.deepMinutes,
            remMinutes = duration.remMinutes,
            targetMinutes = duration.targetMinutes,
            sleepMinutes = sleepMinutes,
            sleepLatencyMinutes = duration.sleepLatencyMinutes,
            stageTimeline = stageTimeline,
            wakeCount = wakeCount,
            sleepScore = sleepEfficiency,
            avgNoise = environment.stats.noise.avg,
            isNoiseDanger = environment.flags.isNoiseDanger,

            dailyBedTimes = mapOf(actualDate to bedTime),
            dailyWakeTimes = mapOf(actualDate to wakeTime),
            dailySleepMinutes = mapOf(actualDate to sleepMinutes),
            dailyScores = mapOf(actualDate to sleepEfficiency),
            dailySleepLatencyMinutes = mapOf(actualDate to duration.sleepLatencyMinutes),
            dailyWakeCounts = mapOf(actualDate to wakeCount),
            dailyAvgNoises = mapOf(actualDate to environment.stats.noise.avg),
            totalWakeCount = wakeCount,

            sleepMetrics = SleepMetrics(
                wakeCountScore = sleepMetrics.wakeCountScore,
                continuityScore = sleepMetrics.continuityScore,
                deepScore = sleepMetrics.deepScore,
                remScore = sleepMetrics.remScore,
                latencyScore = sleepMetrics.latencyScore,
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
