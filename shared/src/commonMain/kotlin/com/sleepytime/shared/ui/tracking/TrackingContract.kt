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
        val isAlarmTriggered: Boolean = false,
        val musicTitle: String? = null,

        val sleepLatencyMinutes: Int = 0,

        val elapsedMillis: Long = 0,
        val durationMillis: Long = 0,
        val sessionId: String? = null,
        val currentSleepStageType: PredictionStageType = PredictionStageType.AWAKE,

        val avgNoise: Float = 32f,
        val avgTemperature: Float = 22.5f,
        val avgHumidity: Float = 40f,

        val stddevNoise: Float = 0.0f,
        val stddevTemp: Float = 0.0f,
        val stddevHumidity: Float = 0.0f,

        val maxNoise: Float = 0.0f,
        val maxTemp: Float = 0.0f,
        val maxHumidity: Float = 0.0f,

        val minNoise: Float = 0.0f,
        val minTemp: Float = 0.0f,
        val minHumidity: Float = 0.0f,

        val isNoiseDanger: Boolean = false,
        val isTempExtreme: Boolean = false,
        val isHumidityExtreme: Boolean = false,

        val environmentHistory: List<EnvironmentFeature.Snapshot> = emptyList()
    )

    sealed class Intent {
        data class StartTracking(val durationMillis: Long, val musicTitle: String?) : Intent()
        object DiscardTracking : Intent()
        object FinishTracking : Intent()
    }
    sealed class Effect {
        data class NavigateToTracking(val durationMillis: Long, val sessionId: String) : Effect()
        data class NavigateToReport(val sessionId: String) : Effect()

        object NavigateToHome: Effect()
        data class NavigateToWakeUp(val sessionId: String) : Effect()
    }
}
