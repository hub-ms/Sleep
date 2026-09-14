package com.sleepytime.shared.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sleepytime.shared.ui.theme.SleepAppTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun WithdrawConfirmContent(
    onOptionSelected: (String) -> Unit
) {
    val options = listOf(
        "잠시 쉬기 (데이터 보존)",
        "알림 끄기",
        "게스트로 전환",
        "그래도 탈퇴할게요"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(24.dp)
    ) {
        Text(
            text = "회원탈퇴",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.Black
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "탈퇴 전에 다른 방법을 고려해 보세요",
            color = Color.Gray,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(32.dp))

        options.forEach { option ->
            val isDestructive = option == "그래도 탈퇴할게요"

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clickable { onOptionSelected(option) },
                color = if (isDestructive) Color(0xFFFFEBEB) else Color(0xFFF2F2F2),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = option,
                    modifier = Modifier.padding(16.dp),
                    color = if (isDestructive) Color.Red else Color.Black,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Text(
            text = "수면 기록을 삭제하면 다시 불러올 수 없어요",
            color = Color.LightGray,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Preview
@Composable
fun WithdrawConfirmScreenPreview() {
    SleepAppTheme {
        WithdrawConfirmContent(onOptionSelected = {})
    }
}
