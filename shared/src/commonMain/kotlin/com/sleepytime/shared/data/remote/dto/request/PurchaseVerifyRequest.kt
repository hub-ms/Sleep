package com.sleepytime.shared.data.remote.dto.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PurchaseVerifyRequest(
    @SerialName("productId") val productId: String,
    @SerialName("purchaseToken") val purchaseToken: String,
    @SerialName("orderId") val orderId: String? = null,
)

@Serializable
data class RestorePurchasesRequest(
    @SerialName("purchases") val purchases: List<PurchaseVerifyRequest>,
)
