package com.soundsleeper.app.data.local.dao
import com.soundsleeper.app.data.local.generated.SleepDatabase
import com.soundsleeper.app.domain.model.Alarm
class SleepSettingDao(
    db: SleepDatabase
) {
    private val queries = db.sleepSettingEntityQueries
    fun upsertAlarm(
        hour: Int,
        minute: Int,
        isEnabled: Boolean,
        isVibrationEnabled: Boolean,
        isSmartAlarmEnabled: Boolean,
        smartAlarmRange: Int,
        sound: Alarm.Sound
    ) {
        queries.upsertAlarm(
            alarmHour = hour,
            alarmMinute = minute,
            isAlarmEnabled = isEnabled,
            isVibrationEnabled = isVibrationEnabled,
            isSmartAlarmEnabled = isSmartAlarmEnabled,
            smartAlarmRange = smartAlarmRange,
            sound = sound
        )
    }
    fun updateAlarmTime(hour: Int, minute: Int) {
        queries.updateAlarmTime(alarmHour = hour, alarmMinute = minute)
    }
    fun updateEnabled(isEnabled: Boolean) {
        queries.updateEnabled(isEnabled)
    }
    fun updateVibrationEnabled(isEnabled: Boolean) {
        queries.updateVibrationEnabled(isEnabled)
    }
    fun updateSmartAlarmEnabled(isEnabled: Boolean) {
        queries.updateSmartAlarmEnabled(isEnabled)
    }
    fun updateSmartAlarmRange(range: Int) {
        queries.updateSmartAlarmRange(range)
    }
    fun updateSound(sound: Alarm.Sound) {
        queries.updateSound(sound)
    }
    fun updateReminderTime(hour: Int, minute: Int) {
        queries.updateReminderTime(reminderHour = hour, reminderMinute = minute)
    }
    fun updateReminderEnabled(isEnabled: Boolean) {
        queries.updateReminderEnabled(isEnabled)
    }
}