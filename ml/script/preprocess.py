"""전처리 공용 모듈 — 채널 레이아웃, 에포크 특징, 멜스펙토그램, 정규화 통계, Kotlin parity.

무거운 의존성(mne, tensorflow)이 전혀 없습니다 — numpy/scipy만 씁니다. 이렇게 분리한 이유는
옛 channels.py/compute_stats.py/validate_labels.py/epoch_features.py/feature_dataset.py와
동일합니다: build_dataset_hybrid.py(mne 필요)나 model.py/train.py(tensorflow 필요)를 설치하지
않고도 채널 계산·통계 검사·Kotlin parity를 돌릴 수 있어야 합니다. 또한 여기 있는 계산 중
상당수는 Kotlin(SleepFeatureBuilder.kt, 그리고 향후 오디오 멜스펙토그램 계산)에 **같은 식으로
구현되어야 하는** 것들이라, parity fixture를 만들고 비교하기 쉽도록 한 파일에 모아둡니다.

이 파일은 기존 6개 파일(channels.py, epoch_features.py, compute_stats.py,
export_parity_fixture.py, feature_dataset.py의 비-TF 부분, build_features.py)을 합친 것입니다.

신규: 오디오(마이크) 멜스펙토그램 계산(audio_epoch_to_melspec)을 추가했습니다. APSAA/AI-Hub
오디오(비지도 디노이징 사전학습용) 및 향후 폰 마이크 입력을 학습/추론에 쓰기 위한 전처리입니다.
tf.signal 대신 numpy로
직접 구현한 이유는 (a) 이 파일이 TF-free여야 한다는 기존 제약을 지키기 위해서이고, (b) 안드로이드
쪽 SensorBridge.kt가 이미 자체 FFT(fftInPlace)와 Hann 윈도우를 손으로 구현해 두었으므로, 같은
방식(완전한 복소 FFT + Hann 윈도우 + Nyquist까지의 전력 스펙트�럼)으로 맞추면 Kotlin 이식이
가장 쉽기 때문입니다.
"""
import argparse
import re
from pathlib import Path

import numpy as np

# ============================================================
# 0. 경로
# ============================================================
# 💡 cwd(현재 작업 디렉토리)가 로컬/Colab에서 다를 수 있어 cwd에 의존하지 않도록 이 파일
# (ml/script/preprocess.py) 자신의 위치를 기준으로 ml/ 폴더를 찾습니다.
BASE_DIR = Path(__file__).resolve().parent.parent
REPO_ROOT = BASE_DIR.parent
CLASSIFIER_KT = (
    REPO_ROOT / "shared" / "src" / "androidMain" / "kotlin" / "com" / "sleepytime" / "shared"
    / "platform" / "SleepStageClassifier.kt"
)
PARITY_FIXTURE_OUT = (
    REPO_ROOT / "shared" / "src" / "commonTest" / "kotlin" / "com" / "sleepytime" / "shared"
    / "util" / "ParityFixture.kt"
)

EDF_SUBJECTS = BASE_DIR / "data" / "sleep_edf" / "subjects"
BID_SUBJECTS = BASE_DIR / "data" / "bidsleep" / "subjects"
SA_SUBJECTS = BASE_DIR / "data" / "sleep_accel" / "subjects"
# PSG-Audio는 더 이상 쓰지 않습니다(오디오+실제 라벨 데이터가 전혀 없어져 audio_core 스테이지
# 자체가 삭제됨). 오디오 정규화 통계는 라벨이 필요 없으므로 APSAA/AI-Hub(비지도)로 계산합니다.
APSAA_SUBJECTS = BASE_DIR / "data" / "apsaa" / "subjects"
AIHUB_SUBJECTS = BASE_DIR / "data" / "aihub_selfsleep" / "subjects"

EDF_NORM_PATH = BASE_DIR / "data" / "sleep_edf" / "norm_stats.npy"
ACCEL_NORM_PATH = BASE_DIR / "data" / "accel_domain" / "norm_stats.npy"
AUDIO_NORM_PATH = BASE_DIR / "data" / "audio_domain" / "norm_stats.npy"

# ============================================================
# 1. 채널 레이아웃 (옛 channels.py)
# ============================================================
SAMPLE_RATE = 50
WINDOW = 1500  # 50Hz * 30s
EPOCH_SEC = 30
N_CLASSES = 4
N_CHANNELS = 7

# Accel 도메인 채널 순서 — SleepStageClassifier.kt의 CH_* 상수와 반드시 일치해야 합니다.
ACCEL_CHANNEL_NAMES = [
    "accel_x", "accel_y", "accel_z", "tilt",
    "activity_variability_3min", "activity_trend_3min", "time_feature",
]
# PSG 도메인(연구용, 앱 미배포). 두 도메인이 같은 텐서 폭을 공유하도록 7채널로 맞춥니다.
PSG_CHANNEL_NAMES = ["eeg1", "eeg2", "eog", "emg", "reserved1", "reserved2", "time_feature"]

# 세션 시작 이후 경과 시간을 정규화하는 기준(8시간). 앱의 정의(elapsedMs / 28_800_000)와 맞춥니다.
TIME_FEATURE_SPAN_SEC = 8 * 60 * 60.0

LOOKBACK_EPOCHS = 6  # 30초 epoch 6개 = 최근 3분


def causal_time_feature(elapsed_sec):
    """세션 시작 이후 경과 시간을 8시간으로 정규화(상한 1.0). 미래 정보를 쓰지 않습니다."""
    return float(min(max(float(elapsed_sec), 0.0) / TIME_FEATURE_SPAN_SEC, 1.0))


def time_feature_bucket(time_feature, n_buckets):
    """time_feature(0~1)를 FiLM 조건화용 정수 버킷 인덱스(0~n_buckets-1)로 양자화합니다.

    model.py의 context_transformer_body가 domain FiLM과 같은 방식으로 "밤 안에서 지금이
    어디쯤인지"를 조건화하기 위해 씁니다. 버킷 수가 늘어날수록 세밀해지지만 각 버킷의
    학습 샘플이 줄어드므로, 기본값은 8개(각 버킷 ≈ 1시간) 정도가 적당합니다.
    """
    idx = int(float(time_feature) * n_buckets)
    return int(min(max(idx, 0), n_buckets - 1))


