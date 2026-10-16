package com.soundsleeper.app.platform

import com.soundsleeper.app.domain.model.BillingProductDetails
import com.soundsleeper.app.domain.model.BillingPurchaseResult

expect class PlayBillingManager {
    suspend fun queryProductDetails(productIds: List<String>): List<BillingProductDetails>
    /** [planId] 는 [BillingProductDetails.planId] — 화면에 보여 준 바로 그 오퍼로 결제한다. */
    suspend fun launchPurchaseFlow(planId: String): BillingPurchaseResult?
    suspend fun queryActivePurchases(): List<BillingPurchaseResult>
}
