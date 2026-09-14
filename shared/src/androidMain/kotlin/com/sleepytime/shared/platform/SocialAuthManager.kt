package com.sleepytime.shared.platform

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
import com.sleepytime.shared.BuildConfig
import com.sleepytime.shared.MainActivity
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
                    cont.resume(token?.accessToken)
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