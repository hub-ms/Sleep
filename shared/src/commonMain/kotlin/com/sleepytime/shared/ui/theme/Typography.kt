package com.sleepytime.shared.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sleepytime.shared.resources.Res
import com.sleepytime.shared.resources.pretendard_bold
import com.sleepytime.shared.resources.pretendard_medium
import com.sleepytime.shared.resources.pretendard_regular
import org.jetbrains.compose.resources.Font

@Composable
fun rememberPretendard(): FontFamily {
    val regular = Font(Res.font.pretendard_regular, FontWeight.Normal)
    val medium  = Font(Res.font.pretendard_medium,  FontWeight.Medium)
    val bold    = Font(Res.font.pretendard_bold,    FontWeight.Bold)
    return remember { FontFamily(regular, medium, bold) }
}

@Composable
fun SleepTypography(): Typography {
    val pretendard = rememberPretendard()
    return remember(pretendard) {
        Typography(
            titleLarge = TextStyle(
                fontFamily = pretendard,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 32.sp
            ),
            bodyLarge = TextStyle(
                fontFamily = pretendard,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 24.sp
            ),
            bodyMedium = TextStyle(
                fontFamily = pretendard,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = 20.sp
            ),
            labelSmall = TextStyle(
                fontFamily = pretendard,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = 16.sp
            )
        )
    }
}

val Typography.sectionTitle: TextStyle  get() = titleLarge
val Typography.bodyHighlight: TextStyle get() = bodyLarge
val Typography.bodyText: TextStyle      get() = bodyMedium
val Typography.caption: TextStyle       get() = labelSmall