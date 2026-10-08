package com.somniotune.app.platform

import com.somniotune.app.domain.model.BillingProductDetails
import com.somniotune.app.domain.model.BillingPurchaseResult

actual class PlayBillingManager {
    actual suspend fun queryProductDetails(productIds: List<String>): List<BillingProductDetails> = emptyList()
    actual suspend fun launchPurchaseFlow(productId: String): BillingPurchaseResult? = null
    actual suspend fun queryActivePurchases(): List<BillingPurchaseResult> = emptyList()
}
