package com.sleepytime.app.entity_new

import jakarta.persistence.*
import java.time.LocalDateTime

enum class SubscriptionStatusType { ACTIVE, CANCELED, IN_GRACE_PERIOD, ON_HOLD, PAUSED, EXPIRED }

@Entity
@Table(
    name = "subscriptions",
    indexes = [
        Index(name = "subscription_user_id_idx", columnList = "user_id"),
        Index(name = "subscription_purchase_token_idx", columnList = "purchase_token", unique = true),
    ]
)
class SubscriptionEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "product_id", nullable = false)
    var productId: String,

    @Column(name = "purchase_token", nullable = false, unique = true, length = 1024)
    var purchaseToken: String,

    var orderId: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: SubscriptionStatusType,

    var startTime: LocalDateTime? = null,
    var expiryTime: LocalDateTime? = null,

    @Column(nullable = false)
    var autoRenewing: Boolean = false,

    var latestNotificationType: Int? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
)
