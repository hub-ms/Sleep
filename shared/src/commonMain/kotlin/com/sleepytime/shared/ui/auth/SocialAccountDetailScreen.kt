package com.sleepytime.shared.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sleepytime.shared.enum_.AuthProvider
import com.sleepytime.shared.resources.Res
import com.sleepytime.shared.resources.ic_caret_right
import com.sleepytime.shared.ui.theme.SleepAppTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@ExperimentalMaterial3Api
@Composable
fun SocialAccountDetailContent(
    provider: AuthProvider,
    isPrimary: Boolean,
    isConnected: Boolean,
    onBack: () -> Unit,
    onChangePrimary: () -> Unit,
    onDisconnect: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "${getDisplayName(provider)} 계정", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_caret_right), // 뒤로가기 아이콘이 Res.drawable.ic_back 이면 교체
                            contentDescription = "뒤로가기",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                text = "연결 상태",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = if (isConnected) "연결됨" else "연결 안 됨",
                color = if (isConnected) Color.Green else Color.Red,
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(Modifier.height(32.dp))

            if (!isPrimary && isConnected) {
                Button(
                    onClick = onChangePrimary,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("대표 계정으로 설정", color = Color.White)
                }
            } else if (isPrimary) {
                Surface(
                    color = Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "현재 대표 계정입니다",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (isConnected) {
                OutlinedButton(
                    onClick = onDisconnect,
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, Color.Red),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                ) {
                    Text("연결 해제")
                }
            }
        }
    }
}

private fun getDisplayName(provider: AuthProvider): String {
    return when (provider) {
        AuthProvider.KAKAO -> "카카오"
        AuthProvider.GOOGLE -> "구글"
        AuthProvider.APPLE -> "애플"
        AuthProvider.EMAIL -> "이메일"
    }
}

@Preview
@Composable
@ExperimentalMaterial3Api
fun SocialAccountDetailScreenPreview() {
    SleepAppTheme {
        SocialAccountDetailContent(
            provider = AuthProvider.GOOGLE,
            isPrimary = true,
            isConnected = true,
            onBack = {},
            onChangePrimary = {},
            onDisconnect = {}
        )
    }
}
