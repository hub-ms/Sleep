package com.soundsleeper.app.ui.onboarding

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.style.TextAlign
import com.soundsleeper.app.resources.Res
import com.soundsleeper.app.resources.bg_splash
import com.soundsleeper.app.resources.splash_title
import com.soundsleeper.app.ui.theme.SleepTheme
import com.soundsleeper.app.ui.theme.sectionTitle
import io.github.alexzhirkevich.compottie.Compottie
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val SPLASH_CHARACTER_LOTTIE_ASSET = "files/splash_character.json"

/**
 * 앱을 켜면 가장 먼저 보이는 화면.
 *
 * 예전에는 안드로이드가 그려 주는 기본 스플래시(검은 배경 + 런처 아이콘)가 전부였고, 곧바로
 * 온보딩 페이저가 떴다. 로그인된 사용자는 그 온보딩이 스쳐 지나간 뒤에야 홈에 닿아서
 * 매번 잠깐 깜빡였다. 이 화면이 로그인 상태가 정해질 때까지 머물며 그 깜빡임을 없앤다.
 */
@Composable
fun SplashContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SleepTheme.background),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(Res.drawable.bg_splash),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )
        SplashCharacter()
        Text(
            text = stringResource(Res.string.splash_title),
            style = MaterialTheme.typography.sectionTitle,
            color = Color.White
        )
        Text(
            text = stringResource(Res.string.splash_title),
            style = MaterialTheme.typography.sectionTitle,
            color = Color.White,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * [SPLASH_CHARACTER_LOTTIE_ASSET] 이 있으면 그것을, 없으면 같은 분위기의 Canvas 그림을 그린다.
 *
 * 에셋이 없을 때 [rememberLottieComposition] 은 예외를 삼키고 값이 null 인 상태로 남으므로,
 * 그 경우를 폴백 분기로 사용한다. `OnboardingIllustration.kt` 와 동일한 패턴이다.
 */
@Composable
private fun SplashCharacter(modifier: Modifier = Modifier) {
    val composition by rememberLottieComposition {
        LottieCompositionSpec.JsonString(
            Res.readBytes(SPLASH_CHARACTER_LOTTIE_ASSET).decodeToString()
        )
    }

    val lottie = composition
    if (lottie != null) {
        Image(
            painter = rememberLottiePainter(
                composition = lottie,
                iterations = Compottie.IterateForever
            ),
            contentDescription = null,
            modifier = modifier
        )
    } else {
        val transition = rememberInfiniteTransition(label = "splash")
        val float by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "float"
        )
        Canvas(modifier = modifier) {
            drawSleepingCharacter(breath = float)
        }
    }
}

/** 별이 떠 있는 밤하늘 위, 초승달에 기대 자는 캐릭터. [breath] 는 0~1 의 숨쉬기 위상. */
private fun DrawScope.drawSleepingCharacter(breath: Float) {
    val w = size.width
    val h = size.height
    val moonColor = Color(0xFFFFE9A8)
    val bodyColor = Color(0xFFB9C6FF)

    // 달빛 번짐
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(moonColor.copy(alpha = 0.25f), Color.Transparent),
            center = Offset(w * 0.5f, h * 0.55f),
            radius = w * 0.5f
        ),
        radius = w * 0.5f,
        center = Offset(w * 0.5f, h * 0.55f)
    )

    // 초승달 — 큰 원에서 작은 원을 덜어내 만든다.
    val moonPath = Path().apply {
        addOval(
            androidx.compose.ui.geometry.Rect(
                offset = Offset(w * 0.18f, h * 0.30f),
                size = Size(w * 0.64f, w * 0.64f)
            )
        )
    }
    val biteePath = Path().apply {
        addOval(
            androidx.compose.ui.geometry.Rect(
                offset = Offset(w * 0.34f, h * 0.22f),
                size = Size(w * 0.60f, w * 0.60f)
            )
        )
    }
    val crescent = Path().apply {
        op(moonPath, biteePath, androidx.compose.ui.graphics.PathOperation.Difference)
    }
    drawPath(crescent, color = moonColor)

    // 캐릭터: 달 오른쪽 위에 걸터앉아 고개를 숙인 모습. 숨에 맞춰 아주 조금 오르내린다.
    val bob = (breath - 0.5f) * h * 0.02f
    val headCenter = Offset(w * 0.62f, h * 0.40f + bob)
    val headRadius = w * 0.11f
    drawCircle(color = bodyColor, radius = headRadius, center = headCenter)

    // 몸통
    drawCircle(
        color = bodyColor,
        radius = w * 0.09f,
        center = Offset(w * 0.62f, h * 0.40f + headRadius * 1.5f + bob)
    )

    // 감은 눈 두 개 — 아래로 볼록한 짧은 호
    val eyeY = headCenter.y + headRadius * 0.05f
    listOf(-1f, 1f).forEach { side ->
        val eyeX = headCenter.x + side * headRadius * 0.38f
        drawArc(
            color = Color(0xFF2A2F45),
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(eyeX - headRadius * 0.20f, eyeY - headRadius * 0.20f),
            size = Size(headRadius * 0.40f, headRadius * 0.40f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = headRadius * 0.12f)
        )
    }

    // 잠들었다는 신호인 z 세 개. 숨 위상에 따라 함께 흐려졌다 진해진다.
    val zAlpha = 0.35f + breath * 0.65f
    listOf(
        Triple(w * 0.78f, h * 0.30f, w * 0.055f),
        Triple(w * 0.85f, h * 0.22f, w * 0.040f),
        Triple(w * 0.90f, h * 0.15f, w * 0.028f),
    ).forEach { (x, y, s) ->
        drawPath(
            path = zPath(Offset(x, y), s),
            color = Color.White.copy(alpha = zAlpha)
        )
    }
}

/** 'Z' 모양 — 위 가로선, 대각선, 아래 가로선. */
private fun zPath(topLeft: Offset, side: Float): Path = Path().apply {
    val t = side * 0.22f
    moveTo(topLeft.x, topLeft.y)
    lineTo(topLeft.x + side, topLeft.y)
    lineTo(topLeft.x + side, topLeft.y + t)
    lineTo(topLeft.x + t * 1.6f, topLeft.y + side - t)
    lineTo(topLeft.x + side, topLeft.y + side - t)
    lineTo(topLeft.x + side, topLeft.y + side)
    lineTo(topLeft.x, topLeft.y + side)
    lineTo(topLeft.x, topLeft.y + side - t)
    lineTo(topLeft.x + side - t * 1.6f, topLeft.y + t)
    lineTo(topLeft.x, topLeft.y + t)
    close()
}
