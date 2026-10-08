package com.soundsleeper.app.platform

actual class PurchaseIdentityManager {
    actual fun identify(userId: String) {}
    actual fun reset() {}
}
