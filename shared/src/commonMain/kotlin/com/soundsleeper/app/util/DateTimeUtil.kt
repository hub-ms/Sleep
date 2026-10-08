package com.soundsleeper.app.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
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
        val seconds = (milliSeconds / 1000) % 60
        val minutes = (milliSeconds / (1000 * 60)) % 60
        val hours = (milliSeconds / (1000 * 60 * 60))
        return if (hours > 0) {
            "${hours}시간 ${minutes}분"
        } else {
            "${minutes}분 ${seconds}초"
        }
    }
    fun formatDate(date: LocalDate): String {
        val year = date.year
        val month = date.month
        val day = date.day
        val dayOfWeek: DayOfWeek = date.dayOfWeek

        val weekDay = when (dayOfWeek) {
            DayOfWeek.MONDAY -> "월"
            DayOfWeek.TUESDAY -> "화"
            DayOfWeek.WEDNESDAY -> "수"
            DayOfWeek.THURSDAY -> "목"
            DayOfWeek.FRIDAY -> "금"
            DayOfWeek.SATURDAY -> "토"
            DayOfWeek.SUNDAY -> "일"
        }

        return "${year}.${month.number.pad()}.${day.pad()}(${weekDay})"
    }
    fun formatCalendarMonth(date: LocalDate): String {
        val year = date.year
        val month = date.month

        return "${year}.${month.number.pad()}"
    }
    fun formatCalendarWeek(date: LocalDate): String {
        val monday = date.minus(date.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
        val sunday = monday.plus(6, DateTimeUnit.DAY)

        return "${monday.year}.${monday.month.number.pad()}.${monday.day.pad()}~" +
                "${sunday.year}.${sunday.month.number.pad()}.${sunday.day.pad()}"
    }
    fun formatDateLabel(date: LocalDate): String {
        return "${date.month.number}/${date.day}"
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
    // hourUnit/minuteUnit 기본값은 한국어다. Compose 리소스는 @Composable/suspend 컨텍스트에서만
    // 읽을 수 있는데 이 함수는 그런 제약 없이 여러 곳(ViewModel 포함일 수 있음)에서 불리므로,
    // 번역된 단위가 필요한 호출부(화면)만 stringResource 로 읽은 값을 인자로 넘기고
    // 그 외 호출부는 그대로 두어도 깨지지 않게 한다.
    fun formatSleepDurationFromMillis(
        totalMillis: Long?,
        hourUnit: String = "시간",
        minuteUnit: String = "분",
    ): String {
        if (totalMillis == null) return ""

        val totalMinutes = (totalMillis / 60000.0).roundToLong()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60

        return when {
            hours == 0L -> "${minutes}$minuteUnit"
            else -> "${hours}$hourUnit ${minutes}$minuteUnit"
        }
    }
    fun formatSleepMusicSeconds(seconds: Int): String {
        val minutes = seconds / 60
        val secs = seconds % 60
        return "${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
    }
    private fun Int.pad() = toString().padStart(2, '0')
}