def compute_causal_trend_variability(recent_epoch_means, current_epoch_mean):
    """recent_epoch_means(과거 epoch들의 활동량 평균, 시간순)에 현재 epoch까지 포함해 선형
    추세(기울기)와 변동성(표준편차)을 계산합니다. 미래 데이터를 쓰지 않습니다.

    SleepFeatureBuilder.kt의 computeCausalTrendVariability()와 같은 식이어야 합니다.
    """
    window = list(recent_epoch_means) + [current_epoch_mean]
    if len(window) < 3:
        return 0.0, 0.0
    v = np.asarray(window, dtype=np.float64)
    t = np.arange(len(v), dtype=np.float64)
    t_centered = t - t.mean()
    denom = np.sum(t_centered ** 2)
    trend = float(np.sum(t_centered * (v - v.mean())) / denom) if denom > 0 else 0.0
    variability = float(np.std(v))
    return trend, variability


def compute_tilt(x, y, z):
    """에포크 평균에서 각 축이 얼마나 벗어났는지의 L1 합."""
    return np.abs(x - x.mean()) + np.abs(y - y.mean()) + np.abs(z - z.mean())


def activity_magnitude(x, y, z):
    """활동량 크기 = 가속도 벡터 크기. 추세/변동성 채널의 기반 신호입니다."""
    return np.sqrt(
        np.asarray(x, dtype=np.float64) ** 2
        + np.asarray(y, dtype=np.float64) ** 2
        + np.asarray(z, dtype=np.float64) ** 2
    )


def build_epoch_channels(x, y, z, variability, trend, time_feature):
    """한 에포크의 (WINDOW, N_CHANNELS) 입력을 조립합니다. ACCEL_CHANNEL_NAMES 순서입니다."""
    n = len(x)
    return np.column_stack([
        x, y, z,
        compute_tilt(x, y, z),
        np.full(n, variability, dtype=np.float32),
        np.full(n, trend, dtype=np.float32),
        np.full(n, time_feature, dtype=np.float32),
    ]).astype(np.float32)


# ============================================================
# 2. 오디오 멜스펙토그램 (신규 — 스마트폰 마이크 입력 전처리)
# ============================================================
# 원본 오디오(APSAA/AI-Hub, 폰 마이크 등)는 소스마다 샘플레이트가 다르지만, 호흡음/코골이는
# 대부분 8kHz 이하 대역에 있습니다. 16kHz로 통일해 두면 원본이 더 높은 샘플레이트여도 연산량이
# 줄고, 온디바이스(Kotlin) 계산 비용도 그만큼 줄어듭니다.
AUDIO_SAMPLE_RATE = 16000
AUDIO_EPOCH_SEC = EPOCH_SEC  # accel epoch과 동일한 30초 단위로 맞춤
N_MELS = 40
# ⚠️ n_fft는 반드시 2의 거듭제곱이어야 합니다. numpy(np.fft.fft)는 임의 길이를 처리하지만,
# Kotlin 쪽(SensorBridge.kt의 fftInPlace와 같은 radix-2 알고리즘)은 2의 거듭제곱 길이만
# 올바르게 동작합니다 — 애초에 512(32ms @16kHz)로 맞춰야 Python/Kotlin이 같은 FFT 구현을
# 공유할 수 있습니다(400으로 두면 Kotlin에서 별도의 느린 O(n^2) DFT가 필요해짐).
AUDIO_N_FFT = 512       # 16kHz에서 32ms
AUDIO_HOP_LENGTH = 256  # 16kHz에서 16ms (50% 오버랩)
# 한 epoch(30s)의 멜스펙토그램 프레임 수. (epoch_samples - n_fft) / hop + 1.
AUDIO_EPOCH_SAMPLES = AUDIO_SAMPLE_RATE * AUDIO_EPOCH_SEC
N_MEL_FRAMES = 1 + (AUDIO_EPOCH_SAMPLES - AUDIO_N_FFT) // AUDIO_HOP_LENGTH


def _hz_to_mel(hz):
    return 2595.0 * np.log10(1.0 + np.asarray(hz, dtype=np.float64) / 700.0)


def _mel_to_hz(mel):
    return 700.0 * (10.0 ** (np.asarray(mel, dtype=np.float64) / 2595.0) - 1.0)


def build_mel_filterbank(sample_rate=AUDIO_SAMPLE_RATE, n_fft=AUDIO_N_FFT, n_mels=N_MELS,
                          fmin=0.0, fmax=None):
    """표준 삼각형 멜 필터뱅크. shape (n_mels, n_fft//2 + 1) — 전력 스펙트럼(0~Nyquist)에
    곱해 멜 에너지를 얻습니다. Kotlin에서도 같은 공식으로 재현 가능하도록 라이브러리를
    쓰지 않고 직접 구현했습니다(librosa 등에 의존하지 않음).
    """
    fmax = fmax if fmax is not None else sample_rate / 2.0
    n_bins = n_fft // 2 + 1

    mel_min, mel_max = _hz_to_mel(fmin), _hz_to_mel(fmax)
    mel_points = np.linspace(mel_min, mel_max, n_mels + 2)
    hz_points = _mel_to_hz(mel_points)
    bin_points = np.floor((n_fft + 1) * hz_points / sample_rate).astype(int)
    bin_points = np.clip(bin_points, 0, n_bins - 1)

    fb = np.zeros((n_mels, n_bins), dtype=np.float64)
    for m in range(1, n_mels + 1):
        f_prev, f_curr, f_next = bin_points[m - 1], bin_points[m], bin_points[m + 1]
        # 상승 구간(f_prev -> f_curr)
        if f_curr > f_prev:
            for k in range(f_prev, f_curr + 1):
                fb[m - 1, k] = (k - f_prev) / (f_curr - f_prev)
        # 하강 구간(f_curr -> f_next)
        if f_next > f_curr:
            for k in range(f_curr, f_next + 1):
                fb[m - 1, k] = (f_next - k) / (f_next - f_curr)
    return fb.astype(np.float32)


