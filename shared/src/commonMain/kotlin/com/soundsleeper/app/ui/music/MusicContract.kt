package com.soundsleeper.app.ui.music

import com.soundsleeper.app.domain.model.SleepMusic
import kotlinx.datetime.LocalDateTime

object MusicContract {
    data class State(
        val musicList: List<SleepMusic> = emptyList(),
        val selectedMusic: SleepMusic? = null,
        val isPlaying: Boolean = false,
        val isFavorite: Boolean = false,
        val startTime: LocalDateTime? = null,
        val elapsedSeconds: Int = 0,
        val timerMinutes: Int? = 30,
        val remainingSeconds: Int? = null,
        val isAutoStopEnabled: Boolean = false,
        val isLooping: Boolean = true
    )
    sealed class Intent {
        data class MusicSelected(val music: SleepMusic?, val isAutoPlay: Boolean = true) : Intent()
        object TogglePlaying: Intent()

        /**
         * 재생을 '끈다'. 토글과 달리 현재 상태와 무관하게 멈추는 쪽으로만 간다.
         *
         * 타이머 만료와 자동 종료는 예전에 TogglePlaying 을 썼는데, 그 사이 다른 경로로
         * 재생이 이미 멈춰 있으면 끄려던 신호가 오히려 음악을 다시 켰다.
         */
        object StopPlaying: Intent()
        object ToggleAlarmPreview : Intent()
        data class ToggleFavorite(val music: SleepMusic) : Intent()
        /** 분 단위 고정 타이머. null 이면 타이머를 끈다. */
        data class SetTimer(val minutes: Int?) : Intent()
        /** 얕은 잠에 들면 자동으로 끄는 모드. */
        object SetAutoStopTimer : Intent()
        object ToggleLooping : Intent()
    }
}


