package com.somniotune.app.domain.repository

import com.somniotune.app.domain.model.Alarm
import com.somniotune.app.domain.model.SleepSetting
import kotlinx.coroutines.flow.Flow

interface SleepSettingRepository {
    suspend fun upsertAlarm(alarm: Alarm)
    suspend fun updateAlarmTime(hour: Int, minute: Int)
    suspend fun updateAlarmEnabled(isEnabled: Boolean)
    suspend fun updateVibrationEnabled(isEnabled: Boolean)
    suspend fun updateSmartAlarmEnabled(isEnabled: Boolean)
    suspend fun updateSmartAlarmRange(range: Int)
    suspend fun updateSound(sound: Alarm.Sound)
    fun observeSettings(): Flow<SleepSetting>
    suspend fun updateReminderTime(hour: Int, minute: Int)

    suspend fun updateReminderEnabled(isEnabled: Boolean)
}