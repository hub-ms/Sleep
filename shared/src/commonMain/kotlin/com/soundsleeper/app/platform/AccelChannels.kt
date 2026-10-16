package com.soundsleeper.app.platform

/**
 * 온디바이스 모델의 입력 레이아웃 — 학습 코드(`ml/script/channels.py`)와 **반드시 같아야** 하는
 * 유일한 정의입니다.
 *
 * 💡 이 상수들은 원래 SleepStageClassifier의 expect/actual 안에 있어서 androidMain / iosMain /
 * jvmMain 세 곳에 똑같이 복제돼 있었습니다. 복제된 값은 조용히 어긋납니다 — 실제로 과거에
 * androidMain이 `NUM_CLASSES = 5`를 선언한 채 4클래스 모델을 돌려 출력 텐서 크기 계산이
 * 어긋났고, 16채널(EDF 8 + Accel 8) 정규화 배열의 앞 8개(EEG용)만 참조하는 버그도 있었습니다.
 * 플랫폼과 무관한 값이므로 commonMain 한 곳에 모읍니다.
 *
 * 💡 Phase 1(학습/서빙 불일치 제거): 심박 채널을 제거해 8 -> 7채널이 되었습니다. 앱은 심박을
 * 수집하지 않아 추론 시 학습셋 평균(상수 65.44)을 그 채널에 꽂고 있었는데, 학습에서는 실측
 * 심박을 쓰고 서빙에서는 상수를 넣으면 그 채널의 기여분이 전부 손실되고 오프라인 지표만
 * 부풀려집니다. 웨어러블 의존성을 추가하지 않기로 했으므로 채널 자체를 없애 일치시켰습니다.
 */
object AccelChannels {

    /** 모델이 한 에포크에 기대하는 타임스텝 수 (50Hz × 30초). */
    const val WINDOW_SIZE = 1500

    /** 에포크 길이(초). */
    const val EPOCH_SEC = 30

    /** 입력 채널 수. `ml/script/channels.py`의 N_CHANNELS와 같아야 합니다. */
    const val NUM_CHANNELS = 7

    /** 출력 클래스 수: 0=Wake, 1=Light(N1+N2), 2=Deep(N3+N4), 3=REM. */
    const val NUM_CLASSES = 4

    // ── 채널 인덱스 (ml/script/channels.py의 ACCEL_CHANNEL_NAMES 순서) ──────────
    const val CH_ACCEL_X = 0
    const val CH_ACCEL_Y = 1
    const val CH_ACCEL_Z = 2

    /** 에포크 평균에서 각 축이 벗어난 정도의 L1 합. */
    const val CH_TILT = 3

    /**
     * 최근 3분 활동량(가속도 벡터 크기)의 표준편차.
     *
     * 🐛 과거에 앱은 이 자리에 소음/MFCC 에너지를 넣고 있었고, CH_TILT 자리에는 자이로스코프
     * 크기를 넣고 있어서 학습/추론 채널의 물리적 의미가 완전히 달랐습니다(c7ba84b에서 수정).
     */
    const val CH_ACTIVITY_VARIABILITY = 4

    /** 최근 3분 활동량의 선형 추세(기울기). */
    const val CH_ACTIVITY_TREND = 5

    /** 세션 시작 이후 경과 시간을 8시간으로 정규화한 값(상한 1.0). */
    const val CH_TIME_FEATURE = 6

    /**
     * `time_feature` 정규화 기준(밀리초) = 8시간.
     *
     * 💡 Phase 1: 학습은 `경과 / 야간_전체_길이`를 쓰고 있었는데, 밤이 끝나야 알 수 있는 값이라
     * 추론 시 계산이 불가능하고 "지금 밤의 97% 지점"이라는 라벨 누수에 가까웠습니다. 앱이 쓰던
     * 이 고정 8시간 정의에 학습을 맞췄습니다(ml/script/channels.py의 TIME_FEATURE_SPAN_SEC).
     */
    const val TIME_FEATURE_SPAN_MS = 8L * 60L * 60L * 1000L

    /** 추세/변동성을 계산할 때 되돌아보는 에포크 수 (6 × 30초 = 3분). */
    const val ACTIVITY_LOOKBACK_EPOCHS = 6

    /**
     * m/s² → g 변환 계수.
     *
     * 🐛 학습/서빙 단위 불일치: Android `Sensor.TYPE_ACCELEROMETER`는 **m/s²**를 돌려줍니다
     * (정지 상태에서 크기 ≈ 9.81). 반면 학습 데이터인 Apple Watch 가속도(Sleep-Accel의
     * `*_acceleration.txt`, BIDSleep의 `motion.csv`)는 **g** 단위입니다(정지 시 ≈ 1.0).
     * 그런데 AndroidTrackingManager는 `accel.values[0..2]`를 변환 없이 그대로 모델 입력으로
     * 넘기고 있었습니다(같은 파일의 `MOVEMENT_THRESHOLD_MS2`와 SignalProcessor의 `- 9.81f`가
     * m/s² 단위임을 뒷받침합니다).
     *
     * 결과적으로 앱은 학습 때보다 약 9.8배 큰 값을 넣고 있었고, 정지 상태의 z축(-9.81)을
     * 학습 통계(mean -0.364 / std 0.660)로 z-score 정규화하면 약 **-14 sigma**가 됩니다.
     * tilt·activity_variability·activity_trend도 모두 가속도에서 파생되므로 함께 9.8배
     * 어긋났습니다 — 오프라인 지표와 무관하게 온디바이스 추론이 사실상 무의미해지는 수준입니다.
     *
     * 모델 입력을 만들 때만 g로 변환합니다. 움직임 보정 로직(SignalProcessor,
     * countMovements)은 계속 m/s² 기준으로 동작하므로 그쪽 값은 변환하지 않습니다.
     */
    const val MS2_TO_G = 1.0f / 9.80665f

    val CHANNEL_NAMES = listOf(
        "accel_x", "accel_y", "accel_z", "tilt",
        "activity_variability_3min", "activity_trend_3min", "time_feature",
    )
}
