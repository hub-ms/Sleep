package com.sleepytime.shared.ui.setting

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.sleepytime.shared.domain.model.User
import com.sleepytime.shared.enum_.AuthProvider
import com.sleepytime.shared.resources.Res
import com.sleepytime.shared.resources.ic_apple
import com.sleepytime.shared.resources.ic_caret_right
import com.sleepytime.shared.resources.ic_email
import com.sleepytime.shared.resources.ic_google
import com.sleepytime.shared.resources.ic_kakao
import com.sleepytime.shared.ui.auth.AuthContract
import com.sleepytime.shared.ui.auth.WithdrawStep
import org.jetbrains.compose.resources.painterResource

@Composable
fun AccountSettingContent(
    state: AuthContract.State,
    onSocialClick: (AuthProvider) -> Unit,
    onEmailClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onWithdrawClick: () -> Unit,
    onResetClick: () -> Unit,
    onWithdrawConfirm: () -> Unit,
    onWithdrawCancel: () -> Unit
) {
    val userType = state.userType
    val isGuest = !state.isAuthenticated || state.userType is User.AuthInfo.Guest

    val allProviders = listOf(
        AuthProvider.KAKAO,
        AuthProvider.GOOGLE,
        AuthProvider.APPLE
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7F7))
            .padding(16.dp)
    ) {
        Text(
            text = "계정",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )

        Spacer(Modifier.height(8.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White
        ) {
            Column {
                allProviders.forEachIndexed { index, provider ->
                    val isConnected = !isGuest && state.connectedProviders.contains(provider)
                    val isPrimary = !isGuest && (provider == state.primaryProvider)

                    AccountConnectItem(
                        uiState = AccountItemUiState.Social(
                            text = getAuthProviderDisplayName(provider),
                            icon = painterResource(getAuthProviderIconRes(provider)),
                            isConnected = isConnected
                        ),
                        isGuest = isGuest,
                        isConnected = isConnected,
                        isPrimary = isPrimary,
                        showDivider = index != allProviders.lastIndex,
                        onClick = { onSocialClick(provider) }
                    )
                }
                val isEmailConnected = !isGuest && state.isEmailConnected
                AccountConnectItem(
                    uiState = AccountItemUiState.Email(
                        text = "이메일",
                        icon = painterResource(Res.drawable.ic_email)
                    ),
                    isGuest = isGuest,
                    isConnected = isEmailConnected,
                    isPrimary = !isGuest && (state.primaryProvider == AuthProvider.EMAIL),
                    showDivider = false,
                    onClick = onEmailClick
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "기타",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )

        Spacer(Modifier.height(8.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White
        ) {
            OtherSection(
                isGuest = isGuest,
                onLogoutClicked = onLogoutClick,
                onWithdrawClicked = onWithdrawClick,
                onResetClicked = onResetClick
            )
        }

        if (state.withdrawStep == WithdrawStep.WARNING) {
            WithdrawWarningDialog(
                onConfirm = onWithdrawConfirm,
                onCancel = onWithdrawCancel
            )
        }
    }
}

@Composable
fun AccountConnectItem(
    uiState: AccountItemUiState,
    onClick: () -> Unit,
    isGuest: Boolean,
    isConnected: Boolean,
    showDivider: Boolean,
    isPrimary: Boolean = false
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = when (uiState) {
                    is AccountItemUiState.Social -> uiState.icon
                    is AccountItemUiState.Email -> uiState.icon
                },
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )

            Spacer(Modifier.width(10.dp))

            Text(
                text = when (uiState) {
                    is AccountItemUiState.Social -> uiState.text
                    is AccountItemUiState.Email -> uiState.text
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color.Black
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                // 🚀 게스트가 아니며 대표 플랫폼일 때 라벨 노출
                if (isPrimary && !isGuest) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFEFE5FF)
                    ) {
                        Text(
                            text = "대표",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = Color(0xFF7B4DFF),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }

                Text(
                    text = when {
                        isGuest -> "로그인"
                        isConnected -> "관리"
                        else -> "연결"
                    },
                    // 연결 상태에 따른 색상 구분 가시화
                    color = if (isConnected) Color.Gray else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Icon(
                painter = painterResource(Res.drawable.ic_caret_right),
                contentDescription = null,
                tint = Color.Gray
            )
        }

        if (showDivider) {
            HorizontalDivider(
                color = Color(0xFFEAEAEA),
                thickness = 1.dp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
fun OtherSection(
    isGuest: Boolean,
    onLogoutClicked: () -> Unit,
    onWithdrawClicked: () -> Unit,
    onResetClicked: () -> Unit
) {
    Column {
        if (isGuest) {
            OtherItem(
                text = "데이터 초기화",
                isDestructive = false,
                onClick = onResetClicked
            )
        } else {
            OtherItem(
                text = "로그아웃",
                isDestructive = false,
                onClick = onLogoutClicked
            )
            HorizontalDivider(
                color = Color(0xFFEAEAEA),
                thickness = 1.dp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            OtherItem(
                text = "회원탈퇴",
                isDestructive = true,
                onClick = onWithdrawClicked
            )
        }
    }
}

@Composable
fun OtherItem(
    text: String,
    isDestructive: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            color = if (isDestructive) Color.Red else Color.Black,
            style = MaterialTheme.typography.bodyLarge
        )

        Icon(
            painter = painterResource(Res.drawable.ic_caret_right),
            contentDescription = null,
            tint = Color.Gray
        )
    }
}

@Composable
fun WithdrawWarningDialog(
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Dialog(onDismissRequest = onCancel) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF1E2235),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .widthIn(min = 280.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "정말 탈퇴하시겠어요?",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "탈퇴하면 모든 데이터가 삭제되고 복구가 불가능합니다",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(24.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onCancel() },
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF2A2F45)
                    ) {
                        Text(
                            text = "머무르기",
                            modifier = Modifier.padding(12.dp),
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onConfirm() },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = "탈퇴하기",
                            modifier = Modifier.padding(12.dp),
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

fun getAuthProviderIconRes(provider: AuthProvider) = when (provider) {
    AuthProvider.KAKAO -> Res.drawable.ic_kakao
    AuthProvider.GOOGLE -> Res.drawable.ic_google
    AuthProvider.APPLE -> Res.drawable.ic_apple
    AuthProvider.EMAIL -> Res.drawable.ic_email
}

fun getAuthProviderDisplayName(provider: AuthProvider): String {
    return when (provider) {
        AuthProvider.KAKAO -> "카카오"
        AuthProvider.GOOGLE -> "구글"
        AuthProvider.APPLE -> "애플"
        AuthProvider.EMAIL -> "이메일"
    }
}

sealed class AccountItemUiState {
    data class Social(
        val text: String,
        val icon: Painter,
        val isConnected: Boolean
    ) : AccountItemUiState()

    data class Email(
        val text: String,
        val icon: Painter
    ) : AccountItemUiState()
}
