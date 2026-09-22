package com.sleepytime.shared.domain.model

enum class BillingPurchaseState {
    PURCHASED, PENDING, UNSPECIFIED
}

data class BillingProductDetails(
    val productId: String,
    val formattedPrice: String,
    val priceAmountMicros: Long,
    val currencyCode: String,
    val billingPeriodIso8601: String,
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
    const val MONTHLY = "premium_monthly"
    const val YEARLY = "premium_yearly"

    val ALL = listOf(MONTHLY, YEARLY)
}
