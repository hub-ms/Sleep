package com.sleepytime.shared.data.local.repository

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.coroutines.FlowSettings
import com.sleepytime.shared.util.PreferencesKeys.Alarm.ENABLED
import com.sleepytime.shared.util.PreferencesKeys.Alarm.HOUR
import com.sleepytime.shared.util.PreferencesKeys.Alarm.IS_TIMER
import com.sleepytime.shared.util.PreferencesKeys.Alarm.MINUTE
import com.sleepytime.shared.util.PreferencesKeys.Alarm.TIMER_MINUTES
import com.sleepytime.shared.domain.model.Alarm
import com.sleepytime.shared.domain.repository.SleepSettingsRepository
import com.sleepytime.shared.util.PreferencesKeys.Alarm.ALARM_NAME
import com.sleepytime.shared.util.PreferencesKeys.Alarm.SMART_ALARM
import com.sleepytime.shared.util.PreferencesKeys.Alarm.SMART_ALARM_RANGE
import com.sleepytime.shared.util.PreferencesKeys.Alarm.VIBRATION
import com.sleepytime.shared.util.PreferencesKeys.Alarm.VOLUME
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
            settings.getStringOrNullFlow(ALARM_NAME),
            settings.getBooleanFlow(VIBRATION, false),
            settings.getFloatFlow(VOLUME, 0.5f),
            settings.getBooleanFlow(SMART_ALARM, false),
            settings.getIntFlow(SMART_ALARM_RANGE, 15)
        ) { flows ->
            val hour = flows[0] as Int
            val minute = flows[1] as Int
            val isEnabled = flows[2] as Boolean
            val alarmName = flows[3] as? String
            val isVibration = flows[4] as Boolean
            val volume = flows[5] as Float
            val isSmartAlarm = flows[6] as Boolean
            val smartAlarmRange = flows[7] as Int

            Alarm(
                hour = hour,
                minute = minute,
                isEnabled = isEnabled,
                sound = if (alarmName == null) {
                    Alarm.Sound.DEFAULT.copy(volume = volume)
                } else {
                    Alarm.Sound(
                        id = alarmName,
                        titleRes = ResourceMapper.getAlarmTitleRes(alarmName).toString(),
                        filePath = "files/$alarmName.ogg",
                        volume = volume
                    )
                },
                isVibrationEnabled = isVibration,
                isSmartAlarmEnabled = isSmartAlarm,
                smartAlarmRange = smartAlarmRange
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
        if (name != null) settings.putString(ALARM_NAME, name)
        else settings.remove(ALARM_NAME)
    }

    override suspend fun setTimerMinutes(minutes: Int) {
        settings.putInt(TIMER_MINUTES, minutes)
    }

    override suspend fun setIsTimer(enabled: Boolean) {
        settings.putBoolean(IS_TIMER, enabled)
    }

    override suspend fun setVibrationEnabled(enabled: Boolean) {
        settings.putBoolean(VIBRATION, enabled)
    }

    override suspend fun setSmartAlarmEnabled(enabled: Boolean) {
        settings.putBoolean(SMART_ALARM, enabled)
    }

    override suspend fun setSmartAlarmRange(range: Int) {
        settings.putInt(SMART_ALARM_RANGE, range)
    }

    override suspend fun setVolume(volume: Float) {
        settings.putFloat(VOLUME, volume)
    }
}