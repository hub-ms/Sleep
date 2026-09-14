package com.sleepytime.shared.ui.setting

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sleepytime.shared.ui.alarm.CommonTimePicker
import com.sleepytime.shared.ui.auth.AuthContract
import com.sleepytime.shared.ui.component.ToggleSettingItem
import com.sleepytime.shared.ui.theme.SleepTheme
import com.sleepytime.shared.ui.theme.bodyText
import com.sleepytime.shared.ui.theme.sectionTitle
import kotlinx.coroutines.launch

@Composable
fun NotificationSettingContent(
    authState: AuthContract.State,
    onIntent: (AuthContract.Intent) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SleepTheme.gradients.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingCard {
                ToggleSettingItem(
                    title = "프로모션 알림",
                    subtitle = "이벤트 및 할인 혜택 소식을 알려드립니다",
                    checked = authState.isPushEnabled,
                    onCheckedChange = {
                        onIntent(AuthContract.Intent.TogglePushNotification(it))
                        scope.launch {
                            snackbarHostState.showSnackbar(if (it) "프로모션 알림이 켜졌습니다" else "프로모션 알림이 꺼졌습니다",
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                )
                ToggleSettingItem(
                    title = "업데이트 알림",
                    subtitle = "새로운 기능 및 서비스 공지사항을 안내합니다",
                    checked = authState.isUpdateEnabled,
                    onCheckedChange = {
                        onIntent(AuthContract.Intent.ToggleUpdate(it))
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (it) "업데이트 알림이 켜졌습니다" else "업데이트 알림이 꺼졌습니다",
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                )
                HorizontalDivider(color = Color(0xFF2A2C3D), thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))
                ToggleSettingItem(
                    title = "취침 시각 알림",
                    subtitle = "설정하신 시간에 맞춰 수면 준비를 도와드립니다",
                    checked = authState.isReminderEnabled,
                    onCheckedChange = {
                        onIntent(AuthContract.Intent.ToggleSleepReminder(it))
                        scope.launch {
                            snackbarHostState.showSnackbar(if (it) "취침 시각 알림이 켜졌습니다" else "취침 시각 알림이 꺼졌습니다",
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                )
                
                AnimatedVisibility(
                    visible = authState.isReminderEnabled,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    BedtimePickerSection(
                        hour = authState.reminderHour,
                        minute = authState.reminderMinute,
                        onTimeChanged = { h, m -> 
                            onIntent(AuthContract.Intent.ChangeReminderTime(h, m))
                        }
                    )
                }

                HorizontalDivider(color = Color(0xFF2A2C3D), thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))
                ToggleSettingItem(
                    title = "주간 수면 리포트 알림",
                    subtitle = "한 주간의 수면 통계 및 분석 리포트를 전달합니다",
                    checked = authState.isWeeklyReportEnabled,
                    onCheckedChange = {
                        onIntent(AuthContract.Intent.ToggleWeeklyReport(it))
                        scope.launch {
                            snackbarHostState.showSnackbar(if (it) "주간 리포트 알림이 켜졌습니다" else "주간 리포트 알림이 꺼졌습니다",
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                )
                AnimatedVisibility(
                    visible = authState.isWeeklyReportEnabled,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    ReportDeliverySection(
                        selectedMethod = authState.reportDeliveryMethod,
                        onMethodChanged = { 
                            onIntent(AuthContract.Intent.ChangeReportDeliveryMethod(it))
                        }
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        ) { data ->
            Card(
                modifier = Modifier
                    .navigationBarsPadding(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
            ) {
                Text(
                    modifier = Modifier
                        .padding(8.dp),
                    text = data.visuals.message,
                    color = Color.Black,
                    style = MaterialTheme.typography.bodyText,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun BedtimePickerSection(
    hour: Int,
    minute: Int,
    onTimeChanged: (Int, Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            CommonTimePicker(
                isEnabled = true,
                items = (0..23).toList(),
                selectedValue = hour,
                onValueChanged = { value, _ -> onTimeChanged(value, minute) }
            )
            Text(
                text = ":",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            CommonTimePicker(
                isEnabled = true,
                items = (0..59).toList(),
                selectedValue = minute,
                onValueChanged = { value, _ -> onTimeChanged(hour, value) }
            )
        }
    }
}

@Composable
fun ReportDeliverySection(
    selectedMethod: AuthContract.ReportDeliveryMethod,
    onMethodChanged: (AuthContract.ReportDeliveryMethod) -> Unit
) {
    val methods = AuthContract.ReportDeliveryMethod.entries
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        methods.forEach { method ->
            val title = when(method) {
                AuthContract.ReportDeliveryMethod.PUSH -> "앱 푸시 알림"
                AuthContract.ReportDeliveryMethod.EMAIL -> "이메일 알림"
            }
            
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onMethodChanged(method) },
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    RadioButton(
                        selected = method == selectedMethod,
                        onClick = { onMethodChanged(method) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = MaterialTheme.colorScheme.primary,
                            unselectedColor = Color.Gray
                        )
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
