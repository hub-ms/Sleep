package com.soundsleeper.app.ui.component

import com.soundsleeper.app.ui.theme.caption
import androidx.compose.ui.Alignment
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.soundsleeper.app.ui.theme.SleepTheme.primary
import com.soundsleeper.app.ui.theme.SleepTheme.secondary

private const val ILLUSTRATION_DURATION_MILLIS = 2800

@Composable
private fun ReplayableIllustration(
    modifier: Modifier = Modifier,
    illustration: @Composable (phase: Float) -> Unit,
) {
    var replayToken by remember { mutableIntStateOf(0) }
    var isFinished by remember { mutableStateOf(false) }
    val phase = remember { Animatable(0f) }

    LaunchedEffect(replayToken) {
        isFinished = false
        phase.snapTo(0f)
        phase.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = ILLUSTRATION_DURATION_MILLIS, easing = LinearEasing)
        )
        isFinished = true
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            illustration(phase.value)
        }
        // 버튼이 나타났다 사라지며 높이가 변하면 위 내용이 들썩인다. 자리를 고정해 둔다.
        Box(
            modifier = Modifier.height(40.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isFinished) {
                TextButton(onClick = { replayToken++ }) {
                    Text(
                        text = "다시보기",
                        style = MaterialTheme.typography.caption,
                        color = primary
                    )
                }
            }
        }
    }
}

@Composable
fun ChargingPhoneIllustration(modifier: Modifier = Modifier) {
    val primary = primary
    val secondary = secondary

    ReplayableIllustration(modifier = modifier) { phase ->
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawChargingPhone(phase, primary, secondary)
        }
    }
}

/**
 * 충전기가 이미 꽂혀 있을 때 보여주는 일러스트.
 *
 * 이미 꽂은 사람에게 "꽂으세요"를 반복해 봐야 의미가 없다. 대신 다음으로 중요한 것,
 * 즉 폰을 침대 옆 책상에 올려 두는 모습을 보여준다.
 */
@Composable
fun BedsideChargerIllustration(modifier: Modifier = Modifier) {
    val primary = primary
    val secondary = secondary

    ReplayableIllustration(modifier = modifier) { phase ->
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawBedsideCharger(phase, primary, secondary)
        }
    }
}

/** 시작은 빠르고 끝은 부드럽게. 케이블이 툭 멈추지 않고 포트에 스르륵 붙게 만든다. */
private fun easeOut(x: Float): Float = 1f - (1f - x) * (1f - x)

/** 0이 되기 전까지 선형으로 줄어드는 값. 글로우처럼 "한 번 번쩍이고 사라지는" 표현에 쓴다. */
private fun fadeOut(x: Float): Float = (1f - x).coerceIn(0f, 1f)

private const val TRAVEL_END = 0.55f
private const val GLOW_END = 0.72f

