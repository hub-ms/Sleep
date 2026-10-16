package com.soundsleeper.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.soundsleeper.app.enum_.SleepStageType

@Immutable
data class SleepScoreColors(
    val excellent: Color = Color(0xFF2BC9A8),
    val good: Color = Color(0xFF4D9BF0),
    val fair: Color = Color(0xFFFFA726),
    val poor: Color = Color(0xFFEF5350)
)
fun SleepScoreColors.getColorForScore(score: Int): Color = when {
    score >= 81 -> excellent
    score >= 61 -> good
    score >= 41 -> fair
    else -> poor
}
@Immutable
data class SleepStageColors(
    val awake: Color = Color(0xFFFFB04D), // 주황 - 어두운 배경에서도 눈에 잘 띄는 경고성 색상
    val light: Color = Color(0xFF5AA9F5), // 선명한 파랑 - REM/DEEP과 명확히 구분되는 채도/밝기
    val rem: Color = Color(0xFFF06292),   // 핑크 - 기존 청록은 LIGHT와 가깝고 헷갈렸음
    val deep: Color = Color(0xFF8B6FD4)   // 진보라 - 기존 남색은 어두운 배경과 대비가 낮았음
)
fun SleepStageColors.getColorForStage(stageType: SleepStageType): Color = when (stageType) {
    SleepStageType.AWAKE -> awake
    SleepStageType.LIGHT -> light
    SleepStageType.REM -> rem
    SleepStageType.DEEP -> deep
}
internal val BackgroundDark = Color(0xFF070913)
internal val SurfaceDark = Color(0xFF5D6B9E) // 대비율 4.53 : 1 확보
internal val PrimaryDark = Color(0xFF90B4FF)
internal val SecondaryDark = Color(0xFFB19DFF)
internal val ErrorDark = Color(0xFFE94A6A)


val SleepDarkColors = darkColorScheme(
    background = BackgroundDark,
    surface = SurfaceDark,
    primary = PrimaryDark,
    secondary = SecondaryDark,
    error = ErrorDark,
)

val DefaultSleepScoreColors = SleepScoreColors()
val DefaultSleepStageColors = SleepStageColors()
val LocalSleepScoreColors = staticCompositionLocalOf { DefaultSleepScoreColors }
val LocalSleepStageColors = staticCompositionLocalOf { DefaultSleepStageColors }

@Composable
fun SleepAppTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SleepDarkColors,
        typography = sleepTypography(),
    ) {
        CompositionLocalProvider(
            LocalSleepScoreColors provides DefaultSleepScoreColors,
            LocalSleepStageColors provides DefaultSleepStageColors,
            content = content
        )
    }
}
object SleepTheme {
    val background: Color
        @Composable get() = MaterialTheme.colorScheme.background
    val surface: Color
        @Composable get() = MaterialTheme.colorScheme.surface
    val primary: Color
        @Composable get() = MaterialTheme.colorScheme.primary
    val secondary: Color
        @Composable get() = MaterialTheme.colorScheme.secondary
    val warning: Color
        @Composable get() = MaterialTheme.colorScheme.error
    val score: SleepScoreColors
        @Composable get() = LocalSleepScoreColors.current
    val stage: SleepStageColors
        @Composable get() = LocalSleepStageColors.current
}
