package com.sleepytime.shared.ui.report

import com.sleepytime.shared.domain.model.EnvironmentFeature
import com.sleepytime.shared.domain.model.SleepMetrics
import com.sleepytime.shared.domain.model.SleepStage
import com.sleepytime.shared.enum_.ReportTab
import com.sleepytime.shared.enum_.SleepStageType
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Duration.Companion.minutes

object ReportContract {
    data class State(
        val selectedTab: ReportTab = ReportTab.WEEKLY,
        val date: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
        val isPreview: Boolean = true,
        val sessionDates: Set<LocalDate> = emptySet(),
        val reportData: ReportData,
    )

    data class ReportData(
        val environmentHistory: List<EnvironmentFeature.Snapshot>,
        val bedTime: LocalDateTime,
        val wakeTime: LocalDateTime,
        val awakeMinutes: Double,
        val lightMinutes: Double,
        val deepMinutes: Double,
        val remMinutes: Double,
        val totalMinutes: Double,
        val stageTimeline: List<SleepStage>,

        val sleepLatencyMinutes: Double,
        val wakeCount: Int,
        val sleepScore: Int,

        val avgHeartRate: Float,
        val avgNoise: Float,
        val avgTemperature: Float,
        val avgHumidity: Float,

        val isHeartRateAnomaly: Boolean,
        val isNoiseDanger: Boolean,
        val isTempExtreme: Boolean,
        val isHumidityExtreme: Boolean,

        val dailyLatencyMinutes: Map<LocalDate, Double>,
        val dailyBedTimes: Map<LocalDate, LocalDateTime>,
        val dailyWakeTimes: Map<LocalDate, LocalDateTime>,

        val totalWakeCount: Int,
        val dailyScores: Map<LocalDate, Int>,

        val dailyAvgHeartRates: Map<LocalDate, Float>,
        val dailyAvgNoises: Map<LocalDate, Float>,
        val dailyAvgTemps: Map<LocalDate, Float>,
        val dailyAvgHumidities: Map<LocalDate, Float>,

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
        )
    ) {

        val averageHeartRate: Float? get() = dailyAvgHeartRates.values.takeIf { it.isNotEmpty() }?.average()?.toFloat()
        val averageNoise: Float? get() = dailyAvgNoises.values.takeIf { it.isNotEmpty() }?.average()?.toFloat()
        val averageTemp: Float? get() = dailyAvgTemps.values.takeIf { it.isNotEmpty() }?.average()?.toFloat()
        val averageHumidity: Float? get() = dailyAvgHumidities.values.takeIf { it.isNotEmpty() }?.average()?.toFloat()

        val averageLatencyMinutes: Long? get() {
            if (dailyLatencyMinutes.isEmpty()) return null
            return dailyLatencyMinutes.values.average().toLong()
        }
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
        data class SelectReportMode(val tab: ReportTab) : Intent()
        data class SleepEnvironmentClicked(val environmentHistory: List<EnvironmentFeature.Snapshot>, val avgHeartRate: Float, val avgNoise: Float, val avgTemperature: Float, val avgHumidity: Float) : Intent()
        data class LoadFinishedSession(val sessionId: String) : Intent()
        data class PrevMonthClicked(val date: LocalDate) : Intent()
        data class NextMonthClicked(val date: LocalDate) : Intent()
    }

    sealed class Effect {
        object NavigateToSleepEnvironment : Effect()
    }
}
