package com.soundsleeper.app

import android.app.Application
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.kakao.sdk.common.KakaoSdk
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.soundsleeper.app.android.BuildConfig
import com.soundsleeper.app.di.androidModule
import com.soundsleeper.app.di.sharedModule
import com.soundsleeper.app.platform.AndroidContextProvider
import com.soundsleeper.app.util.AppLogger
import com.soundsleeper.app.util.plantCrashlytics
import com.zoyi.channel.plugin.android.ChannelIO
import io.github.aakira.napier.Napier
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext.startKoin


class SleepApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val appConfig = AppConfig(
            baseUrl = BuildConfig.BASE_URL,
            googleOAuthClientId = BuildConfig.GOOGLE_OAUTH_CLIENT_ID,
            channelTalkPluginKey = BuildConfig.CHANNELTALK_PLUGIN_KEY,
            revenueCatApiKey = BuildConfig.REVENUECAT_API_KEY,
            isDebug = BuildConfig.DEBUG,
            versionName = BuildConfig.VERSION_NAME,
        )
        // plant 가 appConfig 보다 먼저 호출되면 isDebug 를 알 수 없어 가드가 불가능하다.
        AppLogger.plant(isDebug = appConfig.isDebug)
        // 디버그 빌드에서 개발자가 테스트하며 던지는 크래시가 콘솔에 섞이지 않도록
        // 릴리즈에서만 수집을 켠다(기본값은 true라 명시적으로 꺼줘야 한다).
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = !appConfig.isDebug
        if (!appConfig.isDebug) {
            AppLogger.plantCrashlytics()
        }
        AndroidContextProvider.context = this
        KakaoSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
        ChannelIO.initialize(this)
        // Purchases.configure()는 Purchases.sharedInstance를 쓰는 다른 모든 호출(상품 조회,
        // 구매, 로그인 등)보다 먼저 실행되어야 한다. PlayBillingManager가 Koin 그래프에서
        // 바로 생성될 수 있으므로 startKoin 이전에 호출한다.
        // RevenueCat 대시보드 가입/키 발급 전에는 키가 비어있을 수 있다. 빈 키로 configure를
        // 호출하면 IllegalArgumentException으로 앱이 바로 죽으므로, 키가 없으면 건너뛴다
        // (PlayBillingManager/PurchaseIdentityManager는 Purchases.isConfigured로 이 상태를 감지해
        // 구독 기능만 조용히 비활성화한다).
        if (appConfig.revenueCatApiKey.isNotBlank()) {
            Purchases.logLevel = if (appConfig.isDebug) LogLevel.DEBUG else LogLevel.INFO
            Purchases.configure(PurchasesConfiguration.Builder(this, appConfig.revenueCatApiKey).build())
        } else {
            Napier.w("RevenueCat API 키가 설정되지 않아 구독 기능이 비활성화됩니다 (local.properties: revenuecat.api.key)")
        }
        startKoin {
            androidContext(this@SleepApp)
            modules(androidModule(appConfig), sharedModule)
        }
    }
}