private fun DrawScope.drawChargingPhone(phase: Float, primary: Color, secondary: Color) {
    val bodyColor = Color.White.copy(alpha = 0.85f)

    // ── 폰 본체 ────────────────────────────────────────────────
    val phoneHeight = size.height * 0.62f
    val phoneWidth = phoneHeight * 0.5f
    val phoneLeft = (size.width - phoneWidth) / 2f
    val phoneTop = size.height * 0.06f
    val phoneBottom = phoneTop + phoneHeight
    val phoneRadius = CornerRadius(phoneWidth * 0.16f)

    drawRoundRect(
        color = primary.copy(alpha = 0.12f),
        topLeft = Offset(phoneLeft, phoneTop),
        size = Size(phoneWidth, phoneHeight),
        cornerRadius = phoneRadius
    )
    drawRoundRect(
        color = bodyColor,
        topLeft = Offset(phoneLeft, phoneTop),
        size = Size(phoneWidth, phoneHeight),
        cornerRadius = phoneRadius,
        style = Stroke(width = 2.dp.toPx())
    )
    // 상단 스피커 슬릿
    drawRoundRect(
        color = bodyColor.copy(alpha = 0.5f),
        topLeft = Offset(size.width / 2f - phoneWidth * 0.12f, phoneTop + phoneHeight * 0.045f),
        size = Size(phoneWidth * 0.24f, phoneHeight * 0.012f),
        cornerRadius = CornerRadius(phoneHeight * 0.006f)
    )

    // ── 케이블 ────────────────────────────────────────────────
    val connectorWidth = phoneWidth * 0.32f
    val connectorHeight = phoneHeight * 0.085f
    val plugInY = phoneBottom - connectorHeight * 0.35f   // 살짝 꽂혀 들어간 위치
    val startY = size.height * 1.05f                      // 화면 아래에서 출발
    val travel = (phase / TRAVEL_END).coerceIn(0f, 1f)
    val connectorTop = startY + (plugInY - startY) * easeOut(travel)
    val connectorCenterX = size.width / 2f

    // 커넥터 뒤로 늘어지는 선. 왼쪽 아래 바깥으로 빠지게 그려서 "선이 딸려 온다"는 느낌을 준다.
    val cable = Path().apply {
        moveTo(connectorCenterX, connectorTop + connectorHeight)
        quadraticTo(
            connectorCenterX - size.width * 0.02f, connectorTop + size.height * 0.30f,
            size.width * 0.14f, size.height * 1.02f
        )
    }
    drawPath(
        path = cable,
        color = secondary.copy(alpha = 0.85f),
        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
    )
    drawRoundRect(
        color = secondary,
        topLeft = Offset(connectorCenterX - connectorWidth / 2f, connectorTop),
        size = Size(connectorWidth, connectorHeight),
        cornerRadius = CornerRadius(connectorWidth * 0.25f)
    )

    // ── 꽂히는 순간의 글로우 ──────────────────────────────────
    if (phase in TRAVEL_END..GLOW_END) {
        val g = (phase - TRAVEL_END) / (GLOW_END - TRAVEL_END)
        drawCircle(
            color = primary.copy(alpha = fadeOut(g) * 0.45f),
            radius = phoneWidth * (0.25f + 0.35f * g),
            center = Offset(connectorCenterX, phoneBottom)
        )
    }

    // ── 충전 표시 (번개) ──────────────────────────────────────
    val boltAlpha = ((phase - 0.5f) / 0.18f).coerceIn(0f, 1f)
    if (boltAlpha > 0f) {
        val boltHeight = phoneHeight * 0.28f
        val boltWidth = boltHeight * 0.55f
        val boltLeft = size.width / 2f - boltWidth / 2f
        val boltTop = phoneTop + phoneHeight * 0.36f
        fun px(fx: Float, fy: Float) = Offset(boltLeft + boltWidth * fx, boltTop + boltHeight * fy)

        val bolt = Path().apply {
            val p0 = px(0.58f, 0f)
            moveTo(p0.x, p0.y)
            px(0.16f, 0.56f).let { lineTo(it.x, it.y) }
            px(0.44f, 0.56f).let { lineTo(it.x, it.y) }
            px(0.34f, 1f).let { lineTo(it.x, it.y) }
            px(0.82f, 0.42f).let { lineTo(it.x, it.y) }
            px(0.52f, 0.42f).let { lineTo(it.x, it.y) }
            close()
        }
        drawPath(path = bolt, color = primary.copy(alpha = boltAlpha))
    }
}

// ── 침대 옆 책상에 폰을 올려 두는 일러스트 ──────────────────────────
private const val DESCEND_END = 0.55f
private const val SETTLE_END = 0.72f

