package com.sleepytime.shared.di

import android.content.Context
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.SharedPreferencesSettings
import com.russhwolf.settings.coroutines.FlowSettings
import com.russhwolf.settings.coroutines.toFlowSettings
import com.sleepytime.shared.AndroidSocialAuthService
import com.sleepytime.shared.BuildConfig
import com.sleepytime.shared.DatabaseHolder
import com.sleepytime.shared.platform.SleepStageClassifier
import com.sleepytime.shared.platform.SocialAuthManager
import com.sleepytime.shared.platform.AndroidVolumeObserver
import com.sleepytime.shared.platform.AndroidFileSaver
import com.sleepytime.shared.platform.AndroidSleepMeasureManager
import com.sleepytime.shared.platform.CsvExporter
import com.sleepytime.shared.platform.DeviceSensorProvider
import com.sleepytime.shared.platform.LocationProvider
import com.sleepytime.shared.platform.SleepMeasureManager
import com.sleepytime.shared.platform.AndroidAudioSystem
import com.sleepytime.shared.platform.DatabaseDriverFactory
import com.sleepytime.shared.data.local.generated.SleepDatabase
import com.sleepytime.shared.data.local.repository.AuthRepositoryImpl
import com.sleepytime.shared.data.local.repository.SleepMusicRepositoryImpl
import com.sleepytime.shared.data.local.repository.SleepSessionRepositoryImpl
import com.sleepytime.shared.data.local.repository.TokenRepositoryImpl
import com.sleepytime.shared.data.local.repository.WeatherRepositoryImpl
import com.sleepytime.shared.data.remote.api.AuthApi
import com.sleepytime.shared.platform.HeartRateMonitor
import com.sleepytime.shared.platform.NoiseDetector
import com.sleepytime.shared.platform.SensorBridge
import com.sleepytime.shared.platform.AesGcmSecureStorage
import com.sleepytime.shared.platform.AndroidTrackingManager
import com.sleepytime.shared.util.GridCoordinateConverter
import com.sleepytime.shared.util.WeatherCorrectionCalculator
import com.sleepytime.shared.platform.FileSaver
import com.sleepytime.shared.platform.AndroidMusicPlayer
import com.sleepytime.shared.platform.AudioSystem
import com.sleepytime.shared.platform.MusicPlayer
import com.sleepytime.shared.platform.SocialAuthService
import com.sleepytime.shared.ui.tracking.SleepAnalyzer
import com.sleepytime.shared.domain.repository.*
import com.sleepytime.shared.util.JwtLocalParser
import com.sleepytime.shared.platform.SecureStorage
import com.sleepytime.shared.platform.TrackingManager
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

@ExperimentalSettingsApi
@UnstableApi
val androidModule = module {

    // ── 인프라 ──────────────────────────────────────────────
    single<DatabaseDriverFactory> { DatabaseDriverFactory(androidContext()) }

    single<FusedLocationProviderClient> {
        LocationServices.getFusedLocationProviderClient(androidContext())
    }

    single<String> { BuildConfig.KMA_SERVICE_KEY }
    single<AudioSystem> { AndroidAudioSystem(androidContext(), get()) }
    single { AndroidVolumeObserver(androidContext()) }

    single<HttpClient> {
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
                level = if (BuildConfig.DEBUG) LogLevel.BODY else LogLevel.NONE
            }
            defaultRequest {
                url(BuildConfig.BASE_URL)
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
    single<SleepDatabase> {
        runBlocking {
            DatabaseHolder.getInstance(get<DatabaseDriverFactory>())
        }
    }

    single<SocialAuthService> { AndroidSocialAuthService(get()) }
    single { GridCoordinateConverter() }
    single { WeatherCorrectionCalculator() }
    single<SleepMeasureManager> { AndroidSleepMeasureManager(get(), get(), get()) }
    single { CsvExporter(get()) }
    single<FileSaver> { AndroidFileSaver(androidContext()) }
    single { SleepAnalyzer(get()) }
    single { SleepStageClassifier() }
    single { SensorBridge(androidContext(), get(), get()) }


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
    single<SocialAuthManager> { SocialAuthManager(androidContext()) }
    single<LocationProvider> {
        LocationProvider(fusedClient = get(), context = androidContext())
    }
    single { DeviceSensorProvider(androidContext()) }
    single { HeartRateMonitor() }
    single { NoiseDetector() }

    // ── API ─────────────────────────────────────────────────
    single { AuthApi(get()) }

    // ── Repository ──────────────────────────────────────────
    single<TokenRepository> { TokenRepositoryImpl(get(), get(), get()) }
    single<WeatherRepository> {
        WeatherRepositoryImpl(
            httpClient = get(),
            settings = get(),
            locationProvider = get(),
            sensorProvider = get(),
            gridConverter = get(),
            calculator = get(),
            kmaServiceKey = get()
        )
    }
    single<MusicPlayer> { AndroidMusicPlayer() }
    single<AuthRepository> {
        AuthRepositoryImpl(
            httpClient = get(),
            tokenRepository = get(),
            userDao = get(),
            socialAuthManager = get(),
            settings = get()
        )
    }
    single<SleepMusicRepository> { SleepMusicRepositoryImpl(get()) }
    single<SleepSessionRepository> {
        SleepSessionRepositoryImpl(get(),get(), get())
    }
    single {
        AndroidTrackingManager(
            context = androidContext(),
            classifier = get(),
            measureManager = get(),
            sleepSessionRepository = get(),
            weatherRepository = get(),
            heartRateMonitor = get(),
            noiseDetector = get(),
            sensorBridge = get(),
            musicPlayer = get(),
            csvExporter = get()
        )
    } bind TrackingManager::class
}