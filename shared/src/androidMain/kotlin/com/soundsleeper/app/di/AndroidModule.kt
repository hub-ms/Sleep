@file:OptIn(InternalVoyagerApi::class)

package com.soundsleeper.app.di

import android.content.Context
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.media3.common.util.UnstableApi
import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.SharedPreferencesSettings
import com.russhwolf.settings.coroutines.FlowSettings
import com.russhwolf.settings.coroutines.toFlowSettings
import com.soundsleeper.app.AndroidSocialAuthService
import com.soundsleeper.app.AppConfig
import com.soundsleeper.app.DatabaseHolder
import com.soundsleeper.app.platform.SleepStageClassifier
import com.soundsleeper.app.platform.SocialAuthManager
import com.soundsleeper.app.platform.AndroidAudioSystem
import com.soundsleeper.app.platform.BedtimeReminderScheduler
import com.soundsleeper.app.platform.AndroidBedtimeReminderScheduler
import com.soundsleeper.app.platform.ChatSupportManager
import com.soundsleeper.app.platform.AndroidChatSupportManager
import com.soundsleeper.app.platform.CrashReporter
import com.soundsleeper.app.platform.FirebaseCrashReporter
import com.soundsleeper.app.platform.DatabaseDriverFactory
import com.soundsleeper.app.data.local.generated.SleepDatabase
import com.soundsleeper.app.data.local.repository.AuthRepositoryImpl
import com.soundsleeper.app.data.local.repository.BillingRepositoryImpl
import com.soundsleeper.app.data.local.repository.LegalRepositoryImpl
import com.soundsleeper.app.data.local.repository.SleepAdviceRepositoryImpl
import com.soundsleeper.app.data.local.repository.SleepMusicRepositoryImpl
import com.soundsleeper.app.data.local.repository.SleepSessionRepositoryImpl
import com.soundsleeper.app.data.local.repository.TokenRepositoryImpl
import com.soundsleeper.app.data.local.repository.VersionRepositoryImpl
import com.soundsleeper.app.data.remote.api.AuthApi
import com.soundsleeper.app.data.remote.api.BillingApi
import com.soundsleeper.app.data.remote.api.LegalApi
import com.soundsleeper.app.domain.repository.SleepAdviceRepository
import com.soundsleeper.app.data.remote.api.SleepAdviceApi
import com.soundsleeper.app.data.remote.api.SleepApi
import com.soundsleeper.app.platform.PlayBillingManager
import com.soundsleeper.app.platform.PurchaseIdentityManager
import com.soundsleeper.app.data.remote.dto.response.AuthInfoResponse
import com.soundsleeper.app.platform.AesGcmSecureStorage
import com.soundsleeper.app.platform.AndroidTrackingManager
import com.soundsleeper.app.platform.AndroidMusicPlayer
import com.soundsleeper.app.platform.AudioSystem
import com.soundsleeper.app.platform.MusicPlayer
import com.soundsleeper.app.platform.SocialAuthService
import com.soundsleeper.app.util.SleepAnalyzer
import com.soundsleeper.app.domain.repository.*
import com.soundsleeper.app.platform.ActiveSessionStore
import com.soundsleeper.app.util.JwtLocalParser
import com.soundsleeper.app.platform.SecureStorage
import com.soundsleeper.app.platform.TrackingManager
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.AuthCircuitBreaker
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module
import java.util.concurrent.TimeUnit
import kotlin.time.ExperimentalTime

