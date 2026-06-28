package com.sleepytime.shared.ui.setting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sleepytime.shared.resources.Res
import com.sleepytime.shared.resources.ic_app_info
import com.sleepytime.shared.resources.ic_legal_info
import com.sleepytime.shared.ui.theme.SleepAppTheme
import com.sleepytime.shared.ui.theme.sectionTitle
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
@Composable
fun AppInfoContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "앱 정보",
            style = MaterialTheme.typography.sectionTitle,
            color = Color.White
        )

        SettingCard(text = "버전 정보") {
            SettingItem(painterResource(Res.drawable.ic_app_info), "현재 버전 1.0.0") { }
            SettingItem(painterResource(Res.drawable.ic_app_info), "최신 버전 확인") { }
        }

        SettingCard(text = "법적 고지") {
            SettingItem(painterResource(Res.drawable.ic_legal_info), "서비스 이용약관") { }
            SettingItem(painterResource(Res.drawable.ic_legal_info), "개인정보 처리방침") { }
            SettingItem(painterResource(Res.drawable.ic_legal_info), "오픈소스 라이선스") { }
        }
    }
}

@Preview
@Composable
fun AppInfoScreenPreview() {
    SleepAppTheme {
        AppInfoContent()
    }
}
