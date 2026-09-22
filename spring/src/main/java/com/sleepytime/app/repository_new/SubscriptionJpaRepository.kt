package com.sleepytime.app.repository_new

import com.sleepytime.app.entity_new.SubscriptionEntity
import com.sleepytime.app.entity_new.SubscriptionStatusType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SubscriptionJpaRepository : JpaRepository<SubscriptionEntity, Long> {
    fun findByPurchaseToken(purchaseToken: String): SubscriptionEntity?
    fun findByUserIdOrderByUpdatedAtDesc(userId: Long): List<SubscriptionEntity>
    fun findFirstByUserIdAndStatusInOrderByExpiryTimeDesc(
        userId: Long,
        statuses: List<SubscriptionStatusType>,
    ): SubscriptionEntity?
}
