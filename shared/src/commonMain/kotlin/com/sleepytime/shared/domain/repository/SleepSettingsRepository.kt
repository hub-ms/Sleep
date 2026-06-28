package com.sleepytime.shared.domain.repository

import com.sleepytime.shared.domain.model.Alarm
import kotlinx.coroutines.flow.Flow

interface SleepSettingsRepository {
    fun observeSettings(): Flow<Alarm>
    suspend fun setWakeUpTime(hour: Int, minute: Int)
    suspend fun setAlarmEnabled(enabled: Boolean)
    suspend fun setSelectedMusic(name: String?)
    suspend fun setTimerMinutes(minutes: Int)
    suspend fun setIsTimer(enabled: Boolean)
}