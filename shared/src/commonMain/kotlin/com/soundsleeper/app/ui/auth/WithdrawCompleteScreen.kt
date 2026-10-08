package com.soundsleeper.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.soundsleeper.app.ui.theme.SleepTheme.background
import com.soundsleeper.app.ui.theme.SleepTheme.primary

@Composable
fun WithdrawCompleteContent(
    onNavigateToOnboarding: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            // 탈퇴 로딩 화면과 같이 앱 테마 배경을 쓴다.
            .background(background),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "회원탈퇴 완료",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White
        )
        
        Spacer(Modifier.height(32.dp))

        Surface(
            modifier = Modifier.clickable { onNavigateToOnboarding() },
            color = primary,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "홈으로 이동",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                Color.White
            )
        }
    }
}

