package com.soundsleeper.app.util

import kotlin.math.ln

/**
 * epoch 별 클래스 확률 시퀀스를 **하나의 일관된 히프노그램**으로 디코딩한다.
 *
 * 왜 필요한가: 모델은 각 epoch 의 확률만 내놓고, 지금까지 앱은 그걸 epoch 마다 독립적으로
 * argmax 했다. 그러면 확률이 비등한 구간에서 단계가 30초 단위로 튀고(Deep↔REM 같은
 * 생리학적으로 불가능한 전이까지), 사용자가 보는 수면그래프가 톱니처럼 된다.
 * 학습 쪽에는 이 역할을 하는 CRF 가 있지만 .tflite 로 나가지 않아(convert.py 가 Softmax 만
 * 씌운다) 기기에서는 제약이 통째로 빠져 있었다.
 *
 * 두 단계로 처리한다.
 *  1. **Viterbi** — 전이확률을 함께 고려해 전체 시퀀스에서 가장 그럴듯한 경로를 찾는다.
 *     불가능한 전이는 [SleepStageTransitions.maskImpossible]로 확률 0이 되어 경로에서 배제된다.
 *  2. **규칙 보정** — Viterbi 로 표현할 수 없는 제약(최소 지속시간, REM 잠복기)을 적용한다.
 *
 * 구현은 `ml/script/evaluate.py`의 `viterbi_smooth`와 같은 수식이다(로그 공간 덧셈).
 */
