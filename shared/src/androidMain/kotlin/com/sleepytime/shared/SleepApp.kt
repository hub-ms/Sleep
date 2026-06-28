package com.sleepytime.shared

import android.app.Application
import com.kakao.sdk.common.KakaoSdk
import com.russhwolf.settings.ExperimentalSettingsApi
import com.sleepytime.shared.di.androidModule
import com.sleepytime.shared.di.sharedModule
import com.sleepytime.shared.platform.AndroidContextProvider
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.koin.android.ext.koin.androidContext
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.context.startKoin
import kotlin.time.ExperimentalTime

@ExperimentalTime
@ExperimentalSettingsApi
@ExperimentalCoroutinesApi
@KoinExperimentalAPI
class SleepApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Napier.base(DebugAntilog())
        AndroidContextProvider.context = applicationContext
        KakaoSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
        startKoin {
            androidContext(this@SleepApp)
            modules(androidModule, sharedModule)
        }
    }
}