package com.sleepytime.shared.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun SleepAppTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) SleepDarkColors else SleepLightColors
    val sleepColors = if (darkTheme) {
        SleepColors(textPrimary = TextPrimaryDark, textSecondary = TextSecondaryDark)
    } else {
        SleepColors(textPrimary = TextPrimaryLight, textSecondary = TextSecondaryLight)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SleepTypography(),
    ) {
        CompositionLocalProvider(
            LocalSleepColors provides sleepColors,
            content = content
        )
    }
}
