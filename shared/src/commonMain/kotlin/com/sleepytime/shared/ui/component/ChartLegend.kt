package com.sleepytime.shared.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sleepytime.shared.ui.report.LegendItem
import com.sleepytime.shared.ui.theme.SleepTheme
import com.sleepytime.shared.ui.theme.caption

// SleepTimeLineChart 옆에 세로로 배치할 때는 각 항목을 그래프와 동일한 16dp 높이 행으로
// 위에서부터 쌓아 그래프의 각 단계 행과 같은 높이에 정렬되도록 한다. (horizontal = false)
private val LEGEND_ROW_HEIGHT = 16.dp

@Composable
fun ChartLegend(
    items: List<LegendItem>,
    modifier: Modifier = Modifier,
    horizontal: Boolean = false,
) {
    if (horizontal) {
        // 그래프 아래쪽에 가로로 배치할 때는 항목들을 한 줄에 고르게 펼쳐 표시한다.
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item -> LegendEntry(item) }
        }
    } else {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            items.forEach { item ->
                Box(
                    modifier = Modifier.height(LEGEND_ROW_HEIGHT),
                    contentAlignment = Alignment.CenterStart
                ) {
                    LegendEntry(item)
                }
            }
        }
    }
}

@Composable
private fun LegendEntry(item: LegendItem) {
    Row(
        modifier = Modifier.wrapContentWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(item.color)
        )
        Text(
            text = item.label,
            style = MaterialTheme.typography.caption,
            color = SleepTheme.textColors.primary,
            maxLines = 1
        )
    }
}