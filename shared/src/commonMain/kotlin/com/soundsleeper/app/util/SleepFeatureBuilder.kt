package com.soundsleeper.app.util

import com.soundsleeper.app.platform.AccelChannels
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 원시 센서 샘플을 모델 입력 채널로 바꾸는 **순수 계산** 부분입니다.
 *
 * 💡 SleepAnalyzer에서 떼어낸 이유는 테스트 가능성입니다. 이 계산은 학습 쪽
 * `ml/script/channels.py`와 식이 완전히 같아야 하는데, SleepAnalyzer.analyzeWindow는
 * TFLite 모델(SleepStageClassifier)과 시계(Clock)에 의존해서 단위 테스트로 검증할 수
 * 없었습니다. 과거에 바로 이 지점에서 두 번(자이로 -> tilt, 소음 -> 변동성/추세) 채널이
 * 어긋났으므로, 회귀 테스트가 붙을 수 있는 형태로 분리합니다.
 *
 * 대응 관계:
 *   resampleTo                  ↔ channels.py resample_to_grid (단, 아래 주석의 한계 참고)
 *   computeCausalTrendVariability ↔ channels.py compute_causal_trend_variability
 *   computeTilt                 ↔ channels.py compute_tilt
 *   activityMagnitude           ↔ channels.py activity_magnitude
 *   timeFeature                 ↔ channels.py causal_time_feature
 *   buildEpochChannels          ↔ channels.py build_epoch_channels
 */
object SleepFeatureBuilder {

    /**
     * 가속도 축(0,1,2)만 m/s² → g로 변환한 사본을 돌려줍니다. 나머지 채널(자이로, 소음)은
     * 그대로 둡니다 — 움직임 보정 로직은 계속 m/s² 기준으로 동작해야 합니다.
     *
     * 🐛 학습 데이터(Apple Watch)는 g 단위인데 Android 가속도계는 m/s²를 주고, 앱은 변환 없이
     * 그대로 모델에 넣고 있었습니다. 자세한 설명은 [AccelChannels.MS2_TO_G] 참고.
     * **모델 입력을 만들기 직전에 반드시 한 번만** 적용해야 합니다(두 번 적용하면 1/96배가 됩니다).
     */
    fun toGravityUnits(samples: List<FloatArray>): List<FloatArray> = samples.map { sample ->
        sample.copyOf().also {
            if (it.size > AccelChannels.CH_ACCEL_X) it[AccelChannels.CH_ACCEL_X] *= AccelChannels.MS2_TO_G
            if (it.size > AccelChannels.CH_ACCEL_Y) it[AccelChannels.CH_ACCEL_Y] *= AccelChannels.MS2_TO_G
            if (it.size > AccelChannels.CH_ACCEL_Z) it[AccelChannels.CH_ACCEL_Z] *= AccelChannels.MS2_TO_G
        }
    }

    /**
     * 세션 시작 이후 경과 시간을 8시간 기준으로 정규화합니다(상한 1.0).
     * 학습 쪽 `causal_time_feature(elapsed_sec)`와 같은 값입니다.
     */
    fun timeFeature(elapsedMs: Long): Float {
        val clamped = if (elapsedMs < 0L) 0L else elapsedMs
        return (clamped.toDouble() / AccelChannels.TIME_FEATURE_SPAN_MS.toDouble())
            .coerceAtMost(1.0)
            .toFloat()
    }

    /**
     * timeFeature(0~1)를 컨텍스트 트랜스포머의 시간대 FiLM 조건화용 정수 버킷 인덱스로
     * 양자화합니다. ml/script/preprocess.py의 time_feature_bucket()과 같은 식이어야 하고,
     * nBuckets 기본값 8은 train.py의 N_TIME_BUCKETS와 맞춥니다.
     */
    fun timeFeatureBucket(timeFeature: Float, nBuckets: Int = 8): Int {
        val idx = (timeFeature * nBuckets).toInt()
        return idx.coerceIn(0, nBuckets - 1)
    }

    /** 활동량 크기 = 가속도 벡터 크기. 추세/변동성 채널의 기반 신호입니다. */
    fun activityMagnitude(x: Float, y: Float, z: Float): Float = sqrt(x * x + y * y + z * z)

    /** 에포크 평균에서 각 축이 얼마나 벗어났는지의 L1 합. */
    fun computeTilt(x: Float, y: Float, z: Float, avgX: Float, avgY: Float, avgZ: Float): Float =
        abs(x - avgX) + abs(y - avgY) + abs(z - avgZ)

