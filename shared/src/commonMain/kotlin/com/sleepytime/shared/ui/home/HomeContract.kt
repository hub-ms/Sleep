package com.sleepytime.shared.ui.home

import kotlinx.datetime.LocalDateTime
import org.jetbrains.compose.resources.StringResource

object HomeContract {
    data class State(
        val selectedTab: String = "홈",
        val previewPosition: Long = 0L,
        val isTimer: Boolean = false,
        val timerMinutes: Int? = null,
        val musicName: StringResource? = null,
        val isPlayingPreview: Boolean = false,
        val showAllMusic: Boolean = false,
        val wakeUpTime: LocalDateTime? = null,
        val duration: Int = 360,
        val isRestoring: Boolean = false,
        val sessionId: String? = null,
        val sleepCount: Int = 0,
        val reportMode: ReportMode = ReportMode.Preview
    )
    sealed class Intent {
        object SleepSettingClicked : Intent()
        object SleepSummaryClicked : Intent()
        object SleepMusicClicked : Intent()

        data class SelectBottomTab(val tab: String) : Intent()
        object TutorialClicked: Intent()

        object ToggleTimer: Intent()
        data class SetTimerMinutes(val minutes: Int): Intent()
    }
    sealed class Effect {
        object NavigateToSleepSetting : Effect()
        data class NavigateToReport(val sessionId: String) : Effect()

        object NavigateToSleepMusicSelection : Effect()

        object NavigateToTutorial: Effect()
    }
}
sealed class ReportMode {
    object Preview : ReportMode()
    object Full : ReportMode()
}
