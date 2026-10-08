package com.somniotune.app.platform

actual class PurchaseIdentityManager {
    actual fun identify(userId: String) {}
    actual fun reset() {}
}