    /**
     * recentMeans(과거 에포크들의 활동량 평균, 시간순)에 현재 에포크까지 포함해 선형 추세(기울기)와
     * 변동성(표준편차)을 계산합니다. 미래 데이터를 쓰지 않습니다.
     *
     * @return (trend, variability)
     */
    fun computeCausalTrendVariability(recentMeans: List<Float>, currentMean: Float): Pair<Float, Float> {
        val window = recentMeans + currentMean
        if (window.size < 3) return 0f to 0f

        val n = window.size
        val tMean = (n - 1) / 2.0
        // 💡 평균/분산은 Double로 누적합니다. Float로 누적하면 1500 샘플 × 7 채널 규모에서
        // 학습 쪽(numpy float64)과 눈에 보이는 차이가 생겨 parity 테스트가 깨집니다.
        val vMean = window.fold(0.0) { acc, v -> acc + v } / n

        var num = 0.0
        var denom = 0.0
        var sqSum = 0.0
        window.forEachIndexed { i, v ->
            val tCentered = i - tMean
            num += tCentered * (v - vMean)
            denom += tCentered * tCentered
            sqSum += (v - vMean) * (v - vMean)
        }
        val trend = if (denom > 0.0) num / denom else 0.0
        val variability = sqrt(sqSum / n)
        return trend.toFloat() to variability.toFloat()
    }

    /**
     * 한 에포크의 (WINDOW_SIZE × NUM_CHANNELS) 모델 입력을 조립합니다.
     * 채널 순서는 [AccelChannels]의 CH_* 정의를 따릅니다.
     *
     * @param resampled WINDOW_SIZE개로 리샘플된 원시 샘플. 각 FloatArray의 앞 3개가 accel x/y/z입니다.
     */
    fun buildEpochChannels(
        resampled: List<FloatArray>,
        variability: Float,
        trend: Float,
        timeFeature: Float,
    ): List<FloatArray> {
        var sumX = 0.0
        var sumY = 0.0
        var sumZ = 0.0
        for (sample in resampled) {
            sumX += sample.getOrElse(AccelChannels.CH_ACCEL_X) { 0f }
            sumY += sample.getOrElse(AccelChannels.CH_ACCEL_Y) { 0f }
            sumZ += sample.getOrElse(AccelChannels.CH_ACCEL_Z) { 1f }
        }
        val n = resampled.size.coerceAtLeast(1)
        val avgX = (sumX / n).toFloat()
        val avgY = (sumY / n).toFloat()
        val avgZ = (sumZ / n).toFloat()

        return resampled.map { sample ->
            val x = sample.getOrElse(AccelChannels.CH_ACCEL_X) { 0f }
            val y = sample.getOrElse(AccelChannels.CH_ACCEL_Y) { 0f }
            val z = sample.getOrElse(AccelChannels.CH_ACCEL_Z) { 1f }
            FloatArray(AccelChannels.NUM_CHANNELS).also {
                it[AccelChannels.CH_ACCEL_X] = x
                it[AccelChannels.CH_ACCEL_Y] = y
                it[AccelChannels.CH_ACCEL_Z] = z
                it[AccelChannels.CH_TILT] = computeTilt(x, y, z, avgX, avgY, avgZ)
                it[AccelChannels.CH_ACTIVITY_VARIABILITY] = variability
                it[AccelChannels.CH_ACTIVITY_TREND] = trend
                it[AccelChannels.CH_TIME_FEATURE] = timeFeature
            }
        }
    }

    /** 에포크의 평균 활동량(가속도 벡터 크기의 평균). 추세/변동성 버퍼에 쌓는 값입니다. */
    fun epochActivityMean(resampled: List<FloatArray>): Float {
        if (resampled.isEmpty()) return 0f
        var sum = 0.0
        for (sample in resampled) {
            sum += activityMagnitude(
                sample.getOrElse(AccelChannels.CH_ACCEL_X) { 0f },
                sample.getOrElse(AccelChannels.CH_ACCEL_Y) { 0f },
                sample.getOrElse(AccelChannels.CH_ACCEL_Z) { 1f },
            )
        }
        return (sum / resampled.size).toFloat()
    }

