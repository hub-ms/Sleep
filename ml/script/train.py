import gc
import time
from pathlib import Path
import numpy as np
import tensorflow as tf
import keras
from sklearn.metrics import classification_report, confusion_matrix
from model import build_dual_domain_model
from postprocess import mode_filter, viterbi_smooth

tf.keras.mixed_precision.set_global_policy("mixed_bfloat16")

# 경로 설정
# 💡 cwd(현재 작업 디렉토리)가 로컬/Colab에서 다를 수 있어(ml/, ml/script/ 등) cwd에 의존하지
# 않도록 이 파일(ml/script/train.py) 자신의 위치를 기준으로 ml/ 폴더를 찾습니다.
BASE_DIR = Path(__file__).resolve().parent.parent
OUTPUT_DIR = BASE_DIR / "output"
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

EDF_DIR    = BASE_DIR / "data" / "sleep_edf" / "subjects"
BID_DIR    = BASE_DIR / "data" / "bidsleep" / "subjects"
ACCEL_DIR  = BASE_DIR / "data" / "sleep_accel" / "subjects"

PSG_NORM_STATS_PATH        = BASE_DIR / "data" / "sleep_edf" / "norm_stats.npy"
# 💡 bidsleep과 sleep_accel은 서로 다른 기기/수집 환경의 데이터라 정규화 통계를 하나로
# 합치지 않고 소스별로 따로 계산합니다(compute_stats.py도 이 두 경로에 각각 저장).
BID_NORM_STATS_PATH        = BASE_DIR / "data" / "bidsleep" / "norm_stats.npy"
SLEEP_ACCEL_NORM_STATS_PATH = BASE_DIR / "data" / "sleep_accel" / "norm_stats.npy"

# 하이퍼파라미터
PSG_WINDOW, PSG_CHANNELS = 1500, 8
ACCEL_WINDOW, ACCEL_CHANNELS = 1500, 8
N_CLASSES = 4
CONTEXT_LEN = 60
PSG_STRIDE = 3
ACCEL_STRIDE = 3

# 💡 학습 시간 단축 시도: BATCH_SIZE를 64로 올려봤더니 스텝당 시간이 오히려 8배 이상
# 느려졌습니다(32일 때 ~103ms/step -> 64일 때 ~860~920ms/step, 실측). 이는 병목이 GPU
# 연산이 아니라 Python 쪽 tf.data 제네레이터(파일 읽기+정규화)라는 뜻이라 32로 되돌립니다.
# STEPS_PER_EPOCH 600->300은 epoch당 실제 걸리는 시간을 절반으로 줄입니다. 다만 이러면
# EarlyStopping의 patience(epoch 단위)가 실질적으로 절반의 step만 견디는 셈이 되어 너무
# 일찍 멈출 수 있으므로, 아래 두 Stage의 patience를 절반→2배로 늘려 이전과 동일한
# "step 기준 허용치"를 유지합니다(Stage1: 8->16, Stage2: 12->24).
BATCH_SIZE = 32
STEPS_PER_EPOCH = 300
VAL_STEPS = 40

# 💡 2단계 학습(Stage 1: PSG 사전학습 → Stage 2: 공유 바디 고정 후 Accel 파인튜닝)의
# 단계별 epoch 수. 이전 EPOCHS=60(단일 단계 공동학습) 예산을 두 단계로 나눴습니다.
PSG_PRETRAIN_EPOCHS = 30
ACCEL_FINETUNE_EPOCHS = 30
STAGE1_PATIENCE = 16
STAGE2_PATIENCE = 24

FOCAL_GAMMA = 1.0
LABEL_SMOOTHING = 0.1

CLASS_NAMES = ["Wake", "Light", "Deep", "REM"]

_MEM_CACHE = {}

# ============================================================
# 1. LR Scheduler & Optimizer
# ============================================================
class WarmupCosineDecay(tf.keras.optimizers.schedules.LearningRateSchedule):
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

