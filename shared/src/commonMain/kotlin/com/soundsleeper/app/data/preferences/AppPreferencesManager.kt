package com.soundsleeper.app.data.preferences

import com.russhwolf.settings.ObservableSettings
import com.soundsleeper.app.enum_.AuthProvider
import com.soundsleeper.app.util.PreferencesKeys.App.FIRST_LAUNCH
import com.soundsleeper.app.util.PreferencesKeys.App.PENDING_SIGNUP_PAYWALL
import com.soundsleeper.app.util.PreferencesKeys.Auth.SOCIAL_PROVIDER

class AppPreferencesManager(
    private val settings: ObservableSettings
) {
    fun getProvider(): AuthProvider? =
        settings.getStringOrNull(SOCIAL_PROVIDER)?.toAuthProvider()
    fun saveProvider(
        provider: AuthProvider
    ) {
        settings.putString(
            SOCIAL_PROVIDER,
            provider.name
        )
    }
    fun saveSignupFlag(
        isNewUser: Boolean
    ) {
        settings.putBoolean(
            PENDING_SIGNUP_PAYWALL,
            isNewUser
        )
    }
    fun resetLocalUserData() {
        settings.remove(SOCIAL_PROVIDER)
        settings.putBoolean(FIRST_LAUNCH, false)
    }
    fun String?.toAuthProvider(): AuthProvider? = runCatching {
        AuthProvider.valueOf(
            this.orEmpty().uppercase()
        )
    }.getOrNull()
}