package com.sleepytime.shared.ui.report

import com.sleepytime.shared.domain.model.EnvironmentFeature
import com.sleepytime.shared.domain.model.SleepMetrics
import com.sleepytime.shared.domain.model.SleepStage
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.math.roundToInt

object ReportContract {
    data class State(
        val date: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
        val isCalendarExpanded: Boolean = false,
        val isPreview: Boolean = true,
        val sessionDates: Set<LocalDate> = emptySet(),
        val reportData: ReportData? = null,
        val weeklyChartData: ReportData? = null,
        val prevDayReportData: ReportData? = null,
        val prevWeeklyChartData: ReportData? = null,
        val showDeleteDialog: Boolean = false,
        val pendingDeleteSessionId: String? = null
    )

    data class ReportData(
        val sessionId: String,
        val environmentHistory: List<EnvironmentFeature.Snapshot>,

        val bedTime: LocalDateTime,
        val wakeTime: LocalDateTime,
        val sleepLatencyMinutes: Double,
        val sleepScore: Int,

        val awakeMinutes: Double,
        val lightMinutes: Double,
        val deepMinutes: Double,
        val remMinutes: Double,
        val sleepMinutes: Double,
        val targetMinutes: Double,
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

        val avgNoise: Float,
        val isNoiseDanger: Boolean,

        val dailyBedTimes: Map<LocalDate, LocalDateTime>,
        val dailyWakeTimes: Map<LocalDate, LocalDateTime>,
        val dailySleepMinutes: Map<LocalDate, Double>,
        val dailyScores: Map<LocalDate, Int>,
        val dailySleepLatencyMinutes: Map<LocalDate, Double>,
        val dailyWakeCounts: Map<LocalDate, Int>,

        val totalWakeCount: Int,

        val dailyAvgNoises: Map<LocalDate, Float>,
    ) {
        private val BASE_HOUR = 18

        val averageBedTime: LocalDateTime? get() {
            if (dailyBedTimes.isEmpty()) return null
            val avgMinutes = dailyBedTimes.values.map { time ->
                if (time.hour >= BASE_HOUR) (time.hour - BASE_HOUR) * 60 + time.minute
                else (time.hour + (24 - BASE_HOUR)) * 60 + time.minute
            }.average().roundToInt()

            val rawHour = (BASE_HOUR + avgMinutes / 60) % 24
            val minute = avgMinutes % 60

            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

            return LocalDateTime(
                year = today.year,
                monthNumber = today.monthNumber,
                dayOfMonth = today.dayOfMonth,
                hour = rawHour,
                minute = minute,
                second = 0,
                nanosecond = 0
            )
        }

        val averageWakeTime: LocalDateTime? get() {
            if (dailyWakeTimes.isEmpty()) return null
            val avgMinutes = dailyWakeTimes.values.map { time ->
                if (time.hour >= BASE_HOUR) (time.hour - BASE_HOUR) * 60 + time.minute
                else (time.hour + (24 - BASE_HOUR)) * 60 + time.minute
            }.average().roundToInt()

            val rawHour = (BASE_HOUR + avgMinutes / 60) % 24
            val minute = avgMinutes % 60

            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

            return LocalDateTime(
                year = today.year,
                monthNumber = today.monthNumber,
                dayOfMonth = today.dayOfMonth,
                hour = rawHour,
                minute = minute,
                second = 0,
                nanosecond = 0
            )
        }
        val averageSleepDuration: Int? get() {
            if (dailySleepMinutes.isEmpty()) return null
            return dailySleepMinutes.values.average().roundToInt()
        }
        val averageSleepLatencyMinutes: Double? get() {
            if (dailySleepLatencyMinutes.isEmpty()) return null
            return dailySleepLatencyMinutes.values.average()
        }

        val averageScore: Int? get() {
            if (dailyScores.isEmpty()) return null
            return dailyScores.values.average().toInt()
        }
    }

    sealed class Intent {
        data class SelectDate(val date: LocalDate) : Intent()
        data class PrevClicked(/*val date: LocalDate, */val unit: DateTimeUnit.DateBased) : Intent()
        data class NextClicked(/*val date: LocalDate, */val unit: DateTimeUnit.DateBased) : Intent()
        data class DeleteReport(val sessionId: String) : Intent()
        data class ConfirmDelete(val sessionId: String) : Intent()
        object DismissDeleteDialog : Intent()
        data class ToggleCalendar(val isExpanded: Boolean) : Intent()
    }
    sealed interface Effect {
        data class ShowToast(val message: String) : Effect
        object NavigateBack : Effect // 이전 화면으로 나가기
    }
}
