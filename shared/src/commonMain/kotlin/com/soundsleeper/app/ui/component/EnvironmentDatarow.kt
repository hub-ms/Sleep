package com.soundsleeper.app.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.soundsleeper.app.domain.model.EnvironmentFeature
import com.soundsleeper.app.enum_.EnvironmentCategory
import com.soundsleeper.app.ui.home.EnvironmentStatus
import com.soundsleeper.app.ui.report.CHART_TOP_PADDING
import com.soundsleeper.app.ui.report.LABEL_WIDTH
import com.soundsleeper.app.ui.report.Y_AXIS_PADDING
import com.soundsleeper.app.ui.theme.SleepTheme
import com.soundsleeper.app.ui.theme.SleepTheme.primary
import com.soundsleeper.app.util.ChartUtil.chunkedAverageOrNull
import com.soundsleeper.app.util.ChartUtil.trimAndInterpolateGaps
import com.soundsleeper.app.util.ChartUtil

data class EnvironmentValues(
    val noiseAvg: Float = 0f,
    val noiseMax: Float = 0f,
    val noiseMin: Float = 0f,
    val isNoiseDanger: Boolean = false,
    val history: List<EnvironmentFeature.Snapshot> = emptyList()
)

@Composable
fun EnvironmentCategory.toStatus(values: EnvironmentValues): EnvironmentStatus = when (this) {
    EnvironmentCategory.NOISE -> EnvironmentStatus(
        primaryColor = when {
            values.noiseAvg == 0f -> Color.White
            values.isNoiseDanger -> MaterialTheme.colorScheme.error
            else -> primary
        }
    )
}

@Composable
fun EnvironmentChart(
    category: EnvironmentCategory,
    values: EnvironmentValues, // values.history에 전체 데이터 리스트가 있다고 가정
    color: Color,
    labelStyle: TextStyle,
) {
    // 30초 표본을 그대로 그리면 점이 너무 촘촘해 밤새 그래프가 뭉개진다. 10분 평균으로 묶는다.
    // 0(측정값 없음)을 단순히 걸러내던 예전 방식은 센서 공백만큼 x축을 조용히 압축해
    // 시간 축이 실제와 어긋났다. 지금은 끝단 결측만 잘라내고 중간은 보간한다.
    val dataPoints = remember(category, values.history) {
        when (category) {
            EnvironmentCategory.NOISE -> values.history.map { it.noise }
        }
            .chunkedAverageOrNull()
            .trimAndInterpolateGaps()
    }

    val environmentGraphYLabels = remember(category, dataPoints) {
        when (category) {
            EnvironmentCategory.NOISE -> ChartUtil.generateYLabels(inputNoises = dataPoints)
        }
    }

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    var viewportWidthPx by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { size -> viewportWidthPx = size.width.toFloat() }
    ) {
        if (viewportWidthPx > 0f && dataPoints.isNotEmpty()) {
            // SleepTimeLineChart(수면 단계 그래프)와 플롯 가로 길이를 맞추기 위해, 왼쪽 라벨 폭(LABEL_WIDTH)
            // 뒤에 Y_AXIS_PADDING만큼의 간격을, 오른쪽 끝에도 동일하게 Y_AXIS_PADDING만큼의 여백을 둔다.
            val usableWidthPx =
                (viewportWidthPx - with(density) { LABEL_WIDTH.toPx() + Y_AXIS_PADDING.toPx() * 2 }).coerceAtLeast(
                    1f
                )
            val stepX = if (dataPoints.size > 1) usableWidthPx / (dataPoints.size - 1) else 0f

            // 그리는 값과 같은 series 에서 범위를 잡아야 한다. 예전에는 원본 history(0 포함,
            // 30초 표본)에서 min/max 를 구해 곡선 스케일이 실제로 그리는 점들과 어긋났다.
            val maxVal = dataPoints.max()
            val minVal = dataPoints.min()
            val range = (maxVal - minVal).coerceAtLeast(1f)
            val dividerColor = SleepTheme.surface

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp) // 높이를 조금 키우면 시인성이 좋아집니다.
            ) {
                Canvas(modifier = Modifier.width(LABEL_WIDTH).fillMaxHeight()) {
                    val usableHeight = (size.height - CHART_TOP_PADDING.toPx()).coerceAtLeast(1f)
                    val stepY = usableHeight / (environmentGraphYLabels.size - 1).coerceAtLeast(1)

                    environmentGraphYLabels.forEachIndexed { i, label ->
                        val y = size.height - i * stepY
                        val measured = textMeasurer.measure(style = labelStyle, text = label)
                        drawText(
                            textLayoutResult = measured,
                            topLeft = Offset(
                                x = (size.width - Y_AXIS_PADDING.toPx() - measured.size.width).coerceAtLeast(
                                    0f
                                ),
                                y = y - measured.size.height / 2f
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.width(Y_AXIS_PADDING))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(end = Y_AXIS_PADDING)
                ) {
                    Canvas(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val usableHeight =
                            (size.height - CHART_TOP_PADDING.toPx()).coerceAtLeast(1f)
                        val stepY =
                            usableHeight / (environmentGraphYLabels.size - 1).coerceAtLeast(1)
                        environmentGraphYLabels.indices.forEach { i ->
                            val y = size.height - i * stepY
                            drawLine(
                                color = dividerColor,
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 1.dp.toPx()
                            )
                        }

                        fun yFor(value: Float): Float {
                            val normalized = (value - minVal) / range
                            return size.height - (normalized * usableHeight)
                        }

                        val points = dataPoints.mapIndexed { index, value ->
                            Offset(index * stepX, yFor(value))
                        }
                        // 예전에는 중점을 향해 quadraticTo 로 이어서 실제 데이터 점을 지나지 않았다.
                        // monotone cubic 은 점을 정확히 지나면서 구간 밖으로 튀지 않는다.
                        val path = ChartUtil.monotoneCubicPath(points)
                        val fillPath = Path().apply {
                            addPath(path)
                            if (points.isNotEmpty()) {
                                lineTo(points.last().x, size.height)
                                lineTo(points.first().x, size.height)
                                close()
                            }
                        }
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(color.copy(0.2f), Color.Transparent),
                                startY = 0f,
                                endY = size.height
                            )
                        )
                        drawPath(
                            path = path,
                            color = color,
                            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
            }
        } else {
            // 데이터가 없을 때의 UI
            Box(
                modifier = Modifier.fillMaxWidth().height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "측정된 데이터가 없어요",
                    style = labelStyle,
                    color = Color.White.copy(0.5f)
                )
            }
        }
    }
}