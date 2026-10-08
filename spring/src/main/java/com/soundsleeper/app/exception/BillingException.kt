package com.soundsleeper.app.exception

sealed class BillingException(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    class InvalidToken(cause: Throwable) : BillingException("Invalid or unrecognized purchase token", cause)
    class ProductMismatch : BillingException("Purchase token does not match the requested product")
    class TokenAlreadyBound : BillingException("Purchase token is already bound to another account")
    class Expired : BillingException("Subscription purchase is expired")
}