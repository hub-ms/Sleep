package com.example.sleep.presentation.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sleep.domain.SleepStage
import com.example.sleep.presentation.viewmodel.ReportViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class StagePoint(val minute: Float, val stage: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    reportViewModel: ReportViewModel = viewModel(),
) {
    val report = reportViewModel.report
    val sleepStages = listOf(
        StagePoint(0f, 0),
        StagePoint(10f, 2),
        StagePoint(30f, 3),
        StagePoint(60f, 1),
        StagePoint(90f, 0)
    )
    LocalDate.now().format(DateTimeFormatter.ofPattern("M월 d일"))
    Column(
        modifier = Modifier
            .background(Color(0xFF0A021D))
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        if (report == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Text("수면 점수", color = Color.White)
            Text(
                "${report.sleepScore}",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(24.dp))

            Text("총 수면 시간", color = Color.White)
            Text(report.totalSleepTime, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(50.dp))
            Text("수면 단계", color = Color.White)
            SleepStageLineChart(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                stages = sleepStages
            )

            Text("단계별 분석", color = Color.White)
            val orderedStages = listOf(SleepStage.AWAKE, SleepStage.REM, SleepStage.LIGHT, SleepStage.DEEP)
            orderedStages.forEach { stage ->
                val duration = report.stageDurations[stage] ?: "0시간 0분"
                val stageName = when (stage) {
                    SleepStage.LIGHT -> "얕은 수면"
                    SleepStage.DEEP -> "깊은 수면"
                    else -> stage.stageName
                }
                StageDurationItem(stage = stage, stageName = stageName, duration = duration)
            }
        }
    }
}

@Composable
fun SleepStageLineChart(
    modifier: Modifier = Modifier,
    stages: List<StagePoint>,
    maxMinutes: Float? = null
) {
    val background = Color(0xFF0A021D)
    val lineColor = Color.White
    val fillColor = Color(0xFF1B0630).copy(alpha = 0.7f)

    val density = LocalDensity.current
    val paddingDp = 12.dp
    val paddingPx = with(density) { paddingDp.toPx() }
    with(density) { 12.sp.toPx() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(background)
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Canvas(modifier = modifier.fillMaxWidth().height(200.dp)) {
            if (stages.isEmpty()) return@Canvas

            val w = size.width
            val h = size.height
            val plotLeft = paddingPx * 2f
            val plotRight = w - paddingPx
            val plotBottom = h - paddingPx * 1.6f
            val plotWidth = plotRight - plotLeft
            val plotHeight = plotBottom - paddingPx

            val maxMin = (maxMinutes ?: stages.maxOf { it.minute }).coerceAtLeast(1f)

            val points = stages.map { sp ->
                val x = plotLeft + (sp.minute / maxMin) * plotWidth
                val y = paddingPx + (1f - (sp.stage.coerceIn(0, 3) / 3f)) * plotHeight
                Offset(x, y)
            }

            // 부드러운 Path (quadratic spline) - 선용
            val linePath = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    val prev = points[i - 1]
                    val cur = points[i]
                    val mid = Offset((prev.x + cur.x) / 2f, (prev.y + cur.y) / 2f)
                    quadraticTo(prev.x, prev.y, mid.x, mid.y)
                }
                val last = points.last()
                val secondLast = points[points.lastIndex - 1]
                quadraticTo(secondLast.x, secondLast.y, last.x, last.y)
            }

            // 채움용 Path: 동일한 곡선으로 만든 뒤 바닥으로 닫기
            val fillPath = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    val prev = points[i - 1]
                    val cur = points[i]
                    val mid = Offset((prev.x + cur.x) / 2f, (prev.y + cur.y) / 2f)
                    quadraticTo(prev.x, prev.y, mid.x, mid.y)
                }
                val last = points.last()
                val secondLast = points[points.lastIndex - 1]
                quadraticTo(secondLast.x, secondLast.y, last.x, last.y)

                lineTo(points.last().x, plotBottom)
                lineTo(points.first().x, plotBottom)
                close()
            }

            // 채움 -> 선 순으로 그림
            drawPath(
                path = fillPath,
                color = fillColor
            )

            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(width = 4f)
            )

            listOf("기상", "얕은", "REM", "깊은")
            Paint().apply {
                isAntiAlias = true
                color = lineColor
            }
            for (i in 0..3) {
                val y = paddingPx + (1f - i / 3f) * plotHeight
                drawLine(
                    color = Color.White.copy(alpha = 0.06f),
                    start = Offset(plotLeft, y),
                    end = Offset(plotRight, y),
                    strokeWidth = 1f
                )
            }

            val xLabels = listOf(0f, maxMin / 2f, maxMin)
            for (xm in xLabels) {
                plotLeft + (xm / maxMin) * plotWidth

            }
        }
    }
}

@Composable
fun StageDurationItem(stage: SleepStage, stageName: String, duration: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(stage.color)
        )
        Text(
            text = stageName,
            modifier = Modifier
                .padding(start = 8.dp)
                .weight(1f),
            fontWeight = FontWeight.Medium
        )
        Text(text = duration, fontWeight = FontWeight.Medium)
    }
}