class SleepHypnogramDecoder(
    transitionMatrix: Array<FloatArray> = SleepStageTransitions.DEFAULT_MATRIX,
    private val minRunEpochs: IntArray = SleepStageTransitions.MIN_RUN_EPOCHS,
    private val remLatencyEpochs: Int = SleepStageTransitions.REM_LATENCY_EPOCHS,
) {
    private val nClasses: Int = transitionMatrix.size

    /** 로그 전이확률. 0 확률은 -무한대 대신 아주 작은 값으로 둬서 NaN 전파를 막는다. */
    private val logTransition: Array<FloatArray> =
        SleepStageTransitions.maskImpossible(transitionMatrix).map { row ->
            FloatArray(row.size) { i -> safeLog(row[i]) }
        }.toTypedArray()

    /**
     * [probabilities] = epoch 순서대로의 클래스 확률(각 FloatArray 길이 = 클래스 수).
     * 반환: 같은 길이의 클래스 인덱스 배열.
     *
     * 빈 입력이면 빈 배열을 돌려준다.
     */
    fun decode(probabilities: List<FloatArray>): IntArray {
        if (probabilities.isEmpty()) return IntArray(0)
        val path = viterbi(probabilities)
        applyMinimumRunLength(path)
        applyRemLatency(path)
        return path
    }

    /**
     * 실시간 경로용 — 지금까지 쌓인 확률 전체를 디코딩하고 **마지막 epoch 의 단계**만 돌려준다.
     *
     * 매 epoch 전체를 다시 디코딩해도 비용은 `epoch수 × 4 × 4` 라 10시간 측정(1200 epoch)에도
     * 2만 번의 곱셈 수준이라 무시할 만하다. 과거 구간의 추정이 뒤늦게 좋아지는 것도 이득이다
     * (세션이 끝나면 [decode]가 전체를 다시 확정한다).
     */
    fun decodeLatest(probabilities: List<FloatArray>): Int {
        val path = decode(probabilities)
        return if (path.isEmpty()) SleepStageTransitions.WAKE else path[path.size - 1]
    }

    /** 로그 공간 Viterbi. 초기 분포는 균등(= 학습 쪽 CRF 와 동일한 가정). */
    private fun viterbi(probabilities: List<FloatArray>): IntArray {
        val t = probabilities.size
        val delta = Array(t) { FloatArray(nClasses) }
        val psi = Array(t) { IntArray(nClasses) }

        val uniformLogPrior = safeLog(1f / nClasses)
        for (s in 0 until nClasses) {
            delta[0][s] = uniformLogPrior + safeLog(probabilities[0].getOrElse(s) { 0f })
        }

        for (i in 1 until t) {
            val emission = probabilities[i]
            for (s in 0 until nClasses) {
                var bestScore = Float.NEGATIVE_INFINITY
                var bestPrev = 0
                for (p in 0 until nClasses) {
                    val score = delta[i - 1][p] + logTransition[p][s]
                    if (score > bestScore) {
                        bestScore = score
                        bestPrev = p
                    }
                }
                delta[i][s] = bestScore + safeLog(emission.getOrElse(s) { 0f })
                psi[i][s] = bestPrev
            }
        }

        val path = IntArray(t)
        var best = 0
        for (s in 1 until nClasses) if (delta[t - 1][s] > delta[t - 1][best]) best = s
        path[t - 1] = best
        for (i in t - 2 downTo 0) path[i] = psi[i + 1][path[i + 1]]
        return path
    }

    /**
     * 최소 지속시간 규칙: [minRunEpochs]보다 짧은 구간은 이웃으로 흡수한다.
     *
     * 어느 이웃으로 흡수할지는 "더 긴 쪽"을 따른다 — 짧은 구간 양옆이 같은 단계면 그 단계로
     * 메워지고, 다르면 더 안정적인(긴) 쪽에 붙인다. 경계(첫/마지막 구간)는 유일한 이웃을 쓴다.
     * 구간을 합치면 이웃 구간이 더 길어져 다시 검사할 필요가 없으므로 한 번만 훑는다.
     */
    private fun applyMinimumRunLength(path: IntArray) {
        if (path.size < 2) return
        var changed = true
        var guard = 0
        // 흡수가 연쇄적으로 또 다른 짧은 구간을 만들 수 있어 수렴할 때까지 반복한다.
        // guard 는 혹시 모를 무한루프 방지용(실제로는 2~3회 안에 끝난다).
        while (changed && guard < 8) {
            changed = false
            guard++
            val runs = toRuns(path)
            for (index in runs.indices) {
                val run = runs[index]
                val minLen = minRunEpochs.getOrElse(run.stage) { 1 }
                if (run.length >= minLen) continue

                val prev = runs.getOrNull(index - 1)
                val next = runs.getOrNull(index + 1)
                val target = when {
                    prev == null && next == null -> continue
                    prev == null -> next!!.stage
                    next == null -> prev.stage
                    prev.stage == next.stage -> prev.stage
                    prev.length >= next.length -> prev.stage
                    else -> next.stage
                }
                for (i in run.start until run.start + run.length) path[i] = target
                changed = true
            }
        }
    }

    /**
     * REM 잠복기 규칙: 수면 개시(첫 비-Wake epoch) 이후 [remLatencyEpochs] 이전에 나타난 REM 은
     * 얕은수면으로 바꾼다. 입면 직후 REM 은 정상 수면에서 사실상 나오지 않는다.
     */
    private fun applyRemLatency(path: IntArray) {
        var sleepOnset = -1
        for (i in path.indices) {
            if (path[i] != SleepStageTransitions.WAKE) {
                sleepOnset = i
                break
            }
        }
        if (sleepOnset < 0) return
        val limit = minOf(path.size, sleepOnset + remLatencyEpochs)
        for (i in sleepOnset until limit) {
            if (path[i] == SleepStageTransitions.REM) path[i] = SleepStageTransitions.LIGHT
        }
    }

    private class Run(val stage: Int, val start: Int, val length: Int)

    private fun toRuns(path: IntArray): List<Run> {
        val runs = ArrayList<Run>()
        var start = 0
        for (i in 1..path.size) {
            if (i == path.size || path[i] != path[start]) {
                runs.add(Run(path[start], start, i - start))
                start = i
            }
        }
        return runs
    }

    private companion object {
        /** log(0) = -Inf 는 Viterbi 비교에서 NaN 을 만들 수 있어 바닥값으로 치환한다. */
        const val LOG_FLOOR = -30f

        fun safeLog(v: Float): Float = if (v <= 0f) LOG_FLOOR else maxOf(ln(v), LOG_FLOOR)
    }
}
