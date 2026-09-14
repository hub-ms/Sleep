package com.sleepytime.shared.ui.tracking

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sleepytime.shared.ui.theme.SleepAppTheme
import com.sleepytime.shared.util.DateTimeUtil.to24TimeString
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.ui.tooling.preview.Preview
@Composable
fun WakeUpContent(
    onStopAlarm: () -> Unit,
) {


    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val currentTime = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A237E), // 깊은 밤
                        Color(0xFF3949AB), // 새벽녘
                        Color(0xFF9575CD)  // 아침 햇살 느낌
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(48.dp)
        ) {
            // 시간 및 메시지 영역
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = currentTime.to24TimeString(),
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Light,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "좋은 아침이에요!",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "충분히 휴식하셨나요?",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }

            // 알람 정지 버튼 (펄스 효과 포함)
            Box(contentAlignment = Alignment.Center) {
                // 배경 펄스 효과
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .scale(pulseScale)
                        .alpha(pulseAlpha)
                        .background(Color.White.copy(alpha = 0.3f), CircleShape)
                )
                
                Surface(
                    modifier = Modifier
                        .size(140.dp)
                        .clickable {
                            onStopAlarm()
                        },
                    shape = CircleShape,
                    color = Color.White,
                    tonalElevation = 8.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "일어나기",
                            color = Color(0xFF3949AB),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun WakeUpScreenPreview() {
    SleepAppTheme {
        WakeUpContent(
            onStopAlarm = {},
        )
    }
}
