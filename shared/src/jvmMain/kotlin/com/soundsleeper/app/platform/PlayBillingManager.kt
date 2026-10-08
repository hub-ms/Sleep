package com.soundsleeper.app.platform

import com.soundsleeper.app.domain.model.BillingProductDetails
import com.soundsleeper.app.domain.model.BillingPurchaseResult

actual class PlayBillingManager {
    actual suspend fun queryProductDetails(productIds: List<String>): List<BillingProductDetails> = emptyList()
    actual suspend fun launchPurchaseFlow(productId: String): BillingPurchaseResult? = null
    actual suspend fun queryActivePurchases(): List<BillingPurchaseResult> = emptyList()
}
