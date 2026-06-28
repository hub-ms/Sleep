package com.sleepytime.shared.ui.setting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sleepytime.shared.resources.Res
import com.sleepytime.shared.resources.ic_notification
import com.sleepytime.shared.ui.theme.SleepAppTheme
import com.sleepytime.shared.ui.theme.sectionTitle
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun NotificationSettingContent() {
    var pushEnabled by remember { mutableStateOf(true) }
    var reminderEnabled by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "알림 설정",
            style = MaterialTheme.typography.sectionTitle,
            color = Color.White
        )

        SettingCard(text = "앱 알림") {
            ToggleSettingItem(
                title = "푸시 알림",
                checked = pushEnabled,
                onCheckedChange = { pushEnabled = it }
            )
            ToggleSettingItem(
                title = "취침 리마인더",
                checked = reminderEnabled,
                onCheckedChange = { reminderEnabled = it }
            )
        }

        SettingCard(text = "활동") {
            SettingItem(painterResource(Res.drawable.ic_notification), "주간 수면 리포트 알림") { }
        }
    }
}

@Composable
fun ToggleSettingItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Preview
@Composable
fun NotificationSettingScreenPreview() {
    SleepAppTheme {
        NotificationSettingContent()
    }
}
