package com.soundsleeper.app.ui.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.soundsleeper.app.resources.Res
import com.soundsleeper.app.ui.theme.SleepTheme.primary
import io.github.alexzhirkevich.compottie.Compottie
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter

enum class OnboardingIllustrationSpec(val lottieAsset: String) {
    SLEEP_TRACKING("files/onboarding_tracking.json"),
}

/**
 * Lottie 에셋이 있으면 그것을, 없으면 같은 분위기의 Compose 애니메이션을 그린다.
 *
 * 에셋이 없을 때 [rememberLottieComposition] 은 예외를 삼키고 값이 null 인 상태로 남으므로,
 * 그 경우를 폴백 분기로 사용한다.
 */
@Composable
fun OnboardingIllustration(
    spec: OnboardingIllustrationSpec,
    modifier: Modifier = Modifier,
) {
    key(spec) {
        val composition by rememberLottieComposition {
            LottieCompositionSpec.JsonString(
                Res.readBytes(spec.lottieAsset).decodeToString()
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
            OnboardingIllustrationFallback(spec = spec, modifier = modifier)
        }
    }
}

/**
 * 순수 Compose 폴백. 저장소의 기존 관용구(WakeUpScreen 의 rememberInfiniteTransition)를 따른다.
 * 하나의 0f~1f 위상값을 계속 돌리고, 각 그림이 그 값을 다르게 해석한다.
 */
@Composable
private fun OnboardingIllustrationFallback(
    spec: OnboardingIllustrationSpec,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "onboardingIllustration")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    val primary = primary
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            when (spec) {
                OnboardingIllustrationSpec.SLEEP_TRACKING -> drawPulsingRings(phase, primary)
            }
        }
    }
}

/** 수면 측정: 가운데 점에서 파동이 퍼져 나간다. */
private fun DrawScope.drawPulsingRings(phase: Float, color: Color) {
    val maxRadius = size.minDimension / 2f
    repeat(3) { index ->
        val p = (phase + index / 3f) % 1f
        drawCircle(
            color = color.copy(alpha = (1f - p) * 0.5f),
            radius = maxRadius * (0.25f + 0.75f * p),
            style = Stroke(width = 2.dp.toPx())
        )
    }
    // 배경 일러스트(달) 위에 겹쳐 그려지므로, 가운데는 얼룩처럼 보이지 않게 아주 옅게만 둔다.
    drawCircle(color = color.copy(alpha = 0.3f), radius = maxRadius * 0.08f)
}

