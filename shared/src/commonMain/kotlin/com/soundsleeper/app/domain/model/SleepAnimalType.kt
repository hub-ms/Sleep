package com.soundsleeper.app.domain.model

import com.soundsleeper.app.resources.Res
import com.soundsleeper.app.resources.animal_bear_summary
import com.soundsleeper.app.resources.animal_bear_title
import com.soundsleeper.app.resources.animal_cat_summary
import com.soundsleeper.app.resources.animal_cat_title
import com.soundsleeper.app.resources.animal_dolphin_summary
import com.soundsleeper.app.resources.animal_dolphin_title
import com.soundsleeper.app.resources.animal_otter_summary
import com.soundsleeper.app.resources.animal_otter_title
import com.soundsleeper.app.resources.animal_penguin_summary
import com.soundsleeper.app.resources.animal_penguin_title
import org.jetbrains.compose.resources.StringResource

/**
 * 온보딩 설문 결과로 보여주는 수면 동물 유형.
 *
 * 설문 답 자체는 예전부터 로컬에 저장만 되고 아무도 읽지 않았다. 이 유형이 그 답을 처음으로
 * 쓰는 곳이다 - 가입 화면에서 "당신은 이런 사람"을 한 장 보여주고, 자세한 분석은 가입해야
 * 볼 수 있다고 말해 가입을 유도한다.
 *
 * [id] 는 저장·비교에 쓰는 안정적인 식별자다. enum 이름이나 화면에 보이는 한국어 문장을
 * 저장값으로 쓰지 않는다 - 이름을 바꾸거나 언어가 바뀌면 이미 저장된 값이 더는 "같은 유형"으로
 * 인식되지 않는, 이 레포가 HomeTab·SurveyOption 에서 이미 한 번씩 겪은 종류의 버그다.
 *
 * [imageName] 은 `drawable/animal_<imageName>.webp` 를 가리킨다. 아직 아트가 없어
 * [com.soundsleeper.app.util.ResourceMapper.getAnimalImageRes] 가 폴백 아이콘을 돌려준다.
 */
enum class SleepAnimalType(
    val id: String,
    val titleRes: StringResource,
    val summaryRes: StringResource,
    val imageName: String,
) {
    BEAR("bear", Res.string.animal_bear_title, Res.string.animal_bear_summary, "bear"),
    OTTER("otter", Res.string.animal_otter_title, Res.string.animal_otter_summary, "otter"),
    CAT("cat", Res.string.animal_cat_title, Res.string.animal_cat_summary, "cat"),
    PENGUIN("penguin", Res.string.animal_penguin_title, Res.string.animal_penguin_summary, "penguin"),
    DOLPHIN("dolphin", Res.string.animal_dolphin_title, Res.string.animal_dolphin_summary, "dolphin");

    companion object {
        fun fromId(id: String?): SleepAnimalType? = entries.firstOrNull { it.id == id }
    }
}

/**
 * 설문 답 -> 동물 유형.
 *
 * 3번 문항 `desired_change`("앞으로 바라는 결과")를 1순위로 쓴다. 선택지 5개가 유형 5종과
 * 1:1 이고, 1·2번 문항이 "지금 겪는 문제"인 데 비해 이 문항은 사용자가 스스로 고른 방향이라
 * 캐릭터로 보여줬을 때 가장 수긍이 간다.
 *
 * 3번을 건너뛴 경우(설문 도중 이탈 후 복귀 등)에는 1번 문항 `sleep_concern` 의 불편함을
 * 같은 결의 유형으로 옮긴다. 둘 다 없으면 null 이고, 호출부는 캐릭터 영역을 그리지 않는다.
 *
 * 순수 함수다 - 저장·네트워크를 건드리지 않으므로 테스트에서 그대로 부를 수 있다.
 */
fun resolveAnimalType(answers: Map<String, Set<String>>): SleepAnimalType? {
    answers["desired_change"]?.firstNotNullOfOrNull { goal ->
        when (goal) {
            "deeper_sleep" -> SleepAnimalType.BEAR
            "refreshed_waking" -> SleepAnimalType.OTTER
            "faster_sleep_onset" -> SleepAnimalType.CAT
            "regular_pattern" -> SleepAnimalType.PENGUIN
            "reduce_snoring" -> SleepAnimalType.DOLPHIN
            else -> null
        }
    }?.let { return it }

    // 1번 문항은 다중 선택이라 Set 의 순회 순서가 곧 우선순위가 되면 안 된다.
    // 불편함이 여러 개면 가장 구체적인 증상(코골이 > 입면 지연 > 잦은 깸 > 피로)을 고른다.
    val concerns = answers["sleep_concern"].orEmpty()
    return when {
        "snoring" in concerns -> SleepAnimalType.DOLPHIN
        "sleep_latency" in concerns -> SleepAnimalType.CAT
        "frequent_waking" in concerns -> SleepAnimalType.BEAR
        "tired_on_waking" in concerns -> SleepAnimalType.OTTER
        else -> null
    }
}
