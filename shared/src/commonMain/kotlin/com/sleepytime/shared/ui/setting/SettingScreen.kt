package com.sleepytime.shared.ui.setting

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.sleepytime.shared.domain.model.User
import com.sleepytime.shared.resources.Res
import com.sleepytime.shared.resources.button_mypage_login
import com.sleepytime.shared.resources.button_sleep_start
import com.sleepytime.shared.resources.ic_app_info
import com.sleepytime.shared.resources.ic_caret_right
import com.sleepytime.shared.resources.ic_legal_info
import com.sleepytime.shared.resources.ic_notification
import com.sleepytime.shared.resources.ic_pencil
import com.sleepytime.shared.resources.ic_profile
import com.sleepytime.shared.resources.ic_sleep
import com.sleepytime.shared.resources.ic_smart_alarm
import com.sleepytime.shared.resources.ic_support
import com.sleepytime.shared.ui.auth.AuthViewModel
import com.sleepytime.shared.ui.theme.*
import io.github.aakira.napier.Napier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingContent(
    authViewModel: AuthViewModel,
    onNavigateToLoginBenefit: () -> Unit,
    onNavigateToAccountSetting: () -> Unit,
    onNavigateToNotification: () -> Unit,
    onNavigateToSleepSetting: () -> Unit,
    onNavigateToAppInfo: () -> Unit,
    onNavigateToSupport: () -> Unit,
) {
    val authState by authViewModel.state.collectAsState()
    val isGuest = authState.userType is User.AuthInfo.Guest

    LaunchedEffect(Unit) {
        if (!isGuest) authViewModel.refreshSocialProfile()
    }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "마이페이지",
            style = MaterialTheme.typography.sectionTitle,
            color = Color.White,
        )
        UserProfileSection(
            user = authState.user,
            userType = authState.userType,
            isGuest = isGuest,
            onLoginBenefitClicked = onNavigateToLoginBenefit
        )
        SettingCard {
            if(!isGuest) {
                SettingItem(painterResource(Res.drawable.ic_profile), "계정 및 프로필") {
                    onNavigateToAccountSetting()
                }
            }
            SettingItem(painterResource(Res.drawable.ic_notification), "알림 및 환경설정") {
                onNavigateToNotification()
            }
            SettingItem(painterResource(Res.drawable.ic_sleep), "수면 및 알람 설정") {
                onNavigateToSleepSetting()
            }
            SettingItem(painterResource(Res.drawable.ic_legal_info), "자주 묻는 질문") {
                onNavigateToSupport()
            }
            SettingItem(painterResource(Res.drawable.ic_app_info), "앱 정보") {
                onNavigateToAppInfo()
            }
        }
    }
}
@Composable
fun SettingCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeContent),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
        ),
    ) {
        Column {
            content()
        }
    }
}

@Composable
fun SettingItem(
    icon: Painter,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyText,
                color = Color.White
            )
        }

        Icon(
            modifier = Modifier.size(24.dp),
            painter = painterResource(Res.drawable.ic_caret_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}
@Composable
fun UserProfileSection(
    user: User?,
    userType: User.AuthInfo,
    isGuest: Boolean,
    onLoginBenefitClicked: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isGuest) {
                    Napier.d("isGuest: $isGuest")
                    Image(
                        painter = painterResource(Res.drawable.ic_profile),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Napier.d("isGuest: $isGuest")
                    Napier.d("profileImageUrl = ${user?.profileImageUrl}")
                    AsyncImage(
                        model = user?.profileImageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                        placeholder = painterResource(Res.drawable.ic_profile),
                        error = painterResource(Res.drawable.ic_profile),
                        onLoading = { Napier.d("이미지 로딩 중") },
                        onSuccess = { Napier.d("이미지 로딩 성공") },
                        onError = { state -> Napier.e("이미지 로딩 실패: ${state.result.throwable}") }
                    )
                }
            }
            Text(
                text = userType.displayName,
                style = MaterialTheme.typography.sectionTitle,
                color = Color.White
            )
        }
        if (isGuest) {
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ),
                onClick = onLoginBenefitClicked
            ) {
                Text(
                    text = stringResource(Res.string.button_mypage_login),
                    style = MaterialTheme.typography.sectionTitle,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}