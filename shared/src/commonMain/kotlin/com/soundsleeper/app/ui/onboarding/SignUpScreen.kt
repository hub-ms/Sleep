package com.soundsleeper.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.LineHeightStyle.Alignment
import com.soundsleeper.app.enum_.AuthProvider
import com.soundsleeper.app.ui.auth.AuthContract
import com.soundsleeper.app.ui.theme.SleepTheme.primary
import com.soundsleeper.app.ui.theme.SleepTheme.surface

/**
 * 가입/로그인 화면.
 *
 * 예전에는 가치 제안 페이저의 마지막 장에 로그인 블록이 얹혀 있었다. 설문이 사이에 들어오면서
 * 독립 화면으로 떼어냈다.
 *
 * 이 화면은 상태를 받는다. 예전 온보딩은 람다 세 개만 받아서, 로그인 실패 메시지가
 * ViewModel 에 쌓여도 사용자에게는 아무것도 보이지 않았다.
 */
@Composable
fun SignUpContent(
    authState: AuthContract.State,
    onGuestLogin: () -> Unit,
    onSocialLogin: (AuthProvider) -> Unit,
    onEmailLogin: () -> Unit,
    onNavigateToTerms: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onWithdrawnAccountRestore: () -> Unit,
    onWithdrawnAccountDismiss: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        OnboardingBackground()
        AuthActions(
            onGuestLogin = onGuestLogin,
            onSocialLogin = onSocialLogin,
            onEmailLogin = onEmailLogin,
            onNavigateToTerms = onNavigateToTerms,
            onNavigateToPrivacy = onNavigateToPrivacy,
        )
        authState.withdrawnAccountMessage?.let { message ->
            AlertDialog(
                onDismissRequest = onWithdrawnAccountDismiss,
                containerColor = surface,
                title = {
                    Text(
                        text = if (authState.isWithdrawnAccountRestorable) "탈퇴한 계정이에요"
                        else "사용할 수 없는 계정이에요",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = { Text(text = message, color = Color.White) },
                confirmButton = {
                    if (authState.isWithdrawnAccountRestorable) {
                        TextButton(onClick = onWithdrawnAccountRestore) {
                            Text("계정 복구하기", color = primary)
                        }
                    } else {
                        TextButton(onClick = onWithdrawnAccountDismiss) {
                            Text("확인", color = primary)
                        }
                    }
                },
                dismissButton = {
                    if (authState.isWithdrawnAccountRestorable) {
                        TextButton(onClick = onWithdrawnAccountDismiss) {
                            Text("취소", color = Color.White)
                        }
                    }
                }
            )
        }
    }
}
