package com.somniotune.app.enum_

import com.somniotune.app.ComponentResource
import com.somniotune.app.resources.Res
import com.somniotune.app.resources.report_range_today
import com.somniotune.app.resources.report_range_week
import org.jetbrains.compose.resources.StringResource

enum class ReportRangeTab(override val resId: StringResource) : ComponentResource {
    TODAY(Res.string.report_range_today),
    WEEK(Res.string.report_range_week),
}