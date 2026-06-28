package com.sleepytime.shared.util

import com.sleepytime.shared.resources.Res
import com.sleepytime.shared.resources.campfire
import com.sleepytime.shared.resources.cricket
import com.sleepytime.shared.resources.delta
import com.sleepytime.shared.resources.forest
import com.sleepytime.shared.resources.lake
import com.sleepytime.shared.resources.sea
import com.sleepytime.shared.resources.stream
import com.sleepytime.shared.resources.theta
import com.sleepytime.shared.resources.title_alarm_bird
import com.sleepytime.shared.resources.title_alarm_cricket
import com.sleepytime.shared.resources.title_alarm_piano
import com.sleepytime.shared.resources.title_alarm_upbeat
import com.sleepytime.shared.resources.title_alarm_wave
import com.sleepytime.shared.resources.title_campfire
import com.sleepytime.shared.resources.title_cricket
import com.sleepytime.shared.resources.title_delta
import com.sleepytime.shared.resources.title_forest
import com.sleepytime.shared.resources.title_lake
import com.sleepytime.shared.resources.title_sea
import com.sleepytime.shared.resources.title_stream
import com.sleepytime.shared.resources.title_theta
import com.sleepytime.shared.resources.title_unknown
import com.sleepytime.shared.resources.title_wind
import com.sleepytime.shared.resources.wind
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

object ResourceMapper {
    fun getDrawableRes(imageName: String?): DrawableResource {
        return when (imageName) {
            "sea" -> Res.drawable.sea
            "forest" -> Res.drawable.forest
            "stream" -> Res.drawable.stream
            "campfire" -> Res.drawable.campfire
            "cricket" -> Res.drawable.cricket
            "wind" -> Res.drawable.wind
            "lake" -> Res.drawable.lake
            "delta" -> Res.drawable.delta
            "theta" -> Res.drawable.theta
            else -> Res.drawable.theta
        }
    }

    fun getMusicTitleRes(musicName: String): StringResource {
        return when (musicName) {
            "sea" -> Res.string.title_sea
            "forest" -> Res.string.title_forest
            "stream" -> Res.string.title_stream
            "campfire" -> Res.string.title_campfire
            "cricket" -> Res.string.title_cricket
            "wind" -> Res.string.title_wind
            "lake" -> Res.string.title_lake
            "delta" -> Res.string.title_delta
            "theta" -> Res.string.title_theta
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
            else -> Res.string.title_unknown
        }
    }
}