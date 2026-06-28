package com.sleepytime.shared.ui.setting

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.sleepytime.shared.domain.model.User
import com.sleepytime.shared.resources.Res
import com.sleepytime.shared.resources.ic_app_info
import com.sleepytime.shared.resources.ic_caret_right
import com.sleepytime.shared.resources.ic_legal_info
import com.sleepytime.shared.resources.ic_notification
import com.sleepytime.shared.resources.ic_profile
import com.sleepytime.shared.resources.ic_support
import com.sleepytime.shared.ui.auth.AuthViewModel
import com.sleepytime.shared.ui.theme.*
import org.jetbrains.compose.resources.painterResource

@Composable
fun SettingContent(
    authViewModel: AuthViewModel,
    onNavigateToLoginBenefit: () -> Unit,
    onNavigateToAccountSetting: () -> Unit,
    onNavigateToNotification: () -> Unit,
    onNavigateToSupport: () -> Unit,
    onNavigateToAppInfo: () -> Unit,
    onNavigateToLegalInfo: () -> Unit,
) {
    val authState by authViewModel.state.collectAsState()
    val isGuest = authState.userType is User.AuthInfo.Guest

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "마이페이지",
            style = MaterialTheme.typography.sectionTitle,
            color = Color.White,
            modifier = Modifier.padding(16.dp)
        )
        UserProfileSection(
            userType = authState.userType,
            isGuest = isGuest,
            onLoginBenefitClicked = onNavigateToLoginBenefit
        )
        SettingCard(
            text = "일반"
        ) {
            SettingItem(painterResource(Res.drawable.ic_profile), "프로필") {
                onNavigateToAccountSetting()
            }
            SettingItem(painterResource(Res.drawable.ic_notification), "알림") {
                onNavigateToNotification()
            }
        }
        SettingCard(
            text = "기타"
        ) {
            SettingItem(painterResource(Res.drawable.ic_support), "고객센터") {
                onNavigateToSupport()
            }
            SettingItem(painterResource(Res.drawable.ic_app_info), "앱 정보") {
                onNavigateToAppInfo()
            }
            SettingItem(painterResource(Res.drawable.ic_legal_info), "법적 정보") {
                onNavigateToLegalInfo()
            }
        }
    }
}

@Composable
fun SettingCard(text: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.sectionTitle,
            color = Color.White,
        )
        Card(
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
    userType: User.AuthInfo,
    isGuest: Boolean,
    onLoginBenefitClicked: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = userType,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text("사용자", style = MaterialTheme.typography.sectionTitle, color = Color.White)
        Text(if (isGuest) "게스트 사용자" else "회원", style = MaterialTheme.typography.caption, color = Color.White)

        if (isGuest) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onLoginBenefitClicked,
                shape = MaterialTheme.shapes.large
            ) {
                Text("로그인하고 데이터 저장하기")
            }
        }
    }
}

@Composable
fun SleepCtaButton(onLoginBenefitClicked: () -> Unit) {
    Button(
        onClick = onLoginBenefitClicked,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        Text(
            text = "수면기록 이어보기",
            style = MaterialTheme.typography.sectionTitle
        )
    }
}
