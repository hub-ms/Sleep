package com.sleepytime.shared.domain.repository

import com.sleepytime.shared.domain.model.BillingPurchaseResult
import com.sleepytime.shared.domain.model.SubscriptionStatus

interface BillingRepository {
    suspend fun verifyPurchase(purchase: BillingPurchaseResult): Result<SubscriptionStatus>
    suspend fun restorePurchases(purchases: List<BillingPurchaseResult>): Result<SubscriptionStatus>
    suspend fun getSubscriptionStatus(): Result<SubscriptionStatus>
}
