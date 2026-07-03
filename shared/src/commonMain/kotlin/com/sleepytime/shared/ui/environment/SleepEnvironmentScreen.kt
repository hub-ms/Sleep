package com.sleepytime.shared.ui.environment
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sleepytime.shared.domain.model.EnvironmentFeature
import com.sleepytime.shared.resources.Res
import com.sleepytime.shared.resources.ic_caret_right
import com.sleepytime.shared.resources.ic_heart_rate
import com.sleepytime.shared.resources.ic_humidity
import com.sleepytime.shared.resources.ic_noise
import com.sleepytime.shared.resources.ic_temperature
import com.sleepytime.shared.ui.theme.SleepAppTheme
import com.sleepytime.shared.ui.theme.bodyHighlight
import com.sleepytime.shared.ui.theme.sectionTitle
import kotlinx.datetime.Clock
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.time.Duration.Companion.minutes

data class EnvironmentGraphItem(
    val title: String,
    val icon: DrawableResource,
    val dataExtractor: (EnvironmentFeature.Snapshot) -> Float,
    val unit: String,
    val currentValue: Float,
    val minY: Float,
    val maxY: Float,
    val gridCount: Int,
)

data class RangeSet(
    val normal: List<ClosedFloatingPointRange<Float>>,
    val warningLow: List<ClosedFloatingPointRange<Float>>,
    val warningHigh: List<ClosedFloatingPointRange<Float>>,
)

data class EnvironmentLegend(
    val title: String,
    val ranges: RangeSet,
    val normalText: String,
    val warningLowText: String,
    val warningHighText: String,
)

val EnvironmentLegends = mapOf(
    "심박수" to EnvironmentLegend(
        title = "심박수",
        ranges = RangeSet(
            normal = listOf(50f..70f),
            warningLow = listOf(40f..50f),
            warningHigh = listOf(70f..90f),
        ),
        normalText = "정상",
        warningLowText = "낮음",
        warningHighText = "높음",
    ),
    "소음" to EnvironmentLegend(
        title = "소음",
        ranges = RangeSet(
            normal = listOf(0f..40f),
            warningLow = emptyList(),
            warningHigh = listOf(40f..80f),
        ),
        normalText = "고요",
        warningLowText = "",
        warningHighText = "시끄러움",
    ),
    "온도" to EnvironmentLegend(
        title = "온도",
        ranges = RangeSet(
            normal = listOf(18f..22f),
            warningLow = listOf(10f..18f),
            warningHigh = listOf(22f..30f),
        ),
        normalText = "최적",
        warningLowText = "추움",
        warningHighText = "덥다",
    ),
    "습도" to EnvironmentLegend(
        title = "습도",
        ranges = RangeSet(
            normal = listOf(40f..60f),
            warningLow = listOf(20f..40f),
            warningHigh = listOf(60f..80f),
        ),
        normalText = "쾌적",
        warningLowText = "건조",
        warningHighText = "눅눅함",
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepEnvironmentContent(
    avgHeartRate: Float,
    avgNoise: Float,
    environmentHistory: List<EnvironmentFeature.Snapshot>,
    onBack: () -> Unit
) {
    val graphItems = listOf(
        EnvironmentGraphItem("심박수", Res.drawable.ic_heart_rate, { it.heartRate }, "bpm", avgHeartRate, 40f, 120f, 5),
        EnvironmentGraphItem("소음", Res.drawable.ic_noise, { it.noise }, "dB", avgNoise, 0f, 80f, 5),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("실시간 수면 환경", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_caret_right),
                            contentDescription = "뒤로가기",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(graphItems) { item ->
                EnvironmentGraphCard(
                    item = item,
                    history = environmentHistory,
                )
            }
        }
    }
}

@Composable
fun EnvironmentGraphCard(
    item: EnvironmentGraphItem,
    history: List<EnvironmentFeature.Snapshot>,
) {
    fun Float.getStatus(legend: EnvironmentLegend): String = when {
        legend.ranges.normal.any { this in it } -> "normal"
        legend.ranges.warningLow.any { this in it } -> "warningLow"
        legend.ranges.warningHigh.any { this in it } -> "warningHigh"
        else -> "unknown"
    }

    val legend = EnvironmentLegends[item.title] ?: return
    val status = item.currentValue.getStatus(legend)
    val statusColor = when (status) {
        "normal" -> MaterialTheme.colorScheme.primary
        else -> Color(0xFFEF5350)
    }
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        painter = painterResource(item.icon),
                        contentDescription = item.title,
                        tint = statusColor,
                    )
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyHighlight,
                        color = Color.White
                    )
                }
                Text(
                    text = "${item.currentValue}${item.unit}",
                    style = MaterialTheme.typography.sectionTitle,
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
            }
            SingleLineChart(
                data = history,
                dataExtractor = item.dataExtractor,
                minY = item.minY,
                maxY = item.maxY,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            )
        }
    }
}

@Composable
fun SingleLineChart(
    data: List<EnvironmentFeature.Snapshot>,
    dataExtractor: (EnvironmentFeature.Snapshot) -> Float,
    modifier: Modifier = Modifier,
    minY: Float,
    maxY: Float
) {
    val graphColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        if (data.isEmpty()) return@Canvas

        val width = size.width
        val height = size.height
        val leftPadding = width * 0.2f
        val graphWidth = width * 0.8f
        val range = (maxY - minY).coerceAtLeast(1f)

        drawSimpleGrid(leftPadding, graphWidth, height, 5)

        val points = data.map { snapshot ->
            val xRatio = 1f // Simplification for KMP demo
            val x = leftPadding + xRatio * graphWidth
            val value = dataExtractor(snapshot)
            val normalized = ((value - minY) / range).coerceIn(0f, 1f)
            val y = height * (1f - normalized)
            Offset(x, y)
        }

        if (points.size > 1) {
            val path = Path().apply {
                smoothLine(points)
            }
            drawPath(path = path, color = graphColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

fun DrawScope.drawSimpleGrid(leftPadding: Float, graphWidth: Float, height: Float, gridCount: Int) {
    val gridColor = Color.White.copy(0.4f)
    for (i in 0 until gridCount) {
        val y = height * i / (gridCount - 1)
        drawLine(gridColor, Offset(leftPadding, y), Offset(leftPadding + graphWidth, y), 1.dp.toPx())
    }
}

fun Path.smoothLine(points: List<Offset>) {
    if (points.size < 2) return
    moveTo(points.first().x, points.first().y)
    for (i in 1 until points.size) {
        val prev = points[i - 1]
        val current = points[i]
        val midPoint = Offset((prev.x + current.x) / 2f, (prev.y + current.y) / 2f)
        quadraticTo(prev.x, prev.y, midPoint.x, midPoint.y)
    }
    lineTo(points.last().x, points.last().y)
}

@Preview
@Composable
fun SleepEnvironmentScreenPreview() {
    SleepAppTheme {
        SleepEnvironmentContent(
            avgHeartRate = 65f,
            avgNoise = 35f,
            environmentHistory = listOf(
                EnvironmentFeature.Snapshot(22f, 50f),
                EnvironmentFeature.Snapshot(23f, 52f)
            ),
            onBack = {}
        )
    }
}
