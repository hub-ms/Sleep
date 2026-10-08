package com.soundsleeper.app.ui.auth

import com.soundsleeper.app.domain.model.User
import com.soundsleeper.app.enum_.AuthProvider

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
        /** 서버가 마지막 로그인 수단 해제를 거부했을 때(409) 띄우는 차단 안내 문구. */
        val lastAuthMethodBlockMessage: String? = null,
        /**
         * 탈퇴한 계정으로 로그인했을 때 띄울 안내. 유예 기간이 남았으면 복구 여부를 묻고,
         * 지났으면 다시 가입하라고만 알린다.
         */
        val withdrawnAccountMessage: String? = null,
        /** 복구가 가능한 상태인지. 복구 버튼을 보여줄지 결정한다. */
        val isWithdrawnAccountRestorable: Boolean = false,
        /** 이메일 연결이 끝났을 때 띄울 성공 안내. */
        val emailConnectedMessage: String? = null,
        /** 복구 확인을 받고 다시 로그인할 때 쓸 provider. */
        val withdrawnAccountProvider: AuthProvider? = null,
        val isProfileImageUploading: Boolean = false,

        val withdrawStep: WithdrawStep = WithdrawStep.NONE,
        val withdrawInput: String = "",
        val withdrawReason: String? = null, // 추가

        val resetCompleted: Boolean = false,
    )
    sealed class Intent {
        data class SocialLoginClicked(val provider: AuthProvider) : Intent()
        data class SocialConnectClicked(val provider: AuthProvider) : Intent()
        data class SocialDisConnectClicked(val provider: AuthProvider) : Intent()
        object EmailLoginClicked : Intent()
        object EmailConnectClicked: Intent()
        data class SendAuthCodeClicked(val email: String) : Intent()
        /** 메일로 받은 6자리 코드 제출. [isConnectFlow] 면 로그인 대신 이메일 연결을 한다. */
        data class SubmitAuthCode(
            val email: String,
            val code: String,
            val isConnectFlow: Boolean,
        ) : Intent()
        object EmailConnectedMessageShown : Intent()
        data class VerifyEmailToken(val token: String?, val from: String?) : Intent()
        data class DeepLinkAuthSuccess(val authCode: String): Intent()
        object EmailLoginSubmitted: Intent()
        object GuestLoginClicked: Intent()

        /** 복구하지 않고 안내만 닫는다. */
        object WithdrawnAccountDismissed : Intent()
        /** 복구에 동의했다. 같은 provider 로 restore=true 재로그인한다. */
        object WithdrawnAccountRestoreConfirmed : Intent()

        data class UpdateNickname(val nickname: String) : Intent()
        /**
         * 프로필 수정 화면의 저장. UpdateNickname 은 입력칸을 따라가는 화면 상태일 뿐이고,
         * 서버에 실제로 반영하는 건 이 인텐트다. 예전에는 저장 버튼이 화면만 닫아서
         * 바꾼 닉네임이 어디에도 남지 않았다.
         */
        data class SaveProfile(val nickname: String) : Intent()
        object ResetProfileImage : Intent()
        data class UpdateProfileImage(val imageBytes: ByteArray) : Intent() {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other == null || this::class != other::class) return false

                other as UpdateProfileImage

                return imageBytes.contentEquals(other.imageBytes)
            }
            override fun hashCode(): Int = imageBytes.contentHashCode()
        }

        data object MessageShown : Intent()

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
        data object LastAuthMethodBlockDismissed : Intent()
        object LoginBenefitClicked: Intent()
    }

    sealed class Effect {
        data class NavigateToEmailAuth(val token: String?, val from: String?) : Effect()
        object NavigateToHome : Effect()
        object NavigateToSignUpLoading : Effect()
        object NavigateToLoginBenefit: Effect()
        object LaunchInAppReview : Effect()
        /** 프로필 저장이 서버까지 반영됐을 때. 수정 화면은 이때만 닫힌다. */
        object ProfileSaved : Effect()
    }
}
enum class WithdrawStep {
    NONE,           // 기본
    WARNING,        // 1차 경고
    CONFIRM_INPUT,  // 텍스트 입력 확인
    LOADING,        // 처리중
    // 🐛 예전에는 성공/실패 모두 NONE으로 돌아가서 로딩 화면이 둘을 구분할 수 없었고,
    // 그 결과 탈퇴가 실패해도 "탈퇴 완료" 화면이 떴다. 두 결과를 따로 표현한다.
    DONE,           // 탈퇴 성공
    FAILED          // 탈퇴 실패
}