package com.soundsleeper.app.util

import com.soundsleeper.app.platform.AccelChannels
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 학습 전처리(`ml/script/channels.py`)와 앱 전처리([SleepFeatureBuilder])가 같은 입력에서 같은
 * 값을 내는지 검증합니다. 기댓값은 [ParityFixture] — `ml/script/export_parity_fixture.py`가
 * 학습 코드를 실행해 생성한 파일입니다.
 *
 * 💡 왜 필요한가: 이 경계에서 이미 두 번 어긋났습니다. 앱이 tilt 채널에 자이로스코프 크기를,
 * 변동성/추세 채널에 소음/MFCC 에너지를 넣고 있었고(c7ba84b), 정규화 배열은 16채널 중 앞 8개
 * (EEG용)만 참조하고 있었습니다. 이런 불일치는 크래시 없이 정확도만 떨어뜨리므로 테스트로
 * 고정해야 합니다.
 */
class SleepFeatureBuilderParityTest {

    // float 누적 오차를 감안한 허용치. 학습 쪽은 float64로 계산하고 float32로 저장합니다.
    private val tolerance = 1e-5f

    private fun assertClose(expected: Float, actual: Float, label: String) {
        assertTrue(
            abs(expected - actual) <= tolerance,
            "$label: 기댓값 $expected != 실제 $actual (허용치 $tolerance)"
        )
    }

    @Test
    fun 채널_레이아웃이_학습_코드와_같다() {
        assertEquals(7, AccelChannels.NUM_CHANNELS, "채널 수는 7이어야 합니다(심박 제거 후)")
        assertEquals(
            ParityFixture.expectedChannels.first().first().size,
            AccelChannels.NUM_CHANNELS,
            "fixture의 채널 수와 앱의 NUM_CHANNELS가 다릅니다 — export_parity_fixture.py를 다시 실행하세요"
        )
        // 채널 인덱스가 학습 쪽 ACCEL_CHANNEL_NAMES 순서와 같은지 고정합니다.
        assertEquals(0, AccelChannels.CH_ACCEL_X)
        assertEquals(1, AccelChannels.CH_ACCEL_Y)
        assertEquals(2, AccelChannels.CH_ACCEL_Z)
        assertEquals(3, AccelChannels.CH_TILT)
        assertEquals(4, AccelChannels.CH_ACTIVITY_VARIABILITY)
        assertEquals(5, AccelChannels.CH_ACTIVITY_TREND)
        assertEquals(6, AccelChannels.CH_TIME_FEATURE)
    }

    @Test
    fun 에포크별_스칼라가_학습_코드와_같다() {
        val recentMeans = ArrayDeque<Float>()

        ParityFixture.rawEpochs.forEachIndexed { epochIndex, samples ->
            val expected = ParityFixture.expectedScalars[epochIndex]

            val activityMean = SleepFeatureBuilder.epochActivityMean(samples)
            assertClose(expected.activityMean, activityMean, "epoch $epochIndex activityMean")

            val (trend, variability) =
                SleepFeatureBuilder.computeCausalTrendVariability(recentMeans.toList(), activityMean)
            assertClose(expected.trend, trend, "epoch $epochIndex trend")
            assertClose(expected.variability, variability, "epoch $epochIndex variability")

            recentMeans.addLast(activityMean)
            while (recentMeans.size > AccelChannels.ACTIVITY_LOOKBACK_EPOCHS) {
                recentMeans.removeFirst()
            }

            // 학습 쪽은 epoch 시작 시각(= index * 30초)을 경과 시간으로 씁니다.
            val timeFeature = SleepFeatureBuilder.timeFeature(
                epochIndex.toLong() * AccelChannels.EPOCH_SEC * 1000L
            )
            assertClose(expected.timeFeature, timeFeature, "epoch $epochIndex timeFeature")
        }
    }

