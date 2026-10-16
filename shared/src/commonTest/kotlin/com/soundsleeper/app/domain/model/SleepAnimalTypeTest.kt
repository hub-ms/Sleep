package com.soundsleeper.app.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [resolveAnimalType] 규칙 테스트.
 *
 * 이 함수는 가입 화면에서 사용자에게 "당신은 이런 사람"이라고 단언하는 자리에 쓰인다.
 * 설문 답과 유형이 어긋나면 바로 신뢰를 잃는 화면이라, 5개 유형의 매핑을 전부 고정해 둔다.
 */
class SleepAnimalTypeTest {

    private fun answers(vararg pairs: Pair<String, Set<String>>) = mapOf(*pairs)

    // ── 3번 문항(desired_change)이 1순위 ──────────────────────────────

    @Test
    fun `3번 문항의 다섯 선택지가 각각 유형 하나로 간다`() {
        val expected = mapOf(
            "deeper_sleep" to SleepAnimalType.BEAR,
            "refreshed_waking" to SleepAnimalType.OTTER,
            "faster_sleep_onset" to SleepAnimalType.CAT,
            "regular_pattern" to SleepAnimalType.PENGUIN,
            "reduce_snoring" to SleepAnimalType.DOLPHIN,
        )
        for ((goal, animal) in expected) {
            assertEquals(
                animal,
                resolveAnimalType(answers("desired_change" to setOf(goal))),
                "desired_change=$goal",
            )
        }
    }

    @Test
    fun `설문 선택지 전부가 유형에 매핑돼 있다`() {
        // 선택지를 추가해 놓고 매핑을 빠뜨리면 그 답을 고른 사용자만 캐릭터를 못 본다.
        // 화면 코드가 아니라 여기서 먼저 걸리게 한다.
        val optionKeys = listOf(
            "deeper_sleep", "refreshed_waking", "faster_sleep_onset",
            "regular_pattern", "reduce_snoring",
        )
        val resolved = optionKeys.mapNotNull {
            resolveAnimalType(answers("desired_change" to setOf(it)))
        }
        assertEquals(optionKeys.size, resolved.size)
        // 5개 선택지가 5개 유형에 1:1 이어야 한다(두 선택지가 같은 동물로 가면 안 된다).
        assertEquals(SleepAnimalType.entries.size, resolved.toSet().size)
    }

    @Test
    fun `3번 문항이 있으면 1번 문항은 무시한다`() {
        val result = resolveAnimalType(
            answers(
                "sleep_concern" to setOf("snoring"),      // 혼자였다면 DOLPHIN
                "desired_change" to setOf("regular_pattern"),
            )
        )
        assertEquals(SleepAnimalType.PENGUIN, result)
    }

    // ── 3번을 건너뛰면 1번 문항으로 폴백 ──────────────────────────────

    @Test
    fun `3번이 없으면 1번 문항의 불편함으로 정한다`() {
        val expected = mapOf(
            "snoring" to SleepAnimalType.DOLPHIN,
            "sleep_latency" to SleepAnimalType.CAT,
            "frequent_waking" to SleepAnimalType.BEAR,
            "tired_on_waking" to SleepAnimalType.OTTER,
        )
        for ((concern, animal) in expected) {
            assertEquals(
                animal,
                resolveAnimalType(answers("sleep_concern" to setOf(concern))),
                "sleep_concern=$concern",
            )
        }
    }

    @Test
    fun `1번 문항을 여러 개 골라도 결과가 하나로 정해진다`() {
        // 1번은 다중 선택이다. Set 의 순회 순서에 기대면 같은 답에도 결과가 흔들린다.
        // 우선순위(코골이 > 입면 지연 > 잦은 깸 > 피로)가 순서와 무관하게 적용돼야 한다.
        val all = setOf("tired_on_waking", "frequent_waking", "sleep_latency", "snoring")
        assertEquals(SleepAnimalType.DOLPHIN, resolveAnimalType(answers("sleep_concern" to all)))

        val withoutSnoring = all - "snoring"
        assertEquals(
            SleepAnimalType.CAT,
            resolveAnimalType(answers("sleep_concern" to withoutSnoring)),
        )
    }

    // ── 답이 없거나 알 수 없을 때 ─────────────────────────────────────

    @Test
    fun `답이 비어 있으면 null`() {
        assertNull(resolveAnimalType(emptyMap()))
        assertNull(resolveAnimalType(answers("desired_change" to emptySet())))
    }

    @Test
    fun `모르는 선택지 키는 null`() {
        // 설문 선택지를 바꿨는데 옛 답이 기기에 남아 있는 경우.
        assertNull(resolveAnimalType(answers("desired_change" to setOf("made_up_key"))))
        assertNull(resolveAnimalType(answers("made_up_question" to setOf("deeper_sleep"))))
    }

    @Test
    fun `모르는 선택지가 섞여 있으면 아는 쪽을 쓴다`() {
        val result = resolveAnimalType(
            answers("desired_change" to setOf("made_up_key", "deeper_sleep"))
        )
        assertEquals(SleepAnimalType.BEAR, result)
    }

    // ── id 왕복 ───────────────────────────────────────────────────────

    @Test
    fun `id 로 저장했다 되찾을 수 있다`() {
        for (animal in SleepAnimalType.entries) {
            assertEquals(animal, SleepAnimalType.fromId(animal.id))
        }
        assertNull(SleepAnimalType.fromId(null))
        assertNull(SleepAnimalType.fromId("nope"))
    }

    @Test
    fun `id 는 서로 겹치지 않는다`() {
        val ids = SleepAnimalType.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }
}
