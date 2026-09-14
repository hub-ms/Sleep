package com.sleepytime.shared.ui.alarm

import com.sleepytime.shared.domain.model.Alarm
import com.sleepytime.shared.util.ResourceMapper
import kotlinx.datetime.LocalTime

object AlarmContract {
    data class State(
        val isAlarmEnabled: Boolean = true,
        val isAlarmPlaying: Boolean = false,
        val isAlarmPreviewPlaying: Boolean = false,
        val alarmHour: Int = 7,
        val alarmMinute: Int = 30,
        val wakeUpTime: LocalTime? = null,

        val alarmSounds: List<Alarm.Sound> = listOf(
            Alarm.Sound("bird", ResourceMapper.getAlarmTitleRes("bird").toString(), "files/alarm_bird.ogg", 0.8f),
            Alarm.Sound("cricket", ResourceMapper.getAlarmTitleRes("cricket").toString(), "files/alarm_cricket.ogg", 0.8f),
            Alarm.Sound("piano", ResourceMapper.getAlarmTitleRes("piano").toString(), "files/alarm_piano.ogg", 0.8f),
            Alarm.Sound("wave", ResourceMapper.getAlarmTitleRes("wave").toString(), "files/alarm_wave.ogg", 0.8f),
            Alarm.Sound("upbeat", ResourceMapper.getAlarmTitleRes("upbeat").toString(), "files/alarm_upbeat.ogg", 0.8f),
        ),
        val selectedAlarmSound: Alarm.Sound = alarmSounds.first { it.titleRes == ResourceMapper.getAlarmTitleRes("bird").toString()},
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
        object StopAlarmPreview : Intent()
        object StopAlarm: Intent()


        data class SelectAlarmSound(val sound: Alarm.Sound) : Intent()
        data class ChangeVolume(val volume: Float) : Intent()

        object ToggleVibration : Intent()
        object ToggleSmartAlarm : Intent()
        data class SelectSmartAlarmRange(val range: Int): Intent()
    }
    sealed class Effect {
        object NavigateToHome: Effect()
        data class NavigateToReport(val sessionId: String) : Effect()
    }
}


