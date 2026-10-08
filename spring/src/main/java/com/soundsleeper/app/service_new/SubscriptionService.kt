package com.soundsleeper.app.service_new

import com.soundsleeper.app.entity_new.SubscriptionEntity
import com.soundsleeper.app.entity_new.SubscriptionStatusType
import com.soundsleeper.app.exception.BillingException
import com.soundsleeper.app.repository_new.SubscriptionJpaRepository
import com.soundsleeper.app.repository_new.UserJpaRepository
import com.soundsleeper.app.data.remote.dto.request.PurchaseVerifyRequest
import com.soundsleeper.app.data.remote.dto.request.RestorePurchasesRequest
import com.soundsleeper.app.data.remote.dto.response.SubscriptionResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

private val ACTIVE_STATUSES = listOf(
    SubscriptionStatusType.ACTIVE,
    SubscriptionStatusType.IN_GRACE_PERIOD,
    SubscriptionStatusType.ON_HOLD,
)

@Service
class SubscriptionService(
    private val googlePlayBillingService: GooglePlayBillingService,
    private val subscriptionRepository: SubscriptionJpaRepository,
    private val userRepository: UserJpaRepository,
) {
    private val log = LoggerFactory.getLogger(SubscriptionService::class.java)

    @Transactional
    fun verifyAndGrant(userId: Long, request: PurchaseVerifyRequest): SubscriptionResponse {
        val existing = subscriptionRepository.findByPurchaseToken(request.purchaseToken)
        if (existing != null && existing.userId != userId) {
            throw BillingException.TokenAlreadyBound()
        }

        val purchase = runCatching { googlePlayBillingService.getSubscriptionPurchase(request.purchaseToken) }
            .getOrElse { throw BillingException.InvalidToken(it) }

        val lineItem = purchase.lineItems?.firstOrNull { it.productId == request.productId }
            ?: throw BillingException.ProductMismatch()

        val status = mapSubscriptionState(purchase.subscriptionState)

        val entity = (existing ?: SubscriptionEntity(
            userId = userId,
            productId = request.productId,
            purchaseToken = request.purchaseToken,
            status = status,
        )).apply {
            productId = request.productId
            this.status = status
            orderId = lineItem.latestSuccessfulOrderId ?: request.orderId
            startTime = purchase.startTime?.let { toLocalDateTime(it) }
            expiryTime = lineItem.expiryTime?.let { toLocalDateTime(it) }
            autoRenewing = lineItem.autoRenewingPlan?.autoRenewEnabled ?: false
            updatedAt = LocalDateTime.now()
        }
        subscriptionRepository.save(entity)

        if (purchase.acknowledgementState == "ACKNOWLEDGEMENT_STATE_PENDING") {
            runCatching {
                googlePlayBillingService.acknowledgePurchase(request.productId, request.purchaseToken)
            }.onFailure { log.warn("구매 승인(acknowledge) 실패, 다음 RTDN/복원 시 재시도됨: ${it.message}") }
        }

        syncUserPremiumFlag(userId)
        return entity.toResponse()
    }

    @Transactional
    fun restore(userId: Long, request: RestorePurchasesRequest): SubscriptionResponse {
        request.purchases.forEach { purchase ->
            runCatching { verifyAndGrant(userId, purchase) }
                .onFailure { log.warn("구매 복원 중 항목 검증 실패 (token=${purchase.purchaseToken}): ${it.message}") }
        }
        return currentStatus(userId)
    }

    fun currentStatus(userId: Long): SubscriptionResponse {
        val active = subscriptionRepository.findFirstByUserIdAndStatusInOrderByExpiryTimeDesc(userId, ACTIVE_STATUSES)
        return active?.toResponse() ?: SubscriptionResponse(isPremium = false)
    }

    @Transactional
    fun handleRtdn(purchaseToken: String, notificationType: Int) {
        val existing = subscriptionRepository.findByPurchaseToken(purchaseToken)
        if (existing == null) {
            log.info("RTDN 수신: 매칭되는 구독 레코드가 없어 건너뜀 (purchaseToken=$purchaseToken)")
            return
        }

        val purchase = runCatching { googlePlayBillingService.getSubscriptionPurchase(purchaseToken) }
            .getOrElse {
                log.error("RTDN 처리 중 구매 재조회 실패: ${it.message}", it)
                return
            }
        val lineItem = purchase.lineItems?.firstOrNull { it.productId == existing.productId }
            ?: purchase.lineItems?.firstOrNull()

        existing.apply {
            status = mapSubscriptionState(purchase.subscriptionState)
            expiryTime = lineItem?.expiryTime?.let { toLocalDateTime(it) }
            autoRenewing = lineItem?.autoRenewingPlan?.autoRenewEnabled ?: false
            latestNotificationType = notificationType
            updatedAt = LocalDateTime.now()
        }
        subscriptionRepository.save(existing)
        syncUserPremiumFlag(existing.userId)
    }

    @Transactional
    fun syncUserPremiumFlag(userId: Long) {
        val active = subscriptionRepository.findFirstByUserIdAndStatusInOrderByExpiryTimeDesc(userId, ACTIVE_STATUSES)
        val isPremium = active != null && (active.expiryTime == null || active.expiryTime!!.isAfter(LocalDateTime.now()))
        userRepository.findById(userId).ifPresent { user ->
            if (user.isPremium != isPremium) {
                user.isPremium = isPremium
                userRepository.save(user)
            }
        }
    }

    private fun mapSubscriptionState(state: String?): SubscriptionStatusType = when (state) {
        "SUBSCRIPTION_STATE_ACTIVE" -> SubscriptionStatusType.ACTIVE
        "SUBSCRIPTION_STATE_IN_GRACE_PERIOD" -> SubscriptionStatusType.IN_GRACE_PERIOD
        "SUBSCRIPTION_STATE_ON_HOLD" -> SubscriptionStatusType.ON_HOLD
        "SUBSCRIPTION_STATE_PAUSED" -> SubscriptionStatusType.PAUSED
        "SUBSCRIPTION_STATE_CANCELED" -> SubscriptionStatusType.CANCELED
        "SUBSCRIPTION_STATE_EXPIRED" -> SubscriptionStatusType.EXPIRED
        else -> SubscriptionStatusType.EXPIRED
    }

    private fun toLocalDateTime(rfc3339: String): LocalDateTime =
        LocalDateTime.ofInstant(Instant.parse(rfc3339), ZoneId.systemDefault())

    private fun SubscriptionEntity.toResponse(): SubscriptionResponse = SubscriptionResponse(
        isPremium = status in ACTIVE_STATUSES && (expiryTime == null || expiryTime!!.isAfter(LocalDateTime.now())),
        productId = productId,
        status = status.name,
        expiryTimeMillis = expiryTime?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
        willRenew = autoRenewing,
    )
}
