package com.soundsleeper.app.ui.home

import kotlinx.datetime.LocalDateTime

/**
 * 하단 탭 식별자.
 *
 * 예전에는 selectedTab 이 화면에 그려지는 한국어 라벨("홈"/"리포트"/"마이페이지") 문자열
 * 그 자체였다. 탭 이름을 영어로 바꾸는 순간 이 비교들이 전부 어긋나 리포트·마이페이지 탭이
 * 열리지 않게 되는, 화면에 보이는 글자와 내부 상태가 한 몸이었던 버그였다. 탭의 정체성과
 * 화면에 뭐라고 적을지를 분리한다.
 */
enum class HomeTab {
    HOME, REPORT, MYPAGE;

    companion object {
        /** 딥링크 등 외부에서 문자열로 넘어오는 탭 이름을 받는다. */
        fun fromKey(key: String?): HomeTab? = when (key) {
            "HOME" -> HOME
            "REPORT" -> REPORT
            "MYPAGE" -> MYPAGE
            else -> null
        }
    }
}

object HomeContract {
    data class State(
        val selectedTab: HomeTab = HomeTab.HOME,
        val previewPosition: Long = 0L,
        val isTimer: Boolean = false,
        val timerMinutes: Int? = null,
        val musicName: String? = null,
        val isPlayingPreview: Boolean = false,
        val showAllMusic: Boolean = false,
        val wakeUpTime: LocalDateTime? = null,
        val duration: Int = 360,
        val isRestoring: Boolean = false,
        val sessionId: String? = null,
        val sleepCount: Int = 0,
    )
    sealed class Intent {
        object SleepSettingClicked : Intent()
        object SleepSummaryClicked : Intent()
        object SleepMusicClicked : Intent()

        /** 하단 탭 전환. 측정 종료 후 리포트 탭으로 자동 전환할 때도 이 Intent가 쓰인다. */
        data class SelectBottomTab(val tab: HomeTab) : Intent()

        object ToggleTimer: Intent()
        data class SetTimerMinutes(val minutes: Int): Intent()
    }
    sealed class Effect {
        object NavigateToSleepSetting : Effect()
        data class NavigateToReport(val sessionId: String) : Effect()

        object NavigateToSleepMusicSelection : Effect()
    }
}
