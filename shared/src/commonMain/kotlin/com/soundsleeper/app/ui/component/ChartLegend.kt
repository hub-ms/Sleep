package com.soundsleeper.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.soundsleeper.app.ui.report.LegendItem
import com.soundsleeper.app.ui.theme.caption

private val LEGEND_ROW_HEIGHT = 16.dp
@Composable
fun ChartLegend(
    items: List<LegendItem>,
    modifier: Modifier = Modifier,
) {
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

@Composable
private fun LegendEntry(item: LegendItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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
            color = Color.White,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        // 단계별 시간 텍스트는 뺀다. 단계 이름은 타임라인 왼쪽에 붙어 있고, 범례는
        // 비중(percent)만 보여주면 된다.
        Text(
            text = "${item.percent}%",
            style = MaterialTheme.typography.caption,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