# ============================================================
# 2. Loss Function
# ============================================================
def weighted_focal_loss(gamma=1.0, label_smoothing=0.1):
    def loss_fn(y_true, y_pred):
        y_true = tf.cast(y_true, tf.int32)
        y_pred = tf.cast(y_pred, tf.float32)
        
        # Label Smoothing
        one_hot_y = tf.one_hot(y_true, depth=N_CLASSES)
        one_hot_y = one_hot_y * (1.0 - label_smoothing) + (label_smoothing / N_CLASSES)
        
        # Focal Loss (from_logits=True)
        ce = tf.keras.losses.categorical_crossentropy(one_hot_y, y_pred, from_logits=True)
        probs = tf.nn.softmax(y_pred, axis=-1)
        pt = tf.reduce_sum(one_hot_y * probs, axis=-1)
        focal_weight = tf.pow(1.0 - pt, gamma)
        
        return focal_weight * ce
    return loss_fn

# ============================================================
# 3. Data Utilities & Augmentation
# ============================================================
def load_subjects_lazy(directory: Path, subject_ids: list):
    for sid in subject_ids:
        cache_key = (str(directory), sid)
        if cache_key in _MEM_CACHE: continue
        x_p, y_p = directory / f"X_{sid}.npy", directory / f"y_{sid}.npy"
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
                    if uu < N_CLASSES: counts[uu] += cc
    total = np.sum(counts)
    # sqrt inverse weighting
    weights = total / (N_CLASSES * (counts.astype(np.float32) ** 0.5) + 1e-6)
    weights = np.clip(weights / np.mean(weights), min_weight, max_weight)
    return weights.astype(np.float32).tolist()

def count_context_windows(directories, subject_ids_by_dir, stride):
    """주어진 subject 목록에서 stride 간격으로 뽑을 수 있는 CONTEXT_LEN 길이 윈도우 총 개수."""
    total = 0
    for d in directories:
        for sid in subject_ids_by_dir.get(d, []):
            cached = _MEM_CACHE.get((str(d), sid))
            if not cached:
                continue
            n = len(cached[0])
            if n >= CONTEXT_LEN:
                total += (n - CONTEXT_LEN) // stride + 1
    return total


def save_class_distribution(directories, subject_ids_by_dir, out_path):
    """클래스 분포를 stdout 출력으로 흘려보내지 않고 파일로 남겨 재현/비교 가능하게 합니다."""
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


def augment_accel(x_slice):
    # Light augmentation for Accel domain
    # Scaling (±10%)
    if np.random.rand() > 0.5:
        scale = np.random.uniform(0.9, 1.1)
        x_slice[:, :, :6] *= scale # Scale accel, gyro, hr (first 6 channels)
    # Jitter
    if np.random.rand() > 0.5:
        noise = np.random.normal(0, 0.01, size=x_slice.shape).astype(np.float32)
        x_slice += noise
    return x_slice

def create_generator(directories, subject_ids_by_dir, mean_by_dir, std_by_dir, stride, augment=False, shuffle=True):
    """mean_by_dir/std_by_dir: {directory: FloatArray} — 소스 디렉토리별로 서로 다른 정규화
    통계를 적용합니다(예: bidsleep과 sleep_accel은 통계값 자체가 다름)."""
    def gen():
        pairs = [(d, sid) for d in directories for sid in subject_ids_by_dir.get(d, [])]
        while True:
            if shuffle: np.random.shuffle(pairs)
            for d, sid in pairs:
                X, y = _MEM_CACHE.get((str(d), sid), (None, None))
                if X is None: continue
                mean, std = mean_by_dir[d], std_by_dir[d]
                starts = list(range(0, len(X) - CONTEXT_LEN + 1, stride))
                if shuffle: np.random.shuffle(starts)
                for s in starts:
                    x_slice = np.asarray(X[s:s+CONTEXT_LEN], dtype=np.float32)
                    x_slice = (x_slice - mean) / std
                    if augment: x_slice = augment_accel(x_slice)
                    yield x_slice, y[s:s+CONTEXT_LEN]
    return gen

def build_dataset(directories, subject_ids_by_dir, mean_by_dir, std_by_dir, window, channels, stride, augment=False, shuffle=True):
    gen_fn = create_generator(directories, subject_ids_by_dir, mean_by_dir, std_by_dir, stride, augment, shuffle)
    return tf.data.Dataset.from_generator(
        gen_fn,
        output_signature=(
            tf.TensorSpec(shape=(CONTEXT_LEN, window, channels), dtype=tf.float32),
            tf.TensorSpec(shape=(CONTEXT_LEN,), dtype=tf.int32),
        )
    ).batch(BATCH_SIZE, drop_remainder=True)

