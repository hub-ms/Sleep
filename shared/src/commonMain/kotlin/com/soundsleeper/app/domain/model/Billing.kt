package com.soundsleeper.app.domain.model

enum class BillingPurchaseState {
    PURCHASED, PENDING, UNSPECIFIED
}

data class BillingProductDetails(
    val productId: String,
    val formattedPrice: String,
    val priceAmountMicros: Long,
    val currencyCode: String,
    val billingPeriodIso8601: String,

    val freeTrialPeriodIso8601: String? = null,
) {
    val hasFreeTrial: Boolean get() = freeTrialPeriodIso8601 != null
}

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

/**
 * Play Console 에 등록된 구독 **제품 ID**. 표시용 이름("SoundSleeper Pro(Monthly)" 등)이 아니다.
 *
 * Play 상품 ID는 소문자 영문/숫자/`_`/`.` 만 허용하므로, 표시용 이름을 넣으면 queryProductDetails 가
 * responseCode=OK 와 함께 빈 목록을 돌려주고 페이월이 원인 없이 비어 보인다.
 * 사람이 읽을 이름은 PaywallScreen 의 planLabel() 이 ID 에서 매핑한다.
 */
object BillingProducts {
    const val MONTHLY = "sleepytime_pro_monthly"
    const val YEARLY = "sleepytime_pro_yearly"

    val ALL = listOf(MONTHLY, YEARLY)
}
