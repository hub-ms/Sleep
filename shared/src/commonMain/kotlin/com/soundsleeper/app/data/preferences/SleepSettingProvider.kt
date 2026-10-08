@file:OptIn(ExperimentalSettingsApi::class)

package com.soundsleeper.app.data.preferences

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.coroutines.FlowSettings
import com.soundsleeper.app.util.PreferencesKeys.Alarm.ALARM_HOUR
import com.soundsleeper.app.util.PreferencesKeys.Alarm.ALARM_MINUTE
import com.soundsleeper.app.util.PreferencesKeys.Alarm.ALARM_NAME
import com.soundsleeper.app.util.PreferencesKeys.Alarm.ENABLED
import com.soundsleeper.app.util.PreferencesKeys.Alarm.REMINDER_ENABLED
import com.soundsleeper.app.util.PreferencesKeys.Alarm.REMINDER_HOUR
import com.soundsleeper.app.util.PreferencesKeys.Alarm.REMINDER_MINUTE
import com.soundsleeper.app.util.PreferencesKeys.Alarm.SMART_ALARM
import com.soundsleeper.app.util.PreferencesKeys.Alarm.SMART_ALARM_RANGE
import com.soundsleeper.app.util.PreferencesKeys.Alarm.VIBRATION
import com.soundsleeper.app.util.PreferencesKeys.Alarm.VOLUME
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class SleepSettingProvider(
    private val settings: FlowSettings
) {
    fun observeSnapshot(): Flow<SleepSettingSnapshot> {
        return combine<Any?, SleepSettingSnapshot>(
            settings.getIntFlow(ALARM_HOUR, 7),
            settings.getIntFlow(ALARM_MINUTE, 30),
            settings.getIntFlow(REMINDER_HOUR, 11),
            settings.getIntFlow(REMINDER_MINUTE, 0),
            settings.getBooleanFlow(ENABLED, false),
            settings.getStringOrNullFlow(ALARM_NAME),
            settings.getBooleanFlow(VIBRATION, false),
            settings.getFloatFlow(VOLUME, 0.5f),
            settings.getBooleanFlow(SMART_ALARM, false),
            settings.getIntFlow(SMART_ALARM_RANGE, 15),
            settings.getBooleanFlow(REMINDER_ENABLED, false)
        ) { values ->
            SleepSettingSnapshot(
                alarmHour = values[0] as Int,
                alarmMinute = values[1] as Int,
                reminderHour = values[2] as Int,
                reminderMinute = values[3] as Int,
                isEnabled = values[4] as Boolean,
                alarmName = values[5] as String?,
                isVibration = values[6] as Boolean,
                volume = values[7] as Float,
                isSmartAlarm = values[8] as Boolean,
                smartAlarmRange = values[9] as Int,
                reminderEnabled = values[10] as Boolean
            )
        }
    }
}