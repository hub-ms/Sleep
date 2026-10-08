package com.soundsleeper.app.ui.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.soundsleeper.app.enum_.SleepStageType
import com.soundsleeper.app.resources.Res
import com.soundsleeper.app.resources.report_total_sleep
import com.soundsleeper.app.resources.report_unit_hour
import com.soundsleeper.app.resources.report_unit_minute
import com.soundsleeper.app.ui.theme.SleepTheme
import com.soundsleeper.app.ui.theme.bodyHighlight
import com.soundsleeper.app.ui.theme.caption
import com.soundsleeper.app.ui.theme.getColorForStage
import com.soundsleeper.app.util.DateTimeUtil.formatSleepDurationFromMillis
import org.jetbrains.compose.resources.stringResource
import kotlin.math.floor
import kotlin.math.roundToLong

private const val DONUT_GAP_DEGREES = 2f
@Composable
fun rememberStageBreakdown(
    stageTypes: List<SleepStageType>,
    data: ReportContract.ReportData?
): List<LegendItem> {
    val stageColors = SleepTheme.stage
    val stageNames = stageTypes.associateWith { stringResource(it.stageNameRes) }
    return remember(data, stageNames) {
        if (data == null) return@remember emptyList()

        val minutesByStage = mapOf(
            SleepStageType.AWAKE to data.awakeMinutes,
            SleepStageType.LIGHT to data.lightMinutes,
            SleepStageType.REM to data.remMinutes,
            SleepStageType.DEEP to data.deepMinutes,
        )
        // stageTypes 의 순서(AWAKE, LIGHT, REM, DEEP)는 enum 선언 순서와 다르므로
        // ordinal 로 인덱싱하지 말고 항상 stageTypes 를 기준으로 맞춘다.
        val millisByStage = stageTypes.map { type ->
            (minutesByStage.getValue(type) * 60000).roundToLong().coerceAtLeast(0L)
        }
        val total = millisByStage.sum()
        if (total <= 0L) return@remember emptyList()

        val percents = largestRemainderPercents(millisByStage, total)
        stageTypes.mapIndexed { index, type ->
            LegendItem(
                label = stageNames.getValue(type),
                color = stageColors.getColorForStage(stageTypes[index]),
                duration = millisByStage[index],
                percent = percents[index]
            )
        }
    }
}

/**
 * 각 항목을 내림한 뒤 남은 몫을 소수부가 큰 순서대로 1%씩 나눠준다.
 * 항목별로 그냥 반올림하면 합이 99나 101이 되어 사용자 눈에 띄기 때문에,
 * 합이 정확히 100이 되도록 보정한다.
 */
private fun largestRemainderPercents(values: List<Long>, total: Long): List<Int> {
    val exact = values.map { it.toDouble() / total * 100.0 }
    val result = exact.map { floor(it).toInt() }.toMutableList()

    var remaining = 100 - result.sum()
    if (remaining <= 0) return result

    val byFractionDesc = exact.indices.sortedByDescending { exact[it] - floor(exact[it]) }
    var cursor = 0
    while (remaining > 0) {
        result[byFractionDesc[cursor % byFractionDesc.size]]++
        remaining--
        cursor++
    }
    return result
}

/**
 * 수면 단계 비율 도넛 그래프.
 *
 * @param breakdown [rememberStageBreakdown] 의 결과. 링은 네 단계 전체를 100%로 그린다.
 * @param centerMillis 가운데에 표시할 실제 수면 시간(깨어난 시간 제외).
 */
@Composable
fun SleepStageDonut(
    breakdown: List<LegendItem>,
    centerMillis: Long,
    modifier: Modifier = Modifier,
    diameter: Dp = 132.dp,
    strokeWidth: Dp = 20.dp,
) {
    if (breakdown.isEmpty()) return

    val ringTotal = breakdown.sumOf { it.duration }
    if (ringTotal <= 0L) return
    val slices = breakdown.filter { it.duration > 0L }

    Box(
        modifier = modifier.size(diameter),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            // drawArc 의 선은 경계선을 중심으로 그려지므로, 선 두께의 절반만큼 안쪽으로 들여야
            // 링이 캔버스 밖으로 잘리지 않는다.
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val arcTopLeft = Offset(stroke / 2f, stroke / 2f)
            val gap = if (slices.size > 1) DONUT_GAP_DEGREES else 0f

            var startAngle = -90f
            slices.forEach { slice ->
                val sweep = slice.duration.toFloat() / ringTotal * 360f
                drawArc(
                    color = slice.color,
                    startAngle = startAngle + gap / 2f,
                    sweepAngle = (sweep - gap).coerceAtLeast(0.5f),
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = stroke)
                )
                startAngle += sweep
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = formatSleepDurationFromMillis(
                    centerMillis,
                    hourUnit = stringResource(Res.string.report_unit_hour),
                    minuteUnit = stringResource(Res.string.report_unit_minute),
                ),
                style = MaterialTheme.typography.bodyHighlight.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Text(
                text = stringResource(Res.string.report_total_sleep),
                style = MaterialTheme.typography.caption,
                color = Color.White
            )
        }
    }
}