@UnstableApi
@OptIn(
    ExperimentalTime::class,
    ExperimentalMaterial3Api::class,
    ExperimentalCoroutinesApi::class,
    ExperimentalSettingsApi::class,
)
fun androidModule(appConfig: AppConfig) = module {
    single { appConfig }

    // ── 인프라 ──────────────────────────────────────────────
    single<DatabaseDriverFactory> { DatabaseDriverFactory(androidContext()) }

    single<AudioSystem> { AndroidAudioSystem(androidContext()) }

    single<HttpClient> {
        val tokenRepository: TokenRepository = get()

        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) = Napier.d(message, tag = "Ktor")
                }
                level = if (appConfig.isDebug) LogLevel.BODY else LogLevel.NONE
            }
            install(Auth) {
                bearer {
                    // BearerAuthProvider.addRequestHeaders는 기존 Authorization 헤더를 지우고
                    // access token으로 덮어쓴다. 그리고 이 훅은 markAsRefreshTokenRequest()와
                    // 무관하게(플러그인이 AuthCircuitBreaker를 401 재시도 루프에서만 검사한다)
                    // 모든 요청에 대해 돈다. 그래서 아래 갱신 요청에 실어 보내는 refresh token이
                    // 만료된 access token으로 교체돼 /auth/refresh가 항상 실패했다.
                    // 갱신 요청에서는 플러그인이 헤더를 건드리지 않도록 제외한다.
                    sendWithoutRequest { request ->
                        !request.attributes.contains(AuthCircuitBreaker)
                    }
                    loadTokens {
                        val access = tokenRepository.getAccessToken()
                        val refresh = tokenRepository.getRefreshToken()
                        if (access != null && refresh != null) BearerTokens(access, refresh) else null
                    }
                    refreshTokens {
                        val currentRefresh = tokenRepository.getRefreshToken() ?: return@refreshTokens null
                        val response = client.post("${appConfig.baseUrl}auth/refresh") {
                            markAsRefreshTokenRequest()
                            header(HttpHeaders.Authorization, "Bearer $currentRefresh")
                        }
                        when {
                            response.status.isSuccess() -> {
                                val body = response.body<AuthInfoResponse>()
                                tokenRepository.saveAccessToken(body.accessToken)
                                tokenRepository.saveRefreshToken(body.refreshToken)
                                BearerTokens(body.accessToken, body.refreshToken)
                            }
                            // 서버가 refresh token을 거절한 경우에만 세션을 버린다.
                            response.status == HttpStatusCode.Unauthorized ||
                                response.status == HttpStatusCode.BadRequest -> {
                                Napier.w("refresh 거절(${response.status}) → 로컬 세션 정리")
                                tokenRepository.clearAccessToken()   // Flow를 트리거해서 상태 전이 유도
                                tokenRepository.clearRefreshToken()
                                null
                            }
                            // 네트워크/서버 장애(502 등)로 토큰까지 버리면 멀쩡한 세션이 날아간다.
                            else -> {
                                Napier.e("refresh 실패(${response.status}) → 세션은 유지")
                                null
                            }
                        }
                    }
                }
            }
            defaultRequest {
                url(appConfig.baseUrl)
                contentType(ContentType.Application.Json)
            }
            engine {
                config {
                    connectTimeout(15, TimeUnit.SECONDS)
                    readTimeout(15, TimeUnit.SECONDS)
                }
            }
        }
    }
    single { ActiveSessionStore(get<ObservableSettings>()) }
    single<SleepDatabase> {
        runBlocking {
            DatabaseHolder.getInstance(get<DatabaseDriverFactory>())
        }
    }

    single<SocialAuthService> { AndroidSocialAuthService(get()) }
    single { SleepAnalyzer(get()) }
    single { SleepStageClassifier() }


    // ── 보안 & 설정 ─────────────────────────────────────────
    single<SecureStorage> { AesGcmSecureStorage() }
    single<ObservableSettings> {
        SharedPreferencesSettings(
            androidContext().getSharedPreferences("sleepytime_prefs", Context.MODE_PRIVATE)
        )
    }
    single { JwtLocalParser() }
    single<FlowSettings> {
        SharedPreferencesSettings(
            androidContext().getSharedPreferences("sleepytime_prefs", Context.MODE_PRIVATE)
        ).toFlowSettings()
    }


    // ── 플랫폼 서비스 ────────────────────────────────────────
    single<SocialAuthManager> { SocialAuthManager(androidContext(), appConfig.googleOAuthClientId) }
    single<BedtimeReminderScheduler> { AndroidBedtimeReminderScheduler(androidContext()) }
    single<ChatSupportManager> { AndroidChatSupportManager(androidContext(), appConfig.channelTalkPluginKey) }
    single<CrashReporter> { FirebaseCrashReporter() }
    // ── API ─────────────────────────────────────────────────
    single { AuthApi(get()) }
    single { BillingApi(get()) }
    single { SleepAdviceApi(get()) }
    single { SleepApi(get()) }
    single { LegalApi(get()) }

    single { PlayBillingManager(androidContext()) }
    single { PurchaseIdentityManager() }
    single<BillingRepository> { BillingRepositoryImpl(get(), get()) }
    single<SleepAdviceRepository> { SleepAdviceRepositoryImpl(get(), get()) }

    single<TokenRepository> {
        TokenRepositoryImpl(get(), get(), get())
    }
    single<MusicPlayer> { AndroidMusicPlayer() }
    single<AuthRepository> {
        AuthRepositoryImpl(get(), get(), get(), get(), get(), get(), get(), get(), get())
    }
    single<SleepMusicRepository> {
        SleepMusicRepositoryImpl(get())
    }
    single<SleepSessionRepository> {
        SleepSessionRepositoryImpl(get(),get(), get(), get(), get(), get(),get())
    }
    single<VersionRepository> {
        VersionRepositoryImpl()
    }

    single<LegalRepository> {
        LegalRepositoryImpl(get(), get(), get())
    }
    single {
        AndroidTrackingManager(
            context = androidContext(),
            classifier = get(),
            sleepSessionRepository = get(),
            sensorBridge = get(),
            musicPlayer = get(),
            activeSessionStore = get(),
            sleepSettingRepository = get(),
            audioSystem = get(),
            // 모델 파일(assets/sleep_model.tflite)이 없을 때 mock 수면단계로 측정을 진행할지.
            // 디버그 빌드에서만 허용한다 — 릴리즈는 모델이 없으면 지금처럼 측정을 거부해야 한다
            // (SleepStageClassifier.mockModeEnabled 의 설명 참고).
            allowMockModel = appConfig.isDebug
        )
    } bind TrackingManager::class
}
