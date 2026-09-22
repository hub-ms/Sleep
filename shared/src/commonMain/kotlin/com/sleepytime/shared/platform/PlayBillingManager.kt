package com.sleepytime.shared.platform

import com.sleepytime.shared.domain.model.BillingProductDetails
import com.sleepytime.shared.domain.model.BillingPurchaseResult

expect class PlayBillingManager {
    suspend fun queryProductDetails(productIds: List<String>): List<BillingProductDetails>
    suspend fun launchPurchaseFlow(productId: String): BillingPurchaseResult?
    suspend fun queryActivePurchases(): List<BillingPurchaseResult>
}
