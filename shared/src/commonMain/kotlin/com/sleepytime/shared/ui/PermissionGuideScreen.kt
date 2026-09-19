package com.sleepytime.shared.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sleepytime.shared.enum_.PermissionType
import com.sleepytime.shared.platform.isAndroidPlatform
import com.sleepytime.shared.platform.rememberPermissionHandler
import com.sleepytime.shared.resources.Res
import com.sleepytime.shared.resources.ic_alarm_clock
import com.sleepytime.shared.resources.ic_check
import com.sleepytime.shared.resources.ic_microphone
import com.sleepytime.shared.resources.ic_motion
import com.sleepytime.shared.resources.ic_notification
import com.sleepytime.shared.resources.ic_smart_phone
import com.sleepytime.shared.resources.ic_volume_high
import com.sleepytime.shared.ui.onboarding.PermissionContract
import com.sleepytime.shared.ui.theme.caption
import com.sleepytime.shared.ui.theme.sectionTitle
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionGuideContent(
    permissionState: PermissionContract.State,
    onContinue: () -> Unit,
    onUpdatePermission: (PermissionType, Boolean) -> Unit,
    onBackClick: () -> Unit = {},
) {
    val permissionHandler = rememberPermissionHandler { type, granted ->
        onUpdatePermission(type, granted)
    }

    // 💡 초기 진입 시 현재 권한 상태 동기화
    LaunchedEffect(Unit) {
        onUpdatePermission(PermissionType.AUDIO, permissionHandler.checkPermissionState().audio)
        onUpdatePermission(PermissionType.NOTIFICATION, permissionHandler.checkPermissionState().notification)
        onUpdatePermission(PermissionType.ACTIVITY_RECOGNITION, permissionHandler.checkPermissionState().activity)
        onUpdatePermission(PermissionType.BATTERY_OPTIMIZATION, permissionHandler.checkPermissionState().batteryOptimizationIgnored)
    }

    val allGranted = permissionState.isAllGranted()
    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로가기",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (allGranted) "수면 측정을 시작할 준비가 됐어요" else "수면 측정을 위해 권한을 허용해주세요",
                    style = MaterialTheme.typography.sectionTitle,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = if (allGranted) "시작하기 전에 아래 내용을 확인해주세요" else "정확한 수면 측정을 위해 아래 권한이 필요해요",
                    style = MaterialTheme.typography.caption,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }

            // 💡 권한이 이미 모두 허용된 경우에는 권한 설정 UI를 숨기고 안내 가이드만 보여줍니다.
            if (!allGranted) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
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
                        icon = Res.drawable.ic_motion,
                        content = "정확한 수면 분석을 위해 신체 활동 권한이 필요합니다",
                        granted = permissionState.activity,
                        onClick = {
                            permissionHandler.request(PermissionType.ACTIVITY_RECOGNITION)
                        }
                    )
                    if (isAndroidPlatform) {
                        PermissionCard(
                            icon = Res.drawable.ic_check, // 배터리 관련 아이콘이 따로 없다면 ic_check 등 사용
                            content = "수면 중 측정이 끊기지 않도록 설정에서 배터리 사용량을 '제한 없음'으로 변경해 주세요.",
                            granted = permissionState.batteryOptimizationIgnored,
                            onClick = { permissionHandler.request(PermissionType.BATTERY_OPTIMIZATION) }
                        )
                    }
                }
            }

            GuideSection()

            Button(
                onClick = { if (allGranted) onContinue() },
                enabled = allGranted,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (allGranted) MaterialTheme.colorScheme.primary else Color.Gray
                )
            ) {
                Text(
                    text = if (allGranted) "시작하기" else "권한을 모두 허용해주세요",
                    style = MaterialTheme.typography.sectionTitle,
                    color = Color.White
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun GuideSection() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "숙면을 위한 안내",
            style = MaterialTheme.typography.sectionTitle,
            color = Color.White
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GuideTipCard(
                icon = Res.drawable.ic_smart_phone,
                text = "휴대폰은 매트리스 위, 몸에서 가까운 곳에 두세요"
            )
            GuideTipCard(
                icon = Res.drawable.ic_volume_high,
                text = "정확한 소음 측정을 위해 마이크가 가려지지 않게 해주세요"
            )
            GuideTipCard(
                icon = Res.drawable.ic_notification,
                text = "기상 알람이 제때 울리도록 무음 모드를 해제해주세요"
            )
            GuideTipCard(
                icon = Res.drawable.ic_alarm_clock,
                text = "측정 중에는 화면을 꺼두어도 괜찮아요. 앱이 계속 측정해요"
            )
        }
    }
}

@Composable
private fun GuideTipCard(
    icon: DrawableResource,
    text: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.08f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                modifier = Modifier.size(24.dp),
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                modifier = Modifier.weight(1f),
                text = text,
                style = MaterialTheme.typography.caption,
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
                .clickable { if (!granted) onClick() } // 💡 클릭 영역을 Row 전체로 확장하고 가드 적용
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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