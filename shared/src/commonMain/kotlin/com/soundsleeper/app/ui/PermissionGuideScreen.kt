package com.soundsleeper.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.window.Dialog
import com.soundsleeper.app.enum_.PermissionType
import com.soundsleeper.app.platform.isAndroidPlatform
import com.soundsleeper.app.platform.rememberIsCharging
import com.soundsleeper.app.platform.rememberPermissionHandler
import com.soundsleeper.app.resources.Res
import com.soundsleeper.app.resources.common_back
import com.soundsleeper.app.resources.ic_check
import com.soundsleeper.app.resources.ic_microphone
import com.soundsleeper.app.resources.ic_notification
import com.soundsleeper.app.resources.permission_card_activity
import com.soundsleeper.app.resources.permission_card_battery
import com.soundsleeper.app.resources.permission_card_microphone
import com.soundsleeper.app.resources.permission_card_notification
import com.soundsleeper.app.resources.permission_explain_confirm
import com.soundsleeper.app.resources.permission_explain_subtitle
import com.soundsleeper.app.resources.permission_explain_title
import com.soundsleeper.app.resources.permission_guide_charger_tip
import com.soundsleeper.app.resources.permission_guide_charger_unplugged_warning
import com.soundsleeper.app.resources.permission_guide_charging_tip
import com.soundsleeper.app.resources.permission_guide_reopen_hint
import com.soundsleeper.app.resources.permission_guide_start
import com.soundsleeper.app.resources.permission_guide_title
import com.soundsleeper.app.ui.component.BedsideChargerIllustration
import com.soundsleeper.app.ui.component.ChargingPhoneIllustration
import com.soundsleeper.app.ui.onboarding.PermissionContract
import com.soundsleeper.app.ui.theme.SleepTheme.background
import com.soundsleeper.app.ui.theme.SleepTheme.primary
import com.soundsleeper.app.ui.theme.caption
import com.soundsleeper.app.ui.theme.sectionTitle
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionGuideContent(
    onContinue: () -> Unit,
    onUpdatePermission: (PermissionType, Boolean) -> Unit,
    onBackClick: () -> Unit = {},
) {
    val permissionHandler = rememberPermissionHandler { type, granted ->
        onUpdatePermission(type, granted)
    }

    LaunchedEffect(Unit) {
        onUpdatePermission(PermissionType.AUDIO, permissionHandler.checkPermissionState().audio)
        onUpdatePermission(PermissionType.NOTIFICATION, permissionHandler.checkPermissionState().notification)
        onUpdatePermission(PermissionType.ACTIVITY_RECOGNITION, permissionHandler.checkPermissionState().activity)
        onUpdatePermission(PermissionType.BATTERY_OPTIMIZATION, permissionHandler.checkPermissionState().batteryOptimizationIgnored)
    }

    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.common_back),
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = background
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
            GuideSection()

            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = primary
                )
            ) {
                Text(
                    text = stringResource(Res.string.permission_guide_start),
                    style = MaterialTheme.typography.sectionTitle,
                    color = Color.White
                )
            }
            // 이 화면은 첫 측정 때 한 번만 나온다. 다시 볼 방법을 알려 두지 않으면
            // 두 번째 측정부터는 사라진 것처럼 보인다.
            Text(
                text = stringResource(Res.string.permission_guide_reopen_hint),
                style = MaterialTheme.typography.caption,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun GuideSection() {
    // 지금 충전기가 빠져 있다면 일러스트만으로는 부족하다. 실제 상태를 보고 경고를 덧붙인다.
    // 권한이 아니라 안내이므로 아래 "시작하기" 버튼의 활성 조건에는 넣지 않는다.
    val isCharging = rememberIsCharging()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(Res.string.permission_guide_title),
            style = MaterialTheme.typography.sectionTitle,
            color = Color.White
        )
        if (!isCharging) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
            ) {
                Text(
                    text = stringResource(Res.string.permission_guide_charger_unplugged_warning),
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }
        // 팁 카드 네 장을 읽히는 대신 가장 중요한 한 가지를 애니메이션으로 보여준다.
        // 이미 충전기를 꽂은 사람에게 "꽂으세요"를 또 보여줄 이유는 없으므로,
        // 그때는 다음으로 중요한 것(폰을 침대 옆에 올려 두기)으로 바꾼다.
        if (isCharging) {
            BedsideChargerIllustration(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .aspectRatio(0.85f)
            )
            Text(
                text = stringResource(Res.string.permission_guide_charging_tip),
                style = MaterialTheme.typography.caption,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        } else {
            ChargingPhoneIllustration(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .aspectRatio(0.85f)
            )
            Text(
                text = stringResource(Res.string.permission_guide_charger_tip),
                style = MaterialTheme.typography.caption,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
    }
}
@Composable
fun PermissionExplainDialog(
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onConfirm) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(Res.string.permission_explain_title),
                    style = MaterialTheme.typography.sectionTitle,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(Res.string.permission_explain_subtitle),
                    style = MaterialTheme.typography.caption,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                PermissionExplainRow(Res.drawable.ic_microphone, stringResource(Res.string.permission_card_microphone))
                PermissionExplainRow(Res.drawable.ic_notification, stringResource(Res.string.permission_card_notification))
                PermissionExplainRow(Res.drawable.ic_notification, stringResource(Res.string.permission_card_activity))
                if (isAndroidPlatform) {
                    PermissionExplainRow(Res.drawable.ic_check, stringResource(Res.string.permission_card_battery))
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primary)
                ) {
                    Text(
                        text = stringResource(Res.string.permission_explain_confirm),
                        style = MaterialTheme.typography.sectionTitle,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionExplainRow(icon: DrawableResource, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            modifier = Modifier.size(24.dp),
            painter = painterResource(icon),
            contentDescription = null,
            tint = Color.White
        )
        Text(
            text = text,
            style = MaterialTheme.typography.caption,
            color = Color.White
        )
    }
}
