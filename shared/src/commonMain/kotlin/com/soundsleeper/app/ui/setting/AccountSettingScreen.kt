@file:OptIn(ExperimentalStdlibApi::class)

package com.soundsleeper.app.ui.setting

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.soundsleeper.app.domain.model.User
import com.soundsleeper.app.enum_.AuthProvider
import com.soundsleeper.app.resources.account_alt_disable_notification_action
import com.soundsleeper.app.resources.account_alt_disable_notification_description
import com.soundsleeper.app.resources.account_alt_disable_notification_title
import com.soundsleeper.app.resources.account_alt_pause_action
import com.soundsleeper.app.resources.account_alt_pause_description
import com.soundsleeper.app.resources.account_alt_pause_title
import com.soundsleeper.app.resources.account_alt_to_guest_action
import com.soundsleeper.app.resources.account_alt_to_guest_description
import com.soundsleeper.app.resources.account_alt_to_guest_title
import com.soundsleeper.app.resources.account_change_image_description
import com.soundsleeper.app.resources.account_connect
import com.soundsleeper.app.resources.account_copy_description
import com.soundsleeper.app.resources.account_default_nickname
import com.soundsleeper.app.resources.account_disconnect
import com.soundsleeper.app.resources.account_disconnect_blocked_title
import com.soundsleeper.app.resources.account_edit_profile
import com.soundsleeper.app.resources.account_email_connected_title
import com.soundsleeper.app.resources.account_email_copied_message
import com.soundsleeper.app.resources.account_email_not_provided
import com.soundsleeper.app.resources.account_image_option_album
import com.soundsleeper.app.resources.account_image_option_camera
import com.soundsleeper.app.resources.account_image_option_default
import com.soundsleeper.app.resources.account_label_email
import com.soundsleeper.app.resources.account_logout
import com.soundsleeper.app.resources.account_nickname_label
import com.soundsleeper.app.resources.account_nickname_length_error
import com.soundsleeper.app.resources.account_nickname_rule_guide
import com.soundsleeper.app.resources.account_only_login_method
import com.soundsleeper.app.resources.account_withdraw
import com.soundsleeper.app.resources.account_withdraw_bullet_alarm_settings
import com.soundsleeper.app.resources.account_withdraw_bullet_music
import com.soundsleeper.app.resources.account_withdraw_bullet_profile
import com.soundsleeper.app.resources.account_withdraw_bullet_sleep_records
import com.soundsleeper.app.resources.account_withdraw_bullet_social
import com.soundsleeper.app.resources.account_withdraw_bullet_subscription
import com.soundsleeper.app.resources.account_withdraw_confirm_subtitle
import com.soundsleeper.app.resources.account_withdraw_confirm_title
import com.soundsleeper.app.resources.account_withdraw_continue
import com.soundsleeper.app.resources.account_withdraw_final
import com.soundsleeper.app.resources.account_withdraw_reason_inaccurate_data
import com.soundsleeper.app.resources.account_withdraw_reason_inconvenient
import com.soundsleeper.app.resources.account_withdraw_reason_other
import com.soundsleeper.app.resources.account_withdraw_reason_title
import com.soundsleeper.app.resources.account_withdraw_reason_too_many_notifications
import com.soundsleeper.app.resources.auth_provider_email
import com.soundsleeper.app.resources.auth_provider_google
import com.soundsleeper.app.resources.auth_provider_kakao
import com.soundsleeper.app.resources.common_back
import com.soundsleeper.app.resources.common_cancel
import com.soundsleeper.app.resources.common_confirm_short
import com.soundsleeper.app.resources.common_save
import com.soundsleeper.app.resources.Res
import com.soundsleeper.app.resources.ic_camera
import com.soundsleeper.app.resources.ic_caret_left
import com.soundsleeper.app.resources.ic_copy
import com.soundsleeper.app.resources.ic_edit
import com.soundsleeper.app.resources.ic_email
import com.soundsleeper.app.resources.ic_google
import com.soundsleeper.app.resources.ic_kakao
import com.soundsleeper.app.resources.ic_profile
import com.soundsleeper.app.ui.auth.AuthContract
import com.soundsleeper.app.ui.component.SelectableCard
import com.soundsleeper.app.ui.theme.SleepTheme.background
import com.soundsleeper.app.ui.theme.SleepTheme.primary
import com.soundsleeper.app.ui.theme.SleepTheme.surface
import com.soundsleeper.app.ui.theme.bodyHighlight
import com.soundsleeper.app.ui.theme.caption
import com.soundsleeper.app.ui.theme.sectionTitle
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
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
    onLastAuthMethodBlockDismiss: () -> Unit,
    onEmailConnectedMessageShown: () -> Unit = {},
) {
    val user = state.user
    val isGuest = !state.isAuthenticated || state.userType is User.AuthInfo.Guest

    // 수단이 하나뿐이면 해제 버튼 자체를 막는다. 진짜 방어선은 서버(409)지만,
    // 여기서 먼저 막아야 사용자가 눌렀다가 거부당하는 일을 겪지 않는다.
    val isLastAuthMethod = state.connectedProviders.size <= 1

    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .background(background)
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
                    text = user?.nickname ?: stringResource(Res.string.account_default_nickname),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (!isGuest) {
                    Icon(
                        modifier = Modifier.size(20.dp),
                        painter = painterResource(Res.drawable.ic_edit),
                        contentDescription = stringResource(Res.string.account_edit_profile),
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
                // 소셜 로그인은 제공처가 이메일을 주지 않으면 알 길이 없다(카카오는 이메일
                // 동의 항목이 승인·동의돼야 준다). 예전에는 서버가 가짜 주소를 지어내 채워
                // 넣어서 엉뚱한 주소가 자기 이메일인 것처럼 보였다. 지금은 비워 두고,
                // "연결 안 됨"보다 이유를 알 수 있는 문구로 안내한다.
                val emailValue = user?.email
                val emailCopiedMessage = stringResource(Res.string.account_email_copied_message)
                InfoItem(
                    label = stringResource(Res.string.account_label_email),
                    value = emailValue ?: stringResource(Res.string.account_email_not_provided),
                    onCopy = if (emailValue != null) {
                        {
                            scope.launch {
                                clipboardManager.setText(AnnotatedString(emailValue))
                                snackbarHostState.showSnackbar(emailCopiedMessage)
                            }
                        }
                    } else {
                        // 복사할 값이 없는데 복사 버튼이 동작하면 빈 문자열이 복사된다.
                        null
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
                        canDisconnect = !isLastAuthMethod,
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
                        containerColor = primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    onClick = { onLogoutClick() },
                ) {
                    Text(
                        text = stringResource(Res.string.account_logout),
                        style = MaterialTheme.typography.sectionTitle,
                        textAlign = TextAlign.Center,
                    )
                }
                TextButton(
                    onClick = onWithdrawClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(Res.string.account_withdraw), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
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
                colors = CardDefaults.cardColors(containerColor = surface),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Text(
                    text = data.visuals.message,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }

        // 서버가 마지막 로그인 수단 해제를 거부(409)했을 때의 차단 안내.
        // 선제 차단을 뚫고 온 경우(동시 요청, 오래된 화면 상태 등)만 뜬다.
        // 이메일 연결 성공 안내. 연결은 이 화면에서 시작하므로 결과도 여기서 알린다.
        state.emailConnectedMessage?.let { message ->
            AlertDialog(
                onDismissRequest = onEmailConnectedMessageShown,
                containerColor = surface,
                title = { Text(text = stringResource(Res.string.account_email_connected_title), color = MaterialTheme.colorScheme.onSurface) },
                text = { Text(text = message, color = Color.White) },
                confirmButton = {
                    TextButton(onClick = onEmailConnectedMessageShown) {
                        Text(stringResource(Res.string.common_confirm_short), color = primary)
                    }
                }
            )
        }

        state.lastAuthMethodBlockMessage?.let { blockMessage ->
            AlertDialog(
                onDismissRequest = onLastAuthMethodBlockDismiss,
                containerColor = surface,
                title = {
                    Text(
                        text = stringResource(Res.string.account_disconnect_blocked_title),
                        style = MaterialTheme.typography.sectionTitle,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = {
                    Text(
                        text = blockMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                confirmButton = {
                    TextButton(onClick = onLastAuthMethodBlockDismiss) {
                        Text(stringResource(Res.string.common_confirm_short), color = primary)
                    }
                }
            )
        }
    }
}

@Composable
/** onCopy 가 null 이면 복사할 값이 없다는 뜻이므로 복사 버튼 자체를 숨긴다. */
fun InfoItem(label: String, value: String, onCopy: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.White)
            Text(text = value, style = MaterialTheme.typography.bodyLarge, color = Color.White)
        }
        if (onCopy == null) return@Row
        IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
            Icon(
                painter = painterResource(Res.drawable.ic_copy),
                contentDescription = stringResource(Res.string.account_copy_description, label),
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun AccountConnectItem(
    provider: AuthProvider,
    isConnected: Boolean,
    canDisconnect: Boolean,
    showDivider: Boolean,
    onSocialConnect: (AuthProvider) -> Unit,
    onEmailConnect: () -> Unit,
    onSocialDisConnect: (AuthProvider) -> Unit,
    onEmailDisConnect: () -> Unit,
) {
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
                Column {
                    Text(
                        text = getAuthProviderDisplayName(provider),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    if (isConnected && !canDisconnect) {
                        Text(
                            text = stringResource(Res.string.account_only_login_method),
                            style = MaterialTheme.typography.caption,
                            color = Color.White
                        )
                    }
                }
            }

            if (isConnected) {
                val disconnectColor =
                    if (canDisconnect) MaterialTheme.colorScheme.error else Color.White
                Button(
                    modifier = Modifier
                        .border(2.dp, disconnectColor, RoundedCornerShape(16.dp))
                        .height(32.dp),
                    enabled = canDisconnect,   // 마지막 수단이면 누를 수 없다 (서버도 409로 막음)
                    onClick = {
                        when(provider) {
                            AuthProvider.KAKAO, AuthProvider.GOOGLE -> onSocialDisConnect(provider)
                            else -> onEmailDisConnect()
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error.copy(0.4f),
                        disabledContainerColor = surface,
                        disabledContentColor = Color.White
                    )
                ) {
                    Text(
                        text = stringResource(Res.string.account_disconnect),
                        style = MaterialTheme.typography.caption,
                        color = if (canDisconnect) MaterialTheme.colorScheme.onError else Color.White
                    )
                }
            } else {
                Button(
                    modifier = Modifier.height(32.dp),
                    onClick = {
                        // else 로 두면 APPLE 까지 이메일 연결 흐름을 타 버린다.
                        when (provider) {
                            AuthProvider.EMAIL -> onEmailConnect()
                            else -> onSocialConnect(provider)
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primary)
                ) {
                    Text(
                        text = stringResource(Res.string.account_connect),
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
        if (showDivider) {
            HorizontalDivider(
                color = surface,
                thickness = 1.dp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

/**
 * 닉네임 규칙. 서버(UserService.applyNickname)가 실제로 강제하는 것과 같은 값이어야 한다.
 * 한쪽만 바뀌면 사용자는 저장을 눌러본 뒤에야 거절당한 이유를 알게 된다.
 */
private const val NICKNAME_MIN_LENGTH = 2
private const val NICKNAME_MAX_LENGTH = 12
private const val NICKNAME_CHANGE_COOLDOWN_DAYS = 30

@Composable
fun ProfileEditContent(
    user: User?,
    isImageUploading: Boolean,
    message: String?,
    onMessageShown: () -> Unit,
    onBackClick: () -> Unit,
    onUpdateNickName: (String) -> Unit,
    onSaveClick: (String) -> Unit,
    onImageChangeClick: (Int) -> Unit // 0: Default, 1: Album, 2: Camera
) {
    var nickname by remember(user?.nickname) { mutableStateOf(user?.nickname ?: "") }
    var showImageOptions by remember { mutableStateOf(false) }

    // 서버가 거절할 입력을 저장 버튼을 눌러본 뒤에야 알게 되면 왕복이 한 번 더 생긴다.
    // UserService.applyNickname 과 같은 규칙을 화면에서 미리 본다.
    val trimmedNickname = nickname.trim()
    val isLengthValid = trimmedNickname.length in NICKNAME_MIN_LENGTH..NICKNAME_MAX_LENGTH
    val isChanged = trimmedNickname != (user?.nickname ?: "")
    val showLengthError = nickname.isNotEmpty() && !isLengthValid

    val snackbarHostState = remember { SnackbarHostState() }
    // 업로드 실패가 조용히 묻히면 "선택했는데 아무 일도 안 일어난다"로 보인다.
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            onMessageShown()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
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
                    contentDescription = stringResource(Res.string.common_back),
                    tint = Color.White
                )
            }
            Text(
                text = stringResource(Res.string.account_edit_profile),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
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
                if (isImageUploading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }
            Surface(
                modifier = Modifier
                    .size(36.dp)
                    .clickable { showImageOptions = true },
                shape = CircleShape,
                color = primary,
                shadowElevation = 4.dp
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_camera),
                    contentDescription = stringResource(Res.string.account_change_image_description),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(8.dp)
                )

                DropdownMenu(
                    expanded = showImageOptions,
                    onDismissRequest = { showImageOptions = false },
                    modifier = Modifier.background(surface)
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.account_image_option_default), color = MaterialTheme.colorScheme.onSurface) },
                        onClick = {
                            showImageOptions = false
                            onImageChangeClick(0)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.account_image_option_album), color = MaterialTheme.colorScheme.onSurface) },
                        onClick = {
                            showImageOptions = false
                            onImageChangeClick(1)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.account_image_option_camera), color = MaterialTheme.colorScheme.onSurface) },
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
            label = { Text(stringResource(Res.string.account_nickname_label)) },
            modifier = Modifier.fillMaxWidth(),
            isError = showLengthError,
            supportingText = {
                Text(
                    text = if (showLengthError) {
                        stringResource(Res.string.account_nickname_length_error, NICKNAME_MIN_LENGTH, NICKNAME_MAX_LENGTH)
                    } else {
                        stringResource(Res.string.account_nickname_rule_guide, NICKNAME_MIN_LENGTH, NICKNAME_MAX_LENGTH, NICKNAME_CHANGE_COOLDOWN_DAYS)
                    },
                    style = MaterialTheme.typography.caption,
                    color = if (showLengthError) MaterialTheme.colorScheme.error else Color.White
                )
            },
            // 예전에는 입력 글자색이 흰색으로 고정돼 있었다. 라이트 테마의 흰 입력창 위에서는
            // 쓴 글자가 보이지 않는 실제 버그였다.
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedLabelColor = primary,
                unfocusedLabelColor = Color.White
            ),
            singleLine = true
        )

        // 저장은 화면 아래 폭 전체를 쓰는 버튼으로 둔다. 상단 우측의 작은 "저장" 글자는
        // 누를 곳이 좁은 데다, 바로 위 뒤로가기 꺾쇠와 역할이 반대인데 거리가 가까웠다.
        Spacer(Modifier.weight(1f))

        Button(
            onClick = { onSaveClick(trimmedNickname) },
            enabled = isLengthValid && isChanged && !isImageUploading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(stringResource(Res.string.common_save))
        }
    }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        ) { data ->
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = surface),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Text(
                    text = data.visuals.message,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * 탈퇴 이유별로 제안할 대안.
 *
 * 각 항목에 대응하는 인텐트는 AuthViewModel에 이미 구현돼 있었지만 어떤 화면도 호출하지 않아
 * 사실상 잠들어 있었다. 이 화면이 그 인텐트들을 실제로 이어준다.
 */
enum class WithdrawAlternative(
    val titleRes: StringResource,
    val descriptionRes: StringResource,
    val actionLabelRes: StringResource,
) {
    PAUSE(
        titleRes = Res.string.account_alt_pause_title,
        descriptionRes = Res.string.account_alt_pause_description,
        actionLabelRes = Res.string.account_alt_pause_action
    ),
    DISABLE_NOTIFICATION(
        titleRes = Res.string.account_alt_disable_notification_title,
        descriptionRes = Res.string.account_alt_disable_notification_description,
        actionLabelRes = Res.string.account_alt_disable_notification_action
    ),
    TO_GUEST(
        titleRes = Res.string.account_alt_to_guest_title,
        descriptionRes = Res.string.account_alt_to_guest_description,
        actionLabelRes = Res.string.account_alt_to_guest_action
    ),
}

/**
 * 탈퇴 이유의 정체성(key)과 화면에 보일 문구(labelRes)를 분리한다. 예전에는 화면에 적힌
 * 한국어 문구 그 자체로 선택 상태를 비교했는데, 영어 로케일에서는 그 문구가 달라지므로
 * 선택 비교가 깨진다.
 *
 * 대안이 없는 이유(기타)는 alternative 가 null. "더 이상 앱이 필요하지 않아요"는
 * "사용하기 불편해요"와 똑같이 PAUSE 로 이어져 제안할 내용이 겹쳐서 목록에서 뺐다.
 */
data class WithdrawReasonOption(val key: String, val labelRes: StringResource, val alternative: WithdrawAlternative?)

private val withdrawReasons: List<WithdrawReasonOption> = listOf(
    WithdrawReasonOption("inconvenient", Res.string.account_withdraw_reason_inconvenient, WithdrawAlternative.PAUSE),
    WithdrawReasonOption("too_many_notifications", Res.string.account_withdraw_reason_too_many_notifications, WithdrawAlternative.DISABLE_NOTIFICATION),
    WithdrawReasonOption("inaccurate_data", Res.string.account_withdraw_reason_inaccurate_data, WithdrawAlternative.TO_GUEST),
    WithdrawReasonOption("other", Res.string.account_withdraw_reason_other, null),
)

/**
 * 회원탈퇴 1단계. 이유를 고르면 그 자리에서 맞춤 대안을 펼쳐 보여준다.
 *
 * 예전에는 이유를 누르는 즉시 다음 단계로 넘어가서, 붙잡을 기회 없이 이탈이 그대로 진행됐다.
 */
@Composable
fun WithdrawalReasonContent(
    onReasonSelected: (String) -> Unit,
    onAlternativeSelected: (WithdrawAlternative) -> Unit,
    onBackClick: () -> Unit
) {
    var selectedReasonKey by remember { mutableStateOf<String?>(null) }
    val selectedAlternative = withdrawReasons.firstOrNull { it.key == selectedReasonKey }?.alternative

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        // 상단 꺾쇠는 뺐다. 뒤로 가는 길은 하단 "취소" 버튼과 시스템 뒤로가기로 남아 있다.
        //
        // headlineMedium 은 sleepTypography() 가 재정의하지 않아 Material 기본값(28sp)으로
        // 떨어지고 Pretendard 도 잃는다. 그 크기로는 이 문장이 두 줄로 접혀 제목이 화면
        // 윗부분을 다 먹었다. 테마가 정의한 sectionTitle(24sp)로 낮추고 한 줄로 고정한다.
        Text(
            stringResource(Res.string.account_withdraw_reason_title),
            style = MaterialTheme.typography.sectionTitle,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier.padding(top = 16.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            withdrawReasons.forEach { option ->
                SelectableCard(
                    text = stringResource(option.labelRes),
                    isSelected = selectedReasonKey == option.key,
                    onClick = { selectedReasonKey = option.key }
                )
            }
        }
        selectedAlternative?.let { alternative ->
            WithdrawAlternativeSection(
                alternatives = listOf(alternative),
                onAlternativeSelected = onAlternativeSelected,
            )
        }
        WithdrawActionRow(
            confirmLabel = stringResource(Res.string.account_withdraw_continue),
            confirmEnabled = selectedReasonKey != null,
            onCancel = onBackClick,
            onConfirm = { selectedReasonKey?.let(onReasonSelected) }
        )
    }
}
@Composable
private fun WithdrawAlternativeSection(
    alternatives: List<WithdrawAlternative>,
    onAlternativeSelected: (WithdrawAlternative) -> Unit,
) {
    if (alternatives.isEmpty()) return

    Column(
        modifier = Modifier.padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        alternatives.forEach { alternative ->
            WithdrawAlternativeCard(
                alternative = alternative,
                onClick = { onAlternativeSelected(alternative) }
            )
        }
    }
}

@Composable
private fun WithdrawAlternativeCard(
    alternative: WithdrawAlternative,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(
                color = surface,
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(alternative.titleRes),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                stringResource(alternative.descriptionRes),
                color = Color.White,
                style = MaterialTheme.typography.bodySmall
            )
            TextButton(
                onClick = onClick,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(alternative.actionLabelRes), color = primary)
            }
        }
    }
}

/** 탈퇴 화면 하단 공통 버튼 행. 안전한 선택(취소)을 왼쪽에 둔다. */
@Composable
private fun WithdrawActionRow(
    confirmLabel: String,
    confirmEnabled: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, surface)
        ) {
            Text(
                text = stringResource(Res.string.common_cancel),
                style = MaterialTheme.typography.bodyHighlight,
                color = Color.White
            )
        }
        Button(
            onClick = onConfirm,
            enabled = confirmEnabled,
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(confirmLabel, color = MaterialTheme.colorScheme.onError, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun WithdrawalConfirmDetailContent(
    onWithdrawClick: () -> Unit,
    onBackClick: () -> Unit,
    errorMessage: String? = null,
    onErrorShown: () -> Unit = {},
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .background(background)
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                painter = painterResource(Res.drawable.ic_caret_left),
                contentDescription = stringResource(Res.string.common_back),
                tint = Color.White
            )
        }

        Text(
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            text = stringResource(Res.string.account_withdraw_confirm_title),
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
        )
        Text(
            stringResource(Res.string.account_withdraw_confirm_subtitle),
            color = Color.White,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        SettingCard {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 되돌릴 수 없는 삭제이므로, 무엇이 사라지는지 항목 이름만 나열하지 말고
                // 구체적으로 알려준다.
                BulletItem(stringResource(Res.string.account_withdraw_bullet_sleep_records))
                BulletItem(stringResource(Res.string.account_withdraw_bullet_alarm_settings))
                BulletItem(stringResource(Res.string.account_withdraw_bullet_social))
                BulletItem(stringResource(Res.string.account_withdraw_bullet_music))
                BulletItem(stringResource(Res.string.account_withdraw_bullet_profile))
                BulletItem(stringResource(Res.string.account_withdraw_bullet_subscription))
            }
        }

        Spacer(Modifier.weight(1f))

        // 탈퇴 요청이 실패했을 때 사용자가 그 사실을 알 수 있는 유일한 자리다.
        errorMessage?.let { message ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clickable { onErrorShown() },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        WithdrawActionRow(
            confirmLabel = stringResource(Res.string.account_withdraw_final),
            confirmEnabled = true,
            onCancel = onBackClick,
            onConfirm = onWithdrawClick
        )
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
                .background(Color.White, CircleShape)
        )
        Text(text, color = Color.White, style = MaterialTheme.typography.bodyMedium)
    }
}

fun getAuthProviderIconRes(provider: AuthProvider) = when (provider) {
    AuthProvider.KAKAO -> Res.drawable.ic_kakao
    AuthProvider.GOOGLE -> Res.drawable.ic_google
    AuthProvider.EMAIL -> Res.drawable.ic_email
}

@Composable
fun getAuthProviderDisplayName(provider: AuthProvider): String {
    return when (provider) {
        AuthProvider.KAKAO -> stringResource(Res.string.auth_provider_kakao)
        AuthProvider.GOOGLE -> stringResource(Res.string.auth_provider_google)
        AuthProvider.EMAIL -> stringResource(Res.string.auth_provider_email)
    }
}
