package com.example.sleep.domain.model

import androidx.compose.ui.graphics.Color

// 수면 단계
enum class SleepStage(val stageName: String, val color: Color) {
    AWAKE("각성", Color(0xFFC9C9C9)),
    LIGHT("얕은 수면", Color(0xFF8C9EFF)),
    DEEP("깊은 수면", Color(0xFF536DFE)),
    REM("렘 수면", Color(0xFF3D5AFE))
}
