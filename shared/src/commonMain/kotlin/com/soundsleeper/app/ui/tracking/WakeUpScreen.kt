package com.soundsleeper.app.ui.tracking

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soundsleeper.app.util.DateTimeUtil.to24TimeString
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
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

            // 알람 정지 버튼 — 누르고 있으면 화면을 채울 때까지 커진다.
            // 한 번의 탭으로 알람이 꺼지면 잠결에 스치기만 해도 꺼지므로, 끝까지 누르고 있어야
            // 완료되도록 했다. 측정 종료의 "위로 밀어서" 제스처와 같은 의도다.
            HoldToWakeButton(
                pulseScale = pulseScale,
                pulseAlpha = pulseAlpha,
                onComplete = onStopAlarm
            )
        }
    }
}


/** 버튼이 화면을 완전히 덮는 데 필요한 배율. 대각선 길이를 기준으로 잡아야 모서리까지 찬다. */
private const val WAKE_BUTTON_FILL_SCALE = 12f

/** 끝까지 누르고 있어야 하는 시간. 너무 짧으면 실수로, 너무 길면 짜증스럽다. */
private const val WAKE_HOLD_MILLIS = 1200

/**
 * 누르고 있는 동안 커지다가 화면을 가득 채우면 [onComplete] 을 호출한다.
 *
 * 손을 떼면 원래 크기로 되돌아간다. 완료는 한 번만 발생하도록 플래그로 막는다 —
 * 애니메이션이 끝나는 프레임과 손을 떼는 프레임이 겹치면 두 번 호출될 수 있다.
 */
@Composable
private fun HoldToWakeButton(
    pulseScale: Float,
    pulseAlpha: Float,
    onComplete: () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    var isPressed by remember { mutableStateOf(false) }
    var hasCompleted by remember { mutableStateOf(false) }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            // 남은 구간만큼만 시간을 잡아야, 떼었다 다시 눌러도 속도가 일정하다.
            val remaining = ((1f - progress.value) * WAKE_HOLD_MILLIS).toInt().coerceAtLeast(1)
            progress.animateTo(1f, tween(remaining, easing = LinearEasing))
            if (!hasCompleted) {
                hasCompleted = true
                onComplete()
            }
        } else if (!hasCompleted) {
            progress.animateTo(0f, tween(250, easing = LinearEasing))
        }
    }

    val scale = 1f + progress.value * (WAKE_BUTTON_FILL_SCALE - 1f)

    Box(contentAlignment = Alignment.Center) {
        // 배경 펄스는 누르기 전에만 돈다. 커지는 동안에도 같이 뛰면 산만하다.
        if (progress.value == 0f) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .scale(pulseScale)
                    .alpha(pulseAlpha)
                    .background(Color.White.copy(alpha = 0.3f), CircleShape)
            )
        }
        Surface(
            modifier = Modifier
                .size(140.dp)
                .scale(scale)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        }
                    )
                },
            shape = CircleShape,
            color = Color.White,
            tonalElevation = 8.dp
        ) {}
        // 글자는 버튼과 같이 커지면 금세 화면을 넘어가므로 원래 크기로 따로 올린다.
        Text(
            text = "일어나기",
            color = Color(0xFF3949AB),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.alpha(1f - progress.value)
        )
    }
}