def _hann_window(n_fft):
    """SensorBridge.kt의 hannWindow와 정확히 같은 공식(0.5*(1-cos(2*pi*n/(N-1))))입니다.
    같은 공식을 써야 Python(학습)과 Kotlin(온디바이스)이 같은 프레임 에너지를 냅니다.
    """
    n = np.arange(n_fft, dtype=np.float64)
    return 0.5 * (1.0 - np.cos(2.0 * np.pi * n / (n_fft - 1)))


def audio_epoch_to_melspec(samples, sample_rate=AUDIO_SAMPLE_RATE, n_mels=N_MELS,
                            n_fft=AUDIO_N_FFT, hop_length=AUDIO_HOP_LENGTH, mel_fb=None):
    """한 epoch(30초)의 raw 오디오 샘플(1차원, mono)을 (n_frames, n_mels) 로그-멜 에너지로 변환합니다.

    STFT를 tf.signal이나 librosa 없이 직접 구현했습니다: 프레임마다 Hann 윈도우를 씌우고
    완전한 복소 FFT(np.fft.fft)를 돌려 Nyquist까지의 전력 스펙트럼을 취한 뒤, 멜 필터뱅크를
    곱합니다. SensorBridge.kt의 computeFrameEnergy()와 같은 순서(윈도우 -> FFT -> 전력 ->
    가중치 행렬 곱)라서, 추후 Kotlin으로 포팅할 때 기존 FFT 인프라를 그대로 재사용할 수 있습니다.

    samples가 짧으면(마지막 epoch 등) 0으로 패딩합니다. mel_fb를 넘기면 매 호출마다
    필터뱅크를 다시 만들지 않아 반복 호출(여러 epoch)에서 빠릅니다.
    """
    samples = np.asarray(samples, dtype=np.float64)
    if mel_fb is None:
        mel_fb = build_mel_filterbank(sample_rate, n_fft, n_mels)

    n = len(samples)
    if n < n_fft:
        samples = np.concatenate([samples, np.zeros(n_fft - n, dtype=np.float64)])
        n = n_fft

    n_frames = 1 + (n - n_fft) // hop_length
    if n_frames < 1:
        n_frames = 1

    window = _hann_window(n_fft)
    out = np.zeros((n_frames, n_mels), dtype=np.float32)
    eps = 1e-10
    for i in range(n_frames):
        start = i * hop_length
        frame = samples[start:start + n_fft]
        if len(frame) < n_fft:
            # 마지막 프레임이 끝에서 모자라면 0으로 패딩합니다(프레임 수를 고정해 shape을 보장).
            frame = np.concatenate([frame, np.zeros(n_fft - len(frame), dtype=np.float64)])
        windowed = frame * window
        spectrum = np.fft.fft(windowed)
        power = np.abs(spectrum[: n_fft // 2 + 1]) ** 2
        mel_energy = mel_fb @ power
        out[i] = np.log(mel_energy + eps).astype(np.float32)
    return out


# ============================================================
# 3. 에포크 특징 시퀀스 (옛 epoch_features.py) — ablation/부가 입력용으로 유지
# ============================================================
MOVEMENT_THRESHOLDS_G = (0.01, 0.05, 0.20)
MOVEMENT_EPOCH_TAT_THRESHOLD = 0.02
DIFF_LAGS = (1, 10, 50, 150)
TREND_LOOKBACKS = (6, 20, 60)
SINCE_MOVEMENT_CAP = 60


def _tat_name(threshold_g):
    return "tat_{:03d}mg".format(int(threshold_g * 1000))


FEATURE_NAMES = [
    "act_mean", "act_std", "act_range",
    "dev_mean", "dev_p50", "dev_p90", "dev_max",
    *[_tat_name(t) for t in MOVEMENT_THRESHOLDS_G],
    "zcm", "bout_count", "bout_longest",
    *["diff_std_lag{}".format(lag) for lag in DIFF_LAGS],
    "tilt_mean", "tilt_max",
    "posture_x", "posture_y", "posture_z", "posture_change",
    *["trend_{}ep".format(lb) for lb in TREND_LOOKBACKS],
    *["var_{}ep".format(lb) for lb in TREND_LOOKBACKS],
    "epochs_since_movement", "act_rel_night", "act_z_night",
    "time_feature",
]
N_FEATURES = len(FEATURE_NAMES)
FEATURE_INDEX = {name: i for i, name in enumerate(FEATURE_NAMES)}


def percentile_linear(sorted_values, q):
    """numpy의 기본 백분위수(method='linear')를 명시적으로 구현합니다(Kotlin 이식용)."""
    n = len(sorted_values)
    if n == 0:
        return 0.0
    if n == 1:
        return float(sorted_values[0])
    idx = (q / 100.0) * (n - 1)
    lo = int(np.floor(idx))
    hi = min(lo + 1, n - 1)
    frac = idx - lo
    return float(sorted_values[lo] + (sorted_values[hi] - sorted_values[lo]) * frac)


def _linear_trend(values):
    if len(values) < 3:
        return 0.0
    v = np.asarray(values, dtype=np.float64)
    t = np.arange(len(v), dtype=np.float64)
    t_centered = t - t.mean()
    denom = float(np.sum(t_centered ** 2))
    if denom <= 0.0:
        return 0.0
    return float(np.sum(t_centered * (v - v.mean())) / denom)


def _bout_stats(above):
    n = len(above)
    if n == 0:
        return 0.0, 0.0
    count = 0
    longest = 0
    current = 0
    prev = False
    for flag in above:
        if flag:
            current += 1
            if not prev:
                count += 1
        else:
            if current > longest:
                longest = current
            current = 0
        prev = bool(flag)
    if current > longest:
        longest = current
    return float(count) / n, float(longest) / n


class CausalFeatureExtractor:
    """녹화(한 밤) 하나에 대해 **과거만 보고** 에포크 특징을 스트리밍으로 계산합니다."""

    def __init__(self):
        self._act_mean_history = []
        self._prev_posture = None
        self._epochs_since_movement = None
        self._n = 0
        self._running_mean = 0.0
        self._running_m2 = 0.0

    def _update_running(self, value):
        self._n += 1
        delta = value - self._running_mean
        self._running_mean += delta / self._n
        self._running_m2 += delta * (value - self._running_mean)

    def _running_std(self):
        if self._n < 2:
            return 0.0
        return float(np.sqrt(self._running_m2 / self._n))

    def update(self, x, y, z, elapsed_sec):
        x = np.asarray(x, dtype=np.float64)
        y = np.asarray(y, dtype=np.float64)
        z = np.asarray(z, dtype=np.float64)
        n = len(x)
        if n == 0:
            return np.zeros(N_FEATURES, dtype=np.float32)

        act = np.sqrt(x ** 2 + y ** 2 + z ** 2)
        act_sorted = np.sort(act)
        act_median = percentile_linear(act_sorted, 50.0)
        dev = np.abs(act - act_median)
        dev_sorted = np.sort(dev)

        act_mean = float(act.mean())
        f = {
            "act_mean": act_mean,
            "act_std": float(act.std()),
            "act_range": float(act_sorted[-1] - act_sorted[0]),
            "dev_mean": float(dev.mean()),
            "dev_p50": percentile_linear(dev_sorted, 50.0),
            "dev_p90": percentile_linear(dev_sorted, 90.0),
            "dev_max": float(dev_sorted[-1]),
        }

        for t in MOVEMENT_THRESHOLDS_G:
            f[_tat_name(t)] = float((dev > t).mean())

        centered = act - act_median
        if n >= 2:
            crossings = int(np.sum(np.signbit(centered[:-1]) != np.signbit(centered[1:])))
            f["zcm"] = crossings / (n - 1)
        else:
            f["zcm"] = 0.0

        bout_count, bout_longest = _bout_stats(dev > MOVEMENT_THRESHOLDS_G[1])
        f["bout_count"] = bout_count
        f["bout_longest"] = bout_longest

        for lag in DIFF_LAGS:
            f["diff_std_lag{}".format(lag)] = (
                float(np.std(act[lag:] - act[:-lag])) if n > lag else 0.0
            )

        tilt = np.abs(x - x.mean()) + np.abs(y - y.mean()) + np.abs(z - z.mean())
        f["tilt_mean"] = float(tilt.mean())
        f["tilt_max"] = float(tilt.max())

        posture = np.array([x.mean(), y.mean(), z.mean()], dtype=np.float64)
        f["posture_x"] = float(posture[0])
        f["posture_y"] = float(posture[1])
        f["posture_z"] = float(posture[2])

        if self._prev_posture is None:
            f["posture_change"] = 0.0
        else:
            a_norm = float(np.sqrt(np.sum(posture ** 2)))
            b_norm = float(np.sqrt(np.sum(self._prev_posture ** 2)))
            if a_norm < 1e-9 or b_norm < 1e-9:
                f["posture_change"] = 0.0
            else:
                cos = float(np.dot(posture, self._prev_posture) / (a_norm * b_norm))
                f["posture_change"] = float(np.arccos(min(max(cos, -1.0), 1.0)))

        for lb in TREND_LOOKBACKS:
            window = self._act_mean_history[-lb:] + [act_mean]
            f["trend_{}ep".format(lb)] = _linear_trend(window)
            f["var_{}ep".format(lb)] = float(np.std(window)) if len(window) >= 3 else 0.0

        is_movement_epoch = f[_tat_name(MOVEMENT_THRESHOLDS_G[1])] > MOVEMENT_EPOCH_TAT_THRESHOLD
        if is_movement_epoch:
            self._epochs_since_movement = 0
        elif self._epochs_since_movement is not None:
            self._epochs_since_movement += 1
        since = SINCE_MOVEMENT_CAP if self._epochs_since_movement is None else self._epochs_since_movement
        f["epochs_since_movement"] = min(since, SINCE_MOVEMENT_CAP) / SINCE_MOVEMENT_CAP

        self._update_running(act_mean)
        f["act_rel_night"] = act_mean / self._running_mean if self._running_mean > 1e-9 else 1.0
        running_std = self._running_std()
        f["act_z_night"] = (act_mean - self._running_mean) / running_std if running_std > 1e-9 else 0.0

        f["time_feature"] = causal_time_feature(elapsed_sec)

        self._act_mean_history.append(act_mean)
        self._prev_posture = posture

        return np.array([f[name] for name in FEATURE_NAMES], dtype=np.float32)


def features_from_raw_epochs(X, elapsed_sec_per_epoch=None):
    """원시 에포크 배열 X: (n_epochs, WINDOW, >=3)에서 특징 행렬 (n_epochs, N_FEATURES)을 만듭니다."""
    X = np.asarray(X)
    n_epochs = len(X)
    if n_epochs == 0:
        return np.zeros((0, N_FEATURES), dtype=np.float32)

    if elapsed_sec_per_epoch is None:
        if X.shape[-1] > 6:
            elapsed_sec_per_epoch = X[:, 0, 6].astype(np.float64) * TIME_FEATURE_SPAN_SEC
        else:
            elapsed_sec_per_epoch = np.arange(n_epochs, dtype=np.float64) * EPOCH_SEC

    extractor = CausalFeatureExtractor()
    out = np.zeros((n_epochs, N_FEATURES), dtype=np.float32)
    for i in range(n_epochs):
        epoch = np.asarray(X[i])
        out[i] = extractor.update(epoch[:, 0], epoch[:, 1], epoch[:, 2], elapsed_sec_per_epoch[i])
    return out


# ============================================================
# 4. 특징/윈도우 데이터 준비 (옛 feature_dataset.py의 비-TF 부분)
# ============================================================
_FEATURE_CACHE = {}


def list_feature_pairs(directories):
    """[(dir, sid), ...] — F_*.npy와 y_*.npy가 모두 있는 subject만 모읍니다."""
    pairs = []
    for d in directories:
        if not d.exists():
            continue
        for f in sorted(d.glob("F_*.npy")):
            sid = f.name[len("F_"):-len(".npy")]
            if (d / f"y_{sid}.npy").exists():
                pairs.append((d, sid))
    return pairs


def load_feature_pairs(pairs, cache=None):
    cache = _FEATURE_CACHE if cache is None else cache
    for d, sid in pairs:
        key = (str(d), sid)
        if key in cache:
            continue
        F = np.load(d / f"F_{sid}.npy").astype(np.float32)
        y = np.load(d / f"y_{sid}.npy").astype(np.int32)
        n = min(len(F), len(y))
        if n == 0:
            continue
        if len(F) != len(y):
            print(f"  경고: {sid} — F {len(F)} != y {len(y)}, 짧은 쪽({n})에 맞춥니다.")
        cache[key] = (F[:n], y[:n])
    return cache


def get_feature_pair(pair, cache=None):
    cache = _FEATURE_CACHE if cache is None else cache
    return cache.get((str(pair[0]), pair[1]))


def compute_feature_stats(pairs, cache=None):
    """특징별 mean/std. **학습 fold에서만** 호출해야 합니다(테스트셋 누수 방지)."""
    n_total = 0
    mean = np.zeros(N_FEATURES, dtype=np.float64)
    m2 = np.zeros(N_FEATURES, dtype=np.float64)
    for p in pairs:
        cached = get_feature_pair(p, cache)
        if not cached:
            continue
        F = cached[0].astype(np.float64)
        n_b = len(F)
        if n_b == 0:
            continue
        mean_b = F.mean(axis=0)
        m2_b = F.var(axis=0) * n_b
        n_new = n_total + n_b
        delta = mean_b - mean
        mean += delta * (n_b / n_new)
        m2 += m2_b + delta ** 2 * (n_total * n_b / n_new)
        n_total = n_new
    if n_total == 0:
        raise ValueError("학습 fold에 특징 샘플이 없습니다 — build_features를 먼저 실행하세요.")
    std = np.sqrt(m2 / n_total)
    std = np.where(std < 1e-6, 1.0, std).astype(np.float32)
    return mean.astype(np.float32), std


def class_counts(pairs, cache=None):
    counts = np.zeros(N_CLASSES, dtype=np.int64)
    for p in pairs:
        cached = get_feature_pair(p, cache)
        if not cached:
            continue
        u, c = np.unique(cached[1], return_counts=True)
        for uu, cc in zip(u, c):
            if 0 <= uu < N_CLASSES:
                counts[uu] += cc
    return counts


def class_weights(counts, mode="sqrt_inverse", min_w=0.5, max_w=4.0):
    if mode == "none":
        return np.ones(N_CLASSES, dtype=np.float32)
    total = counts.sum()
    if total == 0:
        return np.ones(N_CLASSES, dtype=np.float32)
    if mode == "sqrt_inverse":
        w = total / (N_CLASSES * (counts.astype(np.float64) ** 0.5) + 1e-6)
    elif mode == "inverse":
        w = total / (N_CLASSES * counts.astype(np.float64) + 1e-6)
    else:
        raise ValueError(f"알 수 없는 class_weight 모드: {mode}")
    w = np.clip(w / w.mean(), min_w, max_w)
    return w.astype(np.float32)


def transition_matrix(pairs, laplace=1.0, cache=None):
    """학습 fold 라벨 시퀀스에서 경험적 상태전이 확률(Viterbi/CRF 초기값용). 테스트셋 라벨은 보지 않습니다."""
    counts = np.full((N_CLASSES, N_CLASSES), laplace, dtype=np.float64)
    for p in pairs:
        cached = get_feature_pair(p, cache)
        if not cached:
            continue
        y = cached[1]
        for a, b in zip(y[:-1], y[1:]):
            if 0 <= a < N_CLASSES and 0 <= b < N_CLASSES:
                counts[a, b] += 1
    return counts / counts.sum(axis=1, keepdims=True)


def window_starts(n_epochs, context_len, stride):
    """학습 윈도우 시작점. 녹화 꼬리가 빠지지 않도록 마지막 윈도우를 끝에 붙입니다."""
    if n_epochs <= 0:
        return []
    if n_epochs <= context_len:
        return [0]
    starts = list(range(0, n_epochs - context_len + 1, stride))
    if starts[-1] + context_len < n_epochs:
        starts.append(n_epochs - context_len)
    return starts


def pad_window(F, y, start, context_len):
    """(특징, 라벨, 손실 마스크)를 반환합니다. 패딩 구간의 마스크는 0입니다(손실에서 제외)."""
    seg_f = np.asarray(F[start: start + context_len])
    seg_y = np.asarray(y[start: start + context_len])
    pad = context_len - len(seg_f)
    if pad <= 0:
        return seg_f, seg_y, np.ones(context_len, dtype=np.float32)
    seg_f = np.concatenate([np.repeat(seg_f[0:1], pad, axis=0), seg_f], axis=0)
    seg_y = np.concatenate([np.full(pad, seg_y[0], dtype=np.int32), seg_y], axis=0)
    mask = np.concatenate(
        [np.zeros(pad, dtype=np.float32), np.ones(context_len - pad, dtype=np.float32)]
    )
    return seg_f, seg_y, mask


def count_windows(pairs, context_len, stride, cache=None):
    total = 0
    for p in pairs:
        cached = get_feature_pair(p, cache)
        if cached:
            total += len(window_starts(len(cached[0]), context_len, stride))
    return total


# ============================================================
# 5. 원시 에포크(X_*.npy) -> 특징(F_*.npy) 빌드 (옛 build_features.py)
# ============================================================
def build_features_for_directory(directory: Path, force=False):
    if not directory.exists():
        print(f"건너뜀: {directory} 없음")
        return 0, 0

    built, skipped = 0, 0
    for x_path in sorted(directory.glob("X_*.npy")):
        sid = x_path.name[len("X_"):-len(".npy")]
        y_path = directory / f"y_{sid}.npy"
        f_path = directory / f"F_{sid}.npy"

        if not y_path.exists():
            print(f"  경고: {sid} — y_{sid}.npy 없음. 전처리를 먼저 완료하세요.")
            skipped += 1
            continue
        if f_path.exists() and not force:
            skipped += 1
            continue

        X = np.load(x_path, mmap_mode="r")
        if X.ndim != 3 or X.shape[-1] != N_CHANNELS:
            print(f"  경고: {sid} — 채널 수 {X.shape[-1] if X.ndim == 3 else '?'} != {N_CHANNELS}. "
                  f"구버전 전처리 산출물이므로 건너뜁니다(build_dataset_hybrid.py로 재생성 필요).")
            skipped += 1
            continue

        F = features_from_raw_epochs(X)
        if not np.isfinite(F).all():
            bad = [FEATURE_NAMES[i] for i in np.where(~np.isfinite(F).all(axis=0))[0]]
            print(f"  경고: {sid} — 유한하지 않은 특징이 있습니다: {bad}")
        np.save(f_path, F)
        built += 1
        print(f"  {sid}: {X.shape} -> {F.shape}")

    return built, skipped


def run_build_features(force=False):
    accel_dirs = [BID_SUBJECTS, SA_SUBJECTS]
    print(f"특징 {N_FEATURES}개: {', '.join(FEATURE_NAMES)}\n")
    total_built, total_skipped = 0, 0
    for directory in accel_dirs:
        print(f"[{directory.parent.name}] {directory}")
        built, skipped = build_features_for_directory(directory, force=force)
        total_built += built
        total_skipped += skipped
    print(f"\n생성 {total_built}개 / 건너뜀 {total_skipped}개")
    if total_built == 0:
        print("새로 만든 것이 없습니다. 전처리(build_dataset_hybrid.py)를 먼저 완료했는지, "
              "또는 --force가 필요한지 확인하세요.")


# ============================================================
# 6. 정규화 통계 + Kotlin parity (옛 compute_stats.py, 오디오 도메인 확장)
# ============================================================
def to_kotlin_array(name, arr):
    return f"actual val {name} = floatArrayOf(" + ", ".join(f"{x:.6f}f" for x in arr) + ")"


def compute_domain_stats_multi(directories, n_channels, channel_names, save_path, glob_pattern="X_*.npy"):
    """여러 디렉토리의 X_*.npy(또는 glob_pattern)를 한 모집단으로 보고 Welford 병합으로 mean/std를 계산합니다."""
    x_files = []
    for directory in directories:
        x_files.extend(sorted(directory.glob(glob_pattern)))

    if not x_files:
        print(f"경고: {[str(d) for d in directories]}에 분석할 파일이 없습니다.")
        return None

    n_total = 0
    mean = np.zeros(n_channels, dtype=np.float64)
    M2 = np.zeros(n_channels, dtype=np.float64)
    skipped = []

    for f in x_files:
        X = np.load(f, mmap_mode="r")
        if X.shape[-1] != n_channels:
            skipped.append((f.name, X.shape[-1]))
            continue
        flat = np.asarray(X).reshape(-1, n_channels).astype(np.float64)
        del X
        n_b = flat.shape[0]
        if n_b == 0:
            continue
        mean_b = flat.mean(axis=0)
        M2_b = flat.var(axis=0) * n_b
        n_new = n_total + n_b
        delta = mean_b - mean
        mean = mean + delta * (n_b / n_new)
        M2 = M2 + M2_b + delta ** 2 * (n_total * n_b / n_new)
        n_total = n_new
        del flat

    if skipped:
        print(f"⚠️ 채널 수가 {n_channels}가 아니어서 제외한 파일 {len(skipped)}개: {skipped[:5]}")
    if n_total == 0:
        raise ValueError(f"{[str(d) for d in directories]}: 통계 계산할 샘플 없음")

    std = np.sqrt(M2 / n_total).astype(np.float32)
    std = np.where(std < 1e-6, 1.0, std)
    stats = {"mean": mean.astype(np.float32), "std": std, "channel_names": channel_names}
    save_path.parent.mkdir(parents=True, exist_ok=True)
    np.save(save_path, stats)
    print(f"성공: {save_path} 저장됨 (n={n_total:,} samples, files={len(x_files) - len(skipped)})")
    return stats


def compute_audio_domain_stats(directories, n_mels=N_MELS, save_path=AUDIO_NORM_PATH):
    """오디오 도메인(멜 빈별) 정규화 통계. A_*.npy(원시 오디오 샘플, (n_epochs, AUDIO_EPOCH_SAMPLES))를
    멜스펙토그램으로 변환한 뒤 멜 빈별 mean/std를 계산합니다.

    💡 APSAA/AI-Hub(비지도, 라벨 없음)의 오디오로부터 오디오 인코더 입력 정규화 기준을
    만듭니다 — 이 계산 자체는 라벨이 전혀 필요 없으므로(PSG-Audio가 빠져도 그대로 동작).
    accel과 마찬가지로, 온디바이스(Kotlin)도 같은 통계를 상수로 가져야 학습/서빙이 일치합니다
    (Phase 8 — 아직 Kotlin 쪽 AUDIO_MEL_MEAN/STD 상수는 없으므로 지금은 .npy 저장까지만 합니다).
    """
    a_files = []
    for directory in directories:
        if directory.exists():
            a_files.extend(sorted(directory.glob("A_*.npy")))
    if not a_files:
        print(f"경고: {[str(d) for d in directories]}에 오디오(A_*.npy) 파일이 없습니다.")
        return None

    mel_fb = build_mel_filterbank(AUDIO_SAMPLE_RATE, AUDIO_N_FFT, n_mels)
    n_total = 0
    mean = np.zeros(n_mels, dtype=np.float64)
    M2 = np.zeros(n_mels, dtype=np.float64)

    for f in a_files:
        A = np.load(f, mmap_mode="r")
        for i in range(len(A)):
            mel = audio_epoch_to_melspec(np.asarray(A[i]), mel_fb=mel_fb).astype(np.float64)
            n_b = mel.shape[0]
            if n_b == 0:
                continue
            mean_b = mel.mean(axis=0)
            M2_b = mel.var(axis=0) * n_b
            n_new = n_total + n_b
            delta = mean_b - mean
            mean = mean + delta * (n_b / n_new)
            M2 = M2 + M2_b + delta ** 2 * (n_total * n_b / n_new)
            n_total = n_new

    if n_total == 0:
        raise ValueError("오디오 통계 계산할 샘플 없음")
    std = np.sqrt(M2 / n_total).astype(np.float32)
    std = np.where(std < 1e-6, 1.0, std)
    stats = {"mean": mean.astype(np.float32), "std": std, "n_mels": n_mels}
    save_path.parent.mkdir(parents=True, exist_ok=True)
    np.save(save_path, stats)
    print(f"성공: {save_path} 저장됨 (멜 프레임 n={n_total:,}, 파일={len(a_files)})")
    return stats


def parse_kotlin_array(source, name):
    """SleepStageClassifier.kt에서 `actual val <name> = floatArrayOf(...)` 값을 읽습니다."""
    m = re.search(rf"val\s+{re.escape(name)}\s*=\s*floatArrayOf\(([^)]*)\)", source)
    if not m:
        return None
    return np.array([float(v.strip().rstrip("fF")) for v in m.group(1).split(",") if v.strip()],
                     dtype=np.float32)


def check_kotlin_parity(stats):
    """앱의 ACCEL_CHANNEL_MEAN/STD 상수가 방금 계산한 통계와 같은지 검사합니다.

    AUDIO_MEL_MEAN/STD는 아직 Kotlin 쪽에 상수가 없으므로(Phase 8, 온디바이스 오디오 추론은
    향후 작업) 이 함수는 accel 상수만 검사합니다 — 오디오 통계 자체는
    compute_audio_domain_stats()가 .npy로 저장해 두므로, 그 상수가 추가되면 이 함수를
    AUDIO_MEL_MEAN/STD 검사로 확장하면 됩니다.
    """
    if not CLASSIFIER_KT.exists():
        print(f"\n⚠️ Kotlin 파일을 찾지 못해 비교를 건너뜁니다: {CLASSIFIER_KT}")
        return False

    source = CLASSIFIER_KT.read_text(encoding="utf-8")
    ok = True
    for name, expected in (("ACCEL_CHANNEL_MEAN", stats["mean"]), ("ACCEL_CHANNEL_STD", stats["std"])):
        actual = parse_kotlin_array(source, name)
        if actual is None:
            print(f"\n❌ {name}을(를) {CLASSIFIER_KT.name}에서 찾지 못했습니다.")
            ok = False
        elif len(actual) != len(expected):
            print(f"\n❌ {name} 길이 불일치: 앱 {len(actual)}개 != 계산값 {len(expected)}개")
            ok = False
        elif not np.allclose(actual, expected, atol=1e-6):
            worst = int(np.argmax(np.abs(actual - expected)))
            print(f"\n❌ {name} 값 불일치. 가장 큰 차이: 채널 {worst} "
                  f"({ACCEL_CHANNEL_NAMES[worst] if worst < len(ACCEL_CHANNEL_NAMES) else '?'}) "
                  f"앱 {actual[worst]:.6f} != 계산값 {expected[worst]:.6f}")
            ok = False
        else:
            print(f"✅ {name}: 앱 상수가 계산값과 일치")

    if not ok:
        print(f"\n→ {CLASSIFIER_KT.name}의 두 줄을 아래로 교체하세요:\n")
        print("        " + to_kotlin_array("ACCEL_CHANNEL_MEAN", stats["mean"]))
        print("        " + to_kotlin_array("ACCEL_CHANNEL_STD", stats["std"]))
    return ok


def run_compute_stats():
    print(f"채널 수 {N_CHANNELS} / Accel 채널: {ACCEL_CHANNEL_NAMES}")

    compute_domain_stats_multi([EDF_SUBJECTS], N_CHANNELS, PSG_CHANNEL_NAMES, EDF_NORM_PATH)

    accel_stats = compute_domain_stats_multi(
        [BID_SUBJECTS, SA_SUBJECTS], N_CHANNELS, ACCEL_CHANNEL_NAMES, ACCEL_NORM_PATH
    )

    if APSAA_SUBJECTS.exists() or AIHUB_SUBJECTS.exists():
        compute_audio_domain_stats([APSAA_SUBJECTS, AIHUB_SUBJECTS])
    else:
        print(f"\n건너뜀: {APSAA_SUBJECTS} / {AIHUB_SUBJECTS} 모두 없음 (오디오 전처리 미완료)")

    edf_stats = np.load(EDF_NORM_PATH, allow_pickle=True).item() if EDF_NORM_PATH.exists() else None
    if edf_stats is not None:
        print("\n// Sleep-EDF Expanded (연구용 — 앱 미사용)")
        print(to_kotlin_array("EDF_CHANNEL_MEAN", edf_stats["mean"]))
        print(to_kotlin_array("EDF_CHANNEL_STD", edf_stats["std"]))
    else:
        print(f"\n건너뜀: {EDF_NORM_PATH} 없음")

    if accel_stats is None:
        print("\n건너뜀: Accel 통계를 계산할 .npy가 없습니다 — build_dataset_hybrid.py를 먼저 실행하세요.")
        return

    print("\n// Accel Domain (BIDSleep + Sleep-Accel 통합 — 학습과 앱이 공유하는 유일한 기준)")
    print(to_kotlin_array("ACCEL_CHANNEL_MEAN", accel_stats["mean"]))
    print(to_kotlin_array("ACCEL_CHANNEL_STD", accel_stats["std"]))

    print("\n=== 앱 상수 일치 검사 ===")
    check_kotlin_parity(accel_stats)


# ============================================================
# 7. Kotlin parity fixture 생성 (옛 export_parity_fixture.py, 멜 fixture 추가)
# ============================================================
def make_raw_epochs(n_epochs=6, samples_per_epoch=16, seed=12345):
    """재현 가능한 합성 가속도 입력. 에포크마다 활동량 수준을 바꿔 추세/변동성이 0이 아니게 만듭니다."""
    rng = np.random.default_rng(seed)
    epochs = []
    for e in range(n_epochs):
        amp = 0.02 + 0.1 * e
        base = np.array([0.03, -0.05, -0.98], dtype=np.float64)
        noise = rng.normal(0.0, amp, size=(samples_per_epoch, 3))
        epochs.append((base + noise).astype(np.float32))
    return epochs


def make_raw_audio_epoch(n_samples=800, freq_hz=200.0, sample_rate=AUDIO_SAMPLE_RATE, seed=777):
    """재현 가능한 합성 오디오(사인파 + 약한 노이즈). 멜스펙토그램 parity fixture용 — 실제
    audio_epoch_to_melspec()을 그대로 돌려 기댓값을 만들므로, 실제 길이(30초 전체)가 아니라
    프레임 1~2개만 나오는 짧은 길이로 충분합니다(Kotlin 테스트가 빨라집니다).
    """
    rng = np.random.default_rng(seed)
    t = np.arange(n_samples, dtype=np.float64) / sample_rate
    tone = 0.5 * np.sin(2.0 * np.pi * freq_hz * t)
    noise = rng.normal(0.0, 0.01, size=n_samples)
    return (tone + noise).astype(np.float32)


def _fmt(v):
    return f"{float(v):.9f}f"


def run_export_parity_fixture():
    from collections import deque

    raw_epochs = make_raw_epochs()
    recent = deque(maxlen=LOOKBACK_EPOCHS)

    raw_lines, expected_lines, scalar_lines = [], [], []
    for e, samples in enumerate(raw_epochs):
        x, y, z = samples[:, 0], samples[:, 1], samples[:, 2]
        activity_mean = float(activity_magnitude(x, y, z).mean())
        trend, variability = compute_causal_trend_variability(recent, activity_mean)
        recent.append(activity_mean)
        tf_val = causal_time_feature(e * EPOCH_SEC)

        channels = build_epoch_channels(x, y, z, variability, trend, tf_val)
        assert channels.shape == (len(samples), N_CHANNELS), channels.shape

        raw_lines.append(
            "        listOf(\n"
            + "".join(
                f"            floatArrayOf({_fmt(sx)}, {_fmt(sy)}, {_fmt(sz)}),\n"
                for sx, sy, sz in samples
            )
            + "        ),"
        )
        expected_lines.append(
            "        listOf(\n"
            + "".join(
                "            floatArrayOf(" + ", ".join(_fmt(v) for v in row) + "),\n"
                for row in channels
            )
            + "        ),"
        )
        scalar_lines.append(
            f"        EpochScalars({_fmt(activity_mean)}, {_fmt(variability)}, "
            f"{_fmt(trend)}, {_fmt(tf_val)}),"
        )

    # 멜스펙토그램 fixture: 짧은 합성 오디오 1개에 대한 (n_frames, n_mels) 기댓값.
    audio_samples = make_raw_audio_epoch()
    mel = audio_epoch_to_melspec(audio_samples)
    mel_sample_lines = ", ".join(_fmt(v) for v in audio_samples)
    mel_frame_lines = "\n".join(
        "        floatArrayOf(" + ", ".join(_fmt(v) for v in frame) + "),"
        for frame in mel
    )

    body = f'''package com.sleepytime.shared.util

/**
 * 자동 생성 파일 — 직접 수정하지 마세요.
 *
 * `ml/script/preprocess.py`(run_export_parity_fixture)가 학습 쪽 전처리를 실행해 만든
 * 기댓값입니다. 채널 순서: {", ".join(ACCEL_CHANNEL_NAMES)}
 *
 * 멜스펙토그램 fixture는 신규입니다(오디오 인코더 입력). Kotlin 쪽 멜 계산이 아직 구현되지
 * 않았다면(Phase 8), rawAudioSamples -> expectedMelFrames 매핑만 기록해 두고 추후 구현 시
 * 바로 비교할 수 있게 합니다.
 */
object ParityFixture {{

    const val SAMPLES_PER_EPOCH = {len(raw_epochs[0])}
    const val N_EPOCHS = {len(raw_epochs)}

    data class EpochScalars(
        val activityMean: Float,
        val variability: Float,
        val trend: Float,
        val timeFeature: Float,
    )

    val rawEpochs: List<List<FloatArray>> = listOf(
{chr(10).join(raw_lines)}
    )

    val expectedChannels: List<List<FloatArray>> = listOf(
{chr(10).join(expected_lines)}
    )

    val expectedScalars: List<EpochScalars> = listOf(
{chr(10).join(scalar_lines)}
    )

    // ── 오디오 멜스펙토그램 fixture (신규) ──
    const val AUDIO_SAMPLE_RATE = {AUDIO_SAMPLE_RATE}
    const val AUDIO_N_FFT = {AUDIO_N_FFT}
    const val AUDIO_HOP_LENGTH = {AUDIO_HOP_LENGTH}
    const val AUDIO_N_MELS = {N_MELS}

    val rawAudioSamples: FloatArray = floatArrayOf({mel_sample_lines})

    /** rawAudioSamples를 audio_epoch_to_melspec()에 넣었을 때의 기댓값. (frame, mel). */
    val expectedMelFrames: List<FloatArray> = listOf(
{mel_frame_lines}
    )
}}
'''
    PARITY_FIXTURE_OUT.parent.mkdir(parents=True, exist_ok=True)
    PARITY_FIXTURE_OUT.write_text(body, encoding="utf-8")
    print(f"생성: {PARITY_FIXTURE_OUT}")
    print(f"  에포크 {len(raw_epochs)}개 × 채널 {N_CHANNELS}개, 멜스펙토그램 프레임 {mel.shape[0]}개 × 멜 {mel.shape[1]}개")


# ============================================================
# 8. CLI
# ============================================================
def main():
    p = argparse.ArgumentParser(description="전처리 공용 유틸리티")
    sub = p.add_subparsers(dest="cmd", required=True)

    sub.add_parser("build-features", help="X_*.npy -> F_*.npy").add_argument(
        "--force", action="store_true", help="이미 있는 F_*.npy도 다시 생성"
    )
    sub.add_parser("stats", help="정규화 통계 계산 + Kotlin parity 검사")
    sub.add_parser("fixture", help="Kotlin parity fixture(ParityFixture.kt) 생성")

    args = p.parse_args()
    if args.cmd == "build-features":
        run_build_features(force=args.force)
    elif args.cmd == "stats":
        run_compute_stats()
    elif args.cmd == "fixture":
        run_export_parity_fixture()


if __name__ == "__main__":
    main()
