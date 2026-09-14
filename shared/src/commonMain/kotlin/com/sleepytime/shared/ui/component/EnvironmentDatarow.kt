package com.sleepytime.shared.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sleepytime.shared.domain.model.EnvironmentFeature
import com.sleepytime.shared.enum_.EnvironmentCategory
import com.sleepytime.shared.ui.home.EnvironmentStatus
import com.sleepytime.shared.ui.report.CHART_TOP_PADDING
import com.sleepytime.shared.ui.report.LABEL_WIDTH
import com.sleepytime.shared.ui.report.Y_AXIS_PADDING
import com.sleepytime.shared.ui.theme.SleepTheme
import com.sleepytime.shared.ui.theme.caption
import com.sleepytime.shared.util.ChartUtil
import kotlin.math.roundToInt

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
            values.noiseAvg == 0f -> Color.White.copy(0.4f)
            values.isNoiseDanger -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.primary
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
    val dataPoints = remember(category, values.history) {
        when (category) {
            EnvironmentCategory.NOISE -> values.history.map { it.noise }
        }.filter { it > 0f }
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
            val usableWidthPx =
                (viewportWidthPx - with(density) { LABEL_WIDTH.toPx() }).coerceAtLeast(1f)
            val stepX = if (dataPoints.size > 1) usableWidthPx / (dataPoints.size - 1) else 0f

            val maxVal = when (category) {
                EnvironmentCategory.NOISE -> values.history.maxOf { it.noise }
            }
            val minVal = when (category) {
                EnvironmentCategory.NOISE -> values.history.minOf { it.noise }
            }
            val range = (maxVal - minVal).coerceAtLeast(1f)

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
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
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
                                color = Color.White.copy(alpha = 0.08f),
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 1.dp.toPx()
                            )
                        }

                        fun yFor(value: Float): Float {
                            val normalized = (value - minVal) / range
                            return size.height - (normalized * usableHeight)
                        }

                        val path = Path()
                        val points = dataPoints.mapIndexed { index, value ->
                            Offset(index * stepX, yFor(value))
                        }

                        if (points.isNotEmpty()) {
                            path.moveTo(points[0].x, points[0].y)
                            for (i in 1 until points.size) {
                                val prev = points[i - 1]
                                val curr = points[i]
                                path.quadraticTo(
                                    prev.x, prev.y,
                                    (prev.x + curr.x) / 2f, (prev.y + curr.y) / 2f
                                )
                            }
                            path.lineTo(points.last().x, points.last().y)
                        }
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
                    color = SleepTheme.textColors.primary.copy(0.5f)
                )
            }
        }
    }
}