    /**
     * 샘플 수를 WINDOW_SIZE개로 선형보간합니다.
     *
     * ⚠️ 한계: 타임스탬프가 아니라 **인덱스** 기준 보간입니다. 앱은 가속도계를
     * SENSOR_DELAY_GAME(약 20ms = 50Hz)로 등록하므로 30초 버킷에 평균 ~1500 샘플이 모여
     * 학습 그리드(50Hz × 30초)와 명목상 일치하지만, 샘플이 누락되거나 간격이 흔들리면
     * (doze, 센서 지연) 실제 시각과 어긋납니다. AndroidTrackingManager의 RawAccel은
     * 타임스탬프를 갖고 있는데 SleepAnalyzer에 오기 전에 버려지므로, 시간 기준 보간으로
     * 바꾸려면 타임스탬프를 함께 전달하도록 레이어를 수정해야 합니다(후속 작업).
     */
    fun resampleTo(samples: List<FloatArray>, targetCount: Int = AccelChannels.WINDOW_SIZE): List<FloatArray> {
        if (samples.size == targetCount) return samples
        if (samples.isEmpty()) return List(targetCount) { FloatArray(AccelChannels.NUM_CHANNELS) }
        if (samples.size == 1) return List(targetCount) { samples[0].copyOf() }

        val result = ArrayList<FloatArray>(targetCount)
        val ratio = (samples.size - 1).toFloat() / (targetCount - 1).toFloat()
        val channelCount = samples[0].size

        for (i in 0 until targetCount) {
            val srcIndex = i * ratio
            val lo = srcIndex.toInt().coerceIn(0, samples.size - 1)
            val hi = (lo + 1).coerceAtMost(samples.size - 1)
            val frac = srcIndex - lo

            result.add(FloatArray(channelCount) { ch ->
                val a = samples[lo].getOrElse(ch) { 0f }
                val b = samples[hi].getOrElse(ch) { 0f }
                a + (b - a) * frac
            })
        }
        return result
    }

    // ============================================================
    // 오디오 멜스펙토그램 (신규 — 마이크 입력을 모델이 쓸 입력으로 바꾸는 계산)
    // ============================================================
    // ⚠️ ml/script/preprocess.py의 audio_epoch_to_melspec() / build_mel_filterbank()와
    // 반드시 같은 식이어야 합니다. ParityFixture(rawAudioSamples/expectedMelFrames)가
    // 이 함수들의 출력을 학습 쪽 계산과 대조합니다(SleepFeatureBuilderParityTest 참고).
    //
    // n_fft가 512(2의 거듭제곱)인 이유: numpy(np.fft.fft, 학습 쪽)는 임의 길이를 처리하지만,
    // 여기 fftInPlace는 radix-2 Cooley-Tukey라 2의 거듭제곱 길이만 올바릅니다. 두 구현이
    // 같은 알고리즘을 공유하도록 학습 쪽 n_fft를 512로 맞췄습니다(ml/script/preprocess.py 참고).

    /** 모델이 기대하는 오디오 샘플레이트(Hz). 원본(APSAA/AI-Hub, 폰 마이크 등)이 더 높은 샘플레이트여도 학습 전처리에서 이 값으로 다운샘플링합니다. */
    const val AUDIO_SAMPLE_RATE = 16000

    /** FFT 프레임 길이(샘플). 16kHz에서 32ms. 반드시 2의 거듭제곱. */
    const val AUDIO_N_FFT = 512

    /** 프레임 간 이동 간격(샘플). 16kHz에서 16ms(50% 오버랩). */
    const val AUDIO_HOP_LENGTH = 256

    /** 멜 필터뱅크 빈 개수. */
    const val AUDIO_N_MELS = 40

    private fun hzToMel(hz: Double): Double = 2595.0 * (ln(1.0 + hz / 700.0) / ln(10.0))

    private fun melToHz(mel: Double): Double = 700.0 * (10.0.pow(mel / 2595.0) - 1.0)

    /**
     * 표준 삼각형 멜 필터뱅크. shape (nMels, nFft/2+1) — 전력 스펙트럼(0~Nyquist)에 곱해
     * 멜 에너지를 얻습니다. preprocess.py의 build_mel_filterbank()와 동일한 식입니다.
     */
    fun buildMelFilterbank(
        sampleRate: Int = AUDIO_SAMPLE_RATE,
        nFft: Int = AUDIO_N_FFT,
        nMels: Int = AUDIO_N_MELS,
        fMin: Double = 0.0,
        fMax: Double = sampleRate / 2.0,
    ): Array<FloatArray> {
        val nBins = nFft / 2 + 1
        val melMin = hzToMel(fMin)
        val melMax = hzToMel(fMax)
        val melPoints = DoubleArray(nMels + 2) { i -> melMin + (melMax - melMin) * i / (nMels + 1) }
        val binPoints = IntArray(nMels + 2) { i ->
            val hz = melToHz(melPoints[i])
            floor((nFft + 1) * hz / sampleRate).toInt().coerceIn(0, nBins - 1)
        }

        val fb = Array(nMels) { FloatArray(nBins) }
        for (m in 1..nMels) {
            val fPrev = binPoints[m - 1]
            val fCurr = binPoints[m]
            val fNext = binPoints[m + 1]
            if (fCurr > fPrev) {
                for (k in fPrev..fCurr) {
                    fb[m - 1][k] = (k - fPrev).toFloat() / (fCurr - fPrev).toFloat()
                }
            }
            if (fNext > fCurr) {
                for (k in fCurr..fNext) {
                    fb[m - 1][k] = (fNext - k).toFloat() / (fNext - fCurr).toFloat()
                }
            }
        }
        return fb
    }

