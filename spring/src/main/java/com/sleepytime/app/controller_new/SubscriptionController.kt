package com.sleepytime.app.controller_new

import com.sleepytime.app.service_new.BillingException
import com.sleepytime.app.service_new.SubscriptionService
import com.sleepytime.shared.data.remote.dto.request.PurchaseVerifyRequest
import com.sleepytime.shared.data.remote.dto.request.RestorePurchasesRequest
import com.sleepytime.shared.data.remote.dto.response.SubscriptionResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.User
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/billing")
class SubscriptionController(
    private val subscriptionService: SubscriptionService
) {
    @PostMapping("/verify")
    fun verify(
        @AuthenticationPrincipal principal: User?,
        @RequestBody request: PurchaseVerifyRequest,
    ): ResponseEntity<SubscriptionResponse> {
        if (principal == null) return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        val response = subscriptionService.verifyAndGrant(principal.username.toLong(), request)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/restore")
    fun restore(
        @AuthenticationPrincipal principal: User?,
        @RequestBody request: RestorePurchasesRequest,
    ): ResponseEntity<SubscriptionResponse> {
        if (principal == null) return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        val response = subscriptionService.restore(principal.username.toLong(), request)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/status")
    fun status(
        @AuthenticationPrincipal principal: User?,
    ): ResponseEntity<SubscriptionResponse> {
        if (principal == null) return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        return ResponseEntity.ok(subscriptionService.currentStatus(principal.username.toLong()))
    }

    @ExceptionHandler(BillingException::class)
    fun handleBillingException(e: BillingException): ResponseEntity<Map<String, String>> {
        val status = when (e) {
            is BillingException.InvalidToken -> HttpStatus.BAD_REQUEST
            is BillingException.ProductMismatch -> HttpStatus.BAD_REQUEST
            is BillingException.TokenAlreadyBound -> HttpStatus.CONFLICT
            is BillingException.Expired -> HttpStatus.GONE
        }
        return ResponseEntity.status(status)
            .body(mapOf("error" to (e::class.simpleName ?: "BillingException"), "message" to (e.message ?: "")))
    }
}
