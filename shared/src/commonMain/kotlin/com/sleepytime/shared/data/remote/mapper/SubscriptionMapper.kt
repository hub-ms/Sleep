package com.sleepytime.shared.data.remote.mapper

import com.sleepytime.shared.data.remote.dto.request.PurchaseVerifyRequest
import com.sleepytime.shared.data.remote.dto.response.SubscriptionResponse
import com.sleepytime.shared.domain.model.BillingPurchaseResult
import com.sleepytime.shared.domain.model.SubscriptionStatus

fun SubscriptionResponse.toDomain(): SubscriptionStatus = SubscriptionStatus(
    isPremium = isPremium,
    productId = productId,
    status = status,
    expiryTimeMillis = expiryTimeMillis,
    willRenew = willRenew,
)

fun BillingPurchaseResult.toRequest(): PurchaseVerifyRequest = PurchaseVerifyRequest(
    productId = productId,
    purchaseToken = purchaseToken,
    orderId = orderId,
)
