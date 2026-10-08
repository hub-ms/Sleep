package com.soundsleeper.app.dto_new.request

data class PubSubPushEnvelope(
    val message: PubSubMessage,
    val subscription: String? = null,
)

data class PubSubMessage(
    val data: String,
    val messageId: String? = null,
    val publishTime: String? = null,
)

data class DeveloperNotification(
    val version: String? = null,
    val packageName: String? = null,
    val eventTimeMillis: Long? = null,
    val subscriptionNotification: SubscriptionNotification? = null,
)

data class SubscriptionNotification(
    val version: String? = null,
    val notificationType: Int,
    val purchaseToken: String,
    val subscriptionId: String,
)
