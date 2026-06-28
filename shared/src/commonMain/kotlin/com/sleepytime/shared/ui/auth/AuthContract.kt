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

        val resetCompleted: Boolean = false,
    )
    sealed class UiState {
        object Loading : UiState()
        object Waiting : UiState()
        object Success : UiState()
        data class Error(val message: String) : UiState()
    }
    
    sealed class Intent {
        data class SocialLoginClicked(val provider: AuthProvider) : Intent()
        data class SocialConnectClicked(val provider: AuthProvider) : Intent()
        object EmailLoginClicked : Intent()
        object EmailConnectClicked: Intent()
        data class SendAuthCodeClicked(val email: String) : Intent()
        data class VerifyEmailToken(val token: String?, val from: String?) : Intent()
        data class DeepLinkAuthSuccess(val authCode: String): Intent()
        object EmailLoginSubmitted: Intent()
        object GuestLoginClicked: Intent()
    
        object LogoutClicked: Intent()
        object LogoutConfirmed: Intent()
        object LogoutCancelled: Intent()
    
        object WithdrawClicked: Intent()
        object WithdrawContinue: Intent()
        object WithdrawPause : Intent()
        object WithdrawDisableNotification : Intent()
        object WithdrawToGuest : Intent()
        object WithdrawConfirmed : Intent()
        object WithdrawCancelled: Intent()
    
        object ResetDataClicked: Intent()
    
        data class ChangePrimaryProvider(val provider: AuthProvider?) : Intent()
        data class DisconnectProvider(val provider: AuthProvider) : Intent()
        data object EmailDisconnectClicked : Intent()
        object LoginBenefitClicked: Intent()
    }
    
    sealed class Effect {
        data class NavigateToEmailAuth(val token: String?, val from: String?) : Effect()
        object NavigateToOnboarding: Effect()
        object NavigateToHome : Effect()
        object NavigateToLoginBenefit: Effect()
    }
}
enum class WithdrawStep {
    NONE,           // 湲곕낯
    WARNING,        // 1李?寃쎄퀬
    CONFIRM_INPUT,  // ?띿뒪???낅젰
    LOADING         // 泥섎━以?
}


