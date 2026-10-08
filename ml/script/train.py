"""멀티모달 수면 단계 분류 모델 학습 — 분리 인코더 + late fusion 전략.

⚠️ TensorFlow가 필요합니다(이 환경에는 없어 실행 검증 불가 — Colab에서 실행). CRF의 핵심 수학
(forward 알고리즘/Viterbi)은 이 세션에서 numpy로 브루트포스 대조 검증을 마쳤습니다
(test_model.py 참고). 그 외 TF 그래프 구성/학습 루프 자체의 동작은 Colab에서 처음 실행될 때
확인이 필요합니다.

## 왜 "분리 인코더 + late fusion"인가

사용하는 데이터셋은 가속도(기존 3개 소스)·MESA·APSAA·AI-Hub·WISDM다. **PSG-Audio는
데이터셋에서 완전히 제외했다** — 즉 "오디오+실제 수면단계 라벨"이 동시에 있는 데이터가
전혀 없다. 그래서(사용자가 직접 확정한 전략):
  - 가속도 인코더는 기존 3개 accel 소스(가속도+라벨 확실)로만 지도학습합니다 → `--stage accel_core`
  - MESA(가속도만, 라벨 확실하나 NSRR 승인 필요)는 가속도 인코더와는 별개의 진단용 자기지도
    (sleep/wake) 실험으로만 씁니다 → `--stage mesa_selfsup`
  - WISDM(가속도만, 라벨 없음, 공개)은 accel_epoch_encoder 자체의 자기지도(디노이징) 사전학습
    으로만 씁니다 → `--stage accel_selfsup_wisdm`
  - APSAA/AI-Hub(오디오만, 라벨 없음)는 오디오 인코더가 일상 소음에 강건해지도록 돕는
    디노이징 사전학습으로만 씁니다(오디오 인코더의 **유일한** 학습 경로) → `--stage audio_denoise_aux`
  - accel_core로 학습된 가속도 인코더와 audio_denoise_aux로 사전학습된 오디오 인코더를 모아,
    Late Fusion 헤드만 "동기화가 보장된 소량 샘플"로 미세조정합니다 → `--stage fusion_finetune`
    (이 데이터는 사용자가 추후 직접 수집해야 합니다 — 지금은 자리만 마련해 둡니다). 오디오
    인코더가 실제 수면단계 라벨을 처음이자 유일하게 보는 지점이 바로 여기입니다.

각 스테이지는 그 스테이지에 필요한 데이터 폴더가 없으면(지금 이 저장소의 상태) 명확한 안내
메시지를 출력하고 조용히 종료합니다 — build_dataset_hybrid.py가 원본 폴더 부재를 처리하는
방식과 동일합니다.
"""
import argparse
import json
from datetime import datetime
from pathlib import Path

import numpy as np
import tensorflow as tf
from scipy.spatial.transform import Rotation

import model as model_module
from evaluate import evaluate_subjects
from preprocess import (
    AUDIO_SAMPLE_RATE,
    N_CLASSES,
    N_MEL_FRAMES,
    N_MELS,
    audio_epoch_to_melspec,
    build_mel_filterbank,
    time_feature_bucket,
)
from splits import SPLIT_SEED, list_subject_pairs, pairs_to_by_dir, split_subject_pairs

tf.keras.mixed_precision.set_global_policy("mixed_bfloat16")

# ============================================================
# 0. 경로 / 공용 하이퍼파라미터
# ============================================================
BASE_DIR = Path(__file__).resolve().parent.parent
OUTPUT_DIR = BASE_DIR / "output"
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

BID_DIR = BASE_DIR / "data" / "bidsleep" / "subjects"
ACCEL_DIR = BASE_DIR / "data" / "sleep_accel" / "subjects"
# MESA는 NSRR 승인이 필요한 gated 데이터셋이라 아직 이 저장소에 없습니다 — 승인 후 같은
# 패턴(X_*.npy 또는 A_*.npy)으로 받아오면 이 경로만 채워집니다.
MESA_DIR = BASE_DIR / "data" / "mesa" / "subjects"
# APSAA/AI-Hub도 아직 없습니다. 둘 다 오디오 노이즈 강건성 보조 학습용이라 "오디오 파일 더미"면
# 충분하므로 같은 폴더에 합쳐서 둡니다.
AUDIO_AUX_DIRS = [
    BASE_DIR / "data" / "apsaa" / "subjects",
    BASE_DIR / "data" / "aihub_selfsleep" / "subjects",
]
# fusion_finetune용 소량 동기화 샘플(사용자가 직접 수집해야 함 — 폰 마이크+가속도 동시 녹음).
FUSION_SYNC_DIR = BASE_DIR / "data" / "fusion_sync" / "subjects"
# 개선 6: WISDM(공개, 신청 불필요) — accel_epoch_encoder 자기지도(디노이징) 사전학습 전용.
WISDM_DIR = BASE_DIR / "data" / "wisdm" / "subjects"

ACCEL_NORM_STATS_PATH = BASE_DIR / "data" / "accel_domain" / "norm_stats.npy"

ACCEL_WINDOW, ACCEL_CHANNELS = 1500, 7
CONTEXT_LEN = 60  # accel/audio가 ctx_body를 공유하므로 두 도메인이 같은 컨텍스트 길이를 써야 함
ACCEL_STRIDE = 3
N_TIME_BUCKETS = 8  # 시간대 FiLM 버킷 수(약 1시간 단위, 8시간 기준)

BATCH_SIZE = 32
STEPS_PER_EPOCH = 300
VAL_STEPS = 40
EPOCHS = 40
PATIENCE = 20

FOCAL_GAMMA = 1.0
LABEL_SMOOTHING = 0.1
LAMBDA_CRF = 1.0  # focal loss 대비 CRF NLL 가중치. Colab에서 --lambda-crf 0 vs >0 ablation 권장.
AUX_TRANSITION_LOSS_WEIGHT = 0.3  # 개선 5: 메인 분류 손실 대비 전이탐지 보조 손실 가중치.

CLASS_NAMES = ["Wake", "Light", "Deep", "REM"]
EVAL_BATCH_SIZE = 8
EVAL_OVERLAP_STRIDE = None

_MEM_CACHE = {}


# ============================================================
# 1. 손실 / 스케줄러 (기존 그대로 포팅)
# ============================================================
class WarmupCosineDecay(tf.keras.optimizers.schedules.LearningRateSchedule):
    """선형 워밍업 뒤 코사인 감쇠하는 학습률 스케줄(기존 train.py/train_features.py와 동일)."""

    def __init__(self, peak_lr, warmup_steps, decay_steps, alpha=0.01):
        super().__init__()
        self.peak_lr = peak_lr
        self.warmup_steps = warmup_steps
        self.cosine = tf.keras.optimizers.schedules.CosineDecay(
            initial_learning_rate=peak_lr, decay_steps=decay_steps, alpha=alpha
        )

    def __call__(self, step):
        step = tf.cast(step, tf.float32)
        warmup_lr = self.peak_lr * (step / tf.maximum(1.0, tf.cast(self.warmup_steps, tf.float32)))
        return tf.where(step < self.warmup_steps, warmup_lr, self.cosine(step - self.warmup_steps))

    def get_config(self):
        return {"peak_lr": self.peak_lr, "warmup_steps": self.warmup_steps}