    @Test
    fun 조립된_채널_행렬이_학습_코드와_같다() {
        val recentMeans = ArrayDeque<Float>()

        ParityFixture.rawEpochs.forEachIndexed { epochIndex, samples ->
            val activityMean = SleepFeatureBuilder.epochActivityMean(samples)
            val (trend, variability) =
                SleepFeatureBuilder.computeCausalTrendVariability(recentMeans.toList(), activityMean)
            recentMeans.addLast(activityMean)
            while (recentMeans.size > AccelChannels.ACTIVITY_LOOKBACK_EPOCHS) {
                recentMeans.removeFirst()
            }
            val timeFeature = SleepFeatureBuilder.timeFeature(
                epochIndex.toLong() * AccelChannels.EPOCH_SEC * 1000L
            )

            val actual = SleepFeatureBuilder.buildEpochChannels(
                samples, variability, trend, timeFeature
            )
            val expected = ParityFixture.expectedChannels[epochIndex]

            assertEquals(expected.size, actual.size, "epoch $epochIndex 샘플 수")
            expected.forEachIndexed { sampleIndex, expectedRow ->
                val actualRow = actual[sampleIndex]
                assertEquals(expectedRow.size, actualRow.size, "epoch $epochIndex sample $sampleIndex 채널 수")
                expectedRow.indices.forEach { ch ->
                    assertClose(
                        expectedRow[ch],
                        actualRow[ch],
                        "epoch $epochIndex sample $sampleIndex ch $ch (${AccelChannels.CHANNEL_NAMES[ch]})"
                    )
                }
            }
        }
    }

    @Test
    fun timeFeature는_8시간에서_포화되고_음수를_0으로_만든다() {
        assertEquals(0f, SleepFeatureBuilder.timeFeature(0L))
        assertEquals(0f, SleepFeatureBuilder.timeFeature(-5_000L), "음수 경과는 0으로 클램프")
        assertEquals(0.5f, SleepFeatureBuilder.timeFeature(4L * 60 * 60 * 1000))
        assertEquals(1f, SleepFeatureBuilder.timeFeature(AccelChannels.TIME_FEATURE_SPAN_MS))
        assertEquals(1f, SleepFeatureBuilder.timeFeature(20L * 60 * 60 * 1000), "8시간 초과는 1.0에서 포화")
    }

    /**
     * 🐛 학습 데이터(Apple Watch)는 g 단위인데 Android 가속도계는 m/s²를 줍니다. 변환 없이
     * 모델에 넣으면 약 9.8배 큰 값이 들어가고, 학습 통계로 정규화하면 -14 sigma 수준이 되어
     * 추론이 무의미해집니다. 이 테스트로 변환을 고정합니다.
     */
    @Test
    fun 가속도는_모델_입력_전에_m초제곱당미터에서_g로_변환된다() {
        // 정지 상태의 Android 가속도계 샘플: 크기 ≈ 9.80665 m/s²
        val resting = listOf(floatArrayOf(0f, 0f, -9.80665f, 0.1f, 0.2f, 0.3f, 40f))
        val converted = SleepFeatureBuilder.toGravityUnits(resting)

        assertClose(-1f, converted[0][AccelChannels.CH_ACCEL_Z], "정지 z축은 -1g")
        assertClose(1f, SleepFeatureBuilder.epochActivityMean(converted), "정지 활동량 크기는 1g")

        // 자이로/소음 채널은 건드리지 않습니다 — 움직임 보정 로직이 m/s² 기준으로 동작합니다.
        assertClose(0.1f, converted[0][3], "자이로 x 보존")
        assertClose(0.2f, converted[0][4], "자이로 y 보존")
        assertClose(0.3f, converted[0][5], "자이로 z 보존")
        assertClose(40f, converted[0][6], "소음 보존")

        // 원본 리스트는 변형되지 않아야 합니다(두 번 변환되면 1/96배가 됩니다).
        assertClose(-9.80665f, resting[0][AccelChannels.CH_ACCEL_Z], "입력은 그대로 유지")
    }

