package com.soundsleeper.app.platform

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.media3.common.util.UnstableApi
import androidx.compose.material3.ExperimentalMaterial3Api
import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import com.russhwolf.settings.ExperimentalSettingsApi
import com.soundsleeper.app.MainActivity
import io.github.aakira.napier.Napier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.time.ExperimentalTime

/**
 * [BiometricAuthenticator] 의 안드로이드 구현.
 *
 * `BIOMETRIC_STRONG` 만 받는다. `DEVICE_CREDENTIAL`(PIN·패턴)까지 허용하면 화면 잠금만
 * 풀면 세션이 복구되어, 기기를 잠깐 빌려준 사람도 로그인된 상태가 된다. 지문/얼굴을
 * 등록하지 않은 사용자에게는 이 기능을 권하지 않고 평소 로그인을 쓰게 한다.
 */
// MainActivity 에 붙어 있는 opt-in 집합을 그대로 따라간다. Activity 참조 한 줄 때문에
// 딸려 오는 것들이라 이 클래스 자체가 실험적이라는 뜻은 아니다
// (SocialAuthManager 도 같은 이유로 같은 묶음을 달고 있다).
@UnstableApi
@ExperimentalMaterial3Api
@ExperimentalTime
@ExperimentalSettingsApi
@ExperimentalCoroutinesApi
@InternalVoyagerApi
class AndroidBiometricAuthenticator : BiometricAuthenticator {

    private val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG

    private fun activity(): FragmentActivity? =
        MainActivity.instance?.get() as? FragmentActivity

    override suspend fun isAvailable(): Boolean {
        val activity = activity() ?: return false
        return BiometricManager.from(activity).canAuthenticate(authenticators) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    override suspend fun authenticate(
        title: String,
        subtitle: String,
        cancelLabel: String,
    ): Boolean = suspendCancellableCoroutine { cont ->
        val activity = activity()
        if (activity == null) {
            cont.resume(false)
            return@suspendCancellableCoroutine
        }

        // 콜백이 두 번 불리는 경우(인증 실패 후 취소 등)에 resume 이 두 번 일어나면
        // IllegalStateException 으로 앱이 죽는다. 한 번만 넘기도록 묶어 둔다.
        var settled = false
        fun settle(result: Boolean) {
            if (!settled && cont.isActive) {
                settled = true
                cont.resume(result)
            }
        }

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    settle(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // 취소도 여기로 온다. 사용자가 그만둔 것이므로 실패와 같게 다룬다.
                    Napier.d("생체 인증 종료: $errorCode $errString")
                    settle(false)
                }

                // onAuthenticationFailed 는 "지문이 안 맞았다" 로, 프롬프트가 아직 떠
                // 있고 다시 시도할 수 있다. 여기서 끝내면 한 번 삐끗한 사용자가
                // 바로 로그인 화면으로 튕긴다 - 일부러 처리하지 않는다.
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(cancelLabel)
            .setAllowedAuthenticators(authenticators)
            .build()

        cont.invokeOnCancellation { prompt.cancelAuthentication() }
        prompt.authenticate(info)
    }
}
