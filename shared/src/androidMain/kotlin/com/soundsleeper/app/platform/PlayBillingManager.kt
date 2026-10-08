@file:OptIn(InternalVoyagerApi::class, ExperimentalMaterial3Api::class,
    ExperimentalMaterial3Api::class, ExperimentalTime::class, ExperimentalSettingsApi::class,
    ExperimentalCoroutinesApi::class
)

package com.soundsleeper.app.platform

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.media3.common.util.UnstableApi
import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryPurchasesAsync
import com.revenuecat.purchases.ProductType
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesTransactionException
import com.revenuecat.purchases.awaitGetProducts
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.models.StoreProduct
import com.revenuecat.purchases.models.StoreTransaction
import com.russhwolf.settings.ExperimentalSettingsApi
import com.soundsleeper.app.MainActivity
import com.soundsleeper.app.domain.model.BillingProductDetails
import com.soundsleeper.app.domain.model.BillingPurchaseResult
import com.soundsleeper.app.domain.model.BillingPurchaseState
import io.github.aakira.napier.Napier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.time.ExperimentalTime
import com.revenuecat.purchases.models.PurchaseState as RevenueCatPurchaseState

/** Play Billing 서비스 연결 자체가 실패했음을 호출자에게 알린다. */
class BillingConnectionException(
    val responseCode: Int,
    val debugMessage: String,
) : Exception("Play Billing 연결 실패 (responseCode=$responseCode): $debugMessage")

/**
 * 상품 조회·구매 플로우는 RevenueCat SDK(Purchases.sharedInstance)로 처리한다. 구매 엔진만
 * RevenueCat으로 옮기고, 백엔드(Spring)의 자체 영수증 검증은 그대로 유지하기로 했기 때문에
 * RevenueCat이 반환하는 StoreTransaction.purchaseToken(=Google Play의 원본 purchase token)을
 * 그대로 백엔드에 전달한다.
 *
 * 다만 RevenueCat의 복원/CustomerInfo API는 이미 활성화된 구독의 원본 purchase token을
 * 노출하지 않는다. 백엔드의 /api/billing/restore는 이 raw token이 꼭 필요하므로,
 * queryActivePurchases()만 별도의 최소 raw BillingClient로 직접 조회한다.
 */
