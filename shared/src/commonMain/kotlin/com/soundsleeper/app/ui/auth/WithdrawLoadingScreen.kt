package com.soundsleeper.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.soundsleeper.app.ui.theme.SleepTheme.background

@Composable
fun WithdrawLoadingContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            // 다크 테마 앱에서 흰 화면이 한 번 번쩍이면 흐름이 끊겨 보인다.
            // 가입 로딩 화면과 같이 앱 테마 배경을 쓴다.
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(
                text = "계정을 탈퇴하는 중입니다...",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
        }
    }
}
