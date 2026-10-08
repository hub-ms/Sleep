package com.soundsleeper.app.data.preferences

data class SleepSettingSnapshot(
    val alarmHour: Int,
    val alarmMinute: Int,
    val reminderHour: Int,
    val reminderMinute: Int,
    val isEnabled: Boolean,
    val alarmName: String?,
    val isVibration: Boolean,
    val volume: Float,
    val isSmartAlarm: Boolean,
    val smartAlarmRange: Int,
    val reminderEnabled: Boolean,
)