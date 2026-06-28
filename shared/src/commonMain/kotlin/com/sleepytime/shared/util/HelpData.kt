package com.sleepytime.shared.util

import androidx.compose.ui.graphics.painter.Painter
import com.sleepytime.shared.enum_.HelpCategory

data class HelpItem(
    val icon: List<Painter?> = emptyList(),
    val question: String,
    val answer: String,
    val category: HelpCategory = HelpCategory.GENERAL,
)

object HelpData {
    val allHelpItems = listOf(
        HelpItem(
            question = "수면 오각형(레이더) 차트는 무엇인가요?",
            answer = "5가지 핵심 수면 지표의 균형을 시각화합니다. 중심에서 멀어져 오각형의 크기가 커질수록 해당 수면 지표가 건강하고 이상적입니다.",
            category = HelpCategory.CHARTS,
        ),
        HelpItem(
            question = "계단식 타임라인 그래프는 어떻게 읽나요?",
            answer = "수면 중 시간에 따른 단계 변화를 나타냅니다. 위아래로 꺾이는 선(계단 모양)은 수면 상태가 전환되었음을 뜻합니다.",
            category = HelpCategory.CHARTS,
        ),
        HelpItem(
            question = "수면 점수는 어떻게 계산되나요?",
            answer = "수면 점수는 수면 시간, 깊은 수면 비율, 수면 연속성 등 여러 요소를 종합적으로 평가합니다.",
            category = HelpCategory.GENERAL,
        ),
        HelpItem(
            question = "좋은 수면 품질의 기준은 무엇인가요?",
            answer = "일반적으로 7-9시간의 충분한 수면 시간, 깊은 수면 15-20%, REM 수면 20-25%를 권장합니다.",
            category = HelpCategory.TIPS,
        ),
        HelpItem(
            question = "스마트 알람은 무엇인가요?",
            answer = "설정한 알람 시간 전후의 얕은 수면 단계에서 알람을 울려 더 개운하게 깨어날 수 있도록 도와주는 기능입니다.",
            category = HelpCategory.GENERAL,
        )
    )
    val sleepChartHelp = allHelpItems.filter { it.category == HelpCategory.CHARTS }
}
