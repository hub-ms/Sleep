package com.sleepytime.shared.domain.model

import com.sleepytime.shared.resources.Res
import com.sleepytime.shared.resources.title_unknown
import org.jetbrains.compose.resources.StringResource

data class Alarm(
    val hour: Int,
    val minute: Int,

    val isEnabled: Boolean,
    val isVibrationEnabled: Boolean,
    val isSmartAlarmEnabled: Boolean,
    val smartAlarmRange: Int,
    val isGradualVolume: Boolean,

    val sound: Sound,
) {
    data class Sound(
        val id: String,
        val titleRes: StringResource,
        val filePath: String,
        val volume: Float
    ) {
        companion object {
            val DEFAULT = Sound(
                id = "default_sound",
                titleRes = Res.string.title_unknown,
                filePath = "assets/sounds/default.mp3",
                volume = 0.5f
            )
        }
    }
}

