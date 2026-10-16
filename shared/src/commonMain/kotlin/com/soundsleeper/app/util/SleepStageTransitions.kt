package com.soundsleeper.app.util

/**
 * 수면 단계 간 전이 확률과 생리학적 제약.
 *
 * 왜 앱에 있나: 학습 쪽 모델에는 CRF(`ml/script/model.py`의 `TransitionCRF`)가 붙어 있지만
 * **그 CRF는 .tflite 로 나가지 않는다** — `ml/script/convert.py`가 emission logits 위에
 * Softmax만 씌워 내보내기 때문이다. 그래서 기기에서는 전이 제약이 전혀 걸리지 않은 채
 * 매 epoch 독립적으로 argmax 를 하고 있었고, 히프노그램이 Deep↔REM 을 1분 단위로 튀는
 * 결과가 나올 수 있었다. 디코딩을 기기 쪽으로 옮겨 그 제약을 실제 사용자에게 적용한다.
 *
 * 인덱스 순서는 학습 쪽 `CLASS_NAMES`(`ml/script/train.py`)와 같다: 0=Wake, 1=Light, 2=Deep, 3=REM.
 */
object SleepStageTransitions {

    const val WAKE = 0
    const val LIGHT = 1
    const val DEEP = 2
    const val REM = 3

    /** 한 epoch 길이(초). 전이행렬은 이 간격 기준이다. */
    const val EPOCH_SEC = 30

    /**
     * 기본 전이 확률(행=이전 단계, 열=다음 단계).
     *
     * `ml/script/train.py`의 `POPULATION_TRANSITION_PRIOR`와 같은 값에서 출발하되,
     * 생리학적으로 성립하지 않는 **Deep↔REM 직접 전이를 0으로 막고** 행을 다시 정규화했다.
     * 실제 수면에서 깊은수면(N3)과 REM 사이에는 반드시 얕은수면 구간이 끼어든다.
     *
     * ⚠️ 학습이 끝나면 이 값을 **학습된 CRF 전이행렬로 교체**해야 한다 —
     * `ml/script/convert.py`가 `sleep_model.meta.json`에 기록한 `transition_matrix`를
     * 그대로 붙여 넣는다(교체용 Kotlin 리터럴도 같이 출력된다).
     */
    val DEFAULT_MATRIX: Array<FloatArray> = arrayOf(
        floatArrayOf(0.700000f, 0.280000f, 0.010000f, 0.010000f), // Wake  ->
        floatArrayOf(0.050000f, 0.800000f, 0.080000f, 0.070000f), // Light ->
        floatArrayOf(0.010101f, 0.151515f, 0.838384f, 0.000000f), // Deep  ->  (Deep->REM 금지)
        floatArrayOf(0.050505f, 0.202020f, 0.000000f, 0.747475f), // REM   ->  (REM->Deep 금지)
    )

    /**
     * 생리학적으로 불가능한 전이. [maskImpossible]이 이 쌍의 확률을 0으로 만든다.
     * 학습된 행렬을 그대로 가져오더라도(데이터 잡음으로 작은 값이 남아 있을 수 있다)
     * 이 마스킹은 항상 한 번 더 적용한다.
     */
    private val IMPOSSIBLE = listOf(DEEP to REM, REM to DEEP)

    /**
     * 불가능한 전이를 0으로 만들고 각 행을 다시 확률분포로 정규화한다.
     * 어떤 행이 통째로 0이 되면(이론상 없지만) 균등분포로 되돌려 Viterbi 가 -무한대에
     * 갇히지 않게 한다.
     */
    fun maskImpossible(matrix: Array<FloatArray>): Array<FloatArray> {
        val out = Array(matrix.size) { r -> matrix[r].copyOf() }
        for ((from, to) in IMPOSSIBLE) {
            if (from < out.size && to < out[from].size) out[from][to] = 0f
        }
        for (row in out) {
            val sum = row.sum()
            if (sum <= 0f) {
                row.fill(1f / row.size)
            } else {
                for (i in row.indices) row[i] = row[i] / sum
            }
        }
        return out
    }

    /**
     * 단계별 최소 지속 epoch 수. 이보다 짧은 구간은 [SleepHypnogramDecoder]가 이웃 단계로 흡수한다.
     *
     * Wake 가 1인 이유: 한밤중의 짧은 각성(arousal)은 실제로 30초~1분이라 눌러 버리면
     * 오히려 사실과 멀어진다. 반대로 Deep/REM 이 30초만 나타났다 사라지는 것은 사실상
     * 전부 분류 잡음이다.
     */
    val MIN_RUN_EPOCHS = intArrayOf(
        1, // Wake
        1, // Light
        3, // Deep  (90초)
        3, // REM   (90초)
    )

    /**
     * 수면 개시(첫 비-Wake epoch) 이후 REM 이 나타날 수 있는 최소 지연 — 20 epoch = 10분.
     *
     * 정상 수면에서 첫 REM 은 보통 입면 후 70~110분 뒤다. 10분은 그보다 훨씬 느슨하게 잡은
     * 값으로, "입면 직후 REM" 같은 명백한 오분류만 걷어내고 정상 변이는 건드리지 않는다.
     */
    const val REM_LATENCY_EPOCHS = 20
}
