package com.sleepytime.shared.ui.alarm

import com.sleepytime.shared.domain.model.Alarm
import com.sleepytime.shared.util.ResourceMapper
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.resources.StringResource

object AlarmContract {
    data class State(
        val isAlarmEnabled: Boolean = true,
        val alarmHour: Int = 7,
        val alarmMinute: Int = 30,
        val wakeUpTime: LocalTime? = null,

        val alarmSounds: List<Alarm.Sound> = listOf(
            Alarm.Sound("bird", ResourceMapper.getAlarmTitleRes("bird"), "files/alarm_bird.mp3", 0.8f),
            Alarm.Sound("cricket", ResourceMapper.getAlarmTitleRes("cricket"), "files/alarm_cricket.mp3", 0.8f),
            Alarm.Sound("piano", ResourceMapper.getAlarmTitleRes("piano"), "files/alarm_piano.mp3", 0.8f),
            Alarm.Sound("wave", ResourceMapper.getAlarmTitleRes("wave"), "files/alarm_wave.mp3", 0.8f),
            Alarm.Sound("upbeat", ResourceMapper.getAlarmTitleRes("upbeat"), "files/alarm_upbeat.mp3", 0.8f),
        ),
        val selectedAlarmSound: StringResource = alarmSounds.first { it.titleRes == ResourceMapper.getAlarmTitleRes("bird")}.titleRes,
        val systemVolume: Float = 0.3f,
        val appVolume: Float = 0.3f,
        val isMute: Boolean = false,


        val isVibrationEnabled: Boolean = false,

        val isSmartAlarmEnabled: Boolean = false,
        val smartAlarmRangeList: List<Int> = listOf(15, 30, 60),
        val selectedSmartAlarmRange: Int = smartAlarmRangeList.first(),

        val isGradualVolumeEnabled: Boolean = false,

        val isAutoTrackingEnabled: Boolean = false,
        val sleepTrackingModeList: List<SleepTrackingMode> = listOf(
            SleepTrackingMode.AUTO_PHONE,
            SleepTrackingMode.AUTO_WATCH,
        ),
        val selectedSleepTrackingModes: Set<SleepTrackingMode> = setOf(
            SleepTrackingMode.AUTO_PHONE,
        ),
        val autoTrackingTime: LocalTime? = null
    )
    sealed class Intent {
        object ToggleAlarm : Intent()
        data class ChangeAlarmHour(val hour: Int) : Intent()
        data class ChangeAlarmMinute(val minute: Int, val globalIndex: Int) : Intent()
        data class SetWakeUpTime(val time: LocalTime) : Intent()


        data class SelectAlarmSound(val sound: Alarm.Sound) : Intent()
        data class ChangeVolume(val volume: Float) : Intent()

        object ToggleVibration : Intent()

        object ToggleSmartAlarm : Intent()
        data class SelectSmartAlarmRange(val range: Int): Intent()

        object ToggleGradualVolume : Intent()

        object ToggleAutoTracking : Intent()
        data class SelectSleepTrackingMode(val mode: SleepTrackingMode) : Intent()
        data class AutoTrackingTimeChanged(val time: LocalTime) : Intent()

        object SaveButtonClicked: Intent()
    }
    sealed class Effect {
        object NavigateToHome : Effect()
    }
}