def weighted_focal_loss(gamma=FOCAL_GAMMA, label_smoothing=LABEL_SMOOTHING):
    """클래스 불균형에 대응하는 focal loss(라벨 스무딩 포함). y_pred는 logits(from_logits=True)."""
    def loss_fn(y_true, y_pred):
        y_true = tf.cast(y_true, tf.int32)
        y_pred = tf.cast(y_pred, tf.float32)
        one_hot_y = tf.one_hot(y_true, depth=N_CLASSES)
        one_hot_y = one_hot_y * (1.0 - label_smoothing) + (label_smoothing / N_CLASSES)
        ce = tf.keras.losses.categorical_crossentropy(one_hot_y, y_pred, from_logits=True)
        probs = tf.nn.softmax(y_pred, axis=-1)
        pt = tf.reduce_sum(one_hot_y * probs, axis=-1)
        focal_weight = tf.pow(1.0 - pt, gamma)
        return focal_weight * ce
    return loss_fn


def make_crf_combined_loss(crf_layer, gamma=FOCAL_GAMMA, label_smoothing=LABEL_SMOOTHING,
                            lambda_crf=LAMBDA_CRF):
    """focal loss + CRF 음의 로그가능도를 더한 손실.

    CRF NLL은 시퀀스 하나당 스칼라 (B,)인데 focal loss는 (B,T)입니다. nll을 T번 복제해
    (B,T)로 브로드캐스트하면, Keras가 시간축에 대해 평균을 낼 때 결과적으로
    mean_t(focal_t) + lambda_crf * nll 이 되어(원하는 대로) nll이 T로 희석되지 않습니다.
    """
    focal = weighted_focal_loss(gamma, label_smoothing)

    def loss_fn(y_true, y_pred):
        fl = focal(y_true, y_pred)  # (B, T)
        nll = crf_layer.neg_log_likelihood(y_pred, y_true)  # (B,)
        t_len = tf.shape(fl)[1]
        nll_broadcast = tf.tile(tf.expand_dims(nll, axis=1), [1, t_len])
        return fl + lambda_crf * nll_broadcast

    return loss_fn


# 개선 4: 문헌에서 잘 알려진 수면 구조 수준의 대략적인 전이 사전확률 — 자기전이(같은 단계
# 유지)가 가장 흔하고, Deep<->REM처럼 생리학적으로 급격한 전환은 드물다는 일반적인 지식을
# 반영한 근사치입니다. 순서는 CLASS_NAMES(Wake, Light, Deep, REM)와 같습니다.
# ⚠️ 이 프로젝트 자체 데이터로 검증된 값이 아니라 참고용 근사 사전(prior)입니다 — subject
# 수가 늘어날수록 아래 compute_transition_matrix의 경험적 카운트가 이 prior를 자연히
# 압도하므로, 이 값의 정밀도는 subject 수가 적을 때(지금 이 프로젝트의 상황) 가장 중요합니다.
POPULATION_TRANSITION_PRIOR = np.array([
    [0.70, 0.28, 0.01, 0.01],  # Wake ->
    [0.05, 0.80, 0.08, 0.07],  # Light ->
    [0.01, 0.15, 0.83, 0.01],  # Deep ->
    [0.05, 0.20, 0.01, 0.74],  # REM ->
], dtype=np.float64)


def compute_transition_matrix(y_sequences, n_classes=N_CLASSES, prior_weight=10.0):
    """라벨 시퀀스 리스트에서 경험적 상태전이 확률을 계산합니다. CRF의 초기값으로 씁니다.
    (y_sequences: subject별 전체 라벨 시퀀스 리스트, 윈도우/stride 적용 전의 연속열)

    기존에는 모든 셀에 +1(라플라스 스무딩)만 더했습니다. subject가 수십 명 수준이라 희귀한
    전이(예: Deep->REM)는 경험적 카운트가 거의 0이라, +1 스무딩은 "모든 희귀 전이가 서로
    동등하게 가능하다"는 틀린 가정을 암묵적으로 주입했습니다. 대신 POPULATION_TRANSITION_PRIOR를
    prior_weight만큼의 가상 관측치로 섞는 Dirichlet 사후분포로 바꿉니다 — prior_weight는
    "경험적 데이터 몇 명 분량과 맞먹는 신뢰도로 prior를 믿을지"에 대응하고, subject 수가
    많아지면 경험적 카운트가 자연히 prior를 압도합니다."""
    counts = POPULATION_TRANSITION_PRIOR[:n_classes, :n_classes].copy() * prior_weight
    for y in y_sequences:
        for a, b in zip(y[:-1], y[1:]):
            if 0 <= a < n_classes and 0 <= b < n_classes:
                counts[a, b] += 1
    return counts / counts.sum(axis=1, keepdims=True)


# ============================================================
# 2. 실행 디렉토리 / 설정 스냅샷 (기존 패턴 그대로)
# ============================================================
def make_run_dir(tag: str) -> Path:
    stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    run_dir = OUTPUT_DIR / f"{stamp}_{tag}"
    run_dir.mkdir(parents=True, exist_ok=True)
    return run_dir


