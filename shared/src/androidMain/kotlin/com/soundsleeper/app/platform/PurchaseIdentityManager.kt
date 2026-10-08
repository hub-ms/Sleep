package com.soundsleeper.app.platform

import com.revenuecat.purchases.Purchases

actual class PurchaseIdentityManager {
    // RevenueCat 키가 아직 설정되지 않은 동안(local.properties: revenuecat.api.key)에는
    // Purchases.sharedInstance 접근 자체가 크래시를 일으키므로 isConfigured로 먼저 확인한다.
    actual fun identify(userId: String) {
        if (!Purchases.isConfigured) return
        Purchases.sharedInstance.logIn(userId)
    }

    actual fun reset() {
        if (!Purchases.isConfigured) return
        Purchases.sharedInstance.logOut()
    }
}
