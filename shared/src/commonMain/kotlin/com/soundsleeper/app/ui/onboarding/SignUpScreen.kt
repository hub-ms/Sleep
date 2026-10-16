package com.soundsleeper.app.ui.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.soundsleeper.app.domain.model.SleepAnimalType
import com.soundsleeper.app.resources.Res
import com.soundsleeper.app.resources.auth_restore_account
import com.soundsleeper.app.resources.auth_unusable_account_title
import com.soundsleeper.app.resources.auth_withdrawn_title
import com.soundsleeper.app.resources.common_cancel
import com.soundsleeper.app.resources.common_confirm_short
import org.jetbrains.compose.resources.stringResource
import com.soundsleeper.app.enum_.AuthProvider
import com.soundsleeper.app.ui.auth.AuthContract
import com.soundsleeper.app.ui.component.AnimalRevealCard
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
 *
 * 설문을 마친 사용자에게는 **위쪽 절반에 설문 결과(동물 유형)를 먼저 보여준다.** 설문
 * 다음에 있던 별도의 "수면 진단이 끝났어요" 화면은 없앴다 — 그 화면은 결과를 아무것도
 * 보여주지 않고 기대감만 만든 뒤 한 번 더 탭하게 하는 중간 관문이었다. 결과를 바로 보여주고
 * 그 아래에서 가입하게 하는 편이 탭도 한 번 줄고, "이걸 보려면 가입해야 한다"는 이유도
 * 눈앞에 함께 놓인다.
 *
 * [animalType] 이 null 이면(= 가치 제안 화면에서 설문을 건너뛴 경우) 위쪽을 비우고
 * 로그인 블록이 화면을 다 쓴다.
 */
@Composable
fun SignUpContent(
    authState: AuthContract.State,
    animalType: SleepAnimalType?,
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
        Column(
            modifier = Modifier
                .systemBarsPadding()
                .fillMaxSize(),
        ) {
            if (animalType != null) {
                // 위 절반은 결과, 아래 절반은 로그인. weight 로 나눠 두면 작은 화면에서도
                // 로그인 버튼이 화면 밖으로 밀리지 않는다.
                //
                // 카드 자체가 화면보다 커질 수 있는 경우(큰 글꼴 설정, 긴 번역문)를 대비해
                // 이 영역만 스크롤되게 둔다. 아래 로그인 블록은 늘 제자리에 있어야 한다.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AnimalRevealCard(animal = animalType)
                }
            }
            AuthActions(
                modifier = Modifier.weight(1f),
                onGuestLogin = onGuestLogin,
                onSocialLogin = onSocialLogin,
                onEmailLogin = onEmailLogin,
                onNavigateToTerms = onNavigateToTerms,
                onNavigateToPrivacy = onNavigateToPrivacy,
            )
        }
        authState.withdrawnAccountMessage?.let { message ->
            AlertDialog(
                onDismissRequest = onWithdrawnAccountDismiss,
                containerColor = surface,
                title = {
                    Text(
                        text = stringResource(
                            if (authState.isWithdrawnAccountRestorable) Res.string.auth_withdrawn_title
                            else Res.string.auth_unusable_account_title
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = { Text(text = message, color = Color.White) },
                confirmButton = {
                    if (authState.isWithdrawnAccountRestorable) {
                        TextButton(onClick = onWithdrawnAccountRestore) {
                            Text(stringResource(Res.string.auth_restore_account), color = primary)
                        }
                    } else {
                        TextButton(onClick = onWithdrawnAccountDismiss) {
                            Text(stringResource(Res.string.common_confirm_short), color = primary)
                        }
                    }
                },
                dismissButton = {
                    if (authState.isWithdrawnAccountRestorable) {
                        TextButton(onClick = onWithdrawnAccountDismiss) {
                            Text(stringResource(Res.string.common_cancel), color = Color.White)
                        }
                    }
                }
            )
        }
    }
}
