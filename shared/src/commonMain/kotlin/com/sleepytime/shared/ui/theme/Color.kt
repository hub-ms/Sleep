package com.sleepytime.shared.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val BackgroundDark = Color(0xFF0C0C55)
val SurfaceDark = Color(0xFF282878)
val PrimaryDark = Color(0xFF75a3ff)
val SecondaryDark = Color(0xFF5FD0B6)
val ErrorDark = Color(0xFFFF7B7B)

val SleepDarkColors = darkColorScheme(
    background = BackgroundDark,
    primary = PrimaryDark,
    secondary = SecondaryDark,
    surface = SurfaceDark,
    error = ErrorDark,
)
