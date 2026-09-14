package com.sleepytime.shared.ui.music

import com.sleepytime.shared.domain.model.SleepMusic
import com.sleepytime.shared.ui.tracking.TrackingContract.Effect
import kotlinx.datetime.LocalDateTime

object MusicContract {
    data class State(
        val musicList: List<SleepMusic> = emptyList(),
        val selectedMusic: SleepMusic? = null,
        val isPlaying: Boolean = false,
        val isFavorite: Boolean = false,
        val startTime: LocalDateTime? = null,
        val elapsedSeconds: Int = 0,
        val timerMinutes: Int? = null,
        val remainingSeconds: Int? = null,
        val isLooping: Boolean = true
    )
    sealed class Intent {
        data class MusicSelected(val music: SleepMusic?, val isAutoPlay: Boolean = true) : Intent()
        object TogglePlaying: Intent()
        object SleepMusicClicked : Intent()
        object ToggleAlarmPreview : Intent()
        data class ToggleFavorite(val music: SleepMusic) : Intent()
        data class SetTimer(val minutes: Int?) : Intent()
        object ToggleLooping : Intent()
    }
    sealed class Effect {
        object NavigateToSleepMusicSelection : Effect()
        object NavigateBack : Effect()
    }
}


