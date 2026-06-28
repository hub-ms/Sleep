package com.sleepytime.shared.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// Light Theme Colors
val BackgroundLight = Color(0xFFF7FAFC)
val PrimaryLight = Color(0xFF4F7DF3)
val SecondaryLight = Color(0xFF7BC6B6)
val SurfaceLight = Color(0xFFFFFFFF)
val ErrorLight = Color(0xFFE86A6A)
val TextPrimaryLight = Color(0xFF1F2937)
val TextSecondaryLight = Color(0xFF6B7280)

val OnBackgroundLight = Color(0xFF1F2937)
val OnSurfaceLight = Color(0xFF1F2937)
val OnPrimaryLight = Color(0xFFFFFFFF)
val OnSecondaryLight = Color(0xFF1F2937)
val OnErrorLight = Color(0xFFFFFFFF)

// Dark Theme Colors
val BackgroundDark = Color(0xFF0F172A)
val PrimaryDark = Color(0xFF7AA2FF)
val SecondaryDark = Color(0xFF5FD0B6)
val SurfaceDark = Color(0xFF162033)
val ErrorDark = Color(0xFFFF7B7B)
val TextPrimaryDark = Color(0xFFE5E7EB)
val TextSecondaryDark = Color(0xFF94A3B8)

val OnBackgroundDark = Color(0xFFE5E7EB)
val OnSurfaceDark = Color(0xFFE5E7EB)
val OnPrimaryDark = Color(0xFF0F172A)
val OnSecondaryDark = Color(0xFF0F172A)
val OnErrorDark = Color(0xFF0F172A)

data class SleepColors(
    val textPrimary: Color,
    val textSecondary: Color
)

val LocalSleepColors = compositionLocalOf { SleepColors(textPrimary = TextPrimaryLight, textSecondary = TextSecondaryLight) }

val SleepLightColors = lightColorScheme(
    background = BackgroundLight,
    primary = PrimaryLight,
    secondary = SecondaryLight,
    surface = SurfaceLight,
    error = ErrorLight,
    onBackground = OnBackgroundLight,
    onSurface = OnSurfaceLight,
    onPrimary = OnPrimaryLight,
    onSecondary = OnSecondaryLight,
    onError = OnErrorLight,
)

val SleepDarkColors = darkColorScheme(
    background = BackgroundDark,
    primary = PrimaryDark,
    secondary = SecondaryDark,
    surface = SurfaceDark,
    error = ErrorDark,
    onBackground = OnBackgroundDark,
    onSurface = OnSurfaceDark,
    onPrimary = OnPrimaryDark,
    onSecondary = OnSecondaryDark,
    onError = OnErrorDark
)
