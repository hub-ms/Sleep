package com.sleepytime.shared.platform

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.media3.common.util.UnstableApi
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.OAuthCredential
import com.google.firebase.auth.OAuthProvider
import com.kakao.sdk.user.UserApiClient
import com.russhwolf.settings.ExperimentalSettingsApi
import com.sleepytime.shared.BuildConfig
import com.sleepytime.shared.MainActivity
import io.github.aakira.napier.Napier
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
actual class SocialAuthManager(private val context: Context) {
    actual suspend fun getGoogleToken(): String? = runCatching {
        val activity = context.findActivity()
            ?: throw IllegalArgumentException("Activity context is required")
        val credentialManager = CredentialManager.create(activity)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(BuildConfig.GOOGLE_OAUTH_CLIENT_ID)
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
        val activity = context.findActivity()
        if (activity == null) {
            Napier.e("🚨 [SocialAuthManager] Context가 Activity가 아닙니다. ApplicationContext가 주입되었는지 확인하세요.")
            return@suspendCancellableCoroutine cont.resume(null)
        }

        val loginWithAccount = {
            Napier.d("카카오 계정(웹 브라우저) 로그인 시도")
            UserApiClient.instance.loginWithKakaoAccount(activity) { token, error ->
                if (error != null) {
                    Napier.e("카카오 계정 로그인 최종 실패: ${error.message}", error)
                    cont.resumeWithException(error)
                } else {
                    Napier.d("카카오 계정 로그인 성공")
                    cont.resume(token?.accessToken)
                }
            }
        }

        // 카카오톡 앱 로그인 가능 여부 판별
        if (UserApiClient.instance.isKakaoTalkLoginAvailable(activity)) {
            Napier.d("카카오톡 앱 로그인 시도")
            UserApiClient.instance.loginWithKakaoTalk(activity) { token, error ->
                if (error != null) {
                    // 💡 카카오톡 앱 로그인 실패 시 사용자가 창을 닫은 게 아니라면(예: 의존성/앱 에러) 계정 로그인으로 폴백
                    Napier.w("카카오톡 앱 로그인 실패, 카카오 계정 로그인으로 전환합니다. 에러: ${error.message}")

                    if (error is com.kakao.sdk.common.model.ClientError &&
                        error.reason == com.kakao.sdk.common.model.ClientErrorCause.Cancelled) {
                        // 사용자가 명시적으로 취소(뒤로가기 등)한 경우는 폴백 없이 에러 처리
                        cont.resumeWithException(error)
                    } else {
                        loginWithAccount()
                    }
                } else {
                    Napier.d("카카오톡 앱 로그인 성공")
                    cont.resume(token?.accessToken)
                }
            }
        } else {
            loginWithAccount()
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
}