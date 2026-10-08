package com.soundsleeper.app.data.remote.mapper

import com.soundsleeper.app.data.remote.dto.request.PurchaseVerifyRequest
import com.soundsleeper.app.data.remote.dto.request.RestorePurchasesRequest
import com.soundsleeper.app.data.remote.dto.response.SubscriptionResponse
import com.soundsleeper.app.domain.model.BillingPurchaseResult
import com.soundsleeper.app.domain.model.SubscriptionStatus

fun SubscriptionResponse.toDomain(): SubscriptionStatus = SubscriptionStatus(
    isPremium = isPremium,
    productId = productId,
    status = status,
    expiryTimeMillis = expiryTimeMillis,
    willRenew = willRenew,
)

fun BillingPurchaseResult.toPurchaseVerifyRequest(): PurchaseVerifyRequest = PurchaseVerifyRequest(
    productId = productId,
    purchaseToken = purchaseToken,
    orderId = orderId,
)
fun List<BillingPurchaseResult>.toRestorePurchasesRequest(): RestorePurchasesRequest = RestorePurchasesRequest(
    purchases = map { it.toPurchaseVerifyRequest() }
)
