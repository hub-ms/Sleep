@file:OptIn(InternalVoyagerApi::class, ExperimentalMaterial3Api::class,
    ExperimentalMaterial3Api::class, ExperimentalTime::class, ExperimentalSettingsApi::class,
    ExperimentalCoroutinesApi::class
)

package com.sleepytime.shared.platform

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.media3.common.util.UnstableApi
import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.russhwolf.settings.ExperimentalSettingsApi
import com.sleepytime.shared.MainActivity
import com.sleepytime.shared.domain.model.BillingProductDetails
import com.sleepytime.shared.domain.model.BillingPurchaseResult
import com.sleepytime.shared.domain.model.BillingPurchaseState
import io.github.aakira.napier.Napier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.time.ExperimentalTime

actual class PlayBillingManager(private val context: Context) : PurchasesUpdatedListener {

    private val purchaseResultChannel = Channel<BillingPurchaseResult?>(Channel.CONFLATED)

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private suspend fun ensureConnected() {
        if (billingClient.isReady) return
        suspendCancellableCoroutine { cont ->
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onBillingServiceDisconnected() {
                    Napier.w("PlayBillingManager: billing service disconnected")
                }
            })
        }
    }

    actual suspend fun queryProductDetails(productIds: List<String>): List<BillingProductDetails> {
        ensureConnected()
        val products = productIds.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
        val result = billingClient.queryProductDetails(params)
        return result.productDetailsList.orEmpty().mapNotNull { it.toDomain() }
    }

    @UnstableApi
    actual suspend fun launchPurchaseFlow(productId: String): BillingPurchaseResult? {
        ensureConnected()
        val activity = context.findActivity() ?: run {
            Napier.e("PlayBillingManager: Activity context is required to launch purchase flow")
            return null
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            ).build()
        val details = billingClient.queryProductDetails(params).productDetailsList.orEmpty().firstOrNull()
            ?: return null
        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: return null

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offerToken)
                        .build()
                )
            ).build()

        val launchResult = billingClient.launchBillingFlow(activity, flowParams)
        if (launchResult.responseCode != BillingResponseCode.OK) {
            Napier.e("PlayBillingManager: launchBillingFlow failed - ${launchResult.debugMessage}")
            return null
        }
        return purchaseResultChannel.receive()
    }

    actual suspend fun queryActivePurchases(): List<BillingPurchaseResult> {
        ensureConnected()
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        val result = billingClient.queryPurchasesAsync(params)
        return result.purchasesList.mapNotNull { it.toDomain() }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode != BillingResponseCode.OK) {
            purchaseResultChannel.trySend(null)
            return
        }
        purchaseResultChannel.trySend(purchases?.firstOrNull()?.toDomain())
    }

    private fun ProductDetails.toDomain(): BillingProductDetails? {
        val offer = subscriptionOfferDetails?.firstOrNull() ?: return null
        val pricingPhase = offer.pricingPhases.pricingPhaseList.firstOrNull() ?: return null
        return BillingProductDetails(
            productId = productId,
            formattedPrice = pricingPhase.formattedPrice,
            priceAmountMicros = pricingPhase.priceAmountMicros,
            currencyCode = pricingPhase.priceCurrencyCode,
            billingPeriodIso8601 = pricingPhase.billingPeriod,
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
