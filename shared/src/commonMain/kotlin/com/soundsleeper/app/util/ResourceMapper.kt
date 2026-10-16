package com.soundsleeper.app.util

import com.soundsleeper.app.resources.Res
import com.soundsleeper.app.resources.campfire
import com.soundsleeper.app.resources.cricket
import com.soundsleeper.app.resources.meditation
import com.soundsleeper.app.resources.piano
import com.soundsleeper.app.resources.rain
import com.soundsleeper.app.resources.relaxation
import com.soundsleeper.app.resources.space
import com.soundsleeper.app.resources.underwater
import com.soundsleeper.app.resources.wave
import com.soundsleeper.app.resources.wind
import com.soundsleeper.app.resources.title_alarm_bird
import com.soundsleeper.app.resources.title_alarm_cricket
import com.soundsleeper.app.resources.title_alarm_piano
import com.soundsleeper.app.resources.title_alarm_upbeat
import com.soundsleeper.app.resources.title_alarm_wave
import com.soundsleeper.app.resources.title_campfire
import com.soundsleeper.app.resources.title_binaural
import com.soundsleeper.app.resources.title_alpha
import com.soundsleeper.app.resources.title_theta
import com.soundsleeper.app.resources.title_delta
import com.soundsleeper.app.resources.title_cricket
import com.soundsleeper.app.resources.title_meditation
import com.soundsleeper.app.resources.title_piano
import com.soundsleeper.app.resources.title_rain
import com.soundsleeper.app.resources.title_relaxation
import com.soundsleeper.app.resources.title_space
import com.soundsleeper.app.resources.title_underwater
import com.soundsleeper.app.resources.title_unknown
import com.soundsleeper.app.resources.title_wave
import com.soundsleeper.app.resources.title_wind
import com.soundsleeper.app.resources.ic_music_note
import com.soundsleeper.app.resources.ic_sleep
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
        // 뇌파(MusicCategory.WAVE). 제목은 미리 깔아 두되 썸네일·음원 파일이 들어오기 전까지는
        // DatabaseHolder.seedMusic 에 등록하지 않는다 — 등록하면 카드는 보이는데 재생이 실패한다.
        "delta" -> Res.string.title_delta
        "theta" -> Res.string.title_theta
        "alpha" -> Res.string.title_alpha
        "binaural" -> Res.string.title_binaural
            else -> Res.string.title_unknown
        }
    }
    /**
     * 수면 동물 유형의 캐릭터 이미지.
     *
     * 아직 `drawable/animal_*.webp` 아트가 들어오지 않았다. 아트가 준비되면 drawable 에
     * 파일을 넣고 아래 when 에 분기만 추가하면 화면 코드는 건드릴 필요가 없다 - 유형과
     * 그림을 잇는 자리는 여기 한 곳뿐이다.
     *
     * 폴백은 ic_sleep 이다. 레포에 없는 리소스를 미리 참조해 두면 컴파일이 깨진다.
     */
    fun getAnimalImageRes(imageName: String): DrawableResource {
        return when (imageName) {
            // TODO: animal_bear / animal_otter / animal_cat / animal_penguin / animal_dolphin
            //       webp 가 drawable 에 추가되면 여기와 hasAnimalArt 에 함께 분기를 넣는다.
            else -> Res.drawable.ic_sleep
        }
    }

    /**
     * [getAnimalImageRes] 가 진짜 캐릭터 아트를 돌려주는지, 폴백 아이콘인지.
     *
     * 폴백은 `ic_sleep` 벡터라 자기 색(검정)을 들고 있다. 화면이 그대로 그리면 어두운
     * 배경에서 보이지 않으므로 호출부가 테마색으로 덧칠해야 하는데, 진짜 아트(컬러 webp)가
     * 들어오면 그 덧칠이 오히려 그림을 뭉갠다. 어느 쪽인지 여기서 알려 준다 -
     * 아트를 추가할 때 위 when 과 이 함수 두 곳만 같이 고치면 된다.
     */
    fun hasAnimalArt(imageName: String): Boolean = when (imageName) {
        else -> false
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