actual class PlayBillingManager(private val context: Context) {

    // 복원(queryActivePurchases) 전용 조회용 클라이언트. 이 클라이언트로는 구매 플로우를
    // 띄우지 않으므로 리스너는 호출될 일이 없다.
    private val restoreBillingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener { _, _ -> }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private suspend fun ensureConnected() {
        if (restoreBillingClient.isReady) return

        // startConnection 의 리스너는 자동 재연결 과정에서 여러 번 호출될 수 있어 isActive 로 막는다.
        val setupResult = suspendCancellableCoroutine { cont ->
            restoreBillingClient.startConnection(
                object : BillingClientStateListener {
                    override fun onBillingSetupFinished(billingResult: BillingResult) {
                        if (cont.isActive) cont.resume(billingResult)
                    }

                    override fun onBillingServiceDisconnected() {
                        Napier.w("PlayBillingManager: billing service disconnected")
                    }
                }
            )
        }

        if (setupResult.responseCode != BillingResponseCode.OK) {
            Napier.e(
                "PlayBillingManager: billing setup failed" +
                    " responseCode=${setupResult.responseCode}" +
                    " debugMessage=${setupResult.debugMessage}"
            )
            throw BillingConnectionException(setupResult.responseCode, setupResult.debugMessage)
        }
    }

    actual suspend fun queryProductDetails(productIds: List<String>): List<BillingProductDetails> {
        // RevenueCat 키가 아직 설정되지 않은 동안(local.properties: revenuecat.api.key)에는
        // Purchases.sharedInstance 접근 자체가 크래시를 일으키므로 isConfigured로 먼저 확인한다.
        if (!Purchases.isConfigured) {
            Napier.w("PlayBillingManager: RevenueCat 미설정 — 상품 조회를 건너뜁니다")
            return emptyList()
        }
        val products = Purchases.sharedInstance.awaitGetProducts(productIds, ProductType.SUBS)

        val summary = "PlayBillingManager: queryProductDetails" +
            " requested=[${productIds.joinToString()}]" +
            " returned=${products.size}[${products.joinToString { it.purchasingData.productId }}]"
        if (products.size < productIds.size) Napier.e(summary) else Napier.d(summary)

        return products.mapNotNull { it.toDomain() }
    }

    @UnstableApi
    actual suspend fun launchPurchaseFlow(productId: String): BillingPurchaseResult? {
        if (!Purchases.isConfigured) {
            Napier.w("PlayBillingManager: RevenueCat 미설정 — 구매 플로우를 건너뜁니다")
            return null
        }
        val activity = context.findActivity() ?: run {
            Napier.e("PlayBillingManager: Activity context is required to launch purchase flow")
            return null
        }
        val product = Purchases.sharedInstance.awaitGetProducts(listOf(productId), ProductType.SUBS)
            .firstOrNull() ?: return null
        // toDomain() 이 화면에 보여줄 때 체험이 붙은 오퍼를 고르므로, 실제 결제도 같은 오퍼로
        // 띄워야 한다. 그렇지 않으면 "무료 체험 시작하기"를 눌렀는데 체험 없는 오퍼로 결제되는
        // 일이 생긴다.
        val option = product.subscriptionOptions?.freeTrial
            ?: product.subscriptionOptions?.basePlan
            ?: product.subscriptionOptions?.firstOrNull()
            ?: return null

        val params = PurchaseParams.Builder(activity, option).build()
        return try {
            val result = Purchases.sharedInstance.awaitPurchase(params)
            result.storeTransaction.toDomain(requestedProductId = productId)
        } catch (e: PurchasesTransactionException) {
            Napier.e(
                "PlayBillingManager: launchPurchaseFlow failed" +
                    " userCancelled=${e.userCancelled} - ${e.message}"
            )
            null
        }
    }

    actual suspend fun queryActivePurchases(): List<BillingPurchaseResult> {
        ensureConnected()
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        val result = restoreBillingClient.queryPurchasesAsync(params)
        return result.purchasesList.mapNotNull { it.toDomain() }
    }

    private fun StoreProduct.toDomain(): BillingProductDetails? {
        val options = subscriptionOptions
        val option = options?.freeTrial ?: options?.basePlan ?: options?.firstOrNull() ?: return null
        val pricingPhase = option.fullPricePhase ?: option.pricingPhases.firstOrNull() ?: return null
        val trialPhase = option.freePhase

        return BillingProductDetails(
            productId = purchasingData.productId,
            formattedPrice = pricingPhase.price.formatted,
            priceAmountMicros = pricingPhase.price.amountMicros,
            currencyCode = pricingPhase.price.currencyCode,
            billingPeriodIso8601 = pricingPhase.billingPeriod.iso8601,
            freeTrialPeriodIso8601 = trialPhase?.billingPeriod?.iso8601,
        )
    }

    private fun StoreTransaction.toDomain(requestedProductId: String): BillingPurchaseResult {
        val state = when (purchaseState) {
            RevenueCatPurchaseState.PURCHASED -> BillingPurchaseState.PURCHASED
            RevenueCatPurchaseState.PENDING -> BillingPurchaseState.PENDING
            else -> BillingPurchaseState.UNSPECIFIED
        }
        return BillingPurchaseResult(
            productId = productIds.firstOrNull() ?: requestedProductId,
            purchaseToken = purchaseToken,
            orderId = orderId,
            state = state,
            // RevenueCat의 구매 플로우는 awaitPurchase가 성공 반환하기 전에 Google Play에
            // 구매를 자동으로 acknowledge 한다 (StoreTransaction 에는 별도 필드가 없다).
            isAcknowledged = true,
        )
    }

    private fun Purchase.toDomain(): BillingPurchaseResult? {
        val productId = products.firstOrNull() ?: return null
        val state = when (purchaseState) {
            Purchase.PurchaseState.PURCHASED -> BillingPurchaseState.PURCHASED
            Purchase.PurchaseState.PENDING -> BillingPurchaseState.PENDING
            else -> BillingPurchaseState.UNSPECIFIED
        }
        return BillingPurchaseResult(
            productId = productId,
            purchaseToken = purchaseToken,
            orderId = orderId,
            state = state,
            isAcknowledged = isAcknowledged,
        )
    }

    @UnstableApi
    private fun Context.findActivity(): Activity? {
        var context = this
        while (context is ContextWrapper) {
            if (context is Activity) return context
            context = context.baseContext
        }
        return MainActivity.instance?.get()
    }
}
