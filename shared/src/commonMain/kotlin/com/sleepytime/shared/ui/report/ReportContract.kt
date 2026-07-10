package com.sleepytime.shared.ui.report

import com.sleepytime.shared.domain.model.EnvironmentFeature
import com.sleepytime.shared.domain.model.SleepMetrics
import com.sleepytime.shared.domain.model.SleepStage
import com.sleepytime.shared.enum_.ChartTab
import com.sleepytime.shared.enum_.ReportTab
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
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
        val averageBedTime: LocalDateTime? get() {
            if (dailyBedTimes.isEmpty()) return null
            val avgSeconds = dailyBedTimes.values.map { it.toInstant(TimeZone.currentSystemDefault()).epochSeconds }.average()
            return Instant.fromEpochSeconds(avgSeconds.toLong()).toLocalDateTime(TimeZone.currentSystemDefault())
        }
        val averageWakeTime: LocalDateTime? get() {
            if (dailyWakeTimes.isEmpty()) return null
            val avgSeconds = dailyWakeTimes.values.map { it.toInstant(TimeZone.currentSystemDefault()).epochSeconds }.average()
            return Instant.fromEpochSeconds(avgSeconds.toLong()).toLocalDateTime(TimeZone.currentSystemDefault())
        }
        val averageScore: Int? get() {
            if(dailyScores.isEmpty()) return null
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
