package com.sleepytime.shared.ui.report

import com.sleepytime.shared.domain.model.EnvironmentFeature
import com.sleepytime.shared.domain.model.SleepMetrics
import com.sleepytime.shared.domain.model.SleepStage
import com.sleepytime.shared.enum_.ChartTab
import com.sleepytime.shared.enum_.ReportTab
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

object ReportContract {
    data class State(
        val selectedChartTab: ChartTab = ChartTab.SLEEP_DURATION,
        val date: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
        val isPreview: Boolean = true,
        val sessionDates: Set<LocalDate> = emptySet(),
        val reportData: ReportData,
        val weeklyChartData: ReportData,
    )

    data class ReportData(
        val environmentHistory: List<EnvironmentFeature.Snapshot>,
        val bedTime: LocalDateTime,
        val wakeTime: LocalDateTime,

        val sleepLatencyMinutes: Double,
        val awakeMinutes: Double,
        val lightMinutes: Double,
        val deepMinutes: Double,
        val remMinutes: Double,
        val totalMinutes: Double,
        val stageTimeline: List<SleepStage>,

        val sleepMetrics: SleepMetrics = SleepMetrics(
            wakeCountScore = 0.0,
            continuityScore = 0.0,
            deepScore = 0.0,
            remScore = 0.0,
            latencyScore = 0.0,

            awakeMinutes = 0.0,
            lightMinutes = 0.0,
            deepMinutes = 0.0,
            remMinutes = 0.0,
            sleepLatencyMinutes = 0.0,
            wakeCount = 0
        ),

        val wakeCount: Int,
        val sleepScore: Int,

        val avgHeartRate: Float,
        val avgNoise: Float,
        val isHeartRateAnomaly: Boolean,
        val isNoiseDanger: Boolean,

        val dailyBedTimes: Map<LocalDate, LocalDateTime>,
        val dailyWakeTimes: Map<LocalDate, LocalDateTime>,
        val dailyLatencyMinutes: Map<LocalDate, Double>,

        val totalWakeCount: Int,
        val dailyScores: Map<LocalDate, Int>,

        val dailyAvgHeartRates: Map<LocalDate, Float>,
        val dailyAvgNoises: Map<LocalDate, Float>,
    ) {
        private val BASE_HOUR = 18

        val averageBedTime: LocalDateTime? get() {
            if (dailyBedTimes.isEmpty()) return null

            // 1. 기준점(18:00)으로부터 지나간 총 분(Minute) 수로 변환하여 평균 계산
            val avgMinutesFromBase = dailyBedTimes.values.map { time ->
                if (time.hour >= BASE_HOUR) {
                    (time.hour - BASE_HOUR) * 60 + time.minute
                } else {
                    (time.hour + (24 - BASE_HOUR)) * 60 + time.minute
                }
            }.average()

            // 2. 평균 분을 복원하여 시(Hour), 분(Minute) 계산
            val totalMinutes = avgMinutesFromBase.toInt()
            val rawHour = (BASE_HOUR + totalMinutes / 60) % 24
            val minute = totalMinutes % 60

            // 3. LocalDateTime 생성을 위해 현재 오늘 날짜를 가져옵니다.
            val todayDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

            // 4. 오늘 날짜와 계산된 시/분을 결합하여 LocalDateTime 반환
            return LocalDateTime(
                year = todayDate.year,
                monthNumber = todayDate.monthNumber,
                dayOfMonth = todayDate.dayOfMonth,
                hour = rawHour,
                minute = minute,
                second = 0,
                nanosecond = 0
            )
        }

        val averageWakeTime: LocalDateTime? get() {
            if (dailyWakeTimes.isEmpty()) return null

            val avgMinutesFromBase = dailyWakeTimes.values.map { time ->
                if (time.hour >= BASE_HOUR) {
                    (time.hour - BASE_HOUR) * 60 + time.minute
                } else {
                    (time.hour + (24 - BASE_HOUR)) * 60 + time.minute
                }
            }.average()

            val totalMinutes = avgMinutesFromBase.toInt()
            val rawHour = (BASE_HOUR + totalMinutes / 60) % 24
            val minute = totalMinutes % 60

            val todayDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

            return LocalDateTime(
                year = todayDate.year,
                monthNumber = todayDate.monthNumber,
                dayOfMonth = todayDate.dayOfMonth,
                hour = rawHour,
                minute = minute,
                second = 0,
                nanosecond = 0
            )
        }

        val averageScore: Int? get() {
            if (dailyScores.isEmpty()) return null
            // 점수는 단순 산술 평균으로도 정확합니다.
            return dailyScores.values.average().toInt()
        }
    }

    sealed class Intent {
        data class SelectDate(val date: LocalDate) : Intent()
        data class SelectChartMode(val chartTab: ChartTab) : Intent()
        data class LoadFinishedSession(val sessionId: String) : Intent()
        data class PrevMonthClicked(val date: LocalDate) : Intent()
        data class NextMonthClicked(val date: LocalDate) : Intent()
    }
}
