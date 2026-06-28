package com.sleepytime.shared.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sleepytime.shared.enum_.AuthProvider
import com.sleepytime.shared.ui.theme.SleepAppTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun DisconnectConfirmContent(
    provider: AuthProvider,
    onBack: () -> Unit,
    onDisconnect: () -> Unit
) {
    val providerDisplayName = when (provider) {
        AuthProvider.KAKAO -> "카카오"
        AuthProvider.GOOGLE -> "구글"
        AuthProvider.APPLE -> "애플"
        AuthProvider.EMAIL -> "이메일"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "$providerDisplayName 계정 연결 해제",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "연결을 해제하면 해당 계정으로 다시 로그인해야 합니다. 정말 해제하시겠습니까?",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.LightGray,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onBack,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("취소", color = Color.White)
            }

            Button(
                onClick = onDisconnect,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("해제하기", color = Color.White)
            }
        }
    }
}

@Preview
@Composable
fun DisconnectConfirmScreenPreview() {
    SleepAppTheme {
        DisconnectConfirmContent(
            provider = AuthProvider.GOOGLE,
            onBack = {},
            onDisconnect = {}
        )
    }
}
