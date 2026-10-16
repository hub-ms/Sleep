package com.soundsleeper.app.data.local.mapper

import com.soundsleeper.app.data.local.SleepSettingEntity
import com.soundsleeper.app.data.preferences.SleepSettingSnapshot
import com.soundsleeper.app.domain.model.Alarm
import com.soundsleeper.app.domain.model.Reminder
import com.soundsleeper.app.domain.model.SleepSetting
import com.soundsleeper.app.util.ResourceMapper

fun SleepSettingEntity.toDomain() = SleepSetting(
    alarm = Alarm(
        hour = alarmHour,
        minute = alarmMinute,
        isEnabled = isAlarmEnabled,
        isVibrationEnabled = isVibrationEnabled,
        isSmartAlarmEnabled = isSmartAlarmEnabled,
        smartAlarmRange = if (isSmartAlarmEnabled) smartAlarmRange else 0,
        sound = sound,
    ),
    reminder = Reminder(
        hour = reminderHour,
        minute = reminderMinute,
        isEnabled = isReminderEnabled
    )
)
fun SleepSettingSnapshot.toSleepSetting(): SleepSetting {
    val alarmSound = if (alarmName == null) {
            Alarm.Sound.DEFAULT.copy(
                volume = volume
            )
        } else {
            Alarm.Sound(
                id = alarmName,
                titleRes = ResourceMapper
                    .getAlarmTitleRes(alarmName)
                    .toString(),
                filePath = "files/$alarmName.ogg",
                volume = volume
            )
        }
    return SleepSetting(
        alarm = Alarm(
            hour = alarmHour,
            minute = alarmMinute,
            isEnabled = isEnabled,
            sound = alarmSound,
            isVibrationEnabled = isVibration,
            isSmartAlarmEnabled = isSmartAlarm,
            smartAlarmRange = smartAlarmRange
        ),
        reminder = Reminder(
            hour = reminderHour,
            minute = reminderMinute,
            isEnabled = reminderEnabled
        )
    )
}
