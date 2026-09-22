package com.sleepytime.shared.data.remote.api

import com.sleepytime.shared.data.remote.dto.request.PurchaseVerifyRequest
import com.sleepytime.shared.data.remote.dto.request.RestorePurchasesRequest
import com.sleepytime.shared.data.remote.dto.response.SubscriptionResponse
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*

class BillingApi(
    private val client: HttpClient
) {
    suspend fun verifyPurchase(request: PurchaseVerifyRequest): Result<SubscriptionResponse> = runCatching {
        val response = client.post("api/billing/verify") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (response.status.value >= 400) {
            val errorBody = response.bodyAsText()
            Napier.e("verifyPurchase API 에러 - Status: ${response.status}, Body: $errorBody")
            throw Exception("Verify purchase failed: ${response.status} - $errorBody")
        }
        response.body<SubscriptionResponse>()
    }.onFailure { Napier.e("verifyPurchase 예외: ${it.message}", it) }

    suspend fun restorePurchases(request: RestorePurchasesRequest): Result<SubscriptionResponse> = runCatching {
        val response = client.post("api/billing/restore") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (response.status.value >= 400) {
            val errorBody = response.bodyAsText()
            Napier.e("restorePurchases API 에러 - Status: ${response.status}, Body: $errorBody")
            throw Exception("Restore purchases failed: ${response.status} - $errorBody")
        }
        response.body<SubscriptionResponse>()
    }.onFailure { Napier.e("restorePurchases 예외: ${it.message}", it) }

    suspend fun getStatus(): Result<SubscriptionResponse> = runCatching {
        val response = client.get("api/billing/status")
        if (response.status.value >= 400) {
            throw Exception("Get subscription status failed: ${response.status}")
        }
        response.body<SubscriptionResponse>()
    }.onFailure { Napier.e("getStatus 예외: ${it.message}", it) }
}
