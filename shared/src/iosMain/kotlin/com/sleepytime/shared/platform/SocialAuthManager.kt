package com.sleepytime.shared.platform

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AuthenticationServices.*
import platform.Foundation.*
import platform.UIKit.*


actual class SocialAuthManager {
    actual suspend fun getGoogleToken(): String? {
        (GIDSignIn.sharedInstance.signIn)
        return null
    }
    actual suspend fun getKakaoToken(): String? {
        return null
    }
    actual suspend fun getAppleToken(): String? = suspendCancellableCoroutine { cont ->
        val appleIDProvider = ASAuthorizationAppleIDProvider()
        val request = appleIDProvider.createRequest().apply {
            requestedScopes = listOf(
                ASAuthorizationScopeFullName,
                ASAuthorizationScopeEmail
            )
        }
        ASAuthorizationController(listOf(request))
    }
    private fun getRootViewController(): UIViewController? {
        return UIApplication.sharedApplication.keyWindow?.rootViewController
    }
}