package com.soundsleeper.app.data.remote.dto.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SubscriptionResponse(
    @SerialName("isPremium") val isPremium: Boolean = false,
    @SerialName("productId") val productId: String? = null,
    @SerialName("status") val status: String = "NONE",
    @SerialName("expiryTimeMillis") val expiryTimeMillis: Long? = null,
    @SerialName("willRenew") val willRenew: Boolean = false,
)
