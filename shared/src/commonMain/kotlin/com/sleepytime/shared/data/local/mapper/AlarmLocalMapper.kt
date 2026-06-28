package com.sleepytime.shared.data.local.mapper

import com.sleepytime.shared.data.local.AlarmEntity
import com.sleepytime.shared.domain.model.Alarm

fun AlarmEntity.toDomain() = Alarm(
    hour = hour,
    minute = minute,

    isEnabled = isEnabled,
    isVibrationEnabled = isVibrationEnabled,
    isSmartAlarmEnabled = isSmartAlarmEnabled,
    smartAlarmRange = if (isSmartAlarmEnabled) smartAlarmRange else 0,
    isGradualVolume = isGradualVolume,

    sound = sound,
)
fun Alarm.toEntity() = AlarmEntity(
    alarmId = 1L,

    hour = hour,
    minute = minute,

    isEnabled = isEnabled,
    isVibrationEnabled = isVibrationEnabled,
    isSmartAlarmEnabled = isSmartAlarmEnabled,
    smartAlarmRange = if (isSmartAlarmEnabled) smartAlarmRange else 0,
    isGradualVolume = isGradualVolume,

    sound = sound,
)
