package com.somniotune.app.di

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.somniotune.app.data.auth.AuthStatusResolver
import com.somniotune.app.data.auth.SessionManager
import com.somniotune.app.data.auth.UserCacheManager
import com.somniotune.app.data.local.dao.UserDao
import com.somniotune.app.data.local.dao.SleepMusicDao
import com.somniotune.app.data.local.dao.SleepSessionDao
import com.somniotune.app.data.local.dao.SleepSettingDao
import com.somniotune.app.data.local.repository.BundledLegalSource
import com.somniotune.app.data.local.repository.LegalCache
import com.somniotune.app.data.local.repository.SleepSettingRepositoryImpl
import com.somniotune.app.data.preferences.AppPreferencesManager
import com.somniotune.app.data.preferences.SleepSettingProvider
import com.somniotune.app.data.sleep.PredictionHistoryManager
import com.somniotune.app.data.sleep.SleepSessionFactory
import com.somniotune.app.data.sleep.SleepStageTimelineGenerator
import com.somniotune.app.data.sleep.SleepStatisticsCalculator
import com.somniotune.app.domain.repository.SleepSettingRepository
import com.somniotune.app.enum_.LegalType
import com.somniotune.app.platform.AlarmStorage
import com.somniotune.app.ui.alarm.AlarmViewModel
import com.somniotune.app.ui.auth.AuthViewModel
import com.somniotune.app.ui.home.HomeViewModel
import com.somniotune.app.ui.music.MusicViewModel
import com.somniotune.app.ui.paywall.PaywallViewModel
import com.somniotune.app.ui.setting.AppearanceViewModel
import com.somniotune.app.ui.report.ReportViewModel
import com.somniotune.app.ui.tracking.TrackingViewModel
import com.somniotune.app.platform.SensorBridge
import com.somniotune.app.ui.onboarding.PermissionViewModel
import com.somniotune.app.ui.setting.ChatViewModel
import com.somniotune.app.ui.setting.LegalViewModel
import com.somniotune.app.ui.setting.SettingViewModel
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