    @Test
    fun resampleTo는_목표_길이로_맞추고_양끝값을_보존한다() {
        val samples = (0 until 10).map { floatArrayOf(it.toFloat(), 0f, -1f) }

        val up = SleepFeatureBuilder.resampleTo(samples, 25)
        assertEquals(25, up.size)
        assertClose(0f, up.first()[0], "업샘플 첫 값")
        assertClose(9f, up.last()[0], "업샘플 마지막 값")

        val down = SleepFeatureBuilder.resampleTo(samples, 5)
        assertEquals(5, down.size)
        assertClose(0f, down.first()[0], "다운샘플 첫 값")
        assertClose(9f, down.last()[0], "다운샘플 마지막 값")

        // 샘플이 1개뿐이면 반복해서 채웁니다(학습 쪽 resample_to_grid도 상수로 채움).
        val single = SleepFeatureBuilder.resampleTo(listOf(floatArrayOf(7f, 0f, 0f)), 4)
        assertEquals(4, single.size)
        single.forEach { assertClose(7f, it[0], "단일 샘플 반복") }

        // 이미 목표 길이면 그대로 반환합니다.
        assertEquals(samples, SleepFeatureBuilder.resampleTo(samples, 10))
    }

    /**
     * 멜스펙토그램(신규, Phase 8) parity — ml/script/preprocess.py의 audio_epoch_to_melspec()이
     * 같은 합성 오디오 입력에서 만든 기댓값과 Kotlin 구현을 대조합니다. Hann 윈도우 공식,
     * radix-2 FFT, 멜 필터뱅크 구성이 전부 같은 식이어야 멜 값이 일치합니다.
     */
    @Test
    fun 멜스펙토그램이_학습_코드와_같다() {
        assertEquals(
            ParityFixture.AUDIO_SAMPLE_RATE, SleepFeatureBuilder.AUDIO_SAMPLE_RATE,
            "AUDIO_SAMPLE_RATE가 학습 쪽과 다릅니다"
        )
        assertEquals(ParityFixture.AUDIO_N_FFT, SleepFeatureBuilder.AUDIO_N_FFT, "AUDIO_N_FFT가 학습 쪽과 다릅니다")
        assertEquals(
            ParityFixture.AUDIO_HOP_LENGTH, SleepFeatureBuilder.AUDIO_HOP_LENGTH,
            "AUDIO_HOP_LENGTH가 학습 쪽과 다릅니다"
        )
        assertEquals(ParityFixture.AUDIO_N_MELS, SleepFeatureBuilder.AUDIO_N_MELS, "AUDIO_N_MELS가 학습 쪽과 다릅니다")

        val actual = SleepFeatureBuilder.audioEpochToMelSpectrogram(ParityFixture.rawAudioSamples)
        val expected = ParityFixture.expectedMelFrames

        assertEquals(expected.size, actual.size, "멜스펙토그램 프레임 수")
        expected.forEachIndexed { frameIndex, expectedFrame ->
            val actualFrame = actual[frameIndex]
            assertEquals(expectedFrame.size, actualFrame.size, "frame $frameIndex 멜빈 수")
            expectedFrame.indices.forEach { melIndex ->
                assertClose(
                    expectedFrame[melIndex], actualFrame[melIndex],
                    "frame $frameIndex mel $melIndex"
                )
            }
        }
    }

    @Test
    fun 멜_필터뱅크는_모든_값이_0_이상인_삼각형이다() {
        val fb = SleepFeatureBuilder.buildMelFilterbank()
        assertEquals(SleepFeatureBuilder.AUDIO_N_MELS, fb.size)
        val nBins = SleepFeatureBuilder.AUDIO_N_FFT / 2 + 1
        fb.forEach { filter ->
            assertEquals(nBins, filter.size)
            filter.forEach { assertTrue(it >= 0f, "멜 필터뱅크 값은 0 이상이어야 합니다") }
        }
    }
}
