package com.soundsleeper.app.util

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [SleepHypnogramDecoder]가 거는 생리학적 제약을 고정한다.
 *
 * 이 디코더가 하는 일은 "모델이 epoch 마다 흔들려도 사람이 실제로 잘 수 있는 순서로만
 * 히프노그램을 만든다"는 것이라, 테스트도 전부 그 관점에서 쓴다.
 */
class SleepHypnogramDecoderTest {

    private val W = SleepStageTransitions.WAKE
    private val L = SleepStageTransitions.LIGHT
    private val D = SleepStageTransitions.DEEP
    private val R = SleepStageTransitions.REM

    /** 해당 클래스를 `p` 확률로 확신하고 나머지는 균등하게 나눠 가진 분포. */
    private fun peak(stage: Int, p: Float = 0.9f): FloatArray {
        val rest = (1f - p) / 3f
        return FloatArray(4) { if (it == stage) p else rest }
    }

    private fun seq(vararg stages: Int): List<FloatArray> = stages.map { peak(it) }

    @Test
    fun `확신이 뚜렷하면 입력 단계를 그대로 돌려준다`() {
        // 각 구간을 최소 지속시간(Deep/REM 3 epoch)보다 길게 둬서 규칙이 건드리지 않게 한다.
        val stages = IntArray(30) { W } + IntArray(30) { L } + IntArray(30) { D }
        val decoded = SleepHypnogramDecoder().decode(stages.map { peak(it) })
        assertContentEquals(stages, decoded)
    }

    @Test
    fun `Deep에서 REM으로 직접 넘어가지 않는다`() {
        // 모델이 Deep 30개 뒤에 곧바로 REM 30개를 강하게 주장해도,
        // Deep->REM 전이확률이 0이므로 경로가 그대로 이어질 수 없다.
        val input = seq(*(IntArray(30) { D } + IntArray(30) { R }))
        val decoded = SleepHypnogramDecoder().decode(input)

        for (i in 1 until decoded.size) {
            val from = decoded[i - 1]
            val to = decoded[i]
            assertTrue(
                !(from == D && to == R) && !(from == R && to == D),
                "생리학적으로 불가능한 전이가 남아 있다: index=$i ${from}->${to}"
            )
        }
    }

    @Test
    fun `한 epoch짜리 Deep 스파이크는 주변으로 흡수된다`() {
        // 얕은수면 한가운데 Deep 이 30초만 튀는 건 사실상 전부 분류 잡음이다.
        val stages = IntArray(61) { L }.also { it[30] = D }
        val decoded = SleepHypnogramDecoder().decode(stages.map { peak(it) })

        assertEquals(L, decoded[30], "1 epoch 짜리 Deep 스파이크가 남아 있다")
        assertTrue(decoded.none { it == D }, "Deep 구간이 전부 사라져야 한다")
    }

    @Test
    fun `입면 직후 REM은 얕은수면으로 바뀐다`() {
        // Wake 5 → REM 10 → Light 40. REM 잠복기(20 epoch = 10분) 안쪽이라 허용되지 않는다.
        val stages = IntArray(5) { W } + IntArray(10) { R } + IntArray(40) { L }
        val decoded = SleepHypnogramDecoder().decode(stages.map { peak(it) })

        val onset = decoded.indexOfFirst { it != W }
        assertTrue(onset >= 0, "수면 개시 지점을 찾지 못했다")
        val latencyWindow = decoded.slice(
            onset until minOf(decoded.size, onset + SleepStageTransitions.REM_LATENCY_EPOCHS)
        )
        assertTrue(
            latencyWindow.none { it == R },
            "수면 개시 후 ${SleepStageTransitions.REM_LATENCY_EPOCHS} epoch 안에 REM 이 남아 있다: $latencyWindow"
        )
    }

    @Test
    fun `충분히 늦게 나온 REM은 그대로 유지된다`() {
        // Wake 5 → Light 40(=20분, 잠복기 10분을 충분히 넘김) → REM 20.
        val stages = IntArray(5) { W } + IntArray(40) { L } + IntArray(20) { R }
        val decoded = SleepHypnogramDecoder().decode(stages.map { peak(it) })

        assertTrue(decoded.takeLast(20).all { it == R }, "정상 잠복기 뒤의 REM 까지 지워졌다")
    }

    @Test
    fun `빈 입력은 빈 결과를 돌려준다`() {
        assertContentEquals(IntArray(0), SleepHypnogramDecoder().decode(emptyList()))
        assertEquals(W, SleepHypnogramDecoder().decodeLatest(emptyList()))
    }

    @Test
    fun `전이행렬 마스킹은 각 행을 확률분포로 되돌린다`() {
        val masked = SleepStageTransitions.maskImpossible(SleepStageTransitions.DEFAULT_MATRIX)
        assertEquals(0f, masked[D][R], "Deep->REM 이 0 이 아니다")
        assertEquals(0f, masked[R][D], "REM->Deep 이 0 이 아니다")
        for ((i, row) in masked.withIndex()) {
            assertTrue(kotlin.math.abs(row.sum() - 1f) < 1e-5f, "행 $i 의 합이 1이 아니다: ${row.sum()}")
        }
    }

    @Test
    fun `마스킹은 원본 행렬을 건드리지 않는다`() {
        val before = SleepStageTransitions.DEFAULT_MATRIX[L].copyOf()
        SleepStageTransitions.maskImpossible(SleepStageTransitions.DEFAULT_MATRIX)
        assertContentEquals(before, SleepStageTransitions.DEFAULT_MATRIX[L])
    }
}