def compute_transition_matrix(directories, subject_ids_by_dir, n_classes=N_CLASSES, laplace=1.0):
    """💡 Phase 5(후처리): 학습 데이터의 실제 라벨 시퀀스(윈도우/stride 적용 전, subject 전체
    연속열)에서 경험적 상태전이 확률을 계산합니다. Viterbi 스무딩의 전이행렬로 사용합니다.
    (output/에 남아있던 *_transition_matrix.npy는 생성 코드가 없는 정체불명 산출물이라
    신뢰하지 않고, 매번 현재 학습 데이터에서 새로 계산합니다.)"""
    counts = np.full((n_classes, n_classes), laplace, dtype=np.float64)  # 라플라스 스무딩(0 확률 방지)
    for d in directories:
        for sid in subject_ids_by_dir.get(d, []):
            cached = _MEM_CACHE.get((str(d), sid))
            if not cached:
                continue
            y = cached[1]
            for a, b in zip(y[:-1], y[1:]):
                if 0 <= a < n_classes and 0 <= b < n_classes:
                    counts[a, b] += 1
    return counts / counts.sum(axis=1, keepdims=True)


def _report_and_save(y_true, y_pred, label):
    report = classification_report(
        y_true, y_pred,
        labels=list(range(N_CLASSES)),
        target_names=CLASS_NAMES,
        digits=4,
        zero_division=0,
    )
    cm = confusion_matrix(y_true, y_pred, labels=list(range(N_CLASSES)))

    print(f"\n=== {label} 테스트셋 평가 (n={len(y_true)} epochs) ===")
    print(report)
    print("Confusion matrix (rows=true, cols=pred):")
    print(cm)

    (OUTPUT_DIR / f"{label}_classification_report.txt").write_text(report, encoding="utf-8")
    np.save(OUTPUT_DIR / f"{label}_confusion_matrix.npy", cm)
    return report, cm


def evaluate_domain(infer_model, dataset, steps, label, transition_matrix=None, mode_window=5):
    """테스트셋에서 실제로 classification_report/confusion matrix를 계산해 파일로 저장합니다.
    (기존에는 edf_te/bid_te/acc_te가 계산만 되고 어디서도 쓰이지 않아 baseline F1을 측정할 방법이 없었습니다.)

    💡 Phase 5(후처리 연결): 배치의 각 행(윈도우)은 CONTEXT_LEN개 연속 epoch로 이루어진 온전한
    시계열이므로, 전체를 이어붙인 뒤가 아니라 윈도우 단위로 다수결 필터/Viterbi 스무딩을 적용합니다
    (이어붙이면 서로 다른 윈도우/subject 경계를 하나의 시퀀스처럼 스무딩해버리는 문제가 생김).
    원본(raw)과 스무딩 결과를 모두 저장해 실제로 도움이 되는지 비교할 수 있게 합니다."""
    y_true_all, y_pred_all, y_pred_mode_all = [], [], []
    y_pred_viterbi_all = [] if transition_matrix is not None else None

    it = iter(dataset)
    for _ in range(steps):
        x_batch, y_batch = next(it)
        logits = infer_model.predict(x_batch, verbose=0)  # (batch, CONTEXT_LEN, N_CLASSES)
        preds = np.argmax(logits, axis=-1)  # (batch, CONTEXT_LEN)

        y_true_all.append(y_batch.numpy().reshape(-1))
        y_pred_all.append(preds.reshape(-1))
        y_pred_mode_all.append(np.stack([mode_filter(row, window=mode_window) for row in preds]).reshape(-1))

        if transition_matrix is not None:
            probs = tf.nn.softmax(logits, axis=-1).numpy()
            y_pred_viterbi_all.append(
                np.stack([viterbi_smooth(p, transition_matrix) for p in probs]).reshape(-1)
            )

    y_true = np.concatenate(y_true_all)
    raw_report, raw_cm = _report_and_save(y_true, np.concatenate(y_pred_all), label)
    _report_and_save(y_true, np.concatenate(y_pred_mode_all), f"{label}_mode_smoothed")
    if transition_matrix is not None:
        _report_and_save(y_true, np.concatenate(y_pred_viterbi_all), f"{label}_viterbi_smoothed")

    return raw_report, raw_cm


