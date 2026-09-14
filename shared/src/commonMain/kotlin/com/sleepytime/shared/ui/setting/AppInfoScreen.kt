package com.sleepytime.shared.ui.setting

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.sleepytime.shared.resources.Res
import com.sleepytime.shared.resources.bg_onboarding
import com.sleepytime.shared.resources.ic_caret_right
import com.sleepytime.shared.ui.theme.caption
import org.jetbrains.compose.resources.painterResource

private const val PACKAGE_NAME = "com.sleepytime.app" // 실제 앱 패키지명(applicationId)으로 교체

private const val PLAY_STORE_REVIEW_URL =
    "https://play.google.com/store/apps/details?id=$PACKAGE_NAME&showAllReviews=true"

private const val PLAY_STORE_SUBSCRIPTIONS_URL =
    "https://play.google.com/store/account/subscriptions?package=$PACKAGE_NAME"

@Composable
fun AppInfoContent(
    currentVersion: String = "1.0.4",
    isLatestVersion: Boolean = true,
    subscriptionStatusLabel: String = "무료 플랜",
    onNavigateToVersionHistory: () -> Unit = {},
    onNavigateToLicenseCredit: () -> Unit = {},
    onNavigateToTerms: () -> Unit = {},
    onNavigateToPrivacy: () -> Unit = {},
    onManageSubscription: () -> Unit = {},
    onRestorePurchase: () -> Unit = {},
) {
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ===== 헤더 =====
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                modifier = Modifier.size(100.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(Res.drawable.bg_onboarding),
                        contentDescription = "App Icon",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "SleepyTime",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.05f)
            ) {
                Text(
                    text = if (isLatestVersion) {
                        "v$currentVersion (최신 버전 사용 중)"
                    } else {
                        "v$currentVersion (업데이트 가능)"
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        SettingSection {
            InfoLinkItem(
                title = "구독 상태",
                trailingText = subscriptionStatusLabel,
                onClick = {
                    onManageSubscription()
                    uriHandler.openUri(PLAY_STORE_SUBSCRIPTIONS_URL)
                }
            )
            InfoLinkItem(
                title = "구매 복원",
                onClick = {
                    onRestorePurchase()
                }
            )
            InfoLinkItem(
                title = "버전 히스토리",
                onClick = onNavigateToVersionHistory
            )
            InfoLinkItem(
                title = "오픈소스 라이선스 및 음원 크레딧",
                onClick = onNavigateToLicenseCredit
            )
            InfoLinkItem(
                title = "플레이스토어에 리뷰 남기기",
                onClick = {
                    uriHandler.openUri(PLAY_STORE_REVIEW_URL)
                }
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "이용약관",
                style = MaterialTheme.typography.caption,
                textDecoration = TextDecoration.Underline,
                color = Color.Gray,
                modifier = Modifier.clickable { onNavigateToTerms() }
            )
            Text(
                text = "개인정보 처리방침",
                style = MaterialTheme.typography.caption,
                textDecoration = TextDecoration.Underline,
                color = Color.Gray,
                modifier = Modifier.clickable { onNavigateToPrivacy() }
            )
        }
    }
}
@Composable
private fun SettingSection(
    content: @Composable ColumnScope.() -> Unit
) {
    SettingCard {
        content()
    }
}
@Composable
fun InfoLinkItem(
    title: String,
    trailingText: String? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = title, color = Color.White, style = MaterialTheme.typography.bodyMedium)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                trailingText?.let {
                    Text(
                        text = it,
                        color = Color.Gray,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Icon(
                    painter = painterResource(Res.drawable.ic_caret_right),
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
