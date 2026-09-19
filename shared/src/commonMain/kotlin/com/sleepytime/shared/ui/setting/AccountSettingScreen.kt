package com.sleepytime.shared.ui.setting

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.sleepytime.shared.domain.model.User
import com.sleepytime.shared.enum_.AuthProvider
import com.sleepytime.shared.resources.Res
import com.sleepytime.shared.resources.button_sleep_start
import com.sleepytime.shared.resources.ic_apple
import com.sleepytime.shared.resources.ic_camera
import com.sleepytime.shared.resources.ic_caret_left
import com.sleepytime.shared.resources.ic_caret_right
import com.sleepytime.shared.resources.ic_copy
import com.sleepytime.shared.resources.ic_email
import com.sleepytime.shared.resources.ic_google
import com.sleepytime.shared.resources.ic_kakao
import com.sleepytime.shared.resources.ic_pencil
import com.sleepytime.shared.resources.ic_profile
import com.sleepytime.shared.ui.auth.AuthContract
import com.sleepytime.shared.ui.component.AuthMethod
import com.sleepytime.shared.ui.component.toUi
import com.sleepytime.shared.ui.theme.bodyText
import com.sleepytime.shared.ui.theme.caption
import com.sleepytime.shared.ui.theme.sectionTitle
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun AccountSettingContent(
    state: AuthContract.State,
    onEditProfileClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onWithdrawClick: () -> Unit,
    onSocialConnect: (AuthProvider) -> Unit,
    onEmailConnect: () -> Unit,
    onSocialDisConnect: (AuthProvider) -> Unit,
    onEmailDisConnect: () -> Unit,
) {
    val user = state.user
    val isGuest = !state.isAuthenticated || state.userType is User.AuthInfo.Guest
    val primaryProvider = (state.userType as? User.AuthInfo.Member)?.authProvider

    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. 프로필 헤더
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isGuest) {
                    Image(
                        painter = painterResource(Res.drawable.ic_profile),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    AsyncImage(
                        model = user?.profileImageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                        placeholder = painterResource(Res.drawable.ic_profile),
                        error = painterResource(Res.drawable.ic_profile)
                    )
                }
            }

            Row(
                modifier = Modifier.clickable { if (!isGuest) onEditProfileClick() },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = user?.nickname ?: "사용자",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (!isGuest) {
                    Icon(
                        modifier = Modifier.size(20.dp),
                        painter = painterResource(Res.drawable.ic_pencil),
                        contentDescription = "프로필 수정",
                        tint = Color.White
                    )
                }
            }

            // 2. 기본 정보 카드 (복사 기능)
            SettingCard {
//                InfoItem(
//                    label = "닉네임",
//                    value = user?.nickname ?: "닉네임 없음",
//                    onCopy = {
//                        clipboardManager.setText(AnnotatedString(user?.nickname.orEmpty()))
//                        scope.launch { snackbarHostState.showSnackbar("닉네임이 복사되었습니다.") }
//                    }
//                )
                InfoItem(
                    label = "이메일",
                    value = user?.email ?: "연결 안 됨",
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(user?.email.orEmpty()))
                        scope.launch { snackbarHostState.showSnackbar("이메일 주소가 복사되었습니다.") }
                    }
                )
            }
            SettingCard {
                val providers = AuthProvider.entries
                providers.forEachIndexed { index, provider ->
                    val isConnected = provider in state.connectedProviders
                    AccountConnectItem(
                        provider = provider,
                        isConnected = isConnected,
                        isPrimary = provider == primaryProvider,
                        isGuest = isGuest,
                        showDivider = index != providers.lastIndex,
                        onSocialConnect = {
                            onSocialConnect(provider)
                        },
                        onEmailConnect = {
                            onEmailConnect()
                        },
                        onSocialDisConnect = {
                            onSocialDisConnect(provider)
                        },
                        onEmailDisConnect = {
                            onEmailDisConnect()
                        },
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    ),
                    onClick = { onLogoutClick() },
                ) {
                    Text(
                        text = "로그아웃",
                        style = MaterialTheme.typography.sectionTitle,
                        textAlign = TextAlign.Center,
                    )
                }
                TextButton(
                    onClick = onWithdrawClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("회원탈퇴", color = Color(0xFFE24B4A), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // 통일된 스낵바 디자인
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        ) { data ->
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2F45)),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Text(
                    text = data.visuals.message,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun InfoItem(label: String, value: String, onCopy: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text(text = value, style = MaterialTheme.typography.bodyLarge, color = Color.White)
        }
        IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
            Icon(
                painter = painterResource(Res.drawable.ic_copy),
                contentDescription = "$label 복사",
                tint = Color.Gray,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun AccountConnectItem(
    provider: AuthProvider,
    isConnected: Boolean,
    isPrimary: Boolean,
    isGuest: Boolean,
    showDivider: Boolean,
    onSocialConnect: (AuthProvider) -> Unit,
    onEmailConnect: () -> Unit,
    onSocialDisConnect: (AuthProvider) -> Unit,
    onEmailDisConnect: () -> Unit,
) {
    val iconColor = when(provider) {
        AuthProvider.KAKAO -> Color(0xFFFEE500)
        AuthProvider.GOOGLE -> Color.Transparent
        AuthProvider.APPLE -> Color.White
        AuthProvider.EMAIL -> Color.White
    }
    val method = AuthMethod.Member(provider)
    val ui = method.toUi()
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    modifier = Modifier.size(24.dp),
                    painter = painterResource(getAuthProviderIconRes(provider)),
                    contentDescription = null,
                )
                Text(
                    text = getAuthProviderDisplayName(provider),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
                if (isPrimary && !isGuest) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Text(
                            text = "대표",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (isConnected) {
                Button(
                    modifier = Modifier.height(32.dp),
                    onClick = {
                        when(provider) {
                            AuthProvider.KAKAO, AuthProvider.GOOGLE, AuthProvider.APPLE -> onSocialDisConnect(provider)
                            else -> onEmailDisConnect()
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        text = "연결 해제",
                        style = MaterialTheme.typography.caption,
                        color = Color.White
                    )
                }
            } else {
                Button(
                    modifier = Modifier.height(32.dp),
                    onClick = {
                        when(provider) {
                            AuthProvider.KAKAO, AuthProvider.GOOGLE, AuthProvider.APPLE -> onSocialConnect(provider)
                            else -> onEmailConnect()
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        text = "연결하기",
                        style = MaterialTheme.typography.caption,
                        color = Color.White
                    )
                }
            }
        }
        if (showDivider) {
            HorizontalDivider(
                color = Color(0xFF2A2C3D),
                thickness = 1.dp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
fun ProfileEditContent(
    user: User?,
    onBackClick: () -> Unit,
    onUpdateNickName: (String) -> Unit,
    onUpdateEmail: (String) -> Unit,
    onSaveClick: (String, String) -> Unit,
    onImageChangeClick: (Int) -> Unit // 0: Default, 1: Album, 2: Camera
) {
    var nickname by remember(user?.nickname) { mutableStateOf(user?.nickname ?: "") }
    var email by remember(user?.email) { mutableStateOf(user?.email ?: "") }
    var showImageOptions by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    painter = painterResource(Res.drawable.ic_caret_left),
                    contentDescription = "뒤로가기",
                    tint = Color.White
                )
            }
            Text(
                text = "프로필 수정",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                onSaveClick(nickname, email)
            }) {
                Text("저장", color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(Modifier.height(32.dp))

        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = user?.profileImageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(Res.drawable.ic_profile),
                    error = painterResource(Res.drawable.ic_profile)
                )
            }
            Surface(
                modifier = Modifier
                    .size(36.dp)
                    .clickable { showImageOptions = true },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 4.dp
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_camera),
                    contentDescription = "이미지 변경",
                    tint = Color.Black,
                    modifier = Modifier.padding(8.dp)
                )

                DropdownMenu(
                    expanded = showImageOptions,
                    onDismissRequest = { showImageOptions = false },
                    modifier = Modifier.background(Color(0xFF1E2235))
                ) {
                    DropdownMenuItem(
                        text = { Text("기본 이미지로 변경", color = Color.White) },
                        onClick = {
                            showImageOptions = false
                            onImageChangeClick(0)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("앨범에서 선택", color = Color.White) },
                        onClick = {
                            showImageOptions = false
                            onImageChangeClick(1)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("사진 찍기", color = Color.White) },
                        onClick = {
                            showImageOptions = false
                            onImageChangeClick(2)
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(48.dp))

        OutlinedTextField(
            value = nickname,
            onValueChange = { 
                nickname = it
                onUpdateNickName(it)
            },
            label = { Text("닉네임") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = Color.Gray
            ),
            singleLine = true
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { 
                email = it
                onUpdateEmail(it)
            },
            label = { Text("이메일 주소") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = Color.Gray
            ),
            singleLine = true
        )
    }
}

@Composable
fun WithdrawalReasonContent(
    onReasonSelected: (String) -> Unit,
    onBackClick: () -> Unit
) {
    val reasons = listOf(
        "사용하기 불편해요",
        "알림이 너무 많이 와요",
        "데이터가 정확하지 않은 것 같아요",
        "더 이상 앱이 필요하지 않아요",
        "기타"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                painter = painterResource(Res.drawable.ic_caret_left),
                contentDescription = "뒤로가기",
                tint = Color.White
            )
        }

        Text(
            "회원탈퇴를 하시려는\n이유가 무엇인가요?",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            modifier = Modifier.padding(vertical = 24.dp)
        )

        reasons.forEach { reason ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onReasonSelected(reason) },
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.05f)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(reason, color = Color.White)
                    Icon(
                        painter = painterResource(Res.drawable.ic_caret_right),
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun WithdrawalConfirmDetailContent(
    onWithdrawClick: () -> Unit,
    onBackClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                painter = painterResource(Res.drawable.ic_caret_left),
                contentDescription = "뒤로가기",
                tint = Color.White
            )
        }

        Text(
            "정말 탈퇴하시겠어요?",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
        )
        Text(
            "탈퇴 시 아래의 정보가 모두 사라집니다.",
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        SettingCard {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BulletItem("모든 수면 기록 데이터")
                BulletItem("설정한 알람 및 알림 정보")
                BulletItem("연결된 소셜 계정 정보")
                BulletItem("저장된 즐겨찾기 음악")
            }
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onWithdrawClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE24B4A)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("회원탈퇴 하기", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun BulletItem(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(4.dp)
                .background(Color.Gray, CircleShape)
        )
        Text(text, color = Color.LightGray, style = MaterialTheme.typography.bodyMedium)
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