def _add_sample_weights(x, y, class_weights):
    """(x, y) 배치에 클래스별 sample_weight를 붙여 (x, y, sample_weight) 3-튜플로 만듭니다.
    model.fit()이 세 번째 원소를 자동으로 sample_weight로 사용합니다."""
    sw = tf.gather(tf.constant(class_weights, dtype=tf.float32), y)
    return x, y, sw

# ============================================================
# 4. Main Execution
# ============================================================
def main():
    # Stats Load
    # 💡 bidsleep/sleep_accel을 각각 독립적으로 정규화합니다(통계값 자체가 다름).
    psg_stats = np.load(PSG_NORM_STATS_PATH, allow_pickle=True).item()
    bid_stats = np.load(BID_NORM_STATS_PATH, allow_pickle=True).item()
    sleep_accel_stats = np.load(SLEEP_ACCEL_NORM_STATS_PATH, allow_pickle=True).item()
    psg_m, psg_s = psg_stats["mean"], psg_stats["std"]

    psg_mean_by_dir = {EDF_DIR: psg_m}
    psg_std_by_dir = {EDF_DIR: psg_s}
    accel_mean_by_dir = {BID_DIR: bid_stats["mean"], ACCEL_DIR: sleep_accel_stats["mean"]}
    accel_std_by_dir = {BID_DIR: bid_stats["std"], ACCEL_DIR: sleep_accel_stats["std"]}

    # Subject Split
    def get_sids(d): return sorted(p.name.replace("X_", "").replace(".npy", "") for p in d.glob("X_*.npy"))
    edf_sids, bid_sids, acc_sids = get_sids(EDF_DIR), get_sids(BID_DIR), get_sids(ACCEL_DIR)
    
    def split(s): return s[:int(len(s)*0.7)], s[int(len(s)*0.7):int(len(s)*0.85)], s[int(len(s)*0.85):]
    edf_tr, edf_vl, edf_te = split(edf_sids)
    bid_tr, bid_vl, bid_te = split(bid_sids)
    acc_tr, acc_vl, acc_te = split(acc_sids)

    load_subjects_lazy(EDF_DIR, edf_tr + edf_vl + edf_te)
    load_subjects_lazy(BID_DIR, bid_tr + bid_vl + bid_te)
    load_subjects_lazy(ACCEL_DIR, acc_tr + acc_vl + acc_te)

    psg_w = compute_class_weights_multi([EDF_DIR], {EDF_DIR: edf_tr})
    acc_w = compute_class_weights_multi([BID_DIR, ACCEL_DIR], {BID_DIR: bid_tr, ACCEL_DIR: acc_tr})
    print(f"Weights - PSG: {psg_w}, Accel: {acc_w}")

    # 클래스 분포는 이전에는 stdout에만 찍히고 사라졌습니다. 이후 개선(불균형 처리 등) 전후
    # 비교를 위해 파일로 남깁니다.
    save_class_distribution([EDF_DIR], {EDF_DIR: edf_tr}, OUTPUT_DIR / "psg_train_class_distribution.txt")
    save_class_distribution([BID_DIR, ACCEL_DIR], {BID_DIR: bid_tr, ACCEL_DIR: acc_tr}, OUTPUT_DIR / "accel_train_class_distribution.txt")

    # Datasets — 2단계 학습이므로 PSG/Accel을 zip해 하나로 묶지 않고 도메인별로 독립적으로 만듭니다.
    psg_train_ds = build_dataset(
        [EDF_DIR], {EDF_DIR: edf_tr}, psg_mean_by_dir, psg_std_by_dir, PSG_WINDOW, PSG_CHANNELS, PSG_STRIDE, augment=False
    ).map(lambda x, y: _add_sample_weights(x, y, psg_w)).prefetch(tf.data.AUTOTUNE)
    psg_val_ds = build_dataset(
        [EDF_DIR], {EDF_DIR: edf_vl}, psg_mean_by_dir, psg_std_by_dir, PSG_WINDOW, PSG_CHANNELS, PSG_STRIDE, shuffle=False
    ).prefetch(tf.data.AUTOTUNE)

    accel_train_ds = build_dataset(
        [BID_DIR, ACCEL_DIR], {BID_DIR: bid_tr, ACCEL_DIR: acc_tr}, accel_mean_by_dir, accel_std_by_dir,
        ACCEL_WINDOW, ACCEL_CHANNELS, ACCEL_STRIDE, augment=True
    ).map(lambda x, y: _add_sample_weights(x, y, acc_w)).prefetch(tf.data.AUTOTUNE)
    accel_val_ds = build_dataset(
        [BID_DIR, ACCEL_DIR], {BID_DIR: bid_vl, ACCEL_DIR: acc_vl}, accel_mean_by_dir, accel_std_by_dir,
        ACCEL_WINDOW, ACCEL_CHANNELS, ACCEL_STRIDE, shuffle=False
    ).prefetch(tf.data.AUTOTUNE)

    # Model Build
    models = build_dual_domain_model(context_len=CONTEXT_LEN, n_classes=N_CLASSES)
    psg_infer_model = models["psg_infer_model"]
    accel_infer_model = models["accel_infer_model"]
    context_body = models["context_body"]

    # ============================================================
    # Stage 1: PSG(EEG/EOG/EMG) 사전학습 — 공유 컨텍스트 트랜스포머를 형성합니다.
    # 💡 학습 시간 단축: Stage 2에서 실패해 재시도할 때마다 이미 끝난 Stage 1(PSG)을 처음부터
    # 다시 돌리는 게 낭비라, 저장된 체크포인트가 있으면 재학습 없이 그 가중치를 불러와
    # 건너뜁니다. PSG 데이터/모델 구조를 바꿔서 Stage 1을 진짜로 다시 돌리고 싶다면
    # output/psg_inference_model.keras를 지우고 실행하세요.
    # ============================================================
    psg_checkpoint_path = OUTPUT_DIR / "psg_inference_model.keras"
    resumed_stage1 = False
    if psg_checkpoint_path.exists():
        try:
            psg_infer_model.load_weights(psg_checkpoint_path)
            resumed_stage1 = True
            print(f"\n===== Stage 1 건너뜀: 기존 체크포인트 재사용 ({psg_checkpoint_path}) =====")
        except Exception as e:
            print(f"기존 PSG 체크포인트를 불러오지 못해 Stage 1을 처음부터 진행합니다: {type(e).__name__}: {e}")

    if not resumed_stage1:
        print("\n===== Stage 1: PSG 사전학습 (공유 컨텍스트 트랜스포머 형성) =====")
        stage1_lr = WarmupCosineDecay(
            peak_lr=4e-4, warmup_steps=STEPS_PER_EPOCH * 2, decay_steps=STEPS_PER_EPOCH * PSG_PRETRAIN_EPOCHS
        )
        psg_infer_model.compile(
            optimizer=tf.keras.optimizers.AdamW(learning_rate=stage1_lr, weight_decay=1e-4, clipnorm=1.0),
            loss=weighted_focal_loss(gamma=FOCAL_GAMMA),
        )
        stage1_cbs = [
            tf.keras.callbacks.EarlyStopping(monitor="val_loss", patience=STAGE1_PATIENCE, restore_best_weights=True),
            tf.keras.callbacks.CSVLogger(str(OUTPUT_DIR / "train_log_stage1_psg.csv")),
        ]
        psg_infer_model.fit(
            psg_train_ds, validation_data=psg_val_ds,
            epochs=PSG_PRETRAIN_EPOCHS, steps_per_epoch=STEPS_PER_EPOCH, validation_steps=VAL_STEPS,
            callbacks=stage1_cbs,
        )
        psg_infer_model.save(psg_checkpoint_path)

    # ============================================================
    # Stage 2: 공유 바디를 고정하고 Accel 인코더만 이어서 학습합니다.
    # 같은 세션의 accel+PSG 페어 데이터가 없어 고전적인 soft-label distillation(같은 입력을
    # teacher/student에 동시에 넣어 출력 확률을 맞추는 방식)은 적용할 수 없습니다. 대신 PSG로
    # 형성된 공유 표현 공간(context_body)을 고정한 채 Accel 인코더가 그 공간에 맞춰 적응하도록
    # 하는 방식으로 지식을 전이합니다 — 온디바이스에 배포되는 accel_infer_model이 추론 시
    # EEG 없이도 PSG에서 형성된 표현을 간접적으로 물려받습니다.
    # ============================================================
    print("\n===== Stage 2: 공유 바디 고정, Accel 인코더 파인튜닝 =====")
    context_body.trainable = False

    # 🐛 버그 수정: context_body 안의 도메인별 FiLM 조건화(domain_gamma/domain_beta Embedding)는
    # Stage 1에서 domain_id=0(PSG)만 사용하므로 PSG용 행만 학습되고 Accel용 행(domain_id=1)은
    # 무작위 초기값 그대로 남습니다. 그런데 바로 위에서 context_body 전체를 얼려버리면 이
    # Embedding도 함께 얼어붙어, Accel 인코더가 아무리 학습해도 그 뒤에 학습된 적 없는 무작위
    # 조건화가 신호를 망가뜨려 Accel 쪽이 최빈 클래스로 붕괴했습니다(실측: macro F1 0.17,
    # Wake/Deep/REM의 97~98%를 Light로 오분류). 트랜스포머 본체(어텐션/FF)는 그대로 얼리되,
    # 이 두 Embedding만 다시 학습 가능하게 풀어 Accel 도메인 조건화가 실제로 학습되게 합니다.
    for layer in context_body.layers:
        if layer.name in ("domain_gamma", "domain_beta"):
            layer.trainable = True

    stage2_lr = WarmupCosineDecay(
        peak_lr=4e-4, warmup_steps=STEPS_PER_EPOCH * 2, decay_steps=STEPS_PER_EPOCH * ACCEL_FINETUNE_EPOCHS
    )
    accel_infer_model.compile(
        optimizer=tf.keras.optimizers.AdamW(learning_rate=stage2_lr, weight_decay=1e-4, clipnorm=1.0),
        loss=weighted_focal_loss(gamma=FOCAL_GAMMA),
    )
    stage2_cbs = [
        tf.keras.callbacks.EarlyStopping(monitor="val_loss", patience=STAGE2_PATIENCE, restore_best_weights=True),
        tf.keras.callbacks.ModelCheckpoint(str(OUTPUT_DIR / "best_model.keras"), save_best_only=True),
        # .gitignore가 이 경로를 이미 기대하고 있었지만 실제로 기록하는 콜백이 없었습니다.
        tf.keras.callbacks.CSVLogger(str(OUTPUT_DIR / "train_log.csv")),
    ]
    accel_infer_model.fit(
        accel_train_ds, validation_data=accel_val_ds,
        epochs=ACCEL_FINETUNE_EPOCHS, steps_per_epoch=STEPS_PER_EPOCH, validation_steps=VAL_STEPS,
        callbacks=stage2_cbs,
    )
    accel_infer_model.save(OUTPUT_DIR / "accel_inference_model.keras")

    # 💡 Phase 0: edf_te/bid_te/acc_te는 이전에는 계산만 되고 한 번도 쓰이지 않아
    # baseline F1을 측정할 방법이 없었습니다. 학습 종료 후 held-out 테스트셋으로 실제 평가합니다.
    test_psg_ds = build_dataset(
        [EDF_DIR], {EDF_DIR: edf_te}, psg_mean_by_dir, psg_std_by_dir, PSG_WINDOW, PSG_CHANNELS, PSG_STRIDE, shuffle=False
    )
    test_accel_ds = build_dataset(
        [BID_DIR, ACCEL_DIR], {BID_DIR: bid_te, ACCEL_DIR: acc_te}, accel_mean_by_dir, accel_std_by_dir,
        ACCEL_WINDOW, ACCEL_CHANNELS, ACCEL_STRIDE, shuffle=False
    )
    psg_test_steps = max(1, count_context_windows([EDF_DIR], {EDF_DIR: edf_te}, PSG_STRIDE) // BATCH_SIZE)
    accel_test_steps = max(
        1,
        count_context_windows([BID_DIR, ACCEL_DIR], {BID_DIR: bid_te, ACCEL_DIR: acc_te}, ACCEL_STRIDE) // BATCH_SIZE
    )

    # 💡 Phase 5: 후처리(다수결/Viterbi 스무딩)용 상태전이행렬은 학습셋 라벨 시퀀스에서 계산합니다.
    psg_transition_matrix = compute_transition_matrix([EDF_DIR], {EDF_DIR: edf_tr})
    accel_transition_matrix = compute_transition_matrix([BID_DIR, ACCEL_DIR], {BID_DIR: bid_tr, ACCEL_DIR: acc_tr})

    evaluate_domain(psg_infer_model, test_psg_ds, psg_test_steps, "psg", transition_matrix=psg_transition_matrix)
    evaluate_domain(accel_infer_model, test_accel_ds, accel_test_steps, "accel", transition_matrix=accel_transition_matrix)

if __name__ == "__main__":
    main()
