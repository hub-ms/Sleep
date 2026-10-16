package com.soundsleeper.app.ui.tracking

import com.soundsleeper.app.domain.model.EnvironmentFeature
import com.soundsleeper.app.enum_.PredictionStageType
import kotlinx.datetime.LocalDateTime


object TrackingContract {
    data class State(
        val isFinished: Boolean = false,
        val finishedSessionId: String? = null,
        val isTracking: Boolean = false,
        val trackingStartTime: LocalDateTime = LocalDateTime(2000, 1, 1, 23, 0, 0),
        val trackingEndTime: LocalDateTime = LocalDateTime(2000, 1, 2, 7, 0, 0),
        val isAlarmTriggered: Boolean = false,
        val musicName: String? = null,

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

        val environmentHistory: List<EnvironmentFeature.Snapshot> = emptyList(),

        val permissionDenied: Boolean = false
    )

    /** 측정 화면이 TrackingViewModel에 보낼 수 있는 요청들. */
    sealed class Intent {
        /** 홈 화면 "수면 시작" 버튼에서 보내는, 측정을 실제로 시작하는 요청. */
        data class StartTracking(val durationMillis: Long, val musicName: String?) : Intent()
        /** 측정을 저장하지 않고 취소하는 요청(종료 다이얼로그에서 5분 미만일 때). */
        object DiscardTracking : Intent()
        /** 측정을 정상적으로 끝내고 분석/저장까지 진행하는 요청. */
        object FinishTracking : Intent()
    }

    /** TrackingViewModel이 UI(네비게이션)에 보내는 화면 전환 신호들. */
    sealed class Effect {
        /** 측정이 실제로 시작되어 측정 화면으로 이동해야 할 때. */
        data class NavigateToTracking(val durationMillis: Long, val sessionId: String) : Effect()
        /** 측정/분석이 끝나 리포트 화면으로 이동해야 할 때(sessionId는 분석 실패 시 null일 수 있음). */
        data class NavigateToReport(val sessionId: String?) : Effect()

        /** 측정을 취소했거나 세션이 너무 짧아 홈으로 돌아가야 할 때. */
        object NavigateToHome: Effect()
        /** 알람 시각이 되어 기상 화면으로 이동해야 할 때. */
        data class NavigateToWakeUp(val sessionId: String) : Effect()

        // 수면 측정 시작 시 마이크(RECORD_AUDIO) 권한이 없어 측정을 시작하지 못했을 때 발생
        object NavigateToPermissionGuide : Effect()
    }
}