private fun DrawScope.drawBedsideCharger(phase: Float, primary: Color, secondary: Color) {
    val bodyColor = Color.White.copy(alpha = 0.85f)

    // ── 침대 모서리 (왼쪽에 살짝 걸치게) ──────────────────────
    val bedTop = size.height * 0.52f
    drawRoundRect(
        color = primary.copy(alpha = 0.18f),
        topLeft = Offset(-size.width * 0.30f, bedTop),
        size = Size(size.width * 0.55f, size.height * 0.50f),
        cornerRadius = CornerRadius(size.width * 0.05f)
    )
    // 베개
    drawRoundRect(
        color = bodyColor.copy(alpha = 0.35f),
        topLeft = Offset(-size.width * 0.18f, bedTop + size.height * 0.04f),
        size = Size(size.width * 0.30f, size.height * 0.10f),
        cornerRadius = CornerRadius(size.width * 0.03f)
    )

    // ── 협탁 ──────────────────────────────────────────────────
    val deskWidth = size.width * 0.52f
    val deskLeft = size.width * 0.42f
    val deskTopY = size.height * 0.62f
    val deskThickness = size.height * 0.045f

    drawRoundRect(
        color = bodyColor,
        topLeft = Offset(deskLeft, deskTopY),
        size = Size(deskWidth, deskThickness),
        cornerRadius = CornerRadius(deskThickness * 0.4f)
    )
    // 다리 둘
    val legWidth = size.width * 0.025f
    val legHeight = size.height * 0.30f
    listOf(deskLeft + deskWidth * 0.12f, deskLeft + deskWidth * 0.80f).forEach { x ->
        drawRoundRect(
            color = bodyColor.copy(alpha = 0.7f),
            topLeft = Offset(x, deskTopY + deskThickness),
            size = Size(legWidth, legHeight),
            cornerRadius = CornerRadius(legWidth * 0.5f)
        )
    }

    // ── 폰: 위에서 내려와 협탁 위에 놓인다 ────────────────────
    val phoneHeight = size.height * 0.30f
    val phoneWidth = phoneHeight * 0.5f
    val phoneLeft = deskLeft + deskWidth * 0.30f
    val restingTop = deskTopY - phoneHeight
    val startTop = -phoneHeight * 1.1f
    val descend = (phase / DESCEND_END).coerceIn(0f, 1f)
    val phoneTop = startTop + (restingTop - startTop) * easeOut(descend)
    val phoneRadius = CornerRadius(phoneWidth * 0.16f)

    drawRoundRect(
        color = primary.copy(alpha = 0.12f),
        topLeft = Offset(phoneLeft, phoneTop),
        size = Size(phoneWidth, phoneHeight),
        cornerRadius = phoneRadius
    )
    drawRoundRect(
        color = bodyColor,
        topLeft = Offset(phoneLeft, phoneTop),
        size = Size(phoneWidth, phoneHeight),
        cornerRadius = phoneRadius,
        style = Stroke(width = 2.dp.toPx())
    )

    // ── 충전 케이블: 폰 아래에서 협탁 뒤로 늘어진다 ───────────
    val phoneBottomCenter = Offset(phoneLeft + phoneWidth / 2f, phoneTop + phoneHeight)
    val cable = Path().apply {
        moveTo(phoneBottomCenter.x, phoneBottomCenter.y)
        quadraticTo(
            phoneBottomCenter.x + size.width * 0.16f, phoneBottomCenter.y + size.height * 0.22f,
            deskLeft + deskWidth * 0.92f, size.height * 1.02f
        )
    }
    drawPath(
        path = cable,
        color = secondary.copy(alpha = 0.85f),
        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
    )

    // ── 내려놓는 순간의 글로우 ────────────────────────────────
    if (phase in DESCEND_END..SETTLE_END) {
        val g = (phase - DESCEND_END) / (SETTLE_END - DESCEND_END)
        drawCircle(
            color = primary.copy(alpha = fadeOut(g) * 0.40f),
            radius = phoneWidth * (0.3f + 0.4f * g),
            center = Offset(phoneBottomCenter.x, deskTopY)
        )
    }

    // ── 놓인 뒤 뜨는 충전 표시 ────────────────────────────────
    val boltAlpha = ((phase - 0.6f) / 0.18f).coerceIn(0f, 1f)
    if (boltAlpha > 0f) {
        val boltHeight = phoneHeight * 0.30f
        val boltWidth = boltHeight * 0.55f
        val boltLeft = phoneLeft + phoneWidth / 2f - boltWidth / 2f
        val boltTop = phoneTop + phoneHeight * 0.34f
        fun px(fx: Float, fy: Float) = Offset(boltLeft + boltWidth * fx, boltTop + boltHeight * fy)

        val bolt = Path().apply {
            val p0 = px(0.58f, 0f)
            moveTo(p0.x, p0.y)
            px(0.16f, 0.56f).let { lineTo(it.x, it.y) }
            px(0.44f, 0.56f).let { lineTo(it.x, it.y) }
            px(0.34f, 1f).let { lineTo(it.x, it.y) }
            px(0.82f, 0.42f).let { lineTo(it.x, it.y) }
            px(0.52f, 0.42f).let { lineTo(it.x, it.y) }
            close()
        }
        drawPath(path = bolt, color = primary.copy(alpha = boltAlpha))
    }
}
