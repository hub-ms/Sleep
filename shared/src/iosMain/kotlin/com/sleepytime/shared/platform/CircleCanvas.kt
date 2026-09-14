package com.sleepytime.shared.platform

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.NativePaint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb

@Composable
actual fun CircleCanvas(
    isEnabled: Boolean,
) {
    val shadowColor = Color.White.copy(alpha = 0.8f).toArgb()

    Canvas(modifier = Modifier) {
        drawIntoCanvas { canvas ->
            val paint = NativePaint().apply {
                isAntiAlias = true
                color = if (isEnabled) Color.White.toArgb()
                else Color.LightGray.copy(0.4f).toArgb()
            }
            canvas.nativeCanvas.drawCircle(size.width / 2, size.height * 0.35f, 6f, paint)
            canvas.nativeCanvas.drawCircle(size.width / 2, size.height * 0.65f, 6f, paint)
        }
    }
}