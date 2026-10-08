package com.somniotune.app.util

import com.somniotune.app.resources.Res
import com.somniotune.app.resources.campfire
import com.somniotune.app.resources.cricket
import com.somniotune.app.resources.meditation
import com.somniotune.app.resources.piano
import com.somniotune.app.resources.rain
import com.somniotune.app.resources.relaxation
import com.somniotune.app.resources.space
import com.somniotune.app.resources.underwater
import com.somniotune.app.resources.wave
import com.somniotune.app.resources.wind
import com.somniotune.app.resources.title_alarm_bird
import com.somniotune.app.resources.title_alarm_cricket
import com.somniotune.app.resources.title_alarm_piano
import com.somniotune.app.resources.title_alarm_upbeat
import com.somniotune.app.resources.title_alarm_wave
import com.somniotune.app.resources.title_campfire
import com.somniotune.app.resources.title_cricket
import com.somniotune.app.resources.title_meditation
import com.somniotune.app.resources.title_piano
import com.somniotune.app.resources.title_rain
import com.somniotune.app.resources.title_relaxation
import com.somniotune.app.resources.title_space
import com.somniotune.app.resources.title_underwater
import com.somniotune.app.resources.title_unknown
import com.somniotune.app.resources.title_wave
import com.somniotune.app.resources.title_wind
import com.somniotune.app.resources.ic_music_note
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

object ResourceMapper {
    fun getMusicImageRes(imageName: String): DrawableResource {
        return when (imageName) {
            "campfire" -> Res.drawable.campfire
            "cricket" -> Res.drawable.cricket
            "meditation" -> Res.drawable.meditation
            "piano" -> Res.drawable.piano
            "rain" -> Res.drawable.rain
            "relaxation" -> Res.drawable.relaxation
            "space" -> Res.drawable.space
            "underwater" -> Res.drawable.underwater
            "wave" -> Res.drawable.wave
            "wind" -> Res.drawable.wind
            else -> Res.drawable.ic_music_note
        }
    }
    fun getMusicTitleRes(musicName: String): StringResource {
        return when (musicName) {
            "campfire" -> Res.string.title_campfire
            "cricket" -> Res.string.title_cricket
            "meditation" -> Res.string.title_meditation
            "piano" -> Res.string.title_piano
            "rain" -> Res.string.title_rain
            "relaxation" -> Res.string.title_relaxation
            "space" -> Res.string.title_space
            "underwater" -> Res.string.title_underwater
            "wave" -> Res.string.title_wave
            "wind" -> Res.string.title_wind
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