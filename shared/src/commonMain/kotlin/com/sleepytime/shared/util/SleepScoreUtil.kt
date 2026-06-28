package com.sleepytime.shared.util

import com.sleepytime.shared.enum_.SleepScoreLevel
import com.sleepytime.shared.ui.report.SleepScoreStatus
import com.sleepytime.shared.ui.theme.SleepScoreColors

fun Int.toSleepScoreStatus(): SleepScoreStatus = when {
    this >= 91 -> SleepScoreStatus(
        "매우 좋음", SleepScoreColors.EXCELLENT, "현재의 수면 습관을 계속 유지하세요", SleepScoreLevel.EXCELLENT
    )

    this >= 81 -> SleepScoreStatus(
        "좋음",
        SleepScoreColors.GOOD,
        "현재의 수면 습관을 유지하되, 작은 개선이 도움이 될 수 있습니다",
        SleepScoreLevel.GOOD
    )

    this >= 61 -> SleepScoreStatus(
        "보통", SleepScoreColors.FAIR, "수면 환경을 개선하고 규칙적인 수면 시간을 유지해 보세요", SleepScoreLevel.FAIR
    )

    else -> SleepScoreStatus(
        "개선 필요", SleepScoreColors.POOR, "수면 환경을 점검하고 수면 전 루틴을 개선해 보세요", SleepScoreLevel.POOR
    )
}