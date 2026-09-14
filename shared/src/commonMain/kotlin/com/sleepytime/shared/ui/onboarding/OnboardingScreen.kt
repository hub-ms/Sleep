package com.sleepytime.shared.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sleepytime.shared.platform.rememberPermissionHandler
import com.sleepytime.shared.enum_.PermissionType
import com.sleepytime.shared.enum_.AuthProvider
import com.sleepytime.shared.resources.Res
import com.sleepytime.shared.resources.bg_onboarding
import com.sleepytime.shared.resources.ic_check
import com.sleepytime.shared.resources.ic_microphone
import com.sleepytime.shared.resources.ic_motion
import com.sleepytime.shared.resources.ic_notification
import com.sleepytime.shared.ui.component.AuthMethod
import com.sleepytime.shared.ui.component.LoginButton
import com.sleepytime.shared.ui.component.LoginButtonType
import com.sleepytime.shared.ui.theme.SleepAppTheme
import com.sleepytime.shared.ui.theme.caption
import com.sleepytime.shared.ui.theme.sectionTitle
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview


@Composable
fun OnboardingContent(
    onGuestLogin: () -> Unit,
    onSocialLogin: (AuthProvider) -> Unit,
    onEmailLogin: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        OnboardingBackground()
        IntroPage(
            onGuestLogin = onGuestLogin,
            onSocialLogin = onSocialLogin,
            onEmailLogin = onEmailLogin
        )
    }
}

@Composable
fun OnboardingBackground() {
    Image(
        painter = painterResource(Res.drawable.bg_onboarding),
        contentDescription = "Background Image",
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun IntroPage(
    onGuestLogin: () -> Unit,
    onSocialLogin: (AuthProvider) -> Unit,
    onEmailLogin: () -> Unit
) {
    Column(
        modifier = Modifier
            .navigationBarsPadding()
            .windowInsetsPadding(WindowInsets.systemBars)
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "더 나은 수면이\n더 나은 하루를 만듭니다",
            style = MaterialTheme.typography.sectionTitle,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OnboardingButton(
                text = "시작하기",
                onClick = onGuestLogin
            )
            Text(
                text = "또는 다른 방법으로 시작하기",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.7f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LoginButton(
                    method = AuthMethod.Member(
                        provider = AuthProvider.KAKAO
                    ),
                    type = LoginButtonType.Circle
                ) {
                    onSocialLogin(AuthProvider.KAKAO)
                }
                LoginButton(
                    method = AuthMethod.Member(
                        provider = AuthProvider.GOOGLE
                    ),
                    type = LoginButtonType.Circle
                ) {
                    onSocialLogin(AuthProvider.GOOGLE)
                }
                LoginButton(
                    method = AuthMethod.Member(
                        provider = AuthProvider.APPLE
                    ),
                    type = LoginButtonType.Circle
                ) {
                    onSocialLogin(AuthProvider.APPLE)
                }
                LoginButton(
                    method = AuthMethod.Member(
                        provider = AuthProvider.EMAIL
                    ),
                    type = LoginButtonType.Circle
                ) {
                    onEmailLogin()
                }
            }
        }
    }
}

@Composable
fun OnboardingButton(
    text: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        shape = RoundedCornerShape(50.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.sectionTitle,
            textAlign = TextAlign.Center
        )
    }
}

@Preview
@Composable
fun OnboardingIntroPreview() {
    SleepAppTheme {
        OnboardingContent(
            onGuestLogin = {},
            onSocialLogin = {},
            onEmailLogin = {}
        )
    }
}

@Preview
@Composable
fun OnboardingPermissionPreview() {
    SleepAppTheme {
        OnboardingContent(
            onGuestLogin = {},
            onSocialLogin = {},
            onEmailLogin = {}
        )
    }
}

@Preview
@Composable
fun OnboardingLoginPreview() {
    SleepAppTheme {
        OnboardingContent(
            onGuestLogin = {},
            onSocialLogin = {},
            onEmailLogin = {}
        )
    }
}
