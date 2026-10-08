package com.somniotune.app.data.local.repository

import com.somniotune.app.data.auth.UserCacheManager
import com.somniotune.app.data.remote.api.BillingApi
import com.somniotune.app.data.remote.mapper.toDomain
import com.somniotune.app.data.remote.mapper.toPurchaseVerifyRequest
import com.somniotune.app.data.remote.mapper.toRestorePurchasesRequest
import com.somniotune.app.domain.model.BillingPurchaseResult
import com.somniotune.app.domain.model.SubscriptionStatus
import com.somniotune.app.domain.repository.BillingRepository

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
