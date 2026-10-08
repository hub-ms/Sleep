package com.soundsleeper.app.ui.setting
object SettingContract {
    data class State(
        val currentVersion: String = "",
        val latestVersion: String? = null,
        val isLatestVersion: Boolean = true,
        val isCheckingVersion: Boolean = false,
    )
    sealed class Intent {
        data object CheckLatestVersion : Intent()

        data object ClickReview : Intent()

        data object ClickSubscribe : Intent()

        data object ClickManageSubscription : Intent()

        data object OpenStorePage : Intent()
    }
    sealed class Effect {
        data object NavigateToReview : Effect()
        data object NavigateToPaywall : Effect()
        data object NavigateToSubscriptionManage : Effect()
        data object NavigateToStorePage : Effect()
    }
}
