package com.sleepytime.shared.data.local.repository

import com.sleepytime.shared.data.local.dao.UserDao
import com.sleepytime.shared.data.remote.api.BillingApi
import com.sleepytime.shared.data.remote.dto.request.RestorePurchasesRequest
import com.sleepytime.shared.data.remote.mapper.toDomain
import com.sleepytime.shared.data.remote.mapper.toRequest
import com.sleepytime.shared.domain.model.BillingPurchaseResult
import com.sleepytime.shared.domain.model.SubscriptionStatus
import com.sleepytime.shared.domain.repository.BillingRepository

class BillingRepositoryImpl(
    private val billingApi: BillingApi,
    private val userDao: UserDao,
) : BillingRepository {

    override suspend fun verifyPurchase(purchase: BillingPurchaseResult): Result<SubscriptionStatus> =
        billingApi.verifyPurchase(purchase.toRequest())
            .map { it.toDomain() }
            .onSuccess { applyLocalEntitlement(it) }

    override suspend fun restorePurchases(purchases: List<BillingPurchaseResult>): Result<SubscriptionStatus> =
        billingApi.restorePurchases(RestorePurchasesRequest(purchases.map { it.toRequest() }))
            .map { it.toDomain() }
            .onSuccess { applyLocalEntitlement(it) }

    override suspend fun getSubscriptionStatus(): Result<SubscriptionStatus> =
        billingApi.getStatus()
            .map { it.toDomain() }
            .onSuccess { applyLocalEntitlement(it) }

    private suspend fun applyLocalEntitlement(status: SubscriptionStatus) {
        userDao.updatePremiumStatus(status.isPremium)
    }
}