    /** SensorBridge.kt의 hannWindow와 같은 공식(0.5*(1-cos(2*pi*n/(N-1)))). */
    private fun hannWindow(n: Int): DoubleArray = DoubleArray(n) { i ->
        0.5 * (1.0 - cos(2.0 * PI * i / (n - 1)))
    }

    /**
     * 제자리(in-place) radix-2 FFT. SensorBridge.kt의 fftInPlace(노이즈 dB 계산용)와 같은
     * 알고리즘을 멜스펙토그램 전용으로 별도 구현한 것입니다(서로 다른 용도/크기라 공유하지
     * 않고 독립적으로 둡니다 — 노이즈 계산 경로를 건드리지 않기 위함).
     */
    private fun fftInPlace(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 0 until n) {
            if (i < j) {
                val tRe = re[i]; re[i] = re[j]; re[j] = tRe
                val tIm = im[i]; im[i] = im[j]; im[j] = tIm
            }
            var m = n shr 1
            while (m >= 1 && j >= m) {
                j -= m
                m = m shr 1
            }
            j += m
        }
        var len = 2
        while (len <= n) {
            val ang = 2.0 * PI / len
            val wlenRe = cos(ang)
            val wlenIm = -sin(ang)
            var i = 0
            while (i < n) {
                var wRe = 1.0
                var wIm = 0.0
                for (k in 0 until len / 2) {
                    val uRe = re[i + k]
                    val uIm = im[i + k]
                    val vRe = re[i + k + len / 2] * wRe - im[i + k + len / 2] * wIm
                    val vIm = re[i + k + len / 2] * wIm + im[i + k + len / 2] * wRe
                    re[i + k] = uRe + vRe
                    im[i + k] = uIm + vIm
                    re[i + k + len / 2] = uRe - vRe
                    im[i + k + len / 2] = uIm - vIm
                    val nextWRe = wRe * wlenRe - wIm * wlenIm
                    wIm = wRe * wlenIm + wIm * wlenRe
                    wRe = nextWRe
                }
                i += len
            }
            len = len shl 1
        }
    }

    /**
     * 한 epoch(30초)의 raw 오디오 샘플(mono)을 (nFrames, nMels) 로그-멜 에너지로 변환합니다.
     * preprocess.py의 audio_epoch_to_melspec()과 동일한 순서(Hann 윈도우 -> FFT -> 전력
     * 스펙트럼 -> 멜 필터뱅크 곱 -> log)입니다. melFb를 미리 만들어 넘기면(여러 프레임/에포크에
     * 재사용) 매 호출마다 필터뱅크를 다시 만들지 않아 빠릅니다.
     */
    fun audioEpochToMelSpectrogram(
        samples: FloatArray,
        sampleRate: Int = AUDIO_SAMPLE_RATE,
        nMels: Int = AUDIO_N_MELS,
        nFft: Int = AUDIO_N_FFT,
        hopLength: Int = AUDIO_HOP_LENGTH,
        melFb: Array<FloatArray>? = null,
    ): Array<FloatArray> {
        val fb = melFb ?: buildMelFilterbank(sampleRate, nFft, nMels)
        val window = hannWindow(nFft)

        val padded = if (samples.size < nFft) {
            FloatArray(nFft).also { samples.copyInto(it) }
        } else {
            samples
        }
        val n = padded.size
        val nFrames = (1 + (n - nFft) / hopLength).coerceAtLeast(1)
        val eps = 1e-10

        return Array(nFrames) { frameIdx ->
            val start = frameIdx * hopLength
            val re = DoubleArray(nFft)
            val im = DoubleArray(nFft)
            for (i in 0 until nFft) {
                val sampleIdx = start + i
                val v = if (sampleIdx < padded.size) padded[sampleIdx].toDouble() else 0.0
                re[i] = v * window[i]
            }
            fftInPlace(re, im)

            val nBins = nFft / 2 + 1
            val power = DoubleArray(nBins) { k -> re[k] * re[k] + im[k] * im[k] }

            FloatArray(nMels) { melIdx ->
                var energy = 0.0
                val filter = fb[melIdx]
                for (k in 0 until nBins) {
                    energy += filter[k] * power[k]
                }
                ln(energy + eps).toFloat()
            }
        }
    }
}
