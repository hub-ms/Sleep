package com.example.sleep.core.util

import com.example.sleep.domain.SleepStage

object SleepStageCalculator {
    fun calculateStage(sensorData: SensorData): SleepStage {
        return SleepStage.LIGHT
    }
}