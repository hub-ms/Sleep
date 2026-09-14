package com.sleepytime.shared.ui.auth

import com.sleepytime.shared.domain.model.User
import com.sleepytime.shared.enum_.AuthProvider

object AuthContract {
    data class State(
        val user: User? = null,
        val email: String = "",
        val token: String?  = null,
        val from: String? = null,
        val isLoading: Boolean = false,
        val isAuthenticated: Boolean = false,
        val message: String? = null,
        val isEmailValid: Boolean = false,
        val hasAtSymbol: Boolean = false,
        val hasValidDomain: Boolean = false,
        val isCodeSent: Boolean = false,
        val userType: User.AuthInfo = User.AuthInfo.Guest,
        val primaryProvider: AuthProvider? = null,
        val connectedProviders: Set<AuthProvider > = emptySet(),
        val isEmailConnected: Boolean = false,

        val withdrawStep: WithdrawStep = WithdrawStep.NONE,
        val withdrawInput: String = "",
        val withdrawReason: String? = null, // 추가

        val resetCompleted: Boolean = false,

        // 💡 추가: 알림 설정 상태
        val isPushEnabled: Boolean = true,
        val isReminderEnabled: Boolean = true,
        val isWeeklyReportEnabled: Boolean = true,
        val isUpdateEnabled: Boolean = true,

        val reminderHour: Int = 23,
        val reminderMinute: Int = 0,

        val reportDeliveryMethod: ReportDeliveryMethod = ReportDeliveryMethod.PUSH,
    )
    enum class ReportDeliveryMethod {
        PUSH, EMAIL
    }
    sealed class UiState {
        object Loading : UiState()
        object Waiting : UiState()
        object Success : UiState()
        data class Error(val message: String) : UiState()
    }

    sealed class Intent {
        data class SocialLoginClicked(val provider: AuthProvider) : Intent()
        data class SocialConnectClicked(val provider: AuthProvider) : Intent()
        data class SocialDisConnectClicked(val provider: AuthProvider) : Intent()
        object EmailLoginClicked : Intent()
        object EmailConnectClicked: Intent()
        data class SendAuthCodeClicked(val email: String) : Intent()
        data class VerifyEmailToken(val token: String?, val from: String?) : Intent()
        data class DeepLinkAuthSuccess(val authCode: String): Intent()
        object EmailLoginSubmitted: Intent()
        object GuestLoginClicked: Intent()

        data class UpdateNickname(val nickname: String) : Intent()
        data class UpdateEmail(val email: String) : Intent()
        data class SaveProfile(val nickname: String, val email: String?) : Intent()
        object ResetProfileImage : Intent()
        data class UpdateProfileImage(val imageBytes: ByteArray) : Intent()

        object LogoutClicked: Intent()
        object LogoutConfirmed: Intent()
        object LogoutCancelled: Intent()

        object WithdrawClicked: Intent()
        data class SelectWithdrawReason(val reason: String) : Intent() // 추가
        object WithdrawContinue: Intent()
        data class WithdrawInputChanged(val input: String) : Intent()
        object WithdrawPause : Intent()
        object WithdrawDisableNotification : Intent()
        object WithdrawToGuest : Intent()
        object WithdrawConfirmed : Intent()
        object WithdrawCancelled: Intent()

        object ResetDataClicked: Intent()

        data class ChangePrimaryProvider(val provider: AuthProvider?) : Intent()

        data object EmailDisconnectClicked : Intent()
        object LoginBenefitClicked: Intent()

        // 💡 추가: 알림 설정 인텐트
        data class TogglePushNotification(val enabled: Boolean) : Intent()
        data class ToggleSleepReminder(val enabled: Boolean) : Intent()
        data class ToggleWeeklyReport(val enabled: Boolean) : Intent()
        data class ToggleUpdate(val enabled: Boolean) : Intent()

        data class ChangeReminderTime(val hour: Int, val minute: Int) : Intent()
        data class ChangeReportDeliveryMethod(val method: ReportDeliveryMethod) : Intent()
    }

    sealed class Effect {
        data class NavigateToEmailAuth(val token: String?, val from: String?) : Effect()
        object NavigateToOnboarding: Effect()
        object NavigateToHome : Effect()
        object NavigateToLoginBenefit: Effect()
    }
}
enum class WithdrawStep {
    NONE,           // 기본
    WARNING,        // 1차 경고
    CONFIRM_INPUT,  // 텍스트 입력 확인
    LOADING         // 처리중
}