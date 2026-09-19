package com.sleepytime.shared.platform

expect class SleepStageClassifier {
    companion object {

        // 온디바이스 모델이 한 번에 필요로 하는 시퀀스 길이(epoch 개수).
        // SleepAnalyzer는 이 길이만큼 epoch를 모아서 classifySleepStage()를 호출해야 합니다.
        val CONTEXT_LEN: Int

        // 💡 온디바이스 배포 모델은 가속도계 전용(accel_infer_model)이라, 이 8개 채널은
        // Accel 도메인(bidsleep·sleep_accel 평균) 정규화 기준을 씁니다. EDF/PSG 정규화와는
        // 별개입니다 — 예전에 "COMBINED"라는 16채널(EDF 8 + Accel 8) 배열을 쓰면서 앞 8개
        // (EDF용)만 실제로 참조해, 가속도 데이터를 EEG 정규화 기준으로 정규화하던 버그가 있었습니다.
        val ACCEL_CHANNEL_MEAN: FloatArray
        val ACCEL_CHANNEL_STD: FloatArray

        // EDF 채널 인덱스
        val CH_EEG1: Int
        val CH_EEG2: Int
        val CH_EOG: Int
        val CH_EMG: Int

        // Accel 채널 인덱스
        val CH_ACCEL_X: Int
        val CH_ACCEL_Y: Int
        val CH_ACCEL_Z: Int
        val CH_TILT: Int
        val CH_HEART_RATE: Int
        // 💡 Phase 1(로드맵): 이름은 CH_HRV/CH_MFCC_ENERGY로 남아있지만, build_dataset_hybrid.py에서
        // 두 채널을 각각 "최근 3분 활동량 변동성(표준편차)"/"활동량 추세(기울기)"로 재활용했습니다
        // (SleepAnalyzer.kt의 computeCausalTrendVariability 참고). 실제 HRV/MFCC 값이 아닙니다.
        val CH_HRV: Int
        val CH_MFCC_ENERGY: Int
        val CH_TIME_FEATURE: Int
    }

    fun classifySleepStage(sensorData: List<List<FloatArray>>): Int
    fun close()
    fun isReady(): Boolean
}
