package com.soundsleeper.app.domain.model

import com.soundsleeper.app.resources.Res
import com.soundsleeper.app.resources.title_alarm_bird
import kotlinx.serialization.Serializable

data class SleepSetting(
    val alarm: Alarm,
    val reminder: Reminder
)
data class Alarm(
    val hour: Int,
    val minute: Int,

    val isEnabled: Boolean,
    val isVibrationEnabled: Boolean,
    val isSmartAlarmEnabled: Boolean,
    val smartAlarmRange: Int,

    val sound: Sound,
) {
    @Serializable
    data class Sound(
        val id: String = "",
        val titleRes: String,
        val filePath: String,
        val volume: Float
    ) {
        companion object {
            val DEFAULT = Sound(
                id = "default_sound",
                titleRes = Res.string.title_alarm_bird.toString(),
                filePath = "files/alarm_bird.ogg",
                volume = 0.5f
            )
        }
    }
}
data class Reminder(
    val hour: Int,
    val minute: Int,
    val isEnabled: Boolean,
)