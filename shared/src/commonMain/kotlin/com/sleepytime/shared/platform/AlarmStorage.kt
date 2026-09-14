package com.sleepytime.shared.platform

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.coroutines.FlowSettings
import com.russhwolf.settings.coroutines.toFlowSettings
import com.sleepytime.shared.domain.model.Alarm
import com.sleepytime.shared.resources.Res
import com.sleepytime.shared.util.ResourceMapper.getAlarmTitleRes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@ExperimentalSettingsApi
class AlarmStorage(settings: ObservableSettings) {

    private val flowSettings = settings.toFlowSettings()

    companion object {
        private const val KEY_HOUR = "alarm_hour"
        private const val KEY_MINUTE = "alarm_minute"
        private const val KEY_ENABLED = "alarm_enabled"
        private const val KEY_VIBRATION = "alarm_vibration"
        private const val KEY_SMART = "alarm_smart"
        private const val KEY_SMART_RANGE = "alarm_smart_range"
        private const val KEY_SOUND = "alarm_sound"
    }

    suspend fun save(alarm: Alarm) {
        flowSettings.putInt(KEY_HOUR, alarm.hour)
        flowSettings.putInt(KEY_MINUTE, alarm.minute)
        flowSettings.putBoolean(KEY_ENABLED, alarm.isEnabled)
        flowSettings.putBoolean(KEY_VIBRATION, alarm.isVibrationEnabled)
        flowSettings.putBoolean(KEY_SMART, alarm.isSmartAlarmEnabled)
        flowSettings.putInt(KEY_SMART_RANGE, alarm.smartAlarmRange)
        // 💡 문자열이 아닌 객체 자체를 저장
        flowSettings.putObject(KEY_SOUND, alarm.sound)
    }

    suspend fun load(): Alarm? {
        val hour = flowSettings.getInt(KEY_HOUR, -1)
        val minute = flowSettings.getInt(KEY_MINUTE, -1)
        if (hour == -1 || minute == -1) return null

        return Alarm(
            hour = hour,
            minute = minute,
            isEnabled = flowSettings.getBoolean(KEY_ENABLED, false),
            isVibrationEnabled = flowSettings.getBoolean(KEY_VIBRATION, false),
            isSmartAlarmEnabled = flowSettings.getBoolean(KEY_SMART, false),
            smartAlarmRange = flowSettings.getInt(KEY_SMART_RANGE, 0),
            // ✅ "default" 대신 Alarm.Sound.DEFAULT 객체를 기본값으로 사용
            sound = flowSettings.getObject(KEY_SOUND, Alarm.Sound.DEFAULT)
        )
    }

    fun observe(): Flow<Alarm?> {
        return flowSettings.getIntFlow(KEY_HOUR, -1).map {
            load()
        }
    }

    suspend fun setEnabled(isEnabled: Boolean) {
        flowSettings.putBoolean(KEY_ENABLED, isEnabled)
    }

    suspend fun updateTime(hour: Int, minute: Int) {
        flowSettings.putInt(KEY_HOUR, hour)
        flowSettings.putInt(KEY_MINUTE, minute)
    }
    suspend inline fun <reified T> FlowSettings.getObject(key: String, defaultValue: T): T {
        val jsonString = getStringOrNull(key)
        return if (jsonString == null) defaultValue
        else try {
            Json.decodeFromString<T>(jsonString)
        } catch (e: Exception) {
            defaultValue // 파싱 실패 시 기본값 반환 (안정성)
        }
    }
    inline fun <reified T> FlowSettings.getObjectFlow(key: String, defaultValue: T): Flow<T> {
        return getStringOrNullFlow(key).map { jsonString ->
            if (jsonString == null) defaultValue
            else try {
                Json.decodeFromString<T>(jsonString)
            } catch (e: Exception) {
                defaultValue
            }
        }
    }
    suspend inline fun <reified T> FlowSettings.putObject(key: String, value: T) {
        val jsonString = Json.encodeToString(value)
        putString(key, jsonString)
    }
}