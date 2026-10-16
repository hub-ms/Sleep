package com.soundsleeper.app.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 점 하나의 지름. 양옆 숫자(시/분)보다 확실히 작아야 콜론으로 읽힌다. */
private val DOT_SIZE = 6.dp

/** 두 점 사이 간격. */
private val DOT_GAP = 8.dp

/** 점 뒤에 까는 번짐의 지름. 점보다 커야 테두리 밖으로 빛이 새어 나온다. */
private val GLOW_SIZE = 14.dp

/**
 * 시각 피커 가운데의 콜론(:).
 *
 * 예전에는 24×50dp 를 `weight(1f)` 둘로 나눠 점을 높이의 25%·75% 지점에 찍었다. 두 점이
 * 25dp 나 떨어져 있어 콜론이 아니라 따로 떨어진 점 두 개로 보였다. 지금은 점 크기와 간격을
 * 직접 지정하고 세로로는 내용만큼만 차지한다 — 양옆 피커가 `Alignment.CenterVertically`
 * 로 정렬하므로 높이를 억지로 맞춰 줄 필요가 없다.
 *
 * [shadowColor] 는 같은 행의 숫자 피커가 쓰는 색(취침 = secondary, 기상 = primary)을 그대로
 * 받는다. 숫자에는 `Shadow(color = shadowColor)` 가 걸려 있는데 콜론만 그림자가 없으면
 * 가운데가 꺼져 보인다. 번짐은 하단 탭바·PickerStateIcon 과 같은 수법으로 만든다 —
 * 흐린 복제본을 아래 깔기. 6dp Spacer 에 `Modifier.shadow` 를 걸던 예전 방식은 그림자가
 * 사실상 보이지 않았다.
 */
@Composable
fun CircleCanvas(
    isEnabled: Boolean,
    shadowColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.width(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DOT_GAP),
    ) {
        CircleIndicator(isEnabled = isEnabled, shadowColor = shadowColor)
        CircleIndicator(isEnabled = isEnabled, shadowColor = shadowColor)
    }
}

@Composable
private fun CircleIndicator(isEnabled: Boolean, shadowColor: Color) {
    // 숫자 피커가 켜짐/꺼짐으로 흐려질 때 콜론도 같은 속도로 따라가야 한 덩어리로 보인다.
    val dotColor by animateColorAsState(
        targetValue = if (isEnabled) Color.White else Color.White.copy(alpha = 0.4f),
        label = "colonDot"
    )
    Box(
        modifier = Modifier.size(GLOW_SIZE),
        contentAlignment = Alignment.Center
    ) {
        if (isEnabled) {
            Spacer(
                modifier = Modifier
                    .size(GLOW_SIZE)
                    // Unbounded 가 없으면 blur 가 자기 사각형 경계에서 잘린다. 원을 흐리게
                    // 했는데 네모난 빛 덩어리가 나오는 이유가 그것이다(에뮬레이터에서 확인).
                    .blur(6.dp, BlurredEdgeTreatment.Unbounded)
                    .background(shadowColor, CircleShape)
            )
        }
        Spacer(
            modifier = Modifier
                .size(DOT_SIZE)
                .background(dotColor, CircleShape)
        )
    }
}
