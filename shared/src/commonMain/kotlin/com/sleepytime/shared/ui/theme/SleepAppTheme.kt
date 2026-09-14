package com.sleepytime.shared.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Immutable
data class SleepGradients(
    val background: Brush,
    val surface: Brush,
)
@Immutable
data class SleepTextColors(
    val primary: Color,
    val secondary: Color,
)

val DarkTextColors = SleepTextColors(
    primary = Color(0xFFFFFFFF),
    secondary = Color(0xFFA0AEC0),
)

val LocalSleepTextColors = staticCompositionLocalOf { DarkTextColors }
val DarkGradients = SleepGradients(
    background = Brush.verticalGradient(
        colors = listOf(
            BackgroundDark,
            BackgroundDark.copy(0.4f)
        )
    ),
    surface = Brush.verticalGradient(
        colors = listOf(
            SurfaceDark,
            SurfaceDark.copy(0.4f)
        )
    )
)

val LocalSleepGradients = staticCompositionLocalOf { DarkGradients }

@Composable
fun SleepAppTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = SleepDarkColors
    val gradients = DarkGradients
    val textColors = DarkTextColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SleepTypography(),
    ) {
        CompositionLocalProvider(
            LocalSleepGradients provides gradients,LocalSleepTextColors provides textColors,
            content = content
        )
    }
}

object SleepTheme {
    val gradients: SleepGradients
        @Composable
        get() = LocalSleepGradients.current
    val textColors: SleepTextColors
        @Composable
        get() = LocalSleepTextColors.current
}
