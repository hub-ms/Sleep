package com.soundsleeper.app.util

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object ChartUtil {
    private const val BASE_HOUR = 21
    private const val SAMPLE_INTERVAL_SECONDS = 30

    private const val NOISE_BUCKET_MINUTES = 10

    private const val NOISE_BUCKET_SAMPLE_COUNT = NOISE_BUCKET_MINUTES * 60 / SAMPLE_INTERVAL_SECONDS
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
            // 20~100dB 로 고정돼 있었는데, 정작 곡선은 실제 데이터 범위에 맞춰 그려져서
            // 눈금과 선의 높이가 서로 맞지 않았다. 10분 평균을 쓰면 값의 폭이 더 좁아져
            // 이 어긋남이 눈에 띈다. 곡선과 같은 데이터에서 눈금을 만든다.
            val minValue = inputNoises.min()
            val maxValue = inputNoises.max()
            // 값이 거의 일정한 밤이면 범위가 0에 가까워 눈금이 전부 같은 숫자가 된다.
            // 최소 폭을 줘서 눈금이 의미를 갖게 한다.
            val lower = floor(minValue).toInt()
            val upper = ceil(maxValue).toInt().coerceAtLeast(lower + 4)
            val steps = 4
            (0..steps).map { i ->
                (lower + (upper - lower) * i / steps).toString()
            }
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
    fun List<Float>.chunkedAverageOrNull(
        bucketSize: Int = NOISE_BUCKET_SAMPLE_COUNT,
    ): List<Float?> {
        require(bucketSize > 0) { "bucketSize must be positive but was $bucketSize" }
        return chunked(bucketSize).map { bucket ->
            val valid = bucket.filter { it > 0f }
            if (valid.isEmpty()) null else valid.sum() / valid.size
        }
    }
    fun List<Float?>.trimAndInterpolateGaps(): List<Float> {
        val trimmed = dropWhile { it == null }.dropLastWhile { it == null }
        if (trimmed.isEmpty()) return emptyList()

        val result = trimmed.toMutableList()
        var i = 0
        while (i < result.size) {
            if (result[i] == null) {
                // 앞뒤를 잘라냈으므로 이 지점의 좌우에는 반드시 유효값이 있다.
                val left = result[i - 1]!!
                var j = i
                while (result[j] == null) j++
                val right = result[j]!!
                val span = j - (i - 1)
                for (k in i until j) {
                    result[k] = left + (right - left) * (k - (i - 1)) / span
                }
                i = j
            }
            i++
        }
        return result.map { it!! }
    }
    fun monotoneCubicPath(points: List<Offset>): Path {
        val path = Path()
        if (points.isEmpty()) return path

        path.moveTo(points[0].x, points[0].y)
        if (points.size == 1) return path

        val n = points.size
        // 구간 기울기
        val slopes = FloatArray(n - 1) { i ->
            val dx = points[i + 1].x - points[i].x
            if (dx == 0f) 0f else (points[i + 1].y - points[i].y) / dx
        }

        // 각 점의 접선 기울기 초기값
        val tangents = FloatArray(n)
        tangents[0] = slopes[0]
        tangents[n - 1] = slopes[n - 2]
        for (i in 1 until n - 1) {
            tangents[i] = if (slopes[i - 1] * slopes[i] <= 0f) 0f else (slopes[i - 1] + slopes[i]) / 2f
        }

        // 단조성 보정
        for (i in 0 until n - 1) {
            if (slopes[i] == 0f) {
                tangents[i] = 0f
                tangents[i + 1] = 0f
                continue
            }
            val alpha = tangents[i] / slopes[i]
            val beta = tangents[i + 1] / slopes[i]
            if (alpha < 0f) tangents[i] = 0f
            if (beta < 0f) tangents[i + 1] = 0f
            val magnitude = alpha * alpha + beta * beta
            if (magnitude > 9f) {
                val tau = 3f / sqrt(magnitude)
                tangents[i] = tau * alpha * slopes[i]
                tangents[i + 1] = tau * beta * slopes[i]
            }
        }

        for (i in 0 until n - 1) {
            val dx = points[i + 1].x - points[i].x
            path.cubicTo(
                points[i].x + dx / 3f, points[i].y + tangents[i] * dx / 3f,
                points[i + 1].x - dx / 3f, points[i + 1].y - tangents[i + 1] * dx / 3f,
                points[i + 1].x, points[i + 1].y
            )
        }
        return path
    }
}