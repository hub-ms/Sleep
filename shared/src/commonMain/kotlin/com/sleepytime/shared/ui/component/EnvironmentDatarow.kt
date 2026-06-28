package com.sleepytime.shared.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.sleepytime.shared.domain.model.EnvironmentFeature
import com.sleepytime.shared.enum_.EnvironmentCategory
import com.sleepytime.shared.resources.Res
import com.sleepytime.shared.resources.ic_heart_rate
import com.sleepytime.shared.resources.ic_humidity
import com.sleepytime.shared.resources.ic_noise
import com.sleepytime.shared.resources.ic_temperature
import com.sleepytime.shared.ui.home.EnvironmentStatus
import com.sleepytime.shared.ui.home.displayName
import com.sleepytime.shared.ui.theme.EnvironmentColors
import com.sleepytime.shared.ui.report.ReportContract
import com.sleepytime.shared.ui.theme.bodyHighlight
import com.sleepytime.shared.ui.theme.caption
import com.sleepytime.shared.ui.theme.sectionTitle
import org.jetbrains.compose.resources.painterResource

sealed class EnvironmentDisplayMode {
    data class Live(
        val history: List<EnvironmentFeature.Snapshot>
    ) : EnvironmentDisplayMode()

    data class Report(
        val reportData: ReportContract.ReportData
    ) : EnvironmentDisplayMode()
}


private data class EnvironmentValues(
    val heartRate: Float,
    val noise: Float,
    val temperature: Float,
    val humidity: Float,

    val isHeartRateAnomaly: Boolean = false,
    val isNoiseDanger: Boolean = false,
    val isTempExtreme: Boolean = false,
    val isHumidityExtreme: Boolean = false,
)

@Composable
fun EnvironmentDataRow(
    mode: EnvironmentDisplayMode
) {
    val values: EnvironmentValues = when (mode) {
        is EnvironmentDisplayMode.Live -> {
            val latest = mode.history.lastOrNull()

            EnvironmentValues(
                heartRate = latest?.heartRate ?: 0f,
                noise = latest?.noise ?: 0f,
                temperature = latest?.temperature ?: 0f,
                humidity = latest?.humidity ?: 0f
            )
        }

        is EnvironmentDisplayMode.Report -> {
            EnvironmentValues(
                heartRate = mode.reportData.avgHeartRate,
                noise = mode.reportData.avgNoise,
                temperature = mode.reportData.avgTemperature,
                humidity = mode.reportData.avgHumidity,
                isHeartRateAnomaly = mode.reportData.isHeartRateAnomaly,
                isNoiseDanger = mode.reportData.isNoiseDanger,
                isTempExtreme = mode.reportData.isTempExtreme,
                isHumidityExtreme = mode.reportData.isHumidityExtreme,
            )
        }
    }
    EnvironmentDataRowContent(values, mode)

}

