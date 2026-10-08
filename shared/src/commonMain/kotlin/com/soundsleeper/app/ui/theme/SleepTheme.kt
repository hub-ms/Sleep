package com.soundsleeper.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.soundsleeper.app.enum_.SleepStageType

// ==========================================
// 1. 핵심 수면 도메인 전용 색상 구조체 (@Immutable)
// ==========================================

/** 수면 점수 매핑 컬러 구조체 */
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

/** 수면 단계(그래프용) 매핑 컬러 구조체 */
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

// ==========================================
// 2. 기초 원시 색상 및 Material3 매핑
// ==========================================
// 런처 적응형 아이콘의 배경(#030418)에 맞춘 값들. 앱을 켠 순간의 창 배경
// (androidMain/res/values/colors.xml 의 app_background)과 BackgroundDark 가 같아야
// 스플래시 전환에서 색이 튀지 않는다.
internal val BackgroundDark = Color(0xFF030418)
internal val SurfaceDark = Color(0xFF141A33)
internal val PrimaryDark = Color(0xFF7FA8FF)
internal val SecondaryDark = Color(0xFF5FD0B6)

val SleepDarkColors = darkColorScheme(
    background = BackgroundDark,
    surface = SurfaceDark,
    primary = PrimaryDark,
    secondary = SecondaryDark,
    // 필요 최소한의 온컬러(On-Colors) 가독성을 위해 흰색 계열 유지
    onBackground = Color(0xFFFFFFFF),
    onSurface = Color(0xFFFFFFFF),
    onPrimary = Color(0xFFFFFFFF),
    // 배경색을 손으로 복제해 두면 팔레트를 바꿀 때 한쪽만 바뀐다. 상수를 그대로 참조한다.
    onSecondary = BackgroundDark,
)

val DefaultSleepScoreColors = SleepScoreColors()
val DefaultSleepStageColors = SleepStageColors()

// ==========================================
// 3. CompositionLocal 엔트리 포인트
// ==========================================
val LocalSleepScoreColors = staticCompositionLocalOf { DefaultSleepScoreColors }
val LocalSleepStageColors = staticCompositionLocalOf { DefaultSleepStageColors }

@Composable
fun SleepAppTheme(
    content: @Composable () -> Unit
) {
    // typography 를 넘기지 않으면 Pretendard 정의(sleepTypography)가 아무 데도 닿지 않아
    // 앱 전체가 Material 기본 폰트로 그려진다. 실제로 그 상태였다.
    // sleepTypography 는 titleLarge/bodyLarge/bodyMedium/labelSmall 넷만 재정의하므로
    // headlineSmall·labelMedium 등을 쓰는 자리는 여전히 Material 기본값을 쓴다.
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

// ==========================================
// 4. 통합 싱글톤 오브젝트 (Design System Accessor)
// ==========================================
object SleepTheme {
    // 1) 기본 Core 4가지 색상 (MaterialTheme과 연동)
    val background: Color
        @Composable get() = MaterialTheme.colorScheme.background

    val surface: Color
        @Composable get() = MaterialTheme.colorScheme.surface

    val primary: Color
        @Composable get() = MaterialTheme.colorScheme.primary

    val secondary: Color
        @Composable get() = MaterialTheme.colorScheme.secondary

    val score: SleepScoreColors
        @Composable get() = LocalSleepScoreColors.current

    val stage: SleepStageColors
        @Composable get() = LocalSleepStageColors.current
}
