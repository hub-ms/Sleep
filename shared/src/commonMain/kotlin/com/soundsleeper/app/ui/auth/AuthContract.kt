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
        val withdrawReason: String? = null,
    )
    sealed class Intent {
        // 소셜 로그인
        data class SocialLoginClicked(val provider: AuthProvider) : Intent()
        // 다른 소셜 계정 연결
        data class SocialConnectClicked(val provider: AuthProvider) : Intent()
        // 다른 소셜 계정 해제
        data class SocialDisConnectClicked(val provider: AuthProvider) : Intent()
        // 이메일 로그인
        object EmailLoginClicked : Intent()
        // 이메일 계정 연결
        object EmailConnectClicked: Intent()
        // 인증코드 발송
        data class SendAuthCodeClicked(val email: String) : Intent()
        data class SubmitAuthCode(
            val email: String,
            val code: String,
            val isConnectFlow: Boolean,
        ) : Intent()
        // 이메일 연결 성공 안내
        object EmailConnectedMessageShown : Intent()
        // 이메일 토큰 검증
        data class VerifyEmailToken(val token: String?, val from: String?) : Intent()
        // 딥링크 인증 성공
        data class DeepLinkAuthSuccess(val authCode: String): Intent()
        // 이메일 로그인 완료
        object EmailLoginSubmitted: Intent()
        // 게스트 로그인
        object GuestLoginClicked: Intent()

        // 계정 복구 취소
        object WithdrawnAccountDismissed : Intent()
        // 계정 복구
        object WithdrawnAccountRestoreConfirmed : Intent()

        // 닉네임 변경
        data class UpdateNickname(val nickname: String) : Intent()

        // 로그아웃
        object LogoutClicked: Intent()
        // 로그아웃 확인
        object LogoutConfirmed: Intent()
        // 로그아웃 취소
        object LogoutCancelled: Intent()
        // 회원탈퇴
        object WithdrawClicked: Intent()
        // 회원탈퇴 이유 선택
        data class SelectWithdrawReason(val reason: String) : Intent()
        // 팁 보기 클릭
        object AlternativeTipClicked: Intent()
        // 가이드 보기 클릭
        object AlternativeGuideClicked: Intent()
        // 1:1 문의하기 클릭
        object AlternativeSupportClicked: Intent()
        // 데이터 초기화 클릭
        object AlternativePrivacyClicked: Intent()
        // 회원탈퇴 확인
        object WithdrawConfirmed : Intent()
        // 회원탈퇴 취소
        object WithdrawCancelled: Intent()
        // 대표 계정 변경
        data class ChangePrimaryProvider(val provider: AuthProvider?) : Intent()
        // 이메일 계정 연결 해제
        data object EmailDisconnectClicked : Intent()

        // 토스트/에러 문구를 한 번 보여준 뒤 지운다
        data object MessageShown : Intent()
        // 마지막 로그인 수단 해제 차단 안내를 닫는다
        data object LastAuthMethodBlockDismissed : Intent()
        // 프로필(닉네임) 저장
        data class SaveProfile(val nickname: String) : Intent()
        /**
         * 계정 화면에 들어왔다.
         *
         * 연결된 로그인 수단은 로컬에 캐시하지 않는다. 들어올 때마다 서버에 물어야 다른
         * 기기에서 바뀐 연결 상태가 반영되고, 해제 가드와 판단이 어긋나지 않는다.
         */
        data object AccountScreenShown : Intent()
    }

    sealed class Effect {
        data class NavigateToEmailAuth(val token: String?, val from: String?) : Effect()
        object NavigateToHome : Effect()
        object NavigateToSignUpLoading : Effect()
        object LaunchInAppReview : Effect()
        /** 프로필 저장이 서버까지 반영됐을 때. 수정 화면은 이때만 닫힌다. */
        object ProfileSaved : Effect()

        /**
         * 탈퇴 대안 중 "수면 팁"/"측정 가이드" 를 골랐을 때. 둘 다 같은 안내 화면으로 보낸다
         * — 전용 수면 팁 화면이 아직 없고, 충전기·거치 위치 안내가 들어 있는
         * PermissionGuideScreen 이 현재 유일한 팁 지면이다.
         */
        object NavigateToSleepGuide : Effect()
        /** 탈퇴 대안 중 "프라이버시" 를 골랐을 때. 앱 내 프라이버시 설정 메뉴가 아직 없어
         *  개인정보처리방침으로 보낸다. */
        object NavigateToPrivacyPolicy : Effect()
    }
}
enum class WithdrawStep {
    NONE,           // 기본
    REASON,        // 1차 경고
    WARNING,
    DONE,           // 탈퇴 성공
    FAILED          // 탈퇴 실패
}