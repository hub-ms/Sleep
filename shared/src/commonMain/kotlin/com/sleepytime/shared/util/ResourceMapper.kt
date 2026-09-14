package com.sleepytime.shared.util

import com.sleepytime.shared.resources.Res
import com.sleepytime.shared.resources.ambient1
import com.sleepytime.shared.resources.ambient2
import com.sleepytime.shared.resources.ambient3
import com.sleepytime.shared.resources.ambient4
import com.sleepytime.shared.resources.ambient5
import com.sleepytime.shared.resources.cricket
import com.sleepytime.shared.resources.delta_wave
import com.sleepytime.shared.resources.rain
import com.sleepytime.shared.resources.stream
import com.sleepytime.shared.resources.theta_wave
import com.sleepytime.shared.resources.title_alarm_bird
import com.sleepytime.shared.resources.title_alarm_cricket
import com.sleepytime.shared.resources.title_alarm_piano
import com.sleepytime.shared.resources.title_alarm_upbeat
import com.sleepytime.shared.resources.title_alarm_wave
import com.sleepytime.shared.resources.title_ambient1
import com.sleepytime.shared.resources.title_ambient2
import com.sleepytime.shared.resources.title_ambient3
import com.sleepytime.shared.resources.title_ambient4
import com.sleepytime.shared.resources.title_ambient5
import com.sleepytime.shared.resources.title_cricket
import com.sleepytime.shared.resources.title_delta_wave
import com.sleepytime.shared.resources.title_rain
import com.sleepytime.shared.resources.title_stream
import com.sleepytime.shared.resources.title_theta_wave
import com.sleepytime.shared.resources.title_underwater
import com.sleepytime.shared.resources.title_unknown
import com.sleepytime.shared.resources.title_wave
import com.sleepytime.shared.resources.underwater
import com.sleepytime.shared.resources.wave
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

object ResourceMapper {
    fun getDrawableRes(imageName: String?): DrawableResource {
        return when (imageName) {
            "ambient1" -> Res.drawable.ambient1
            "ambient2" -> Res.drawable.ambient2
            "ambient3" -> Res.drawable.ambient3
            "ambient4" -> Res.drawable.ambient4
            "ambient5" -> Res.drawable.ambient5
            "cricket" -> Res.drawable.cricket
            "delta_wave" -> Res.drawable.delta_wave
            "rain" -> Res.drawable.rain
            "stream" -> Res.drawable.stream
            "theta_wave" -> Res.drawable.theta_wave
            "underwater" -> Res.drawable.underwater
            "wave" -> Res.drawable.wave
            else -> Res.drawable.ambient1
        }
    }

    fun getMusicTitleRes(musicName: String): StringResource {
        return when (musicName) {
            "ambient1" -> Res.string.title_ambient1
            "ambient2" -> Res.string.title_ambient2
            "ambient3" -> Res.string.title_ambient3
            "ambient4" -> Res.string.title_ambient4
            "ambient5" -> Res.string.title_ambient5
            "cricket" -> Res.string.title_cricket
            "delta_wave" -> Res.string.title_delta_wave
            "rain" -> Res.string.title_rain
            "stream" -> Res.string.title_stream
            "theta_wave" -> Res.string.title_theta_wave
            "underwater" -> Res.string.title_underwater
            "wave" -> Res.string.title_wave
            else -> Res.string.title_unknown
        }
    }
    fun getAlarmTitleRes(alarmName: String): StringResource {
        return when (alarmName) {
            "bird" -> Res.string.title_alarm_bird
            "cricket" -> Res.string.title_alarm_cricket
            "piano" -> Res.string.title_alarm_piano
            "wave" -> Res.string.title_alarm_wave
            "upbeat" -> Res.string.title_alarm_upbeat
            else -> Res.string.title_alarm_bird
        }
    }
}