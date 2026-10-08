package com.somniotune.app.platform

import com.somniotune.app.domain.model.BillingProductDetails
import com.somniotune.app.domain.model.BillingPurchaseResult

expect class PlayBillingManager {
    suspend fun queryProductDetails(productIds: List<String>): List<BillingProductDetails>
    suspend fun launchPurchaseFlow(productId: String): BillingPurchaseResult?
    suspend fun queryActivePurchases(): List<BillingPurchaseResult>
}
