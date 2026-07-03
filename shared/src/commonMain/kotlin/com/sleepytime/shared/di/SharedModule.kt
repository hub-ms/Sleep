package com.sleepytime.shared.di

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.coroutines.FlowSettings
import com.sleepytime.shared.data.local.dao.AlarmDao
import com.sleepytime.shared.data.local.dao.UserDao
import com.sleepytime.shared.data.local.dao.SleepMusicDao
import com.sleepytime.shared.data.local.dao.SleepSessionDao
import com.sleepytime.shared.data.local.repository.LocalSleepSettingsRepository
import com.sleepytime.shared.domain.repository.SleepSettingsRepository
import com.sleepytime.shared.ui.alarm.AlarmViewModel
import com.sleepytime.shared.ui.auth.AuthViewModel
import com.sleepytime.shared.ui.home.HomeViewModel
import com.sleepytime.shared.ui.music.MusicViewModel
import com.sleepytime.shared.ui.onboarding.OnboardingViewModel
import com.sleepytime.shared.ui.report.ReportViewModel
import com.sleepytime.shared.ui.tracking.TrackingViewModel
import com.sleepytime.shared.platform.SensorBridge
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.koin.dsl.module
import kotlin.time.ExperimentalTime

@ExperimentalSettingsApi
@ExperimentalTime
@ExperimentalCoroutinesApi
val sharedModule = module {
    single<SleepSettingsRepository> { LocalSleepSettingsRepository(get<FlowSettings>()) }
    single { SensorBridge() }

    // ── DAO ──────────────────────────────────────────────────
    single { AlarmDao(get()) }
    single { UserDao(get()) }
    single { SleepMusicDao(get()) }
    single { SleepSessionDao(get()) }

    // ── ViewModels ────────────────────────────────────────────
    single { AuthViewModel(get(), get(), get(), get()) }
    single { OnboardingViewModel() }
    single { HomeViewModel(get(), get(), get(),
        get(),get(), get(), get()) }
    single { AlarmViewModel(get(), get(), get()) }
    single { MusicViewModel(get(), get(), get()) }
    single { TrackingViewModel(get(),get(),get()) }
    single { ReportViewModel(get(), get()) }
}