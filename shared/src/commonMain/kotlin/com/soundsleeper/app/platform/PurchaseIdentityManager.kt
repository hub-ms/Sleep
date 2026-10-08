package com.soundsleeper.app.platform

/**
 * RevenueCat 대시보드 상 구매 기록을 앱 사용자별로 정리하기 위한 식별 브릿지.
 * 서버 측 영수증 검증(billing 관련 API 호출)에는 영향이 없다 — RevenueCat 쪽 기록 정리용.
 */
expect class PurchaseIdentityManager {
    fun identify(userId: String)
    fun reset()
}
