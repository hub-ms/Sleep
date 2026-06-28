package com.sleepytime.shared.data.local.dao

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.sleepytime.shared.data.local.generated.SleepDatabase
import com.sleepytime.shared.data.local.mapper.toDomain
import com.sleepytime.shared.data.local.mapper.toEntity
import com.sleepytime.shared.domain.model.Alarm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

class AlarmDao(db: SleepDatabase) {

    private val queries = db.alarmEntityQueries

    fun upsert(alarm: Alarm) {
        val alarmEntity = alarm.toEntity()
        queries.upsertAlarm(
            hour = alarmEntity.hour,
            minute = alarmEntity.minute,

            isEnabled = alarmEntity.isEnabled,
            isVibrationEnabled = alarmEntity.isVibrationEnabled,
            isSmartAlarmEnabled = alarmEntity.isSmartAlarmEnabled,
            smartAlarmRange = alarmEntity.smartAlarmRange,
            isGradualVolume = alarmEntity.isGradualVolume,

            sound = alarmEntity.sound,
        )
    }

    fun getAlarm(): Alarm? {
        return queries.getAlarm().executeAsOneOrNull()?.toDomain()
    }
    fun observeAlarm(): Flow<Alarm?> {
        return queries.getAlarm()
        .asFlow()
        .mapToOneOrNull(Dispatchers.IO)
        .map { entity ->
            entity?.toDomain()
        }
    }


    fun setEnabled(isEnabled: Boolean) {
        queries.setEnabled(isEnabled)
    }

    fun updateTime(hour: Int, minute: Int) {
        queries.updateTime(hour, minute)
    }
}
