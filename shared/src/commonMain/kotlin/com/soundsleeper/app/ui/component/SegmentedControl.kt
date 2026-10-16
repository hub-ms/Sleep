package com.soundsleeper.app.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.soundsleeper.app.ui.theme.SleepTheme
import com.soundsleeper.app.ui.theme.SleepTheme.primary
import com.soundsleeper.app.ui.theme.bodyText

private val SEGMENT_SHAPE = RoundedCornerShape(10.dp)

/**
 * 하나의 트랙 안에서 칸을 균등 분할하는 세그먼트 컨트롤.
 *
 * SelectableChipGroup 은 알약 칩을 SpaceBetween 으로 흩어 놓아서, 칸마다 글자 길이가 다르면
 * 폭이 들쭉날쭉하고 "몇 개 중 하나를 고르는 것"이라는 느낌이 약하다. 선택지가 고정된 4개처럼
 * 서로 배타적이고 개수가 적을 때는 이 쪽이 상태를 더 분명하게 보여 준다. 카테고리 칩처럼
 * 개수가 유동적인 곳에서는 기존 SelectableChipGroup 을 그대로 쓴다.
 *
 * [selectedItem] 이 null 이면 아무 칸도 채우지 않는다(= 아직 고르지 않음).
 */
@Composable
fun <T> SegmentedControl(
    items: List<T>,
    selectedItem: T?,
    onSelectItem: (T) -> Unit,
    modifier: Modifier = Modifier,
    itemLabel: @Composable (T) -> String = { it.toString() },
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = SleepTheme.surface
    ) {
        Row(modifier = Modifier.padding(3.dp)) {
            items.forEach { item ->
                val isSelected = item == selectedItem
                val background by animateColorAsState(
                    targetValue = if (isSelected) primary else Color.Transparent,
                    label = "segmentBackground"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(SEGMENT_SHAPE)
                        .background(background, SEGMENT_SHAPE)
                        .clickable { onSelectItem(item) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = itemLabel(item),
                        style = MaterialTheme.typography.bodyText,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
