package com.sleepytime.shared.domain.model

sealed class WeatherState {


    // 2. 성공 상태 (기존 WeatherStatus의 핵심 데이터들을 포함)
    data class Success(
        val indoor: IndoorWeather,
        val cached: CachedWeather? = null,
        val correctionParams: CorrectionParams = CorrectionParams()
    ) : WeatherState()

    // 3. 실패 상태
    data class Error(val message: String) : WeatherState()

    // 내부에 데이터 클래스들을 중첩 클래스(Nested Class)로 묶어서 관리

    data class CachedWeather(
        val temp: Float,
        val humidity: Float,
        val precip: Float?,
        val nx: String,
        val ny: String
    )

    data class IndoorWeather(
        val outdoorTemp: Float,
        val outdoorHumidity: Float,
        val indoorTemp: Float,
        val indoorHumidity: Float,
        val season: String,
        val correctionStage: Int,
        val confidence: Float
    )

    data class CorrectionParams(
        val deviceTempOffset: Float = 0f,
        val deviceHumidityOffset: Float = 0f,
        // 지역 기후 보정 (깨진 주석 수정)
        val elevationTempDrop: Float = 0.0065f,  // 고도 100m당 0.65°C
        val coastalHumidityBonus: Float = 0f,
        val climateZoneBias: Float = 0f,
        // 시간 패턴 가중치
        val timePatternWeight: Float = 0.30f,
        // 적응형 파라미터
        val adaptiveTempBias: Float = 0f,
        val adaptiveHumidityBias: Float = 0f
    )

    data class HourlyObservation(
        val hour: Int,
        val indoorTemp: Float,
        val indoorHumidity: Float,
        val sleepQuality: Float,
        val timestamp: Long
    )
}