package com.sleepytime.shared.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

object DateTimeUtil {
    @ExperimentalTime
    fun tickerFlow(period: Duration): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(period.inWholeMilliseconds.milliseconds)
        }
    }
    fun formatElapsedTimeFromMillis(milliSeconds: Long): String {
        val totalSeconds = milliSeconds / 1000

        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60 // 👈 초 계산 추가

        return if (h > 0) "${h.pad()}:${m.pad()}:${s.pad()}"
        else "${m.pad()}:${s.pad()}"
    }
    fun formatDate(date: LocalDate): String {
        val year = date.year
        val month = date.monthNumber
        val day = date.dayOfMonth
        val dayOfWeek: DayOfWeek = date.dayOfWeek

        val weekDay = when (dayOfWeek) {
            DayOfWeek.MONDAY -> "월"
            DayOfWeek.TUESDAY -> "화"
            DayOfWeek.WEDNESDAY -> "수"
            DayOfWeek.THURSDAY -> "목"
            DayOfWeek.FRIDAY -> "금"
            DayOfWeek.SATURDAY -> "토"
            DayOfWeek.SUNDAY -> "일"
            else -> ""
        }

        return "${year}.${month.pad()}.${day.pad()}(${weekDay})"
    }
    fun formatCalendarMonth(date: LocalDate): String {
        val year = date.year
        val month = date.monthNumber

        return "${year}.${month.pad()}"
    }
    fun formatCalendarWeek(date: LocalDate): String {
        val monday = date.minus(date.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
        val sunday = monday.plus(6, DateTimeUnit.DAY)

        return "${monday.year}.${monday.monthNumber.pad()}.${monday.dayOfMonth.pad()}~" +
                "${sunday.year}.${sunday.monthNumber.pad()}.${sunday.dayOfMonth.pad()}"
    }
    fun formatDateLabel(date: LocalDate): String {
        return "${date.monthNumber}/${date.dayOfMonth}"
    }
    fun LocalDateTime.toAmPmTimeString(): String {
        val amPm = if (this.hour < 12) "오전" else "오후"
        val displayHour = when {
            this.hour == 0 -> 12
            this.hour > 12 -> this.hour - 12
            else -> this.hour
        }
        return "$amPm ${displayHour.pad()}:${this.minute.pad()}"
    }
    fun LocalDateTime.to24TimeString(): String = "${this.hour.pad()}:${this.minute.pad()}"
    fun Int.toLocalDateTime(): LocalDateTime {
        val h = ((this / 60) + 24) % 24
        val m = (this % 60 + 60) % 60
        val today = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        return LocalDateTime(today, LocalTime(h, m))
    }
    fun formatSleepDurationFromMillis(
        totalMillis: Long?
    ): String {
        if (totalMillis == null) return ""

        val totalMinutes = (totalMillis / 60000.0).roundToLong()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60

        return when {
            hours == 0L -> "${minutes}분"
            else -> "${hours}시간 ${minutes}분"
        }
    }
    fun formatRemainingTimeFromMillis(
        totalMillis: Long?
    ): String {
        if (totalMillis == null) return ""

        val totalSeconds = totalMillis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return when {
            hours == 0L -> "${minutes}분 ${seconds}초"
            else -> "${hours}시간 ${minutes}분 ${seconds}초"
        }
    }
    fun formatSleepMusicSeconds(seconds: Int): String {
        val minutes = seconds / 60
        val secs = seconds % 60
        return "${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
    }
    private fun Int.pad() = toString().padStart(2, '0')
    private fun Long.pad() = toString().padStart(2, '0')
}