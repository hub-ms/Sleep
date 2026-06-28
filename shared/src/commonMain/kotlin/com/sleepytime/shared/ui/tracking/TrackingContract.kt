package com.sleepytime.shared.ui.tracking

import com.sleepytime.shared.domain.model.EnvironmentFeature
import com.sleepytime.shared.enum_.PredictionStageType
import kotlinx.datetime.LocalDateTime


object TrackingContract {
    data class State(
        val isFinished: Boolean = false,
        val finishedSessionId: String? = null,
        val isTracking: Boolean = false,
        val trackingStartTime: LocalDateTime = LocalDateTime(2000, 1, 1, 23, 0, 0),
        val trackingEndTime: LocalDateTime = LocalDateTime(2000, 1, 2, 7, 0, 0),

        val sleepLatencyMinutes: Int = 0,

        val elapsedSeconds: Int = 0,
        val duration: Int = 0,
        val sessionId: String = "",
        val currentSleepStageType: PredictionStageType = PredictionStageType.AWAKE,

        val avgHeartRate: Float = 62f,
        val avgNoise: Float = 32f,
        val avgTemperature: Float = 22.5f,
        val avgHumidity: Float = 40f,

        val stddevHeartRate: Float = 0.0f,
        val stddevNoise: Float = 0.0f,
        val stddevTemp: Float = 0.0f,
        val stddevHumidity: Float = 0.0f,

        val maxHeartRate: Float = 0.0f,
        val maxNoise: Float = 0.0f,
        val maxTemp: Float = 0.0f,
        val maxHumidity: Float = 0.0f,

        val minHeartRate: Float = 0.0f,
        val minNoise: Float = 0.0f,
        val minTemp: Float = 0.0f,
        val minHumidity: Float = 0.0f,

        val isNoiseDanger: Boolean = false,
        val isTempExtreme: Boolean = false,
        val isHeartRateAnomaly: Boolean = false,
        val isHumidityExtreme: Boolean = false,

        val environmentHistory: List<EnvironmentFeature.Snapshot> = emptyList()
    ) {
        val sleepOnsetTime: LocalDateTime
            get() {
                val minutes = 15
                val totalMinutes = trackingStartTime.minute + minutes
                val extraHours = totalMinutes / 60
                val newMinutes = totalMinutes % 60
                val newHour = trackingStartTime.hour + extraHours

                return LocalDateTime(
                    year = trackingStartTime.year,
                    monthNumber = trackingStartTime.monthNumber,
                    dayOfMonth = trackingStartTime.dayOfMonth,
                    hour = newHour,
                    minute = newMinutes
                )
            }
    }

    sealed class Intent {
        data class StartTracking(val duration: Int, val musicTitle: String?) : Intent()
        object DiscardTracking : Intent()
        object FinishTracking : Intent()



        data class SelectMusic(val musicId: Int) : Intent()
        data class SetMusicVolume(val volume: Float): Intent()
        object ChangeMusicClicked: Intent()
    }
    sealed class Effect {
        data class NavigateToTracking(val duration: Int, val sessionId: String) : Effect()
        data class NavigateToReport(val sessionId: String) : Effect()
        object NavigateToSleepMusicSelection : Effect()
        object NavigateToHome: Effect()
    }
}
