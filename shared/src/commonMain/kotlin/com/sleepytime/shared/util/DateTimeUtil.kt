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
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

object DateTimeUtil {
    @ExperimentalTime
    fun tickerFlow(period: Duration): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(period.inWholeMilliseconds)
        }
    }
    fun formatSleepMusicSeconds(sleepMusicSeconds: Int): String {
        val hours = sleepMusicSeconds / 3600
        val minutes = (sleepMusicSeconds % 3600) / 60
        val secs = sleepMusicSeconds % 60
        return if (hours > 0) {
            "$hours:${minutes.pad()}:${secs.pad()}"
        } else {
            "${minutes.pad()}:${secs.pad()}"
        }
    }
    fun formatSleepTimeSeconds(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "$h:${m.pad()}:${s.pad()}" else "${m.pad()}:${s.pad()}"
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

        return "${year}년 ${month}월 ${day}일 ${weekDay}요일"
    }
    fun formatCalendarMonth(date: LocalDate): String {
        val year = date.year
        val month = date.monthNumber

        return "${year}.${month.pad()}"
    }
    fun formatWeekLabel(startDate: LocalDate, endDate: LocalDate): String {
        val startYear = startDate.year
        val startMonth = startDate.monthNumber
        val startDay = startDate.dayOfMonth

        val endYear = endDate.year
        val endMonth = endDate.monthNumber
        val endDay = endDate.dayOfMonth

        return "${startYear}.${startMonth.pad()}.${startDay}~${endYear}.${endMonth.pad()}.${endDay}"
    }
    fun formatWeekDayLabel(date: LocalDate): String {
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
        return weekDay
    }
    fun formatDateLabel(date: LocalDate): String {
        return date.dayOfMonth.toString()
    }
    fun formatSleepStageTime(minutes: Long): String {
        val h = minutes / 60
        val m = minutes % 60
        return if (m == 0L) "${h}시간"
        else if (h == 0L) "${m}분"
        else "${h}시간${m}분"
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
    fun LocalDateTime.to24TimeString(): String {
        return "${this.hour.pad()}:${this.minute.pad()}"
    }
    fun Int.toLocalDateTime(): LocalDateTime {
        val h = ((this / 60) + 24) % 24
        val m = (this % 60 + 60) % 60
        val today = Clock.System.now()
            .toLocalDateTime(TimeZone.Companion.currentSystemDefault()).date
        return LocalDateTime(today, LocalTime(h, m))
    }
    fun formatSleepDuration(
        start: LocalDateTime?,
        end: LocalDateTime?,
        timeZone: TimeZone = TimeZone.Companion.currentSystemDefault()
    ): String {
        if (start == null || end == null) return ""

        val startInstant = start.toInstant(timeZone)
        var endInstant = end.toInstant(timeZone)

        if (endInstant < startInstant) {
            endInstant = endInstant.plus(1, DateTimeUnit.Companion.DAY, timeZone)
        }

        val duration = endInstant - startInstant
        val totalMinutes = duration.inWholeMinutes

        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60

        return when {
            hours == 0L -> "${minutes}분"
            minutes == 0L -> "${hours}시간"
            else -> "${hours}시간${minutes}분"
        }
    }
    fun formatSleepDurationFromMillis(
        totalMillis: Long?
    ): String {
        if (totalMillis == null) return ""

        val totalMinutes = totalMillis / (1000 * 60)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60

        return when {
            hours == 0L -> "${minutes}분"
            minutes == 0L -> "${hours}시간"
            else -> "${hours}시간 ${minutes}분"
        }
    }
    private fun Int.pad() = toString().padStart(2, '0')
}