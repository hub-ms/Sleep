package com.sleepytime.shared.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

    val isHeartRateAnomaly: Boolean = false,
    val isNoiseDanger: Boolean = false,
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
            )
        }

        is EnvironmentDisplayMode.Report -> {
            EnvironmentValues(
                heartRate = mode.reportData.avgHeartRate,
                noise = mode.reportData.avgNoise,
                isHeartRateAnomaly = mode.reportData.isHeartRateAnomaly,
                isNoiseDanger = mode.reportData.isNoiseDanger,
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
            primaryColor = when {
                values.heartRate == 0f -> Color.White.copy(0.4f)
                values.isHeartRateAnomaly -> EnvironmentColors.ANOMALY
                else -> EnvironmentColors.NORMAL
            }
        )

        EnvironmentCategory.NOISE -> EnvironmentStatus(
            primaryColor = when {
                (values.noise == 0f && mode is EnvironmentDisplayMode.Live) -> Color.White.copy(0.4f)
                values.isNoiseDanger -> EnvironmentColors.ANOMALY
                else -> EnvironmentColors.NORMAL
            }
        )
    }
    val heartRateText = remember(values.heartRate) {
        buildAnnotatedString {
            withStyle(baseSectionStyle) { append("${values.heartRate.toInt()}") }
            withStyle(baseBodyStyle) { append("bpm") }
        }
    }
    val noiseText = remember(values.noise) {
        buildAnnotatedString {
            withStyle(baseSectionStyle) { append("${values.noise.toInt()}") }
            withStyle(baseBodyStyle) { append("dB") }
        }
    }
    val environmentItems = listOf(
        Triple(painterResource(Res.drawable.ic_heart_rate), EnvironmentCategory.HEART_RATE, heartRateText),
        Triple(painterResource(Res.drawable.ic_noise), EnvironmentCategory.NOISE, noiseText)
    )
    Column(
        modifier = Modifier
            .border(2.dp, Color.Green),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        environmentItems.forEach { (icon, label, value) ->
            val status = label.toStatus()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(
                    modifier = Modifier.size(24.dp),
                    painter = icon,
                    contentDescription = label.displayName,
                    tint = status.primaryColor
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyHighlight,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}