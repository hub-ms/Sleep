package com.soundsleeper.app.data.local.repository

import com.soundsleeper.app.data.auth.UserCacheManager
import com.soundsleeper.app.data.remote.api.BillingApi
import com.soundsleeper.app.data.remote.mapper.toDomain
import com.soundsleeper.app.data.remote.mapper.toPurchaseVerifyRequest
import com.soundsleeper.app.data.remote.mapper.toRestorePurchasesRequest
import com.soundsleeper.app.domain.model.BillingPurchaseResult
import com.soundsleeper.app.domain.model.SubscriptionStatus
import com.soundsleeper.app.domain.repository.BillingRepository

class BillingRepositoryImpl(
    private val billingApi: BillingApi,
    private val userCacheManager: UserCacheManager,
) : BillingRepository {

    override suspend fun verifyPurchase(purchase: BillingPurchaseResult): Result<SubscriptionStatus> =
        billingApi.verifyPurchase(purchase.toPurchaseVerifyRequest())
            .map { it.toDomain() }
            .applyEntitlement()

    override suspend fun restorePurchases(purchases: List<BillingPurchaseResult>): Result<SubscriptionStatus> =
        billingApi.restorePurchases(purchases.toRestorePurchasesRequest())
            .map { it.toDomain() }
            .applyEntitlement()

    override suspend fun getSubscriptionStatus(): Result<SubscriptionStatus> =
        billingApi.getStatus()
            .map { it.toDomain() }
            .applyEntitlement()
    private fun Result<SubscriptionStatus>.applyEntitlement(): Result<SubscriptionStatus> =
        onSuccess {
            userCacheManager.updatePremiumStatus(it)
        }
}
