package com.soundsleeper.app.enum_

import com.soundsleeper.app.ComponentResource
import com.soundsleeper.app.resources.Res
import com.soundsleeper.app.resources.report_range_today
import com.soundsleeper.app.resources.report_range_week
import org.jetbrains.compose.resources.StringResource

enum class ReportRangeTab(override val resId: StringResource) : ComponentResource {
    TODAY(Res.string.report_range_today),
    WEEK(Res.string.report_range_week),
}