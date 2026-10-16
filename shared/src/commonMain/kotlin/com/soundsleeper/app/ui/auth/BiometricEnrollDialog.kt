package com.soundsleeper.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.soundsleeper.app.resources.Res
import com.soundsleeper.app.resources.biometric_enroll_confirm
import com.soundsleeper.app.resources.biometric_enroll_description
import com.soundsleeper.app.resources.biometric_enroll_dismiss
import com.soundsleeper.app.resources.biometric_enroll_title
import com.soundsleeper.app.resources.biometric_login_cta
import com.soundsleeper.app.resources.biometric_prompt_cancel
import com.soundsleeper.app.resources.biometric_prompt_subtitle
import com.soundsleeper.app.resources.biometric_prompt_title
import com.soundsleeper.app.resources.ic_fingerprint
import com.soundsleeper.app.ui.onboarding.OnboardingButton
import com.soundsleeper.app.ui.theme.SleepTheme.background
import com.soundsleeper.app.ui.theme.SleepTheme.primary
import com.soundsleeper.app.ui.theme.bodyText
import com.soundsleeper.app.ui.theme.sectionTitle
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * 가입 직후 "다음부터 지문으로 로그인할까요?" 팝업.
 *
 * 한 번만 띄운다([com.soundsleeper.app.util.PreferencesKeys.Auth.BIOMETRIC_PROMPT_SHOWN]).
 * 거절한 사람에게 켤 때마다 다시 물으면 성가신 데다, 어차피 거절하는 사람은 계속 거절한다.
 *
 * 승낙해도 **지금 당장 인증을 요구하지는 않는다.** 방금 가입을 끝낸 사람에게 바로 지문을
 * 대라고 하면 무엇이 더 진행되는 것인지 알 수 없다. 플래그만 켜고, 다음에 앱을 켤 때
 * 스플래시에서 처음 쓰인다.
 */
@Composable
fun BiometricEnrollDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(primary.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_fingerprint),
                        contentDescription = null,
                        tint = primary,
                        modifier = Modifier.size(40.dp),
                    )
                }
                Text(
                    text = stringResource(Res.string.biometric_enroll_title),
                    style = MaterialTheme.typography.sectionTitle,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(Res.string.biometric_enroll_description),
                    style = MaterialTheme.typography.bodyText.copy(lineBreak = LineBreak.Paragraph),
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                )
                OnboardingButton(
                    text = stringResource(Res.string.biometric_enroll_confirm),
                    onClick = onConfirm,
                )
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(Res.string.biometric_enroll_dismiss),
                        style = MaterialTheme.typography.bodyText,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

/**
 * 생체 인증 프롬프트에 띄울 문구 묶음.
 *
 * [com.soundsleeper.app.platform.BiometricAuthenticator.authenticate] 는 Composable 이
 * 아니라서 문자열을 직접 읽을 수 없다. 라우팅 파일(AppScreens)에 리소스 import 를 끌어들이지
 * 않도록 여기서 한 번에 읽어 넘긴다.
 */
data class BiometricPromptLabels(
    val title: String,
    val subtitle: String,
    val cancel: String,
)

@Composable
fun rememberBiometricPromptLabels(): BiometricPromptLabels = BiometricPromptLabels(
    title = stringResource(Res.string.biometric_prompt_title),
    subtitle = stringResource(Res.string.biometric_prompt_subtitle),
    cancel = stringResource(Res.string.biometric_prompt_cancel),
)

/**
 * 지문 인증을 취소했을 때 스플래시 위에 덮는 안내.
 *
 * 화면이 스플래시 그대로 멈춰 있으면 앱이 먹통이 된 것처럼 보인다. 무엇을 기다리는지
 * 말해 주고 다시 시도할 버튼을 둔다.
 */
@Composable
fun BiometricLockedOverlay(
    onRetry: () -> Unit,
    onUseAnotherMethod: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_fingerprint),
                contentDescription = null,
                tint = primary,
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = stringResource(Res.string.biometric_prompt_subtitle),
                style = MaterialTheme.typography.bodyText,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            OnboardingButton(
                text = stringResource(Res.string.biometric_login_cta),
                onClick = onRetry,
            )
            TextButton(onClick = onUseAnotherMethod) {
                Text(
                    text = stringResource(Res.string.biometric_prompt_cancel),
                    style = MaterialTheme.typography.bodyText,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        }
    }
}
