package com.soundsleeper.app.platform

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.media3.common.util.UnstableApi
import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.OAuthCredential
import com.google.firebase.auth.OAuthProvider
import com.kakao.sdk.user.UserApiClient
import com.russhwolf.settings.ExperimentalSettingsApi
import com.soundsleeper.app.MainActivity
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.ExperimentalTime

@UnstableApi
@ExperimentalMaterial3Api
@ExperimentalTime
@ExperimentalSettingsApi
@ExperimentalCoroutinesApi
@InternalVoyagerApi
actual class SocialAuthManager(
    private val context: Context,
    private val googleOAuthClientId: String,
) {
    actual suspend fun getGoogleToken(): String? = runCatching {
        val activity = context.findActivity()
            ?: throw IllegalArgumentException("Activity context is required")
        val credentialManager = CredentialManager.create(activity)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(googleOAuthClientId)
            .setFilterByAuthorizedAccounts(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val result = credentialManager.getCredential(activity, request)
        GoogleIdTokenCredential.createFrom(result.credential.data).idToken
    }.getOrNull()

    actual suspend fun getKakaoToken(): String? = suspendCancellableCoroutine { cont ->
        Napier.d("getKakaoToken")

        // 1. 이미 기존에 발급 받아 저장된 토큰이 있는지 확인
        if (com.kakao.sdk.auth.AuthApiClient.instance.hasToken()) {
            com.kakao.sdk.user.UserApiClient.instance.accessTokenInfo { tokenInfo, error ->
                if (error == null && tokenInfo != null) {
                    // 토큰이 유효함 -> 카카오 SDK 저장소에서 토큰 직접 추출 후 즉시 반환
                    val existingToken = com.kakao.sdk.auth.TokenManagerProvider.instance.manager.getToken()?.accessToken
                    if (existingToken != null) {
                        Napier.d("기존 유효한 카카오 토큰 사용")
                        cont.resume(existingToken)
                        return@accessTokenInfo
                    }
                }
                // 토큰이 만료되었거나 에러 발생 시 아래의 재로그인 프로세스로 진행
                performKakaoLogin(cont)
            }
        } else {
            performKakaoLogin(cont)
        }
    }

    private fun performKakaoLogin(cont: CancellableContinuation<String?>) {
        val activity = context.findActivity() ?: return cont.resume(null)

        val loginWithAccount = {
            UserApiClient.instance.loginWithKakaoAccount(activity) { token, error ->
                if (error != null) {
                    cont.resumeWithException(error)
                } else {
                    ensureEmailScope(activity, token?.accessToken, cont)
                }
            }
        }

        if (UserApiClient.instance.isKakaoTalkLoginAvailable(activity)) {
            UserApiClient.instance.loginWithKakaoTalk(activity) { token, error ->
                if (error != null) {
                    if (error is com.kakao.sdk.common.model.ClientError &&
                        error.reason == com.kakao.sdk.common.model.ClientErrorCause.Cancelled) {
                        cont.resumeWithException(error)
                    } else {
                        loginWithAccount()
                    }
                } else {
                    ensureEmailScope(activity, token?.accessToken, cont)
                }
            }
        } else {
            loginWithAccount()
        }
    }

    /**
     * 이메일 동의를 아직 받지 않았으면 추가 동의를 한 번 요청한다.
     *
     * 카카오는 동의받지 않은 항목을 아예 내려주지 않는다. 예전에는 요청조차 하지 않아 서버가
     * 받을 이메일이 없었고, 그래서 "{id}@kakao.user" 같은 가짜 주소를 지어내 저장했다.
     *
     * 주의: 이 요청만으로 항상 이메일을 받을 수 있는 건 아니다. 카카오 개발자 콘솔에서
     * account_email 동의 항목이 켜져 있어야 하고 비즈니스 앱 심사가 필요하다. 사용자가 동의를
     * 거부할 수도 있다. 어느 경우든 이메일 없이 로그인은 정상 진행되어야 하므로, 실패해도
     * 원래 토큰으로 그대로 이어간다.
     */
    private fun ensureEmailScope(
        activity: Activity,
        accessToken: String?,
        cont: CancellableContinuation<String?>,
    ) {
        if (accessToken == null) {
            cont.resume(null)
            return
        }

        UserApiClient.instance.me { user, error ->
            val needsEmail = error == null && user?.kakaoAccount?.emailNeedsAgreement == true
            if (!needsEmail) {
                cont.resume(accessToken)
                return@me
            }

            UserApiClient.instance.loginWithNewScopes(
                activity,
                listOf(KAKAO_EMAIL_SCOPE)
            ) { newToken, scopeError ->
                if (scopeError != null) {
                    // 동의 거부나 심사 미완료. 이메일 없이 그대로 진행한다.
                    Napier.d("카카오 이메일 추가 동의 실패: ${scopeError.message}")
                    cont.resume(accessToken)
                } else {
                    cont.resume(newToken?.accessToken ?: accessToken)
                }
            }
        }
    }

    actual suspend fun getAppleToken(): String? = suspendCancellableCoroutine { cont ->
        val activity =
            context.findActivity() ?: return@suspendCancellableCoroutine cont.resume(null)

        val provider = OAuthProvider.newBuilder("apple.com")
        val auth = FirebaseAuth.getInstance()
        val pending = auth.pendingAuthResult

        val handleResult: (AuthResult) -> Unit = { result ->
            val credential = result.credential as? OAuthCredential
            cont.resume(credential?.accessToken)
        }

        if (pending != null) {
            pending
                .addOnSuccessListener { handleResult(it) }
                .addOnFailureListener { cont.resume(null) }
        } else {
            auth.startActivityForSignInWithProvider(activity, provider.build())
                .addOnSuccessListener { handleResult(it) }
                .addOnFailureListener { cont.resume(null) }
        }
    }
    private fun Context.findActivity(): Activity? {
        var context = this
        while (context is ContextWrapper) {
            if (context is Activity) return context
            context = context.baseContext
        }
        return MainActivity.instance?.get()
    }

    private companion object {
        /** 카카오 이메일 동의 항목 이름. 콘솔에서 활성화돼 있어야 실제로 받아올 수 있다. */
        const val KAKAO_EMAIL_SCOPE = "account_email"
    }
}
