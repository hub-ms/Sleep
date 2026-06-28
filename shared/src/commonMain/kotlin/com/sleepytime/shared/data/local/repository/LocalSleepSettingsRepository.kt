package com.sleepytime.shared.data.local.repository

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.coroutines.FlowSettings
import com.sleepytime.shared.util.PreferencesKeys.Alarm.ENABLED
import com.sleepytime.shared.util.PreferencesKeys.Alarm.HOUR
import com.sleepytime.shared.util.PreferencesKeys.Alarm.IS_TIMER
import com.sleepytime.shared.util.PreferencesKeys.Alarm.MINUTE
import com.sleepytime.shared.util.PreferencesKeys.Alarm.MUSIC_NAME
import com.sleepytime.shared.util.PreferencesKeys.Alarm.TIMER_MINUTES
import com.sleepytime.shared.domain.model.Alarm
import com.sleepytime.shared.domain.repository.SleepSettingsRepository
import com.sleepytime.shared.util.ResourceMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@ExperimentalSettingsApi
class LocalSleepSettingsRepository(
    private val settings: FlowSettings
) : SleepSettingsRepository {
    override fun observeSettings(): Flow<Alarm> {
        return combine(
            settings.getIntFlow(HOUR, 7),
            settings.getIntFlow(MINUTE, 30),
            settings.getBooleanFlow(ENABLED, false),
            settings.getStringOrNullFlow(MUSIC_NAME) // 라이브러리 확장 함수 형태에 맞게 지정
        ) { hour, minute, isEnabled, musicName ->
            Alarm(
                hour = hour,
                minute = minute,
                isEnabled = isEnabled,
                sound = if (musicName == null) {
                    Alarm.Sound.DEFAULT
                } else {
                    Alarm.Sound(
                        id = musicName,
                        titleRes = ResourceMapper.getMusicTitleRes(musicName),
                        filePath = "assets/sounds/$musicName.mp3",
                        volume = 0.5f
                    )
                },
                isVibrationEnabled = true,
                isSmartAlarmEnabled = false,
                smartAlarmRange = 20,
                isGradualVolume = false,
            )
        }
    }

    override suspend fun setWakeUpTime(hour: Int, minute: Int) {
        settings.putInt(HOUR, hour)
        settings.putInt(MINUTE, minute)
    }

    override suspend fun setAlarmEnabled(enabled: Boolean) {
        settings.putBoolean(ENABLED, enabled)
    }

    override suspend fun setSelectedMusic(name: String?) {
        if (name != null) {
            settings.putString(MUSIC_NAME, name)
        } else {
            settings.remove(MUSIC_NAME)
        }
    }

    override suspend fun setTimerMinutes(minutes: Int) {
        settings.putInt(TIMER_MINUTES, minutes)
    }

    override suspend fun setIsTimer(enabled: Boolean) {
        settings.putBoolean(IS_TIMER, enabled)
    }
}