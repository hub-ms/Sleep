package com.soundsleeper.app.util

import com.soundsleeper.app.domain.model.SleepSession
import com.soundsleeper.app.ui.report.ReportContract
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.minutes

object SleepSessionUtil {
    /**
     * DB에 저장된 [SleepSession] 도메인 모델을 리포트 화면이 그리는 [ReportContract.ReportData]로 변환한다.
     * 취침 시각은 타임라인의 첫 구간 시작 시각으로, 기상 시각은 취침 시각 + 침대에 있던 총 시간으로 계산한다.
     */
    fun SleepSession.toReportData(): ReportContract.ReportData {
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
        )
    }
}
