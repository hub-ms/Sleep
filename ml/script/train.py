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
from sklearn.metrics import f1_score

import model as model_module
from evaluate import evaluate_subjects
from preprocess import (
    AUDIO_SAMPLE_RATE,
    BAND_EDGES_HZ,
    CHANNEL_SETS,
    N_CLASSES,
    N_MEL_FRAMES,
    N_MELS,
    audio_epoch_to_melspec,
    build_mel_filterbank,
    channel_count,
    circadian_channels,
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

# Sleep-EDF Expanded(PSG: EEG/EOG/EMG). 가속도가 없어 accel_core 에는 쓸 수 없지만, 수면 "순서"를
# 배우는 컨텍스트 트랜스포머 사전학습에는 쓴다 — run_psg_context_pretrain 참고.
EDF_DIR = BASE_DIR / "data" / "sleep_edf" / "subjects"
EDF_NORM_STATS_PATH = BASE_DIR / "data" / "sleep_edf" / "norm_stats.npy"

ACCEL_WINDOW, ACCEL_CHANNELS = 1500, 7
TIME_FEATURE_CHANNEL = 6  # ACCEL_CHANNEL_NAMES 에서 time_feature 의 인덱스

# 모델 입력 채널 구성. --channel-set 으로 바꾼다.
#   base      — 기존 7채널. **기본값**이라 베이스라인 실행은 영향을 받지 않는다.
#   circadian — + cycle_sin, cycle_cos (9채널). 수면주기 위상.
#   full      — + band_resp, band_bcg, band_motion (12채널). `preprocess bands` 가 필요하다.
# 어느 쪽이 실제로 나은지는 fold 5개 ablation 으로 판정할 것 — 늘린다고 좋아진다는 보장이 없다.
# ⚠️ base 가 아닌 구성으로 학습한 모델을 앱에 넣으려면 Kotlin 쪽
#    AccelChannels.NUM_CHANNELS / SleepFeatureBuilder 도 같이 바꿔야 한다(그러지 않으면
#    SleepStageClassifier.initialize() 의 shape 검사에서 바로 걸린다).
CHANNEL_SET = "base"
CONTEXT_LEN = 60  # accel/audio가 ctx_body를 공유하므로 두 도메인이 같은 컨텍스트 길이를 써야 함
ACCEL_STRIDE = 3
N_TIME_BUCKETS = 8  # 시간대 FiLM 버킷 수(약 1시간 단위, 8시간 기준)

BATCH_SIZE = 32  # 올리지 말 것: 병목이 GPU가 아니라 Python 제네레이터라 32→64에서 8배 느려진다.
# 한 에폭의 steps는 이제 분할의 실제 윈도 수에서 유도한다(count_accel_windows). 이 상수는
# 그 값의 상한일 뿐이다 — 예전의 고정 300은 학습셋의 1/4만 훑고 한 에폭을 끝냈다.
MAX_STEPS_PER_EPOCH = 600
# 자기지도/디노이징 스테이지(mesa/wisdm/audio)는 라벨이 없어 "데이터를 한 번 훑는다"는 개념이
# 중요하지 않고 고정 스텝 예산으로 돌린다 — 여기만 예전 값을 그대로 쓴다.
STEPS_PER_EPOCH = 300
EPOCHS = 60
PATIENCE = 8  # 감시 지표가 val_macro_f1로 바뀌어 예전(20)만큼 길게 기다릴 이유가 없다.

FOCAL_GAMMA = 1.0
LABEL_SMOOTHING = 0.1
LAMBDA_CRF = 1.0  # focal loss 대비 CRF NLL 가중치. Colab에서 --lambda-crf 0 vs >0 ablation 권장.
AUX_TRANSITION_LOSS_WEIGHT = 0.3  # 개선 5: 메인 분류 손실 대비 전이탐지 보조 손실 가중치.
# CRF 전이행렬 초기값에서 POPULATION_TRANSITION_PRIOR를 몇 개의 가상 관측치로 셀지.
# 기본 10은 실측 전이 19만 건 앞에서 사실상 0의 영향력이다 — 생리학적 제약을 실제로
# 주입하려면 수천 단위가 필요하다. --transition-prior-weight로 ablation 할 것.
TRANSITION_PRIOR_WEIGHT = 10.0
USE_CLASS_WEIGHTS = True  # --no-class-weights 로 끄고 ablation

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


class ValMacroF1(tf.keras.callbacks.Callback):
    """에폭 끝마다 검증셋 전체에 대해 macro F1을 계산해 `logs["val_macro_f1"]`에 넣는다.

    왜 필요한가: EarlyStopping/ModelCheckpoint가 감시하던 `val_loss`는 focal + CRF NLL +
    전이탐지 BCE의 합이라 우리가 실제로 올리려는 지표(macro F1)와 단조 관계가 아니다.
    특히 클래스 가중치를 넣은 뒤로는 손실이 소수 클래스 쪽으로 기울어 val_loss 최소점과
    macro F1 최대점이 더 벌어진다. 목표 지표를 직접 감시한다.

    val_ds는 **유한(finite)** 데이터셋이어야 한다(repeat=False).
    """

    def __init__(self, val_ds):
        super().__init__()
        self._val_ds = val_ds

    def on_epoch_end(self, epoch, logs=None):
        logs = logs if logs is not None else {}
        y_true_all, y_pred_all = [], []
        for inputs, targets in self._val_ds:
            logits = self.model(inputs, training=False)
            # accel_train_model은 [분류, 전이탐지] 2-출력이라 첫 번째만 쓴다.
            if isinstance(logits, (list, tuple)):
                logits = logits[0]
            elif isinstance(logits, dict):
                logits = logits["accel_output"]
            y_pred_all.append(np.argmax(np.asarray(logits, dtype=np.float32), axis=-1).ravel())
            y_true = targets["accel_output"] if isinstance(targets, dict) else targets[0]
            y_true_all.append(np.asarray(y_true).ravel())

        if not y_true_all:
            logs["val_macro_f1"] = 0.0
            return
        y_true = np.concatenate(y_true_all)
        y_pred = np.concatenate(y_pred_all)
        score = f1_score(y_true, y_pred, labels=list(range(N_CLASSES)),
                         average="macro", zero_division=0)
        logs["val_macro_f1"] = float(score)
        per_class = f1_score(y_true, y_pred, labels=list(range(N_CLASSES)),
                             average=None, zero_division=0)
        detail = " ".join(f"{n}={v:.3f}" for n, v in zip(CLASS_NAMES, per_class))
        print(f" — val_macro_f1: {score:.4f}  ({detail})")


def weighted_focal_loss(gamma=FOCAL_GAMMA, label_smoothing=LABEL_SMOOTHING, class_weights=None):
    """클래스 불균형에 대응하는 focal loss(라벨 스무딩 포함). y_pred는 logits(from_logits=True).

    class_weights: 길이 N_CLASSES의 per-class 가중치. 정답 클래스의 가중치를 손실에 곱한다.
    제네레이터가 멀티출력 dict를 내보내므로 Keras의 `class_weight=` 인자는 쓸 수 없다
    (멀티출력 모델에서는 지원되지 않는다) — 그래서 손실 함수 안에서 직접 곱한다.
    None이면 가중치 없음(이전 동작).
    """
    weight_table = None
    if class_weights is not None:
        weight_table = tf.constant(np.asarray(class_weights, dtype=np.float32))

    def loss_fn(y_true, y_pred):
        y_true = tf.cast(y_true, tf.int32)
        y_pred = tf.cast(y_pred, tf.float32)
        one_hot_y = tf.one_hot(y_true, depth=N_CLASSES)
        smoothed = one_hot_y * (1.0 - label_smoothing) + (label_smoothing / N_CLASSES)
        ce = tf.keras.losses.categorical_crossentropy(smoothed, y_pred, from_logits=True)
        probs = tf.nn.softmax(y_pred, axis=-1)
        pt = tf.reduce_sum(smoothed * probs, axis=-1)
        focal_weight = tf.pow(1.0 - pt, gamma)
        loss = focal_weight * ce
        if weight_table is not None:
            # 스무딩 전 원-핫(=실제 정답 클래스)으로 가중치를 고른다. 스무딩된 분포로 고르면
            # 모든 클래스 가중치가 조금씩 섞여 불균형 보정 효과가 희석된다.
            loss = loss * tf.gather(weight_table, y_true)
        return loss
    return loss_fn


def make_crf_combined_loss(crf_layer, gamma=FOCAL_GAMMA, label_smoothing=LABEL_SMOOTHING,
                            lambda_crf=LAMBDA_CRF, class_weights=None):
    """focal loss + CRF 음의 로그가능도를 더한 손실.

    CRF NLL은 시퀀스 하나당 스칼라 (B,)인데 focal loss는 (B,T)입니다. nll을 T번 복제해
    (B,T)로 브로드캐스트하면, Keras가 시간축에 대해 평균을 낼 때 결과적으로
    mean_t(focal_t) + lambda_crf * nll 이 되어(원하는 대로) nll이 T로 희석되지 않습니다.
    """
    focal = weighted_focal_loss(gamma, label_smoothing, class_weights)

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


def compute_transition_matrix(y_sequences, n_classes=N_CLASSES, prior_weight=None):
    """라벨 시퀀스 리스트에서 경험적 상태전이 확률을 계산합니다. CRF의 초기값으로 씁니다.
    (y_sequences: subject별 전체 라벨 시퀀스 리스트, 윈도우/stride 적용 전의 연속열)

    기존에는 모든 셀에 +1(라플라스 스무딩)만 더했습니다. subject가 수십 명 수준이라 희귀한
    전이(예: Deep->REM)는 경험적 카운트가 거의 0이라, +1 스무딩은 "모든 희귀 전이가 서로
    동등하게 가능하다"는 틀린 가정을 암묵적으로 주입했습니다. 대신 POPULATION_TRANSITION_PRIOR를
    prior_weight만큼의 가상 관측치로 섞는 Dirichlet 사후분포로 바꿉니다 — prior_weight는
    "경험적 데이터 몇 명 분량과 맞먹는 신뢰도로 prior를 믿을지"에 대응하고, subject 수가
    많아지면 경험적 카운트가 자연히 prior를 압도합니다."""
    if prior_weight is None:
        prior_weight = TRANSITION_PRIOR_WEIGHT  # 모듈 전역(=CLI로 덮어쓸 수 있는 값)을 호출 시점에 읽는다
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


def compose_run_tag(stage_slug: str, user_tag, folds: int, fold_index: int) -> str:
    """run_dir 이름에 fold 식별자를 **항상** 붙인다.

    예전에는 `--tag base`를 주면 fold 5개가 전부 같은 이름(`{stamp}_base`)으로 저장돼
    evaluate.summarize가 fold를 구분하지 못했고, `*base*of5*` 패턴과도 맞지 않았다.
    이제 `base_fold0of5` … `base_fold4of5`가 되어 ablation 비교(`evaluate compare`)가
    fold 단위로 짝지어진다."""
    base = user_tag or stage_slug
    return f"{base}_fold{fold_index}of{folds}" if folds > 1 else f"{base}_single"


def snapshot_config(run_dir: Path, extra: dict):
    """이 실행의 하이퍼파라미터 전체를 JSON으로 남깁니다(나중 비교의 유일한 근거)."""
    config = {
        "context_len": CONTEXT_LEN, "n_classes": N_CLASSES,
        "batch_size": BATCH_SIZE, "max_steps_per_epoch": MAX_STEPS_PER_EPOCH,
        "epochs": EPOCHS, "patience": PATIENCE, "monitor": "val_macro_f1",
        "focal_gamma": FOCAL_GAMMA, "label_smoothing": LABEL_SMOOTHING, "lambda_crf": LAMBDA_CRF,
        "transition_prior_weight": TRANSITION_PRIOR_WEIGHT,
        "use_class_weights": USE_CLASS_WEIGHTS,
        "aux_transition_loss_weight": AUX_TRANSITION_LOSS_WEIGHT,
        "n_time_buckets": N_TIME_BUCKETS,
        "channel_set": CHANNEL_SET, "n_channels": channel_count(CHANNEL_SET),
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


def count_accel_windows(directories, subject_ids_by_dir, stride):
    """이 분할에서 나오는 (중복 없는) 학습 윈도우 총 개수. steps_per_epoch을 데이터 크기에
    맞춰 잡기 위해 쓴다 — 예전에는 STEPS_PER_EPOCH이 300으로 고정돼 있어 한 에폭이 학습셋의
    1/4만 훑었다."""
    total = 0
    for d in directories:
        for sid in subject_ids_by_dir.get(d, []):
            cached = _MEM_CACHE.get((str(d), sid))
            if cached is None:
                continue
            n_epochs = len(cached[0])
            if n_epochs >= CONTEXT_LEN:
                total += (n_epochs - CONTEXT_LEN) // stride + 1
    return total


def _load_band_features(directory, sid, n_epochs):
    """`B_<sid>.npy`(epoch별 대역 파워)를 읽어온다. 없으면 0으로 채운 배열(= 그 채널의 기여 없음).
    `main.py preprocess bands` 가 만든다."""
    cache_key = ("bands", str(directory), sid)
    cached = _MEM_CACHE.get(cache_key)
    if cached is not None:
        return cached
    path = directory / f"B_{sid}.npy"
    n_bands = len(BAND_EDGES_HZ)
    if path.exists():
        bands = np.load(path).astype(np.float32)
        if len(bands) != n_epochs:
            print(f"경고: {path.name} 길이 불일치({len(bands)} != {n_epochs}) — 0으로 대체합니다.")
            bands = np.zeros((n_epochs, n_bands), dtype=np.float32)
    else:
        bands = np.zeros((n_epochs, n_bands), dtype=np.float32)
    _MEM_CACHE[cache_key] = bands
    return bands


def _extend_channels_for(x_slice, raw_time_feature, bands_slice, channel_set):
    """정규화된 기본 7채널 윈도우에 파생 채널을 덧붙인다.

    파생 채널은 **정규화하지 않는다** — sin/cos 는 이미 [-1,1] 이고 대역 파워는 상대비(0~1)라
    둘 다 다른 채널과 비슷한 스케일이다. 더 중요한 건 이 값들이 norm_stats.npy(7채널 기준)에
    들어 있지 않다는 점이고, 앱(Kotlin)도 같은 규칙을 따라야 학습/서빙이 일치한다.

    raw_time_feature: (CONTEXT_LEN,) — **정규화 전**의 time_feature(0~1). 위상은 여기서 만든다.
    bands_slice:      (CONTEXT_LEN, n_bands) — channel_set == "full" 일 때만 쓰인다.
    """
    if channel_set == "base":
        return x_slice
    sin_v, cos_v = circadian_channels(raw_time_feature)
    extras = [sin_v, cos_v]
    if channel_set == "full":
        extras.extend(bands_slice[:, b] for b in range(bands_slice.shape[1]))
    # 파생값은 epoch 하나 안에서 상수이므로 WINDOW 축으로 펼친다
    # (activity_variability/trend/time_feature 가 이미 쓰는 것과 같은 규약).
    stacked = np.stack(extras, axis=-1).astype(np.float32)  # (CONTEXT_LEN, n_extra)
    tiled = np.repeat(stacked[:, None, :], x_slice.shape[1], axis=1)
    return np.concatenate([x_slice, tiled], axis=-1)


def create_accel_generator(directories, subject_ids_by_dir, mean, std, stride, augment=False,
                           shuffle=True, repeat=True, channel_set=None):
    channel_set = channel_set or CHANNEL_SET
    n_bands = len(BAND_EDGES_HZ)

    def gen():
        pairs = [(d, sid) for d in directories for sid in subject_ids_by_dir.get(d, [])]
        while True:
            if shuffle:
                np.random.shuffle(pairs)
            for d, sid in pairs:
                X, y = _MEM_CACHE.get((str(d), sid), (None, None))
                if X is None:
                    continue
                bands = (_load_band_features(d, sid, len(X)) if channel_set == "full"
                         else np.zeros((len(X), n_bands), dtype=np.float32))
                starts = list(range(0, len(X) - CONTEXT_LEN + 1, stride))
                if shuffle:
                    np.random.shuffle(starts)
                for s in starts:
                    x_slice = np.asarray(X[s:s + CONTEXT_LEN], dtype=np.float32)
                    # 위상 채널은 **정규화 전**의 time_feature(0~1)에서 만들어야 한다.
                    raw_time_feature = x_slice[:, 0, TIME_FEATURE_CHANNEL].copy()
                    if augment:
                        x_slice = _apply_rotation_augment(x_slice)
                    x_slice = (x_slice - mean) / std
                    if augment:
                        x_slice = augment_accel(x_slice)
                    # 🐛 time_bucket 은 **정규화 전** 값에서 뽑아야 한다. 예전에는 정규화된
                    # x_slice 에서 읽어 밤의 앞 절반이 전부 bucket 0 으로 뭉개졌고, 평가 때는
                    # 아예 중간값 4로 고정돼 학습/서빙이 어긋나 있었다.
                    time_bucket = time_feature_bucket(float(raw_time_feature[-1]), N_TIME_BUCKETS)
                    x_slice = _extend_channels_for(
                        x_slice, raw_time_feature, bands[s:s + CONTEXT_LEN], channel_set
                    )
                    transition = _transition_labels(y, s, CONTEXT_LEN)
                    yield x_slice, time_bucket, y[s:s + CONTEXT_LEN], transition
            if not repeat:
                return
    return gen


def build_accel_dataset(directories, subject_ids_by_dir, mean, std, stride, augment=False,
                        shuffle=True, repeat=True, channel_set=None):
    channel_set = channel_set or CHANNEL_SET
    gen_fn = create_accel_generator(directories, subject_ids_by_dir, mean, std, stride, augment,
                                    shuffle, repeat, channel_set)
    return tf.data.Dataset.from_generator(
        gen_fn,
        output_signature=(
            tf.TensorSpec(shape=(CONTEXT_LEN, ACCEL_WINDOW, channel_count(channel_set)), dtype=tf.float32),
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
def run_accel_core(folds=5, fold_index=0, tag=None, epochs=EPOCHS, init_accel_encoder=None,
                   init_context_body=None):
    """가속도 인코더를 기존 지도 데이터(BIDSleep + Sleep-Accel)로 학습합니다.
    옛 train.py의 Stage 2(Accel 파인튜닝)와 같은 역할이지만, PSG 전이학습 없이 ctx_body를
    처음부터 함께 학습합니다(오디오 core가 별도 데이터로 학습되므로 더 이상 PSG가 ctx_body를
    먼저 형성해 줄 선행학습 소스가 아님 — 분리 인코더 전략의 직접적인 결과).

    init_accel_encoder(개선 6, 선택): run_accel_selfsup_wisdm이 저장한 사전학습
    accel_epoch_encoder.keras 경로. 주어지면 지도학습 시작 전에 그 가중치로 인코더를
    초기화합니다(라벨이 훨씬 적은 BIDSleep/Sleep-Accel만으로 처음부터 학습하는 대신, 더
    크고 다양한 WISDM 동작 데이터에서 먼저 "움직임이 어떻게 생겼는지"를 배운 뒤 시작)."""
    tag = compose_run_tag("accelcore", tag, folds, fold_index)
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

    save_class_distribution([BID_DIR, ACCEL_DIR], tr, run_dir / "accel_train_class_distribution.txt")
    # 예전에는 이 가중치를 계산해 출력만 하고 손실에 **전혀 넣지 않았다**(fit에 class_weight를
    # 넘기지 않았고 weighted_focal_loss에도 per-class 항이 없었다). 이제 손실로 들어간다.
    weights = compute_class_weights_multi([BID_DIR, ACCEL_DIR], tr) if USE_CLASS_WEIGHTS else None
    print(f"클래스 가중치: {weights if weights else '사용 안 함(--no-class-weights)'}")

    train_ds = build_accel_dataset([BID_DIR, ACCEL_DIR], tr, mean, std, ACCEL_STRIDE, augment=True).prefetch(tf.data.AUTOTUNE)
    # 검증셋은 **유한 + 비중첩**으로 바꾼다. 예전에는 무한 제네레이터를 shuffle=False로 돌리고
    # validation_steps=40(=1280 윈도)만 소비해서, 매 에폭 val subject 14명 중 앞 1~2명의
    # 똑같은 윈도만 반복 평가했다 — val_loss가 분할 전체를 대표하지 못했다.
    # stride=CONTEXT_LEN이면 val 에포크를 겹치지 않게 한 번씩만 덮는다.
    val_ds = build_accel_dataset(
        [BID_DIR, ACCEL_DIR], vl, mean, std, CONTEXT_LEN, shuffle=False, repeat=False
    ).prefetch(tf.data.AUTOTUNE)

    # 한 에폭이 학습셋 전체를 (대략) 한 번 훑도록 steps를 데이터 크기에서 유도한다.
    train_windows = count_accel_windows([BID_DIR, ACCEL_DIR], tr, ACCEL_STRIDE)
    steps_per_epoch = max(1, min(train_windows // BATCH_SIZE, MAX_STEPS_PER_EPOCH))
    val_windows = count_accel_windows([BID_DIR, ACCEL_DIR], vl, CONTEXT_LEN)
    print(f"학습 윈도 {train_windows:,}개 → steps_per_epoch={steps_per_epoch} "
          f"(batch {BATCH_SIZE}) / 검증 윈도 {val_windows:,}개(비중첩, 매 에폭 전체 소비)")

    y_sequences = [_MEM_CACHE[(str(d), sid)][1] for d, sid in tr_p if (str(d), sid) in _MEM_CACHE]
    init_transition = compute_transition_matrix(y_sequences)

    models = model_module.build_multimodal_model(
        audio_n_mel_frames=N_MEL_FRAMES, audio_n_mels=N_MELS,
        accel_window=ACCEL_WINDOW, accel_channels=channel_count(CHANNEL_SET),
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

    if init_context_body:
        ctx_path = Path(init_context_body)
        if not ctx_path.exists():
            print(f"경고: init_context_body 경로가 없습니다({ctx_path}) — ctx_body 를 처음부터 학습합니다.")
        else:
            # psg_context_pretrain 이 저장한 ctx_body. 두 모델 모두 n_domains=N_DOMAINS 로
            # 만들어지므로 도메인 임베딩 shape 이 맞는다(그래서 model.N_DOMAINS 를 한 곳에 뒀다).
            pretrained_ctx = tf.keras.models.load_model(ctx_path, compile=False)
            models["context_body"].set_weights(pretrained_ctx.get_weights())
            print(f"PSG 컨텍스트 사전학습 가중치 로드: {ctx_path}")

    lr = WarmupCosineDecay(peak_lr=4e-4, warmup_steps=steps_per_epoch * 2, decay_steps=steps_per_epoch * epochs)
    # 개선 5: accel_train_model(메인 분류 + 전이탐지 보조 출력 2개)로 학습합니다. accel_infer_model은
    # 같은 레이어(crf/ctx_body/accel_enc 등)를 공유하는 객체라 가중치가 함께 갱신되므로, 학습이
    # 끝난 뒤 보조 출력이 없는 accel_infer_model만 저장하면 배포/변환(convert.py)은 그대로입니다.
    accel_train_model.compile(
        optimizer=tf.keras.optimizers.AdamW(learning_rate=lr, weight_decay=1e-4, clipnorm=1.0),
        loss={
            # lambda_crf를 호출 시점에 넘긴다. 예전에는 기본 인자(def 시점 바인딩)에 의존해
            # --lambda-crf 가 무시되고 항상 1.0으로 학습됐다(그런데 config.json에는 넘긴 값이
            # 기록돼, ablation 비교가 사실상 같은 설정끼리의 비교였다).
            "accel_output": make_crf_combined_loss(
                crf, gamma=FOCAL_GAMMA, label_smoothing=LABEL_SMOOTHING,
                lambda_crf=LAMBDA_CRF, class_weights=weights,
            ),
            "accel_transition_output": tf.keras.losses.BinaryCrossentropy(),
        },
        loss_weights={"accel_output": 1.0, "accel_transition_output": AUX_TRANSITION_LOSS_WEIGHT},
    )
    # ValMacroF1이 logs에 val_macro_f1을 넣은 뒤에 EarlyStopping/ModelCheckpoint가 읽어야 하므로
    # 콜백 순서가 중요하다(Keras는 리스트 순서대로 on_epoch_end를 호출한다).
    callbacks = [
        ValMacroF1(val_ds),
        tf.keras.callbacks.EarlyStopping(monitor="val_macro_f1", mode="max",
                                         patience=PATIENCE, restore_best_weights=True),
        tf.keras.callbacks.ModelCheckpoint(str(run_dir / "best_model.keras"),
                                           monitor="val_macro_f1", mode="max", save_best_only=True),
        tf.keras.callbacks.CSVLogger(str(run_dir / "train_log.csv")),
    ]
    accel_train_model.fit(
        train_ds, validation_data=val_ds, epochs=epochs,
        steps_per_epoch=steps_per_epoch, callbacks=callbacks,
    )
    accel_infer_model.save(run_dir / "accel_inference_model.keras")
    # 🐛 예전에는 **초기** 전이행렬(init_transition)을 저장했다. 평가는 학습된 행렬
    # (exp(log_transitions))을 쓰는데 디스크에 남는 건 초기값이라, convert.py 가 이 파일을
    # 앱으로 내보내면 학습 결과가 아니라 prior 가 나가게 된다. 학습된 값을 저장한다.
    learned_transition = np.exp(crf.log_transitions.numpy())
    np.save(run_dir / "accel_transition_matrix.npy", learned_transition)
    np.save(run_dir / "accel_transition_matrix_init.npy", init_transition)

    print("\n===== Accel 테스트셋 평가 =====")
    eval_subjects = pairs_to_eval_subjects_accel(te_p, mean, std)
    # evaluate.evaluate_subjects는 (context_len, window, channels) 단일 입력 모델을 가정합니다.
    # accel_infer_model은 [accel_input, time_bucket_id] 2-입력이므로 얇은 래퍼로 감쌉니다.
    wrapped = _SingleInputEvalWrapper(accel_infer_model, N_TIME_BUCKETS)
    prepare_window, window_bucket = make_eval_window_fns()
    evaluate_subjects(
        wrapped, eval_subjects, CONTEXT_LEN, N_CLASSES, CLASS_NAMES, run_dir, "accel",
        transition_matrix=learned_transition,
        batch_size=EVAL_BATCH_SIZE, overlap_stride=EVAL_OVERLAP_STRIDE,
        prepare_window=prepare_window, window_bucket=window_bucket,
    )
    print(f"\n이 실행의 모든 산출물: {run_dir}")
    return run_dir


class _SingleInputEvalWrapper:
    """evaluate.evaluate_subjects 가 기대하는 `model.predict(batch)` 인터페이스를,
    실제로는 [도메인 입력, time_bucket_id] 2-입력을 받는 *_infer_model 위에 씌우는 얇은 래퍼.

    🐛 예전에는 time_bucket_id 를 **중간값(4)으로 고정**했다. 학습 쪽은 윈도마다 실제 값을
    넣는데 평가에서만 상수를 넣으니 train/serve 가 어긋났고, 게다가 학습 쪽 버킷 계산도
    정규화된 값에서 뽑고 있어 실제로는 4가 거의 등장하지 않는 값이었다. 이제 평가도
    윈도별 실제 버킷을 받는다(evaluate.predict_night_probs 의 window_bucket).
    """

    def __init__(self, infer_model, n_time_buckets):
        self._model = infer_model
        self._fallback_bucket = n_time_buckets // 2

    def predict(self, batch, verbose=0, time_buckets=None):
        batch_size = batch.shape[0]
        if time_buckets is None:
            time_bucket = np.full((batch_size,), self._fallback_bucket, dtype=np.int32)
        else:
            time_bucket = np.asarray(time_buckets, dtype=np.int32)
        inputs = {self._model.input_names[0]: batch, "time_bucket_id": time_bucket}
        return self._model.predict(inputs, verbose=verbose)


def make_eval_window_fns(channel_set=None):
    """평가 경로가 학습과 **같은** 윈도 변환을 쓰도록 (prepare_window, window_bucket) 쌍을 만든다.

    학습 제네레이터(create_accel_generator)와 한 글자도 다르면 안 되는 부분이라 여기 한 곳에
    모아 둔다 — 정규화 → 파생 채널 덧붙이기 순서, 그리고 버킷을 **정규화 전** 값에서 뽑는 것.
    """
    channel_set = channel_set or CHANNEL_SET
    n_bands = len(BAND_EDGES_HZ)

    def prepare_window(x_slice, mean, std):
        raw = np.asarray(x_slice, dtype=np.float32)
        raw_time_feature = raw[:, 0, TIME_FEATURE_CHANNEL].copy()
        normalized = (raw - mean) / std
        # 평가 경로에는 B_*.npy 를 윈도 단위로 끌어올 수단이 없어 0으로 둔다.
        # ⚠️ 그래서 channel_set="full" 로 학습했다면 평가의 대역 채널은 전부 0이 되어
        #    지표가 과소평가된다 — full 구성을 실제로 채택할 때 이 경로를 먼저 보완할 것.
        bands = np.zeros((raw.shape[0], n_bands), dtype=np.float32)
        return _extend_channels_for(normalized, raw_time_feature, bands, channel_set)

    def window_bucket(x_slice):
        raw = np.asarray(x_slice, dtype=np.float32)
        return time_feature_bucket(float(raw[-1, 0, TIME_FEATURE_CHANNEL]), N_TIME_BUCKETS)

    return prepare_window, window_bucket


# ============================================================
# 4-b. 스테이지: psg_context_pretrain — Sleep-EDF(PSG)로 컨텍스트 트랜스포머 사전학습
# ============================================================
SLEEP_PERIOD_PAD_EPOCHS = 60  # 수면구간 앞뒤로 남길 여유(60 epoch = 30분)


def trim_to_sleep_period(y, pad_epochs=SLEEP_PERIOD_PAD_EPOCHS):
    """첫 비-Wake epoch 의 pad 이전 ~ 마지막 비-Wake epoch 의 pad 이후만 남기는 슬라이스를 돌려준다.

    Sleep-EDF Cassette 녹화는 하루 24시간을 통째로 담고 있어서, 자르지 않으면 Wake 가 전체의
    63.5% 를 차지한다(실측). 그 대부분은 "잠들기 한참 전"과 "일어난 뒤 한참"이라 수면 구조와
    무관한 구간이다. 30분 여유를 두고 잘라내는 것이 Sleep-EDF 의 표준 전처리다.

    전부 Wake 인 녹화는 (0, 0) 을 돌려준다 — 호출부가 건너뛴다.
    """
    non_wake = np.flatnonzero(y != 0)
    if non_wake.size == 0:
        return 0, 0
    start = max(0, int(non_wake[0]) - pad_epochs)
    end = min(len(y), int(non_wake[-1]) + pad_epochs + 1)
    return start, end


def run_psg_context_pretrain(folds=5, fold_index=0, tag=None, epochs=EPOCHS):
    """Sleep-EDF 197명으로 컨텍스트 트랜스포머(ctx_body)를 먼저 학습시킨다.

    accel_core 는 라벨 있는 피험자가 72명뿐이라 "밤이 어떻게 흘러가는가"를 배우기에 데이터가
    얇다. EEG 는 단계 판별이 쉬우므로 그 구조를 197명치로 먼저 배워 두고, 그 ctx_body 가중치를
    accel_core 에 `--init-context-body` 로 넘긴다. epoch 인코더는 공유하지 않는다(신호의
    물리적 의미가 전혀 다르다).
    """
    tag = compose_run_tag("psgctx", tag, folds, fold_index)
    run_dir = make_run_dir(tag)

    if not EDF_NORM_STATS_PATH.exists():
        print(f"norm_stats 없음: {EDF_NORM_STATS_PATH} — "
              f"`python main.py preprocess stats --domain edf` 를 먼저 실행하세요.")
        return None
    if not EDF_DIR.exists():
        print(f"건너뜀: {EDF_DIR} 없음 (Sleep-EDF 전처리 미완료)")
        return None

    stats = np.load(EDF_NORM_STATS_PATH, allow_pickle=True).item()
    mean, std = stats["mean"], stats["std"]

    pairs = list_subject_pairs([EDF_DIR])
    if not pairs:
        print(f"건너뜀: {EDF_DIR} 에 X_*.npy 가 없습니다.")
        return None
    tr_p, vl_p, te_p = split_subject_pairs(pairs, folds=folds, fold_index=fold_index)
    load_subjects_lazy(EDF_DIR, [sid for d, sid in pairs])
    tr, vl = map(pairs_to_by_dir, (tr_p, vl_p))

    print(f"\nPSG 분할(fold {fold_index}/{folds}): train {len(tr_p)} / val {len(vl_p)} / test {len(te_p)} subject")

    snapshot_config(run_dir, {
        "stage": "psg_context_pretrain", "folds": folds, "fold_index": fold_index, "tag": tag,
        "sleep_period_pad_epochs": SLEEP_PERIOD_PAD_EPOCHS,
        "train_subjects": [f"{d.parent.name}/{s}" for d, s in tr_p],
        "val_subjects": [f"{d.parent.name}/{s}" for d, s in vl_p],
    })

    # 수면구간으로 자른 뒤의 분포로 클래스 가중치를 잡는다(자르기 전 Wake 63.5% 기준으로
    # 잡으면 Wake 를 지나치게 눌러 버린다).
    train_ds = build_psg_dataset([EDF_DIR], tr, mean, std, shuffle=True, repeat=True)
    val_ds = build_psg_dataset([EDF_DIR], vl, mean, std, shuffle=False, repeat=False)

    models = model_module.build_psg_pretrain_model(
        psg_window=ACCEL_WINDOW, psg_channels=ACCEL_CHANNELS,
        context_len=CONTEXT_LEN, n_classes=N_CLASSES, n_time_buckets=N_TIME_BUCKETS,
    )
    train_model = models["train_model"]

    lr = WarmupCosineDecay(peak_lr=4e-4, warmup_steps=STEPS_PER_EPOCH * 2,
                           decay_steps=STEPS_PER_EPOCH * epochs)
    train_model.compile(
        optimizer=tf.keras.optimizers.AdamW(learning_rate=lr, weight_decay=1e-4, clipnorm=1.0),
        loss=weighted_focal_loss(FOCAL_GAMMA, LABEL_SMOOTHING),
    )
    callbacks = [
        tf.keras.callbacks.EarlyStopping(monitor="val_loss", patience=PATIENCE, restore_best_weights=True),
        tf.keras.callbacks.CSVLogger(str(run_dir / "train_log.csv")),
    ]
    train_model.fit(
        train_ds, validation_data=val_ds, epochs=epochs,
        steps_per_epoch=STEPS_PER_EPOCH, callbacks=callbacks,
    )

    out_path = run_dir / "context_body_psg_pretrained.keras"
    models["context_body"].save(out_path)
    print(f"\nctx_body 사전학습 가중치 저장: {out_path}")
    print(f"accel_core 에 넘기려면: --init-context-body {out_path}")
    return run_dir


def create_psg_generator(directories, subject_ids_by_dir, mean, std, shuffle=True, repeat=True):
    """PSG 윈도우 제네레이터. accel 쪽과 다른 점은 (1) 수면구간으로 먼저 자르고,
    (2) 증강(회전/스케일)을 하지 않는다는 것 — EEG 축은 회전 같은 물리적 의미가 없다."""
    def gen():
        pairs = [(d, sid) for d in directories for sid in subject_ids_by_dir.get(d, [])]
        while True:
            if shuffle:
                np.random.shuffle(pairs)
            for d, sid in pairs:
                X, y = _MEM_CACHE.get((str(d), sid), (None, None))
                if X is None:
                    continue
                lo, hi = trim_to_sleep_period(y)
                if hi - lo < CONTEXT_LEN:
                    continue
                starts = list(range(lo, hi - CONTEXT_LEN + 1, CONTEXT_LEN))
                if shuffle:
                    np.random.shuffle(starts)
                for s in starts:
                    x_slice = np.asarray(X[s:s + CONTEXT_LEN], dtype=np.float32)
                    x_slice = (x_slice - mean) / std
                    time_bucket = _time_bucket_from_channels(x_slice)
                    yield x_slice, time_bucket, y[s:s + CONTEXT_LEN]
            if not repeat:
                return
    return gen


def build_psg_dataset(directories, subject_ids_by_dir, mean, std, shuffle=True, repeat=True):
    gen_fn = create_psg_generator(directories, subject_ids_by_dir, mean, std, shuffle, repeat)
    return tf.data.Dataset.from_generator(
        gen_fn,
        output_signature=(
            tf.TensorSpec(shape=(CONTEXT_LEN, ACCEL_WINDOW, ACCEL_CHANNELS), dtype=tf.float32),
            tf.TensorSpec(shape=(), dtype=tf.int32),
            tf.TensorSpec(shape=(CONTEXT_LEN,), dtype=tf.int32),
        )
    ).batch(BATCH_SIZE, drop_remainder=True).map(
        lambda x, tb, y: ({"psg_input": x, "time_bucket_id": tb}, y)
    ).prefetch(tf.data.AUTOTUNE)


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
