package com.sleepytime.shared.util

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

object ChartUtil {
    private const val BASE_HOUR = 21
    private val TIME_FORMATTER = LocalTime.Format {
        hour()          // 시 (2자리 자동으로 맞춰짐)
        char(':')       // 구분자
        minute()        // 분 (2자리 자동으로 맞춰짐)
    }

    private fun timeToRelativeMinutes(time: LocalDateTime): Int {
        return if (time.hour >= BASE_HOUR) {
            (time.hour - BASE_HOUR) * 60 + time.minute
        } else {
            (time.hour + (24 - BASE_HOUR)) * 60 + time.minute
        }
    }
    private fun relativeMinutesToTimeString(relativeMinutes: Int): String {
        val rawHour = (BASE_HOUR + relativeMinutes / 60) % 24
        val minute = relativeMinutes % 60
        return LocalTime(rawHour, minute).format(TIME_FORMATTER)
    }
    fun calculateTimeRange(inputTimes: List<LocalDateTime>): Pair<Int, Int> {
        if (inputTimes.isEmpty()) return Pair(0, 900) // 기본 범위 (21:00 ~ 12:00 = 15시간)

        val minutesList = inputTimes.map { timeToRelativeMinutes(it) }
        val minMin = minutesList.minOrNull() ?: 0
        val maxMin = minutesList.maxOrNull() ?: 1440

        val snappedMin = (floor(minMin / 5f) * 5).toInt()
        val snappedMax = (ceil(maxMin / 5f) * 5).toInt()

        return Pair(max(0, snappedMin), min(1440, snappedMax))
    }
    fun calculateNoiseRange(inputNoises: List<Float>): Pair<Int, Int> {
        if (inputNoises.isEmpty()) return Pair(0, 150)

        val minNoise = inputNoises.min()
        val maxNoise = inputNoises.max()

        val snappedMin = floor(minNoise / 1f).toInt()
        val snappedMax = ceil(maxNoise / 1f).toInt()

        return Pair(snappedMin, snappedMax)
    }
    fun calculateLatencyMinutesRange(inputLatencyMinutes: List<Double>): Pair<Int, Int> {
        if (inputLatencyMinutes.isEmpty()) return Pair(0, 60)

        val minLatency = inputLatencyMinutes.min().toInt()
        val maxLatency = inputLatencyMinutes.max().toInt()

        val snappedMin = (floor(minLatency / 5f) * 5).toInt()
        val snappedMax = (ceil(maxLatency / 5f) * 5).toInt()

        return Pair(max(0, snappedMin), max(10, snappedMax))
    }
    fun generateYLabels(
        inputTimes: List<LocalDateTime> = emptyList(),
        inputNoises: List<Float> = emptyList(),
        inputLatencyMinutes: List<Double> = emptyList()
    ): List<String> = when {
        inputTimes.isNotEmpty() -> {
            val (minBound, maxBound) = calculateTimeRange(inputTimes)
            listOf(relativeMinutesToTimeString(minBound), relativeMinutesToTimeString(maxBound))
        }
        inputNoises.isNotEmpty() -> {
            // 💡 수정: 정상+비정상을 모두 포함하는 넓은 범위 고정 (20 ~ 100 dB)
            listOf("20", "40", "60", "80", "100")
        }
        inputLatencyMinutes.isNotEmpty() -> {
            val (minBound, maxBound) = calculateLatencyMinutesRange(inputLatencyMinutes)
            listOf("${minBound}분", "${maxBound}분")
        }
        else -> listOf("0", "25", "50", "75", "100")
    }
    fun timeToY(chartHeight: Float, time: LocalDateTime, inputTimes: List<LocalDateTime>): Float {
        val (minBound, maxBound) = calculateTimeRange(inputTimes)
        val range = (maxBound - minBound).coerceAtLeast(1)
        val currentMins = timeToRelativeMinutes(time)

        val clampedMins = currentMins.coerceIn(minBound, maxBound)
        val ratio = (clampedMins - minBound).toFloat() / range.toFloat()
        return chartHeight * (1f - ratio)
    }
    fun scoreToY(chartHeight: Float, score: Int): Float {
        val clampedScore = score.coerceIn(0, 100)
        return chartHeight * (1f - clampedScore / 100f)
    }
}