package com.soundsleeper.app.ui.onboarding

object PermissionContract {
    data class State(
        val audio: Boolean = false,
        val notification: Boolean = false,
        val batteryOptimizationIgnored: Boolean = false,
        val activity: Boolean = false
    ) {
        /**
         * 측정을 시작할 수 있는가.
         *
         * 배터리 최적화 제외는 빠져 있다. 그건 런타임 권한이 아니라 설정 앱으로 나가야 하는
         * 항목이라, 여기 포함하면 시스템 권한 대화상자만으로는 이 조건을 영원히 만족시킬 수
         * 없다. 측정 안정성에는 도움이 되므로 안내는 따로 하되 시작을 막지는 않는다.
         */
        fun isAllGranted(): Boolean = audio && notification && activity
    }
}

