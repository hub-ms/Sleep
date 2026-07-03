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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.sleepytime.shared.platform.PermissionState
import com.sleepytime.shared.platform.isAndroidPlatform
import com.sleepytime.shared.resources.Res
import com.sleepytime.shared.resources.bg_onboarding
import com.sleepytime.shared.resources.ic_check
import com.sleepytime.shared.resources.ic_heart_rate
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
    onboardingState: OnboardingContract.State,
    permissionState: PermissionState,
    onNextStepClicked: () -> Unit,
    onUpdatePermission: (PermissionType, Boolean) -> Unit,
    onGuestLogin: () -> Unit,
    onSocialLogin: (AuthProvider) -> Unit,
    onEmailLogin: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        OnboardingBackground(onboardingState = onboardingState)

        when (onboardingState.step) {
            0 -> IntroPage(onNextStepClicked = onNextStepClicked)
            1 -> PermissionPage(
                permissionState = permissionState,
                onAllGranted = onNextStepClicked,
                onUpdatePermission = onUpdatePermission,
            )

            2 -> LoginPage(
                onGuestLogin = onGuestLogin,
                onSocialLogin = onSocialLogin,
                onEmailLogin = onEmailLogin
            )
        }
    }
}

@Composable
fun OnboardingBackground(onboardingState: OnboardingContract.State) {
    if (onboardingState.step == 0 || onboardingState.step == 2) {
        Image(
            painter = painterResource(Res.drawable.bg_onboarding),
            contentDescription = "Background Image",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B1A2B))
        )
    }
}

@Composable
private fun IntroPage(onNextStepClicked: () -> Unit) {
    Column(
        modifier = Modifier
            .navigationBarsPadding()
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceAround,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "더 나은 수면이\n더 나은 하루를 만듭니다",
            style = MaterialTheme.typography.sectionTitle,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        OnboardingButton(
            text = "시작하기",
            onClick = onNextStepClicked
        )
    }
}

@Composable
fun PermissionPage(
    permissionState: PermissionState,
    onAllGranted: () -> Unit,
    onUpdatePermission: (PermissionType, Boolean) -> Unit,
) {
    val permissionHandler = rememberPermissionHandler { type, granted ->
        onUpdatePermission(type, granted)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "수면 측정 권한 설정",
            style = MaterialTheme.typography.sectionTitle,
            color = Color.White
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PermissionCard(
                icon = Res.drawable.ic_microphone,
                content = "주변 소음을 녹음하기 위해 마이크 권한이 필요합니다",
                granted = permissionState.audio,
                onClick = {
                    permissionHandler.request(PermissionType.AUDIO)
                }
            )
            PermissionCard(
                icon = Res.drawable.ic_notification,
                content = "알람을 제시간에 울리기 위해 알림 권한이 필요합니다",
                granted = permissionState.notification,
                onClick = {
                    permissionHandler.request(PermissionType.NOTIFICATION)
                }
            )
            PermissionCard(
                icon = Res.drawable.ic_heart_rate,
                content = "심박수 측정을 위해 신체 활동 권한이 필요합니다",
                granted = permissionState.activity,
                onClick = {
                    permissionHandler.request(PermissionType.ACTIVITY_RECOGNITION)
                }
            )
            if (isAndroidPlatform) {
                PermissionCard(   // ⭐ 추가
                    icon = Res.drawable.ic_motion,  // 적절한 아이콘으로 교체
                    content = "수면 중 측정이 끊기지 않도록 설정에서 배터리 사용량을 '제한 없음'으로 변경해 주세요.",
                    granted = permissionState.batteryOptimizationIgnored,
                    onClick = { permissionHandler.request(PermissionType.BATTERY_OPTIMIZATION) }
                )
            }
        }
        val allGranted = permissionState.isAllGranted()
        Button(
            onClick = { if (allGranted) onAllGranted() },
            enabled = allGranted,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (allGranted) MaterialTheme.colorScheme.primary else Color.Gray
            )
        ) {
            Text(
                text = "시작하기",
                style = MaterialTheme.typography.sectionTitle,
                color = Color.White
            )
        }
    }
}

@Composable
fun PermissionCard(
    icon: DrawableResource,
    content: String,
    granted: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (!granted) onClick() }
            .height(90.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (granted) MaterialTheme.colorScheme.primary.copy(0.4f) else Color.White.copy(
                0.4f
            )
        ),
        border = BorderStroke(
            width = 2.dp,
            color = if (granted) MaterialTheme.colorScheme.primary else Color.Transparent
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)  // ✅ Center → spacedBy
        ) {
            Icon(
                modifier = Modifier.size(32.dp),
                painter = painterResource(icon),
                contentDescription = null,
                tint = if (granted) MaterialTheme.colorScheme.primary else Color.LightGray
            )
            Text(
                modifier = Modifier.weight(1f),
                text = content,
                style = MaterialTheme.typography.caption,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )

            if (granted) {
                Icon(
                    modifier = Modifier.size(32.dp),
                    painter = painterResource(Res.drawable.ic_check),
                    contentDescription = "권한 허용됨",
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else {
                Spacer(Modifier.size(32.dp))  // ✅ 비활성 시 공간 유지 (레이아웃 흔들림 방지)
            }
        }
    }
}

@Composable
private fun LoginPage(
    onGuestLogin: () -> Unit,
    onSocialLogin: (AuthProvider) -> Unit,
    onEmailLogin: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        OnboardingButton(
            text = "수면분석 시작하기",
            onClick = onGuestLogin
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "또는 다른 방법으로 시작하기",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(24.dp))

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
        Spacer(modifier = Modifier.height(48.dp))
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
        shape = RoundedCornerShape(30.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
    }
}

@Preview
@Composable
fun OnboardingIntroPreview() {
    SleepAppTheme {
        OnboardingContent(
            onboardingState = OnboardingContract.State(step = 0),
            permissionState = PermissionState(),
            onNextStepClicked = {},
            onUpdatePermission = { _, _ -> },
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
            onboardingState = OnboardingContract.State(step = 1),
            permissionState = PermissionState(
                audio = true,
                notification = true
            ),
            onNextStepClicked = {},
            onUpdatePermission = { _, _ -> },
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
            onboardingState = OnboardingContract.State(step = 2),
            permissionState = PermissionState(),
            onNextStepClicked = {},
            onUpdatePermission = { _, _ -> },
            onGuestLogin = {},
            onSocialLogin = {},
            onEmailLogin = {}
        )
    }
}
