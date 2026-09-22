package com.sleepytime.shared.platform

import com.sleepytime.shared.domain.model.BillingProductDetails
import com.sleepytime.shared.domain.model.BillingPurchaseResult

// Google Play Billing is Android-only. iOS subscriptions would use StoreKit separately;
// not in scope for this feature, so this bridge is a no-op on iOS.
actual class PlayBillingManager {
    actual suspend fun queryProductDetails(productIds: List<String>): List<BillingProductDetails> = emptyList()
    actual suspend fun launchPurchaseFlow(productId: String): BillingPurchaseResult? = null
    actual suspend fun queryActivePurchases(): List<BillingPurchaseResult> = emptyList()
}
