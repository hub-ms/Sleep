package com.soundsleeper.app.domain.model

enum class BillingPurchaseState {
    PURCHASED, PENDING, UNSPECIFIED
}

/**
 * 화면에 카드 한 장으로 그려지는 구독 선택지 하나 = Play 의 base plan 하나.
 *
 * [productId] 는 Play 의 구독 상품 ID(`dive_premium`) 라서 월간·연간이 **같은 값**을 갖는다.
 * 카드를 구분하고 결제할 오퍼를 되찾는 키는 [planId] 다. 예전에는 productId 하나로 둘을
 * 겸하려다 구독 상품 전체가 카드 한 장으로 접혀서 월간/연간 비교 자체가 불가능했다.
 */
data class BillingProductDetails(
    val productId: String,
    /** base plan(구독 오퍼) 식별자. 선택 상태의 키이자 결제 시 오퍼를 되찾는 값. */
    val planId: String,
    val formattedPrice: String,
    val priceAmountMicros: Long,
    val currencyCode: String,
    val billingPeriodIso8601: String,

    val freeTrialPeriodIso8601: String? = null,
)

data class BillingPurchaseResult(
    val productId: String,
    val purchaseToken: String,
    val orderId: String?,
    val state: BillingPurchaseState,
    val isAcknowledged: Boolean,
)

data class SubscriptionStatus(
    val isPremium: Boolean,
    val productId: String? = null,
    val status: String = "NONE",
    val expiryTimeMillis: Long? = null,
    val willRenew: Boolean = false,
)
object BillingProducts {
    const val PREMIUM_SUBSCRIPTION = "dive_premium"

    // 요금제(Base Plan) ID를 구분하기 위한 상수를 정의합니다. (콘솔과 일치해야 함)
    // base plan ID 상수는 두지 않는다. Play Console 의 base plan ID 철자에 의존하는 대신
    // 결제 주기(billingPeriodIso8601: P1M / P1Y)로 월간·연간을 판정한다 — 철자가 바뀌어도
    // 화면이 조용히 깨지지 않는다.

    // 구글 API 호출 시 사용할 상품 리스트
    val ALL_PRODUCTS = listOf(PREMIUM_SUBSCRIPTION)
}
