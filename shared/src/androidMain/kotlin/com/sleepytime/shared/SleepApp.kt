package com.sleepytime.shared

import android.app.Application
import android.util.Log
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.media3.common.util.UnstableApi
import com.kakao.sdk.common.KakaoSdk
import com.russhwolf.settings.ExperimentalSettingsApi
import com.sleepytime.shared.di.androidModule
import com.sleepytime.shared.di.sharedModule
import com.sleepytime.shared.platform.AndroidContextProvider
import com.sleepytime.shared.util.AppLogger
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.koin.android.ext.koin.androidContext
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.context.startKoin
import kotlin.time.ExperimentalTime

@UnstableApi
@ExperimentalMaterial3Api
@ExperimentalTime
@ExperimentalSettingsApi
@ExperimentalCoroutinesApi
class SleepApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogger.plant()
        Log.d("SleepApp", "onCreate 호출됨, pid=${android.os.Process.myPid()}")
        AndroidContextProvider.context = this
        KakaoSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
        startKoin {
            androidContext(this@SleepApp)
            modules(androidModule, sharedModule)
        }
    }
}