package com.soundsleeper.app.data.local.repository

import com.russhwolf.settings.ExperimentalSettingsApi
import com.soundsleeper.app.data.local.dao.SleepSettingDao
import com.soundsleeper.app.data.local.mapper.toSleepSetting
import com.soundsleeper.app.data.preferences.SleepSettingProvider
import com.soundsleeper.app.domain.model.Alarm
import com.soundsleeper.app.domain.model.SleepSetting
import com.soundsleeper.app.domain.repository.SleepSettingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@ExperimentalSettingsApi
class SleepSettingRepositoryImpl(
    private val sleepSettingDao: SleepSettingDao,
    private val sleepSettingProvider: SleepSettingProvider
) : SleepSettingRepository {
    override suspend fun upsertAlarm(alarm: Alarm) = sleepSettingDao.upsertAlarm(hour = alarm.hour, minute = alarm.minute, isEnabled = alarm.isEnabled, isVibrationEnabled = alarm.isVibrationEnabled,isSmartAlarmEnabled = alarm.isSmartAlarmEnabled, smartAlarmRange = alarm.smartAlarmRange, sound = alarm.sound)
    override suspend fun updateAlarmTime(hour: Int, minute: Int) {
        sleepSettingDao.updateAlarmTime(hour = hour, minute = minute)
    }
    override suspend fun updateAlarmEnabled(isEnabled: Boolean) {
        sleepSettingDao.updateEnabled(isEnabled)
    }
    override suspend fun updateVibrationEnabled(isEnabled: Boolean) {
        sleepSettingDao.updateVibrationEnabled(isEnabled)
    }
    override suspend fun updateSmartAlarmEnabled(isEnabled: Boolean) {
        sleepSettingDao.updateSmartAlarmEnabled(isEnabled)
    }
    override suspend fun updateSmartAlarmRange(range: Int) {
        sleepSettingDao.updateSmartAlarmRange(range)
    }
    override suspend fun updateSound(sound: Alarm.Sound) {
        sleepSettingDao.updateSound(sound)
    }
    override suspend fun updateReminderTime(hour: Int, minute: Int) {
        sleepSettingDao.updateReminderTime(hour, minute)
    }
    override suspend fun updateReminderEnabled(isEnabled: Boolean) {
        sleepSettingDao.updateReminderEnabled(isEnabled)
    }
    override fun observeSettings(): Flow<SleepSetting> = sleepSettingProvider
            .observeSnapshot()
            .map { it.toSleepSetting() }
}