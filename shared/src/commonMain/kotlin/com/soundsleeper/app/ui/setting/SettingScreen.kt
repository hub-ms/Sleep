package com.soundsleeper.app.ui.setting

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.soundsleeper.app.domain.model.User
import com.soundsleeper.app.enum_.AuthProvider
import com.soundsleeper.app.enum_.LoginButtonType
import com.soundsleeper.app.resources.Res
import com.soundsleeper.app.resources.common_privacy_policy
import com.soundsleeper.app.resources.common_terms_of_service
import com.soundsleeper.app.resources.ic_support
import com.soundsleeper.app.resources.ic_caret_right
import com.soundsleeper.app.resources.ic_language
import com.soundsleeper.app.resources.ic_paper
import com.soundsleeper.app.resources.ic_profile
import com.soundsleeper.app.resources.ic_sleep
import com.soundsleeper.app.resources.mypage_account_profile
import com.soundsleeper.app.resources.mypage_contact_support
import com.soundsleeper.app.resources.mypage_language
import com.soundsleeper.app.resources.mypage_leave_review
import com.soundsleeper.app.resources.mypage_open_source_license
import com.soundsleeper.app.resources.mypage_premium_cancel
import com.soundsleeper.app.resources.mypage_premium_subscribe
import com.soundsleeper.app.resources.mypage_sleep_alarm_setting
import com.soundsleeper.app.resources.mypage_title
import com.soundsleeper.app.ui.auth.AuthViewModel
import com.soundsleeper.app.ui.component.AuthMethod
import com.soundsleeper.app.ui.component.LoginButton
import com.soundsleeper.app.ui.theme.SleepTheme.primary
import com.soundsleeper.app.ui.theme.SleepTheme.surface
import com.soundsleeper.app.ui.theme.bodyText
import com.soundsleeper.app.ui.theme.caption
import com.soundsleeper.app.ui.theme.sectionTitle
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
@ExperimentalStdlibApi
fun SettingContent(
    authViewModel: AuthViewModel,
    settingViewModel: SettingViewModel,
    onSocialLogin: (AuthProvider) -> Unit,
    onNavigateToAccountSetting: () -> Unit,
    onNavigateToSleepSetting: () -> Unit,
    onNavigateToLicenseCredit: () -> Unit,
    onChatClick: () -> Unit,
    onNavigateToTerms: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToLanguage: () -> Unit,
) {
    val authState by authViewModel.state.collectAsState()
    val settingState by settingViewModel.state.collectAsState()
    val isGuest = authState.userType is User.AuthInfo.Guest

    val isPremium = authState.user?.isPremium == true

    LaunchedEffect(Unit) {
        if (!isGuest) authViewModel.refreshSocialProfile()
    }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize(),
        // 메뉴 카드는 위에서부터 붙여 쌓고, 남는 세로 공간은 아래 약관 링크 앞의 Spacer 가
        // 한 번에 가져간다. 예전에는 SpaceBetween 으로 카드 사이에 공간을 나눠 줬는데,
        // 카드 수가 줄자 메뉴 사이가 화면 높이만큼 벌어져 한 덩어리로 읽히지 않았다.
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(Res.string.mypage_title),
            style = MaterialTheme.typography.sectionTitle,
            color = Color.White,
        )
        if(isGuest) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LoginButton(
                    method = AuthMethod.Member(provider = AuthProvider.KAKAO),
                    shape = LoginButtonType.FullWidth,
                    onClick = {
                        onSocialLogin(AuthProvider.KAKAO)
                    }
                )
                LoginButton(
                    method = AuthMethod.Member(provider = AuthProvider.GOOGLE),
                    shape = LoginButtonType.FullWidth,
                    onClick = {
                        onSocialLogin(AuthProvider.GOOGLE)
                    }
                )
            }
        }
        SettingCard(modifier = Modifier.fillMaxWidth()) {
            if (!isGuest) {
                SettingItem(
                    icon = painterResource(Res.drawable.ic_profile),
                    title = stringResource(Res.string.mypage_account_profile),
                    onClick = onNavigateToAccountSetting
                )
            }
            SettingItem(
                icon = painterResource(Res.drawable.ic_sleep),
                title = stringResource(Res.string.mypage_sleep_alarm_setting),
                onClick = onNavigateToSleepSetting
            )
            SettingItem(
                icon = painterResource(Res.drawable.ic_language),
                title = stringResource(Res.string.mypage_language),
                onClick = onNavigateToLanguage
            )
            SettingItem(
                title = if (isPremium) stringResource(Res.string.mypage_premium_cancel)
                else stringResource(Res.string.mypage_premium_subscribe),
                onClick = {
                    settingViewModel.sendIntent(
                        if (isPremium) SettingContract.Intent.ClickManageSubscription
                        else SettingContract.Intent.ClickSubscribe
                    )
                }
            )
            SettingItem(
                title = stringResource(Res.string.mypage_leave_review),
                onClick = {
                    settingViewModel.sendIntent(SettingContract.Intent.ClickReview)
                }
            )
            SettingItem(
                icon = painterResource(Res.drawable.ic_support),
                title = stringResource(Res.string.mypage_contact_support),
                onClick = onChatClick
            )
            SettingItem(
                icon = painterResource(Res.drawable.ic_paper),
                title = stringResource(Res.string.mypage_open_source_license),
                onClick = onNavigateToLicenseCredit
            )
            SettingItem(
                title = "현재 버전 v${settingState.currentVersion}",
                subtitle = when {
                    settingState.isCheckingVersion -> "버전 확인 중"
                    settingState.isLatestVersion -> "최신 버전입니다"
                    else -> "업데이트 가능"
                },
                onClick = {
                    settingViewModel.sendIntent(
                        SettingContract.Intent.OpenStorePage
                    )
                }
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.Center
            ) {
                LegalTextLink(stringResource(Res.string.common_terms_of_service), onNavigateToTerms)
                LegalTextLink(stringResource(Res.string.common_privacy_policy), onNavigateToPrivacy)
            }
        }
    }
}

@Composable
private fun LegalTextLink(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyText,
        textDecoration = TextDecoration.Underline,
        color = Color.White,
        modifier = Modifier
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 4.dp)
    )
}

@ExperimentalStdlibApi
@Composable
fun SettingCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = surface.copy(alpha = 0.4f)
        ),
    ) {
        Column {
            content()
        }
    }
}
@Composable
fun SettingItem(
    title: String,
    onClick: () -> Unit,
    icon: Painter? = null,
    subtitle: String? = null,
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

            if (icon != null) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(Modifier.width(12.dp))
            }

            Column {

                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyText,
                    color = Color.White
                )

                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.caption,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }

        Icon(
            painter = painterResource(
                Res.drawable.ic_caret_right
            ),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.size(24.dp)
        )
    }
}