def snapshot_config(run_dir: Path, extra: dict):
    """이 실행의 하이퍼파라미터 전체를 JSON으로 남깁니다(나중 비교의 유일한 근거)."""
    config = {
        "context_len": CONTEXT_LEN, "n_classes": N_CLASSES,
        "batch_size": BATCH_SIZE, "steps_per_epoch": STEPS_PER_EPOCH, "val_steps": VAL_STEPS,
        "epochs": EPOCHS, "patience": PATIENCE,
        "focal_gamma": FOCAL_GAMMA, "label_smoothing": LABEL_SMOOTHING, "lambda_crf": LAMBDA_CRF,
        "n_time_buckets": N_TIME_BUCKETS,
        "split_seed": SPLIT_SEED,
        **extra,
    }
    (run_dir / "config.json").write_text(json.dumps(config, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"설정 스냅샷 저장: {run_dir / 'config.json'}")
    return config


# ============================================================
# 3. Accel 데이터 유틸 (기존 train.py 패턴 포팅)
# ============================================================
def load_subjects_lazy(directory: Path, subject_ids: list, x_prefix="X"):
    for sid in subject_ids:
        cache_key = (str(directory), sid)
        if cache_key in _MEM_CACHE:
            continue
        x_p, y_p = directory / f"{x_prefix}_{sid}.npy", directory / f"y_{sid}.npy"
        if x_p.exists() and y_p.exists():
            _MEM_CACHE[cache_key] = (np.load(x_p, mmap_mode="r"), np.load(y_p).astype(np.int32))


def compute_class_weights_multi(directories, subject_ids_by_dir, min_weight=0.5, max_weight=2.5):
    counts = np.zeros(N_CLASSES, dtype=np.int64)
    for d in directories:
        for sid in subject_ids_by_dir.get(d, []):
            cached = _MEM_CACHE.get((str(d), sid))
            if cached:
                u, c = np.unique(cached[1], return_counts=True)
                for uu, cc in zip(u, c):
                    if uu < N_CLASSES:
                        counts[uu] += cc
    total = np.sum(counts)
    weights = total / (N_CLASSES * (counts.astype(np.float32) ** 0.5) + 1e-6)
    weights = np.clip(weights / np.mean(weights), min_weight, max_weight)
    return weights.astype(np.float32).tolist()


def save_class_distribution(directories, subject_ids_by_dir, out_path):
    counts = np.zeros(N_CLASSES, dtype=np.int64)
    for d in directories:
        for sid in subject_ids_by_dir.get(d, []):
            cached = _MEM_CACHE.get((str(d), sid))
            if cached:
                u, c = np.unique(cached[1], return_counts=True)
                for uu, cc in zip(u, c):
                    if uu < N_CLASSES:
                        counts[uu] += cc
    total = counts.sum()
    lines = [
        f"{name}: {count} ({(count / total * 100 if total else 0):.2f}%)"
        for name, count in zip(CLASS_NAMES, counts)
    ]
    out_path.write_text("\n".join(lines), encoding="utf-8")
    print(f"클래스 분포 저장: {out_path}\n" + "\n".join(lines))
    return counts


def _time_bucket_from_channels(x_slice):
    """7채널 accel 윈도우((CONTEXT_LEN, WINDOW, 7))의 마지막 epoch, time_feature 채널(인덱스 6)의
    첫 샘플에서 time_bucket_id를 복원합니다. time_feature는 한 epoch 내에서 전부 같은 값이므로
    [0,0,6]만 읽으면 됩니다."""
    tf_val = float(x_slice[-1, 0, 6])
    return time_feature_bucket(tf_val, N_TIME_BUCKETS)


SCALABLE_CHANNELS = slice(0, 6)
XYZ_CHANNELS = slice(0, 3)
TILT_CHANNEL = 3
ROTATION_AUGMENT_PROB = 0.5


def _apply_rotation_augment(x_slice):
    """개선 2: 실제 사용자는 폰을 베개 밑/협탁 위/매트리스 아래 등 제각각 다른 자세로 두지만,
    학습 데이터(BIDSleep/Sleep-Accel)는 특정 부착 위치가 고정된 웨어러블 센서로 수집됐습니다.
    윈도우 하나(= 한 학습 샘플) 전체에 동일한 무작위 3D 회전을 가해 "센서가 어떤 방향으로
    놓여도" 모델이 강건해지도록 합니다.

    반드시 **정규화 전(raw 단위)**에 적용해야 합니다 — x/y/z 세 채널은 서로 회전으로 뒤섞이는데,
    각 채널이 이미 서로 다른 평균/표준편차로 정규화돼 있으면 회전행렬을 곱하는 연산이 더 이상
    "센서를 실제로 돌린 것"과 같은 의미가 아니게 됩니다.

    tilt 채널(인덱스 3)은 x,y,z의 에포크 평균 편차 L1합이라 회전 후 값이 달라지므로 다시
    계산합니다. activity_variability_3min/activity_trend_3min(인덱스 4,5)은 가속도 벡터
    "크기"(sqrt(x²+y²+z²)) 기반이라 회전에 불변이므로 다시 계산할 필요가 없습니다."""
    if np.random.rand() > ROTATION_AUGMENT_PROB:
        return x_slice
    rotation = Rotation.random().as_matrix().astype(np.float32)  # (3,3), 매 윈도우마다 하나
    xyz = x_slice[:, :, XYZ_CHANNELS]  # (CONTEXT_LEN, WINDOW, 3)
    rotated = xyz @ rotation.T
    x_slice[:, :, XYZ_CHANNELS] = rotated
    mean_per_epoch = rotated.mean(axis=1, keepdims=True)  # (CONTEXT_LEN, 1, 3)
    x_slice[:, :, TILT_CHANNEL] = np.abs(rotated - mean_per_epoch).sum(axis=-1)
    return x_slice


def augment_accel(x_slice):
    """스케일/가우시안 노이즈 증강(기존 그대로) — 정규화 **후** 값에 적용합니다."""
    if np.random.rand() > 0.5:
        scale = np.random.uniform(0.9, 1.1)
        x_slice[:, :, SCALABLE_CHANNELS] *= scale
    if np.random.rand() > 0.5:
        noise = np.random.normal(0, 0.01, size=x_slice[:, :, SCALABLE_CHANNELS].shape).astype(np.float32)
        x_slice[:, :, SCALABLE_CHANNELS] += noise
    return x_slice


def _transition_labels(y_full, s, context_len):
    """개선 5의 보조 과제 라벨: 윈도우 [s, s+context_len) 각 에포크가 "직전 에포크와 다른
    단계로 바뀐 시점"인지의 이진 라벨입니다. REM/Light 같은 애매한 경계는 분류 오류가 몰리는
    지점이기도 해서, 이 전이 탐지를 보조 손실로 같이 학습시키면 컨텍스트 트랜스포머가 "지금
    막 단계가 바뀌었는가"에 더 민감한 표현을 배우도록 유도합니다 — 새 라벨/데이터 없이 기존
    4-class 라벨에서 바로 파생되는 공짜 신호입니다.

    윈도우 시작 전 에포크(s-1)가 실제로 존재하면 그것도 "직전"으로 써서(윈도우 경계에서도
    정보가 끊기지 않게), 전체 녹화의 첫 에포크(인덱스 0)만 직전이 없으므로 전이 아님(0)으로 둡니다."""
    idx = np.arange(s, s + context_len)
    prev_idx = np.clip(idx - 1, 0, len(y_full) - 1)
    transition = (y_full[prev_idx] != y_full[idx]).astype(np.float32)
    transition[idx == 0] = 0.0
    return transition


def create_accel_generator(directories, subject_ids_by_dir, mean, std, stride, augment=False, shuffle=True):
    def gen():
        pairs = [(d, sid) for d in directories for sid in subject_ids_by_dir.get(d, [])]
        while True:
            if shuffle:
                np.random.shuffle(pairs)
            for d, sid in pairs:
                X, y = _MEM_CACHE.get((str(d), sid), (None, None))
                if X is None:
                    continue
                starts = list(range(0, len(X) - CONTEXT_LEN + 1, stride))
                if shuffle:
                    np.random.shuffle(starts)
                for s in starts:
                    x_slice = np.asarray(X[s:s + CONTEXT_LEN], dtype=np.float32)
                    if augment:
                        x_slice = _apply_rotation_augment(x_slice)
                    x_slice = (x_slice - mean) / std
                    if augment:
                        x_slice = augment_accel(x_slice)
                    time_bucket = _time_bucket_from_channels(x_slice)
                    transition = _transition_labels(y, s, CONTEXT_LEN)
                    yield x_slice, time_bucket, y[s:s + CONTEXT_LEN], transition
    return gen


def build_accel_dataset(directories, subject_ids_by_dir, mean, std, stride, augment=False, shuffle=True):
    gen_fn = create_accel_generator(directories, subject_ids_by_dir, mean, std, stride, augment, shuffle)
    return tf.data.Dataset.from_generator(
        gen_fn,
        output_signature=(
            tf.TensorSpec(shape=(CONTEXT_LEN, ACCEL_WINDOW, ACCEL_CHANNELS), dtype=tf.float32),
            tf.TensorSpec(shape=(), dtype=tf.int32),
            tf.TensorSpec(shape=(CONTEXT_LEN,), dtype=tf.int32),
            tf.TensorSpec(shape=(CONTEXT_LEN,), dtype=tf.float32),
        )
    ).batch(BATCH_SIZE, drop_remainder=True).map(
        lambda x, tb, y, trans: (
            {"accel_input": x, "time_bucket_id": tb},
            {"accel_output": y, "accel_transition_output": trans},
        )
    )


def pairs_to_eval_subjects_accel(pairs, mean, std):
    subjects = []
    for d, sid in pairs:
        cached = _MEM_CACHE.get((str(d), sid))
        if not cached:
            continue
        X, y = cached
        subjects.append((f"{d.parent.name}/{sid}", X, y, mean, std))
    return subjects


# ============================================================
# 4. 스테이지: accel_core — 기존 3개 accel 소스로 가속도 인코더+ctx_body+CRF 지도학습
# ============================================================
def run_accel_core(folds=5, fold_index=0, tag=None, epochs=EPOCHS, init_accel_encoder=None):
    """가속도 인코더를 기존 지도 데이터(BIDSleep + Sleep-Accel)로 학습합니다.
    옛 train.py의 Stage 2(Accel 파인튜닝)와 같은 역할이지만, PSG 전이학습 없이 ctx_body를
    처음부터 함께 학습합니다(오디오 core가 별도 데이터로 학습되므로 더 이상 PSG가 ctx_body를
    먼저 형성해 줄 선행학습 소스가 아님 — 분리 인코더 전략의 직접적인 결과).

    init_accel_encoder(개선 6, 선택): run_accel_selfsup_wisdm이 저장한 사전학습
    accel_epoch_encoder.keras 경로. 주어지면 지도학습 시작 전에 그 가중치로 인코더를
    초기화합니다(라벨이 훨씬 적은 BIDSleep/Sleep-Accel만으로 처음부터 학습하는 대신, 더
    크고 다양한 WISDM 동작 데이터에서 먼저 "움직임이 어떻게 생겼는지"를 배운 뒤 시작)."""
    tag = tag or (f"accelcore_fold{fold_index}of{folds}" if folds > 1 else "accelcore_single")
    run_dir = make_run_dir(tag)

    if not ACCEL_NORM_STATS_PATH.exists():
        print(f"norm_stats 없음: {ACCEL_NORM_STATS_PATH} — preprocess.py stats를 먼저 실행하세요.")
        return None
    accel_stats = np.load(ACCEL_NORM_STATS_PATH, allow_pickle=True).item()
    mean, std = accel_stats["mean"], accel_stats["std"]

    pairs = list_subject_pairs([BID_DIR, ACCEL_DIR])
    tr_p, vl_p, te_p = split_subject_pairs(pairs, folds=folds, fold_index=fold_index)
    load_subjects_lazy(BID_DIR, [sid for d, sid in pairs if d == BID_DIR])
    load_subjects_lazy(ACCEL_DIR, [sid for d, sid in pairs if d == ACCEL_DIR])
    tr, vl, te = map(pairs_to_by_dir, (tr_p, vl_p, te_p))

    print(f"\n분할(fold {fold_index}/{folds}): train {len(tr_p)} / val {len(vl_p)} / test {len(te_p)} subject")
    if not tr_p or not te_p:
        print("학습 또는 테스트 subject가 없습니다 — build_dataset_hybrid.py를 먼저 실행하세요.")
        return None

    snapshot_config(run_dir, {
        "stage": "accel_core", "folds": folds, "fold_index": fold_index, "tag": tag,
        "train_subjects": [f"{d.parent.name}/{s}" for d, s in tr_p],
        "val_subjects": [f"{d.parent.name}/{s}" for d, s in vl_p],
        "test_subjects": [f"{d.parent.name}/{s}" for d, s in te_p],
    })

    weights = compute_class_weights_multi([BID_DIR, ACCEL_DIR], tr)
    save_class_distribution([BID_DIR, ACCEL_DIR], tr, run_dir / "accel_train_class_distribution.txt")
    print(f"가중치: {weights}")

    train_ds = build_accel_dataset([BID_DIR, ACCEL_DIR], tr, mean, std, ACCEL_STRIDE, augment=True).prefetch(tf.data.AUTOTUNE)
    val_ds = build_accel_dataset([BID_DIR, ACCEL_DIR], vl, mean, std, ACCEL_STRIDE, shuffle=False).prefetch(tf.data.AUTOTUNE)

    y_sequences = [_MEM_CACHE[(str(d), sid)][1] for d, sid in tr_p if (str(d), sid) in _MEM_CACHE]
    init_transition = compute_transition_matrix(y_sequences)

    models = model_module.build_multimodal_model(
        audio_n_mel_frames=N_MEL_FRAMES, audio_n_mels=N_MELS,
        accel_window=ACCEL_WINDOW, accel_channels=ACCEL_CHANNELS,
        context_len=CONTEXT_LEN, n_classes=N_CLASSES, n_time_buckets=N_TIME_BUCKETS,
        init_transition_matrix=init_transition,
    )
    accel_infer_model = models["accel_infer_model"]
    accel_train_model = models["accel_train_model"]
    crf = models["crf"]

    if init_accel_encoder:
        init_path = Path(init_accel_encoder)
        if not init_path.exists():
            print(f"경고: init_accel_encoder 경로가 없습니다({init_path}) — 사전학습 없이 처음부터 시작합니다.")
        else:
            pretrained_enc = tf.keras.models.load_model(init_path, compile=False)
            models["accel_encoder"].set_weights(pretrained_enc.get_weights())
            print(f"WISDM 자기지도 사전학습 가중치 로드: {init_path}")

    lr = WarmupCosineDecay(peak_lr=4e-4, warmup_steps=STEPS_PER_EPOCH * 2, decay_steps=STEPS_PER_EPOCH * epochs)
    # 개선 5: accel_train_model(메인 분류 + 전이탐지 보조 출력 2개)로 학습합니다. accel_infer_model은
    # 같은 레이어(crf/ctx_body/accel_enc 등)를 공유하는 객체라 가중치가 함께 갱신되므로, 학습이
    # 끝난 뒤 보조 출력이 없는 accel_infer_model만 저장하면 배포/변환(convert.py)은 그대로입니다.
    accel_train_model.compile(
        optimizer=tf.keras.optimizers.AdamW(learning_rate=lr, weight_decay=1e-4, clipnorm=1.0),
        loss={
            "accel_output": make_crf_combined_loss(crf),
            "accel_transition_output": tf.keras.losses.BinaryCrossentropy(),
        },
        loss_weights={"accel_output": 1.0, "accel_transition_output": AUX_TRANSITION_LOSS_WEIGHT},
    )
    callbacks = [
        tf.keras.callbacks.EarlyStopping(monitor="val_loss", patience=PATIENCE, restore_best_weights=True),
        tf.keras.callbacks.ModelCheckpoint(str(run_dir / "best_model.keras"), save_best_only=True),
        tf.keras.callbacks.CSVLogger(str(run_dir / "train_log.csv")),
    ]
    accel_train_model.fit(
        train_ds, validation_data=val_ds, epochs=epochs,
        steps_per_epoch=STEPS_PER_EPOCH, validation_steps=VAL_STEPS, callbacks=callbacks,
    )
    accel_infer_model.save(run_dir / "accel_inference_model.keras")
    np.save(run_dir / "accel_transition_matrix.npy", init_transition)

    print("\n===== Accel 테스트셋 평가 =====")
    eval_subjects = pairs_to_eval_subjects_accel(te_p, mean, std)
    # evaluate.evaluate_subjects는 (context_len, window, channels) 단일 입력 모델을 가정합니다.
    # accel_infer_model은 [accel_input, time_bucket_id] 2-입력이므로 얇은 래퍼로 감쌉니다.
    wrapped = _SingleInputEvalWrapper(accel_infer_model, N_TIME_BUCKETS)
    evaluate_subjects(
        wrapped, eval_subjects, CONTEXT_LEN, N_CLASSES, CLASS_NAMES, run_dir, "accel",
        transition_matrix=np.exp(crf.log_transitions.numpy()),
        batch_size=EVAL_BATCH_SIZE, overlap_stride=EVAL_OVERLAP_STRIDE,
    )
    print(f"\n이 실행의 모든 산출물: {run_dir}")
    return run_dir


class _SingleInputEvalWrapper:
    """evaluate.evaluate_subjects가 기대하는 model.predict(batch) 단일 입력 인터페이스를,
    실제로는 [도메인 입력, time_bucket_id] 2-입력을 받는 *_infer_model 위에 씌우는 얇은 래퍼.
    time_bucket_id는 평가 시 윈도우별로 정확히 복원하기보다(평가 스크립트가 time_feature를
    따로 넘기지 않음) 중간값(버킷 절반)으로 고정합니다 — 평가 지표에 미치는 영향은 Colab에서
    실측 후 필요하면 개선합니다."""

    def __init__(self, infer_model, n_time_buckets):
        self._model = infer_model
        self._mid_bucket = n_time_buckets // 2

    def predict(self, batch, verbose=0):
        batch_size = batch.shape[0]
        time_bucket = np.full((batch_size,), self._mid_bucket, dtype=np.int32)
        inputs = {self._model.input_names[0]: batch, "time_bucket_id": time_bucket}
        return self._model.predict(inputs, verbose=verbose)


# ============================================================
# 5. 스테이지: mesa_selfsup — MESA 가속도로 sleep/wake 자기지도 사전학습(보조)
# ============================================================
def run_mesa_selfsup(epochs=EPOCHS):
    """MESA 액티그래피 요약 특징(activity/광량 등 스칼라 n_features개)만으로 "지금 깨어있는가/
    자고있는가"를 맞히는 보조 과제로 작은 MLP 인코더를 사전학습합니다.

    ⚠️ 아키텍처 주의: MESA의 Actiwatch Spectrum 기기는 원시 3축 가속도를 전혀 출력하지 않고
    30초 에포크당 활동량/광량 요약값만 냅니다(세션 중 WebSearch로 확인된 사실). 그래서
    build_dataset_hybrid.load_mesa_subject는 X_*.npy(원시 파형 그리드)가 아니라 M_*.npy(에포크당
    스칼라 특징 벡터)를 만들고, 라벨도 PSG 4-class가 아니라 기기 자체의 wake 지표(0=sleep,
    1=wake)입니다. 이 함수가 만드는 인코더의 입력 shape은 (CONTEXT_LEN, n_features)라
    accel_core가 쓰는 (CONTEXT_LEN, ACCEL_WINDOW, ACCEL_CHANNELS) 원시 파형 CNN 인코더와
    근본적으로 다르므로, 여기서 학습된 가중치를 accel_core의 인코더에 직접 로드할 수 없습니다.
    지금은 "액티그래피 요약 특징만으로 수면/각성이 얼마나 분리되는가"를 보는 독립적인 진단용
    보조 실험으로 둡니다.
    """
    if not MESA_DIR.exists():
        print(f"건너뜀: {MESA_DIR} 없음 — MESA는 NSRR 데이터 접근 승인이 필요합니다. "
              f"승인 후 build_dataset_hybrid.py의 load_mesa_subject로 받아오면 이 스테이지가 동작합니다.")
        return None

    pairs = [(MESA_DIR, p.name.replace("M_", "").replace(".npy", ""))
             for p in sorted(MESA_DIR.glob("M_*.npy"))]
    if not pairs:
        print(f"{MESA_DIR}에 M_*.npy가 없습니다.")
        return None
    load_subjects_lazy(MESA_DIR, [sid for _, sid in pairs], x_prefix="M")

    cached = [(sid, _MEM_CACHE[(str(MESA_DIR), sid)]) for sid in (sid for _, sid in pairs)
              if (str(MESA_DIR), sid) in _MEM_CACHE]
    if not cached:
        print(f"{MESA_DIR}의 M_*.npy/y_*.npy 쌍을 메모리에 올리지 못했습니다.")
        return None

    # MESA 피처는 accel_domain(원시 파형) 정규화 통계와 전혀 다른 분포라, 여기서 자체적으로
    # 평균/표준편차를 계산합니다(5차원 안팎의 스칼라라 전체를 메모리에 올려도 가볍습니다).
    all_feats = np.concatenate([X for _, (X, _) in cached], axis=0)
    mesa_mean = all_feats.mean(axis=0).astype(np.float32)
    mesa_std = (all_feats.std(axis=0) + 1e-6).astype(np.float32)
    n_features = all_feats.shape[-1]

    tag = "mesa_selfsup"
    run_dir = make_run_dir(tag)
    np.save(run_dir / "mesa_norm_stats.npy", {"mean": mesa_mean, "std": mesa_std}, allow_pickle=True)

    def gen():
        sids = [sid for _, sid in pairs]
        while True:
            np.random.shuffle(sids)
            for sid in sids:
                M, y = _MEM_CACHE.get((str(MESA_DIR), sid), (None, None))
                if M is None:
                    continue
                starts = list(range(0, len(M) - CONTEXT_LEN + 1, ACCEL_STRIDE))
                np.random.shuffle(starts)
                for s in starts:
                    m_slice = (np.asarray(M[s:s + CONTEXT_LEN], dtype=np.float32) - mesa_mean) / mesa_std
                    # load_mesa_subject가 이미 0=sleep,1=wake로 만들어 뒀으므로 그대로 사용.
                    is_awake = y[s:s + CONTEXT_LEN].astype(np.int32)
                    yield m_slice, is_awake

    ds = tf.data.Dataset.from_generator(
        gen,
        output_signature=(
            tf.TensorSpec(shape=(CONTEXT_LEN, n_features), dtype=tf.float32),
            tf.TensorSpec(shape=(CONTEXT_LEN,), dtype=tf.int32),
        ),
    ).batch(BATCH_SIZE, drop_remainder=True).prefetch(tf.data.AUTOTUNE)

    mesa_enc = model_module.build_mesa_feature_encoder(n_features)
    inp = tf.keras.layers.Input(shape=(CONTEXT_LEN, n_features))
    emb = tf.keras.layers.TimeDistributed(mesa_enc)(inp)
    logits = tf.keras.layers.Dense(2, dtype="float32")(emb)
    selfsup_model = tf.keras.Model(inp, logits, name="mesa_selfsup_model")
    selfsup_model.compile(
        optimizer=tf.keras.optimizers.AdamW(learning_rate=1e-3, weight_decay=1e-4),
        loss=tf.keras.losses.SparseCategoricalCrossentropy(from_logits=True),
    )
    selfsup_model.fit(ds, epochs=epochs, steps_per_epoch=STEPS_PER_EPOCH)
    mesa_enc.save(run_dir / "mesa_feature_encoder_pretrained.keras")
    print(f"\nMESA 자기지도 사전학습된 액티그래피 특징 인코더 저장: "
          f"{run_dir / 'mesa_feature_encoder_pretrained.keras'}")
    print("이 인코더는 accel_core의 원시 파형 CNN 인코더와 입력 shape이 달라 가중치를 직접 "
          "이어받을 수 없습니다 — 액티그래피 요약 특징만으로 sleep/wake가 얼마나 분리되는지 "
          "보는 독립적인 진단 실험 결과로 활용하세요.")
    return run_dir


# ============================================================
# 6. 스테이지: audio_denoise_aux — APSAA/AI-Hub로 오디오 노이즈 강건성 보조 학습(오디오 인코더의 유일한 사전학습)
# ============================================================
def run_audio_denoise_aux(epochs=EPOCHS):
    """APSAA/AI-Hub의 오디오(라벨은 신뢰하지 않음)로, 오디오 인코더 앞단이 일상 소음(화이트
    노이즈 등)에 강건해지도록 디노이징 오토인코더 과제로 사전학습합니다.

    ⚠️ 두 데이터셋 모두 이 저장소에는 아직 없습니다(APSAA는 공개돼 있으나 미다운로드,
    AI-Hub는 신청 절차가 필요). 구조만 준비해 둡니다.
    """
    found_dirs = [d for d in AUDIO_AUX_DIRS if d.exists()]
    if not found_dirs:
        print(f"건너뜀: {[str(d) for d in AUDIO_AUX_DIRS]} 모두 없음 — "
              f"APSAA(공개, 미다운로드)/AI-Hub(신청 필요) 데이터가 준비되면 동작합니다.")
        return None

    a_files = []
    for d in found_dirs:
        a_files.extend(sorted(d.glob("A_*.npy")))
    if not a_files:
        print("오디오(A_*.npy) 파일을 찾지 못했습니다.")
        return None

    tag = "audio_denoise_aux"
    run_dir = make_run_dir(tag)
    mel_fb = build_mel_filterbank(AUDIO_SAMPLE_RATE, n_mels=N_MELS)

    def gen():
        rng = np.random.default_rng(0)
        while True:
            f = a_files[rng.integers(len(a_files))]
            A = np.load(f, mmap_mode="r")
            if len(A) == 0:
                continue
            idx = rng.integers(len(A))
            mel = audio_epoch_to_melspec(np.asarray(A[idx]), mel_fb=mel_fb)
            noisy = mel + rng.normal(0, 0.5, size=mel.shape).astype(np.float32)
            yield noisy[..., None].astype(np.float32), mel[..., None].astype(np.float32)

    ds = tf.data.Dataset.from_generator(
        gen,
        output_signature=(
            tf.TensorSpec(shape=(N_MEL_FRAMES, N_MELS, 1), dtype=tf.float32),
            tf.TensorSpec(shape=(N_MEL_FRAMES, N_MELS, 1), dtype=tf.float32),
        ),
    ).batch(BATCH_SIZE, drop_remainder=True).prefetch(tf.data.AUTOTUNE)

    # 간단한 denoising autoencoder: 오디오 인코더 구조를 그대로 재사용하되 디코더를 덧붙입니다.
    audio_enc = model_module.build_audio_epoch_encoder(N_MEL_FRAMES, N_MELS)
    inp = tf.keras.layers.Input(shape=(N_MEL_FRAMES, N_MELS, 1))
    emb = audio_enc(inp)
    x = tf.keras.layers.Dense((N_MEL_FRAMES // 8) * (N_MELS // 8) * 64, activation="relu")(emb)
    x = tf.keras.layers.Reshape((N_MEL_FRAMES // 8, N_MELS // 8, 64))(x)
    for filters in (64, 32, 16):
        x = tf.keras.layers.Conv2DTranspose(filters, 3, strides=2, padding="same", activation="relu")(x)
    out = tf.keras.layers.Conv2D(1, 3, padding="same", dtype="float32")(x)
    # 업샘플링 경로가 정확히 입력 해상도로 돌아오지 않을 수 있어(스트라이드 누적 오차) 크롭/리사이즈.
    out = tf.image.resize(out, (N_MEL_FRAMES, N_MELS))
    denoise_model = tf.keras.Model(inp, out, name="audio_denoise_model")
    denoise_model.compile(optimizer=tf.keras.optimizers.AdamW(1e-3), loss="mse")
    denoise_model.fit(ds, epochs=epochs, steps_per_epoch=STEPS_PER_EPOCH)
    audio_enc.save(run_dir / "audio_encoder_denoise_pretrained.keras")
    print(f"\n노이즈 강건성 사전학습된 오디오 인코더 저장: {run_dir / 'audio_encoder_denoise_pretrained.keras'}")
    return run_dir


# ============================================================
# 7. 스테이지: accel_selfsup_wisdm — WISDM으로 accel_epoch_encoder 디노이징 자기지도 사전학습
# ============================================================
def run_accel_selfsup_wisdm(epochs=EPOCHS):
    """WISDM(라벨 없는 일상 동작 가속도, 공개·신청 불필요)의 7채널 에포크 그리드로,
    accel_epoch_encoder가 "가속도 파형이 어떻게 생겼는지"를 먼저 배우도록 디노이징
    오토인코더 과제로 사전학습합니다. run_audio_denoise_aux와 같은 구조(인코더+간단한
    디코더, 노이즈를 섞은 뒤 원래 값으로 복원)이지만 1D 가속도 파형이라 Conv1DTranspose를
    씁니다.

    MESA(mesa_selfsup)와의 핵심 차이: MESA는 원시 가속도가 없어 입력 shape 자체가 달라
    accel_core의 CNN 인코더에 가중치를 이어줄 수 없었지만, WISDM은 실제 x/y/z를 제공하므로
    build_dataset_hybrid.load_wisdm_subject가 accel_core와 완전히 같은 (WINDOW, 7) 채널
    레이아웃으로 저장합니다 — 그래서 여기서 저장하는 accel_epoch_encoder 가중치를
    run_accel_core(init_accel_encoder=...)로 그대로 이어줄 수 있습니다.

    ⚠️ WISDM은 수면단계 학습에 전혀 쓰이지 않는 보조 데이터입니다 — 오직 "가속도 CNN
    인코더의 초기 가중치를 더 많고 다양한 동작 데이터로 미리 데워두는" 역할입니다.
    """
    if not WISDM_DIR.exists():
        print(f"건너뜀: {WISDM_DIR} 없음 — WISDM(공개, 신청 불필요)을 "
              f"ml/data/wisdm/raw/WISDM_ar_v1.1_raw.txt로 받아 build_dataset_hybrid.py를 "
              f"실행하면 이 스테이지가 동작합니다.")
        return None

    x_files = sorted(WISDM_DIR.glob("X_*.npy"))
    if not x_files:
        print(f"{WISDM_DIR}에 X_*.npy가 없습니다.")
        return None

    if not ACCEL_NORM_STATS_PATH.exists():
        print(f"norm_stats 없음: {ACCEL_NORM_STATS_PATH} — preprocess.py stats를 먼저 실행하세요.")
        return None
    accel_stats = np.load(ACCEL_NORM_STATS_PATH, allow_pickle=True).item()
    mean, std = accel_stats["mean"], accel_stats["std"]

    tag = "accel_selfsup_wisdm"
    run_dir = make_run_dir(tag)

    def gen():
        rng = np.random.default_rng(0)
        while True:
            f = x_files[rng.integers(len(x_files))]
            X = np.load(f, mmap_mode="r")
            if len(X) == 0:
                continue
            idx = rng.integers(len(X))
            epoch = (np.asarray(X[idx], dtype=np.float32) - mean) / std
            noisy = epoch.copy()
            noisy[:, SCALABLE_CHANNELS] += rng.normal(
                0, 0.3, size=epoch[:, SCALABLE_CHANNELS].shape
            ).astype(np.float32)
            yield noisy, epoch

    ds = tf.data.Dataset.from_generator(
        gen,
        output_signature=(
            tf.TensorSpec(shape=(ACCEL_WINDOW, ACCEL_CHANNELS), dtype=tf.float32),
            tf.TensorSpec(shape=(ACCEL_WINDOW, ACCEL_CHANNELS), dtype=tf.float32),
        ),
    ).batch(BATCH_SIZE, drop_remainder=True).prefetch(tf.data.AUTOTUNE)

    # 간단한 denoising autoencoder: accel_epoch_encoder 구조를 그대로 재사용하되 디코더를 덧붙입니다.
    accel_enc = model_module.build_accel_epoch_encoder(ACCEL_WINDOW, ACCEL_CHANNELS)
    inp = tf.keras.layers.Input(shape=(ACCEL_WINDOW, ACCEL_CHANNELS))
    emb = accel_enc(inp)
    x = tf.keras.layers.Dense((ACCEL_WINDOW // 8) * 64, activation="relu")(emb)
    x = tf.keras.layers.Reshape((ACCEL_WINDOW // 8, 64))(x)
    for filters in (64, 32, 16):
        x = tf.keras.layers.Conv1DTranspose(filters, 3, strides=2, padding="same", activation="relu")(x)
    out = tf.keras.layers.Conv1D(ACCEL_CHANNELS, 3, padding="same", dtype="float32")(x)
    # 업샘플링 경로가 정확히 WINDOW로 돌아오지 않을 수 있어(스트라이드 누적 오차) 리사이즈로 보정
    # (run_audio_denoise_aux의 tf.image.resize와 같은 트릭 — 1D라 더미 공간축을 하나 끼웁니다).
    out = tf.squeeze(tf.image.resize(out[:, :, None, :], (ACCEL_WINDOW, 1)), axis=2)
    denoise_model = tf.keras.Model(inp, out, name="accel_denoise_model")
    denoise_model.compile(optimizer=tf.keras.optimizers.AdamW(1e-3), loss="mse")
    denoise_model.fit(ds, epochs=epochs, steps_per_epoch=STEPS_PER_EPOCH)
    accel_enc.save(run_dir / "accel_encoder_wisdm_pretrained.keras")
    print(f"\nWISDM 자기지도 사전학습된 가속도 인코더 저장: {run_dir / 'accel_encoder_wisdm_pretrained.keras'}")
    print("accel_core 실행 시 --init-accel-encoder로 이 경로를 넘기면 사전학습 효과를 이어받습니다.")
    return run_dir


# ============================================================
# 8. 스테이지: fusion_finetune — 소량 동기화 샘플로 Late Fusion 헤드만 미세조정
# ============================================================
def run_fusion_finetune(accel_checkpoint: str, audio_denoise_checkpoint: str = None, epochs=10):
    """accel_core에서 학습된 가속도 인코더+ctx_body+CRF를 로드하고, 동기화된 소량 샘플
    (FUSION_SYNC_DIR)로 Late Fusion 헤드만 미세조정합니다.

    ⚠️ PSG-Audio를 데이터셋에서 제외했으므로 "audio_core" 체크포인트 자체가 존재하지
    않습니다 — 오디오 인코더는 audio_denoise_aux(APSAA/AI-Hub)로 비지도 사전학습된
    인코더 서브모델(audio_encoder_denoise_pretrained.keras, build_audio_epoch_encoder
    단독 저장분)만 있습니다. 그래서 가중치 주입 방식이 두 체크포인트를 다르게 다룹니다:
      - accel_checkpoint(accel_inference_model.keras)는 기존처럼 by_name으로
        training_model에 통째로 로드합니다(ctx_body/CRF의 기반이 됨).
      - audio_denoise_checkpoint는 인코더 단독 파일이라 by_name 로딩이 적용될 대상이
        모호하므로(TimeDistributed 안에 중첩됨), run_accel_core의 init_accel_encoder와
        같은 방식으로 models["audio_encoder"].set_weights(...)에 직접 주입합니다.
        없으면(None) 오디오 인코더는 랜덤 초기화 상태로 fusion_finetune을 시작합니다.
    이 스테이지가 오디오 인코더가 실제 수면단계 라벨을 처음이자 유일하게 보는 지점입니다.
    """
    if not FUSION_SYNC_DIR.exists():
        print(f"건너뜀: {FUSION_SYNC_DIR} 없음 — Late Fusion 미세조정에는 폰 마이크+가속도를 "
              f"동시에 녹음한 소량의 동기화 샘플이 필요합니다. 아직 수집되지 않았습니다.")
        return None
    if not Path(accel_checkpoint).exists():
        print(f"체크포인트를 찾을 수 없습니다: accel={accel_checkpoint}")
        return None

    tag = "fusion_finetune"
    run_dir = make_run_dir(tag)

    models = model_module.build_multimodal_model(
        audio_n_mel_frames=N_MEL_FRAMES, audio_n_mels=N_MELS,
        accel_window=ACCEL_WINDOW, accel_channels=ACCEL_CHANNELS,
        context_len=CONTEXT_LEN, n_classes=N_CLASSES, n_time_buckets=N_TIME_BUCKETS,
    )
    training_model = models["training_model"]
    try:
        training_model.load_weights(accel_checkpoint, by_name=True, skip_mismatch=True)
    except Exception as e:
        print(f"가중치 로드 실패({type(e).__name__}: {e}) — accel_checkpoint의 아키텍처가 "
              f"현재 model.build_multimodal_model과 호환되는지 확인하세요.")
        return None

    if audio_denoise_checkpoint:
        audio_denoise_path = Path(audio_denoise_checkpoint)
        if not audio_denoise_path.exists():
            print(f"경고: audio_denoise_checkpoint 없음({audio_denoise_path}) — "
                  f"오디오 인코더가 랜덤 초기화 상태로 시작합니다.")
        else:
            pretrained_audio_enc = tf.keras.models.load_model(audio_denoise_path, compile=False)
            models["audio_encoder"].set_weights(pretrained_audio_enc.get_weights())
            print(f"audio_denoise_aux 사전학습 가중치 로드: {audio_denoise_path}")
    else:
        print("audio_denoise_checkpoint 미지정 — 오디오 인코더가 랜덤 초기화 상태로 "
              "fusion_finetune을 시작합니다(audio_denoise_aux를 먼저 돌려 사전학습하는 걸 권장).")

    # 인코더+ctx_body+CRF는 고정하고, fusion 헤드(Concatenate 이후)만 학습합니다 — 동기화
    # 샘플이 소량이라 깊은 인코더까지 같이 학습시키면 바로 과적합됩니다.
    for layer in training_model.layers:
        if layer.name in ("fusion_hidden", "fusion_logits"):
            layer.trainable = True
        else:
            layer.trainable = False

    # TODO(fusion_sync 데이터가 준비되면): FUSION_SYNC_DIR의 (오디오, 가속도, 라벨) 동시 샘플로
    # tf.data 파이프라인을 구성하고 training_model의 fusion_output에만 손실을 적용해 fit합니다.
    # 데이터 포맷이 아직 정해지지 않아(사용자가 수집할 방식에 따라 달라짐) 지금은 자리만 둡니다.
    print("FUSION_SYNC_DIR 데이터 포맷이 아직 정의되지 않았습니다 — 실제 학습 루프는 데이터 수집 "
          "방식이 정해진 뒤 추가합니다. 가중치 로드와 레이어 동결까지는 정상 동작합니다.")
    training_model.save(run_dir / "fusion_finetune_base.keras")
    return run_dir


# ============================================================
# 9. CLI
# ============================================================
def main():
    global LAMBDA_CRF
    p = argparse.ArgumentParser(description="멀티모달 수면 단계 분류 모델 학습")
    p.add_argument("--stage", required=True,
                   choices=["accel_core", "mesa_selfsup", "audio_denoise_aux",
                            "accel_selfsup_wisdm", "fusion_finetune", "all"])
    p.add_argument("--folds", type=int, default=5)
    p.add_argument("--fold-index", type=int, default=0)
    p.add_argument("--tag", type=str, default=None)
    p.add_argument("--epochs", type=int, default=EPOCHS)
    p.add_argument("--lambda-crf", type=float, default=LAMBDA_CRF,
                   help="focal loss 대비 CRF 음의 로그가능도 가중치(0이면 CRF 항 비활성)")
    p.add_argument("--accel-checkpoint", default=None, help="fusion_finetune용 accel_core 체크포인트")
    p.add_argument("--audio-denoise-checkpoint", default=None,
                   help="fusion_finetune용 audio_denoise_aux 사전학습 인코더 체크포인트(선택)")
    p.add_argument("--init-accel-encoder", default=None,
                   help="accel_core 시작 전 accel_epoch_encoder를 초기화할 .keras 경로 "
                        "(run_accel_selfsup_wisdm이 저장한 사전학습 인코더)")
    args = p.parse_args()
    LAMBDA_CRF = args.lambda_crf

    if args.stage in ("accel_core", "all"):
        run_accel_core(args.folds, args.fold_index, args.tag, args.epochs, args.init_accel_encoder)
    if args.stage in ("mesa_selfsup", "all"):
        run_mesa_selfsup(args.epochs)
    if args.stage in ("audio_denoise_aux", "all"):
        run_audio_denoise_aux(args.epochs)
    if args.stage in ("accel_selfsup_wisdm", "all"):
        run_accel_selfsup_wisdm(args.epochs)
    if args.stage == "fusion_finetune":
        if not args.accel_checkpoint:
            print("fusion_finetune에는 --accel-checkpoint가 필요합니다.")
            return
        run_fusion_finetune(args.accel_checkpoint, args.audio_denoise_checkpoint, args.epochs)


if __name__ == "__main__":
    main()
