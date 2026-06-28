package com.sleepytime.shared.ui.music

import com.sleepytime.shared.domain.model.SleepMusic
import kotlinx.datetime.LocalDateTime

object MusicContract {
    data class State(
        val musicList: List<SleepMusic> = emptyList(),
        val selectedMusic: SleepMusic? = null,
        val isPlaying: Boolean = false,
        val startTime: LocalDateTime? = null,
        val elapsedSeconds: Int = 0
    )
    sealed class Intent {
        data class MusicSelected(val music: SleepMusic?) : Intent()
        object ToggleSleepMusic: Intent()
        object ToggleAlarmPreview : Intent()

    }
    sealed class Effect {
        data class NavigateBack(val music: SleepMusic?) : Effect()
    }
}


