package com.soundsleeper.app.domain.repository

import com.soundsleeper.app.domain.model.BillingPurchaseResult
import com.soundsleeper.app.domain.model.SubscriptionStatus

interface BillingRepository {
    suspend fun verifyPurchase(purchase: BillingPurchaseResult): Result<SubscriptionStatus>
    suspend fun restorePurchases(purchases: List<BillingPurchaseResult>): Result<SubscriptionStatus>
    suspend fun getSubscriptionStatus(): Result<SubscriptionStatus>
}
