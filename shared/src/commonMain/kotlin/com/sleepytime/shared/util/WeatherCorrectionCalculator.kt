package com.sleepytime.shared.util

import kotlin.math.pow
import kotlin.math.sqrt

class WeatherCorrectionCalculator {
    fun estimateCoastalFactor(lat: Double, lng: Double): Float {
        val minDist = COASTAL_POINTS.minOf { (clat, clng) ->
            sqrt((lat - clat).pow(2) + (lng - clng).pow(2))
        }
        return (1f - (minDist / 3f).toFloat()).coerceIn(0f, 1f)
    }

    fun estimateKoreanClimateZone(lat: Double, lng: Double): Float = when {
        lat < 34.5 -> -0.5f   // 제주 (온난)
        lat < 36.5 -> -0.2f   // 남부
        lat < 38.0 ->  0.0f   // 중부 (기준)
        else       ->  0.3f   // 북부 (한랭)
    }

    fun estimateUrbanHeatIsland(lat: Double, lng: Double): Float {
        val minDist = URBAN_CENTERS.minOf { (ulat, ulng) ->
            sqrt((lat - ulat).pow(2) + (lng - ulng).pow(2))
        }
        return when {
            minDist < 0.3 -> 1.5f
            minDist < 0.8 -> 0.8f
            minDist < 1.5 -> 0.3f
            else          -> 0.0f
        }
    }

    // ── 계절 유틸 ────────────────────────────────────────────────

    fun getSeason(month: Int): String = when (month) {
        12, 1, 2 -> "winter"
        6, 7, 8  -> "summer"
        else     -> "spring_fall"
    }

    /**
     * @return [tempLo, tempHi, humidityLo, humidityHi]
     */
    fun getSeasonalBounds(season: String): List<Float> = when (season) {
        "winter"     -> listOf(18f, 26f, 30f, 55f)
        "summer"     -> listOf(20f, 28f, 45f, 70f)
        else         -> listOf(17f, 27f, 35f, 65f)
    }

    // ── 고도 계산 (기압 → 미터) ──────────────────────────────────

    fun pressureToAltitudeMeters(pressureHpa: Float): Float =
        44330f * (1f - (pressureHpa / 1013.25f).pow(0.1903f))

    // ── 상수 ─────────────────────────────────────────────────────

    companion object {
        private val COASTAL_POINTS = listOf(
            35.1 to 129.0,   // 부산
            34.8 to 126.4,   // 목포
            37.5 to 126.6,   // 인천
            33.5 to 126.5,   // 제주
            35.5 to 129.4    // 울산
        )
        private val URBAN_CENTERS = listOf(
            37.566 to 126.978,   // 서울
            35.180 to 129.075,   // 부산
            35.870 to 128.600    // 대구
        )
    }
}