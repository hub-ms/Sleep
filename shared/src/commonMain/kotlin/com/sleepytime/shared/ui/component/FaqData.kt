package com.sleepytime.shared.ui.component

import com.sleepytime.shared.enum_.FaqCategory


data class FaqItem(
    val category: FaqCategory,
    val question: String,
    val answer: String
)

object FaqData {
    val items = listOf(
        // ================= 수면 =================
        FaqItem(
            FaqCategory.SLEEP,
            "수면 기록이 시작되지 않아요",
            "앱이 백그라운드에서도 실행 중인지 확인하고, 모든 권한 설정을 허용해주세요."
        ),
        FaqItem(
            FaqCategory.SLEEP,
            "수면 중 중간에 끊겨요",
            "배터리 최적화 모드로 인해 종료될 수 있으니 예외 앱으로 등록해주세요."
        ),
        FaqItem(
            FaqCategory.SLEEP,
            "수면 데이터가 저장되지 않아요",
            "수면 종료 후 자동으로 저장되며, 현재 계정 로그인 상태를 확인해주세요."
        ),
        FaqItem(
            FaqCategory.SLEEP,
            "수면 측정 정확도가 낮은 것 같아요",
            "조용한 환경에서 사용하면 정확도가 더욱 향상됩니다."
        ),

        // ================= 알람 =================
        FaqItem(
            FaqCategory.ALARM,
            "알람이 울리지 않아요",
            "시스템 무음 모드, 방해금지 모드, 그리고 앱의 알림 권한을 확인해주세요."
        ),
        FaqItem(
            FaqCategory.ALARM,
            "알람이 너무 빨리 울려요",
            "스마트 알람 범위를 설정에서 조정해보세요."
        ),
        FaqItem(
            FaqCategory.ALARM,
            "알람 소리를 변경하고 싶어요",
            "수면 설정 메뉴에서 다양한 알람음으로 변경할 수 있습니다."
        ),

        // ================= 분석 =================
        FaqItem(
            FaqCategory.ANALYSIS,
            "수면 점수는 어떻게 계산되나요?",
            "총 수면 시간, 깊은 수면 비율, 중간 각성 횟수 등을 종합하여 계산됩니다."
        ),
        FaqItem(
            FaqCategory.ANALYSIS,
            "수면 점수가 낮은 이유는 무엇인가요?",
            "실제 수면 시간이 부족하거나 자는 동안 자주 깬 경우 점수가 낮아질 수 있습니다."
        ),
        FaqItem(
            FaqCategory.ANALYSIS,
            "REM 수면이란 무엇인가요?",
            "몸은 자고 있지만 뇌는 활발히 활동하며 꿈을 꾸는 단계의 수면입니다."
        ),

        // ================= 계정 =================
        FaqItem(
            FaqCategory.ACCOUNT,
            "로그인을 꼭 해야 하나요?",
            "게스트로도 사용 가능하지만, 데이터 안전 보관 및 동기화를 위해 로그인을 권장합니다."
        ),
        FaqItem(
            FaqCategory.ACCOUNT,
            "기존 데이터가 사라졌어요",
            "동일한 소셜 계정으로 로그인하면 클라우드에 백업된 데이터를 복구할 수 있습니다."
        ),

        // ================= 기타 =================
        FaqItem(
            FaqCategory.GENERAL,
            "앱이 느려요",
            "백그라운드 앱을 정리하거나 최신 버전 업데이트를 권장합니다."
        ),
        FaqItem(
            FaqCategory.GENERAL,
            "권한 설정이 왜 필요한가요?",
            "정확한 수면 분석 및 제시간 알람을 위해 센서와 알림 권한이 필수적입니다."
        )
    )
}
