package com.sleepytime.shared.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun SleepAnalyzingContent(
    onFinished: () -> Unit = {}
) {
    var step by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        repeat(4) {
            delay(600)
            step++
        }
        delay(400)
        onFinished()
    }

    val steps = listOf(
        "수면 데이터 분석 중...",
        "수면 시간 계산 중...",
        "수면 패턴 정리 중...",
        "최적 수면 시간 도출 중..."
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B1020))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "수면 리포트 생성 중",
                color = Color.White,
                fontSize = 22.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFF7C9AFF)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = steps.getOrNull(step) ?: "마무리 중...",
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            steps.forEachIndexed { index, text ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 6.dp)
                ) {
                    when {
                        index < step -> {
                            Text("✓", color = Color.Green, fontSize = 18.sp)
                        }
                        index == step -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        }
                        else -> {
                            Text("○", color = Color.Gray, fontSize = 18.sp)
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = text,
                        color = Color.White,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun SleepAnalyzingScreenPreview() {
    SleepAnalyzingContent()
}

