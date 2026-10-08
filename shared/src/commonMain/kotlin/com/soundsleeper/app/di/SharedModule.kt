package com.soundsleeper.app.di

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.soundsleeper.app.data.auth.AuthStatusResolver
import com.soundsleeper.app.data.auth.SessionManager
import com.soundsleeper.app.data.auth.UserCacheManager
import com.soundsleeper.app.data.local.dao.UserDao
import com.soundsleeper.app.data.local.dao.SleepMusicDao
import com.soundsleeper.app.data.local.dao.SleepSessionDao
import com.soundsleeper.app.data.local.dao.SleepSettingDao
import com.soundsleeper.app.data.local.repository.BundledLegalSource
import com.soundsleeper.app.data.local.repository.LegalCache
import com.soundsleeper.app.data.local.repository.SleepSettingRepositoryImpl
import com.soundsleeper.app.data.preferences.AppPreferencesManager
import com.soundsleeper.app.data.preferences.SleepSettingProvider
import com.soundsleeper.app.data.sleep.PredictionHistoryManager
import com.soundsleeper.app.data.sleep.SleepSessionFactory
import com.soundsleeper.app.data.sleep.SleepStageTimelineGenerator
import com.soundsleeper.app.data.sleep.SleepStatisticsCalculator
import com.soundsleeper.app.domain.repository.SleepSettingRepository
import com.soundsleeper.app.enum_.LegalType
import com.soundsleeper.app.platform.AlarmStorage
import com.soundsleeper.app.ui.alarm.AlarmViewModel
import com.soundsleeper.app.ui.auth.AuthViewModel
import com.soundsleeper.app.ui.home.HomeViewModel
import com.soundsleeper.app.ui.music.MusicViewModel
import com.soundsleeper.app.ui.paywall.PaywallViewModel
import com.soundsleeper.app.ui.setting.AppearanceViewModel
import com.soundsleeper.app.ui.report.ReportViewModel
import com.soundsleeper.app.ui.tracking.TrackingViewModel
import com.soundsleeper.app.platform.SensorBridge
import com.soundsleeper.app.ui.onboarding.PermissionViewModel
import com.soundsleeper.app.ui.setting.ChatViewModel
import com.soundsleeper.app.ui.setting.LegalViewModel
import com.soundsleeper.app.ui.setting.SettingViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import kotlin.time.ExperimentalTime

@OptIn(
    ExperimentalSettingsApi::class,
    ExperimentalTime::class,
    ExperimentalCoroutinesApi::class,
)
val sharedModule = module {
    single<SleepSettingRepository> { SleepSettingRepositoryImpl(get(), get()) }
    single { SensorBridge() }

    // ── DAO ──────────────────────────────────────────────────
    single { AlarmStorage(get()) }
    single { UserDao(get()) }
    single { SleepMusicDao(get()) }
    single { SleepSessionDao(get()) }
    single { SleepSettingDao(get()) }

    // ── 인증 & 환경설정 ──────────────────────────────────────
    single { AuthStatusResolver() }
    single { UserCacheManager(get(), get()) }
    single { SessionManager(get(), get()) }
    single { AppPreferencesManager(get()) }
    single { SleepSettingProvider(get()) }

    // ── 수면 분석 ─────────────────────────────────────────────
    single { PredictionHistoryManager() }
    single { SleepStatisticsCalculator() }
    single { SleepStageTimelineGenerator() }
    single { SleepSessionFactory() }

    // ── ViewModels ────────────────────────────────────────────
    single { AuthViewModel(get(), get(), get(), get(), get(), get()) }
    single { PermissionViewModel(get()) }
    single { HomeViewModel(get(), get(), get(),
        get(),get(), get()) }
    single { AlarmViewModel(get(), get(), get(), get(), get(), get(), get()) }
    single { MusicViewModel(get(), get(), get(), get()) }
    single { TrackingViewModel(get(),get(),get(), get()) }
    single { ReportViewModel(get(), get(), get()) }
    single { ChatViewModel(get(), get()) }
    single { PaywallViewModel(get(), get(), get()) }
    single { AppearanceViewModel(get()) }
    factory {
        SettingViewModel(
            appConfig = get(),
            versionRepository = get()
        )
    }

    single { Json { ignoreUnknownKeys = true } }
    single { LegalCache(get<ObservableSettings>(), get()) }
    single { BundledLegalSource(get()) }

    factory { (type: LegalType) -> LegalViewModel(type, get()) }
}
