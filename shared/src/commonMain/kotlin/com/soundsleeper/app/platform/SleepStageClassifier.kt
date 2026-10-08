package com.soundsleeper.app.platform

/**
 * 온디바이스 수면 단계 분류기.
 *
 * 💡 채널 인덱스/채널 수/클래스 수 같은 **입력 레이아웃 상수는 [AccelChannels]로 옮겼습니다.**
 * 이전에는 이 expect/actual 안에 있어서 androidMain / iosMain / jvmMain 세 곳에 똑같이
 * 복제돼 있었고, 복제된 값은 조용히 어긋납니다(실제로 androidMain이 NUM_CLASSES=5를 선언한 채
 * 4클래스 모델을 돌린 적이 있습니다). 플랫폼마다 다른 것은 모델 파일과 정규화 상수뿐입니다.
 */
expect class SleepStageClassifier {
    companion object {

        // 온디바이스 모델이 한 번에 필요로 하는 시퀀스 길이(epoch 개수).
        // SleepAnalyzer는 이 길이만큼 epoch를 모아서 classifySleepStage()를 호출해야 합니다.
        val CONTEXT_LEN: Int

        // 💡 온디바이스 배포 모델은 가속도계 전용(accel_infer_model)이라 Accel 도메인 정규화
        // 기준을 씁니다. 길이는 [AccelChannels.NUM_CHANNELS]와 같아야 합니다.
        //
        // 🐛 과거에는 "COMBINED"라는 16채널(EDF 8 + Accel 8) 배열을 쓰면서 앞 8개(EDF용)만
        // 실제로 참조해, 가속도 데이터를 EEG 정규화 기준으로 정규화하던 버그가 있었습니다.
        // 지금은 ml/script/compute_stats.py가 이 두 배열을 직접 읽어 계산값과 비교합니다
        // (불일치 시 붙여넣을 리터럴을 출력) — 손으로 유지하는 값의 드리프트를 막기 위함입니다.
        val ACCEL_CHANNEL_MEAN: FloatArray
        val ACCEL_CHANNEL_STD: FloatArray

        // 신규(Phase 8) — 오디오(멜스펙토그램) 입력 정규화 상수. ml/script의
        // audio_denoise_aux(APSAA/AI-Hub) 전처리 + fusion_finetune이 끝나기 전까지는 빈
        // 배열이며, 그동안 오디오 입력 경로는 쓰이지 않는다(androidMain의 isAudioCapable() 참고).
        val AUDIO_MEL_MEAN: FloatArray
        val AUDIO_MEL_STD: FloatArray
    }

    /**
     * 최근 CONTEXT_LEN개 epoch(각 epoch는 WINDOW_SIZE×NUM_CHANNELS)를 받아 가장 최근 epoch의
     * 수면 단계 클래스 인덱스를 추론한다.
     *
     * [audioMelData]는 신규(Phase 8) — 오디오(마이크) 멜스펙토그램을 함께 받는 2-입력 모델을
     * 위한 선택적 파라미터다. 기본값 null은 기존 호출부(accel 단독)를 전혀 바꾸지 않고 그대로
     * 통과시킨다. 실제 배포 모델이 입력을 1개만 받는 동안(지금 상태)에는 이 값이 와도
     * 각 플랫폼 구현이 무시한다 — 2-입력 모델이 배포되면(fusion_infer_model의 .tflite) 같은
     * 코드가 자동으로 두 입력을 함께 쓰도록, androidMain 구현이 모델의 실제 입력 개수를
     * 런타임에 감지해 분기한다.
     *
     * [timeBucketId]는 멀티입력(fusion) 모델의 time_bucket_id 입력에 쓰인다 — 지금(단일 입력)은
     * 쓰이지 않는다. 기본값 4는 과거 androidMain의 고정 placeholder(AUDIO_TIME_BUCKET_PLACEHOLDER)와
     * 같은 중간값이라, 호출부가 이 값을 안 넘겨도 기존 동작이 바뀌지 않는다.
     */
    fun classifySleepStage(
        accelData: List<List<FloatArray>>,
        audioMelData: List<List<FloatArray>>? = null,
        timeBucketId: Int = 4,
    ): Int
    /** 추론 리소스(인터프리터 등)를 해제한다. */
    fun close()
    /** 모델이 로드되어 추론 가능한 상태인지 반환한다. */
    fun isReady(): Boolean
}