@Composable
private fun EnvironmentDataRowContent(
    values: EnvironmentValues,
    mode: EnvironmentDisplayMode
) {
    val baseSectionStyle = MaterialTheme.typography.sectionTitle.toSpanStyle()
    val baseBodyStyle = MaterialTheme.typography.caption.toSpanStyle()

    fun EnvironmentCategory.toStatus(): EnvironmentStatus = when (this) {
        EnvironmentCategory.HEART_RATE -> EnvironmentStatus(
            statusText = when {
                (values.heartRate==0f && mode is EnvironmentDisplayMode.Live) -> "측정 불가"
                values.heartRate<40f -> "낮음"
                values.heartRate in 40f..100f -> "정상"
                else -> "높음"
            },
            primaryColor = when {
                values.heartRate == 0f -> Color.White.copy(0.4f)
                values.isHeartRateAnomaly -> EnvironmentColors.ANOMALY
                else -> EnvironmentColors.NORMAL
            }
        )

        EnvironmentCategory.NOISE -> EnvironmentStatus(
            statusText = when {
                values.noise == 0f -> "측정 불가"
                values.noise < 40f -> "조용함"
                else -> "시끄러움"
            },
            primaryColor = when {
                (values.noise == 0f && mode is EnvironmentDisplayMode.Live) -> Color.White.copy(0.4f)
                values.isNoiseDanger -> EnvironmentColors.ANOMALY
                else -> EnvironmentColors.NORMAL
            }
        )

        EnvironmentCategory.TEMPERATURE -> EnvironmentStatus(
            statusText = when {
                (values.temperature == 0f && mode is EnvironmentDisplayMode.Live) -> "측정 중"
                values.temperature < 18f -> "추움"
                values.temperature in 18f..24f -> "쾌적"
                else -> "더움"
            },
            primaryColor = when {
                values.temperature == 0f -> Color.White.copy(0.4f)
                values.isTempExtreme -> EnvironmentColors.ANOMALY
                else -> EnvironmentColors.NORMAL
            }
        )

        EnvironmentCategory.HUMIDITY -> EnvironmentStatus(
            statusText = when {
                (values.humidity == 0f && mode is EnvironmentDisplayMode.Live) -> "측정 중"
                values.humidity < 40f -> "건조함"
                values.humidity in 40f..60f -> "쾌적"
                else -> "습함"
            },
            primaryColor = when {
                values.humidity == 0f -> Color.White.copy(0.4f)
                values.isHumidityExtreme -> EnvironmentColors.ANOMALY
                else -> EnvironmentColors.NORMAL
            }
        )
    }

    val items = listOf(
        Triple(
            painterResource(Res.drawable.ic_heart_rate),
            EnvironmentCategory.HEART_RATE,
            buildAnnotatedString {
                if (values.heartRate > 0f) {
                    withStyle(baseSectionStyle) { append("${values.heartRate.toInt()}") }
                    withStyle(baseBodyStyle) { append("bpm") }
                } else {
                    withStyle(baseBodyStyle) { append("--") }
                }
            }
        ),
        Triple(
            painterResource(Res.drawable.ic_noise),
            EnvironmentCategory.NOISE,
            buildAnnotatedString {
                if (values.noise > 0f) {
                    withStyle(baseSectionStyle) { append("${values.noise.toInt()}") }
                    withStyle(baseBodyStyle) { append("dB") }
                } else {
                    withStyle(baseBodyStyle) { append("--") }
                }
            }
        ),
        Triple(
            painterResource(Res.drawable.ic_temperature),
            EnvironmentCategory.TEMPERATURE,
            buildAnnotatedString {
                if (values.temperature > 0f) {
                    withStyle(baseSectionStyle) { append("${values.temperature.toInt()}") }
                    withStyle(baseBodyStyle) { append("°C") }
                } else {
                    withStyle(baseBodyStyle) { append("--") }
                }
            }
        ),
        Triple(
            painterResource(Res.drawable.ic_humidity),
            EnvironmentCategory.HUMIDITY,
            buildAnnotatedString {
                if (values.humidity > 0f) {
                    withStyle(baseSectionStyle) { append("${values.humidity.toInt()}") }
                    withStyle(baseBodyStyle) { append("%") }
                } else {
                    withStyle(baseBodyStyle) { append("--") }
                }
            }
        ),
    )

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items.forEach { (icon, label, value) ->
            val status = label.toStatus()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        painter = icon,
                        contentDescription = label.displayName,
                        tint = status.primaryColor
                    )
                    Text(
                        text = label.displayName,
                        style = MaterialTheme.typography.caption,
                        color = Color.White,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyHighlight,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = status.primaryColor.copy(0.4f),
                        border = BorderStroke(1.dp, status.primaryColor)
                    ) {
                        Text(
                            modifier = Modifier.padding(4.dp),
                            text = status.statusText,
                            style = MaterialTheme.typography.caption,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}