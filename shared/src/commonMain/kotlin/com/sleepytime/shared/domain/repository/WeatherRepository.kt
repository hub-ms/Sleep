package com.sleepytime.shared.domain.repository

import com.sleepytime.shared.domain.model.WeatherState

interface WeatherRepository {
    suspend fun fetchCurrentWeather(): WeatherState
    suspend fun getCurrentTemperature(): Float
    suspend fun getCurrentHumidity(): Float
}
