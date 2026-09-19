import gc
import time
from pathlib import Path
import numpy as np
import tensorflow as tf
import keras
from sklearn.metrics import classification_report, confusion_matrix
from model import build_dual_domain_model

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

BATCH_SIZE = 32
STEPS_PER_EPOCH = 600
VAL_STEPS = 40

# 💡 2단계 학습(Stage 1: PSG 사전학습 → Stage 2: 공유 바디 고정 후 Accel 파인튜닝)의
# 단계별 epoch 수. 이전 EPOCHS=60(단일 단계 공동학습) 예산을 두 단계로 나눴습니다.
PSG_PRETRAIN_EPOCHS = 30
ACCEL_FINETUNE_EPOCHS = 30

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

def evaluate_domain(infer_model, dataset, steps, label):
    """테스트셋에서 실제로 classification_report/confusion matrix를 계산해 파일로 저장합니다.
    (기존에는 edf_te/bid_te/acc_te가 계산만 되고 어디서도 쓰이지 않아 baseline F1을 측정할 방법이 없었습니다.)"""
    y_true_all, y_pred_all = [], []
    it = iter(dataset)
    for _ in range(steps):
        x_batch, y_batch = next(it)
        logits = infer_model.predict(x_batch, verbose=0)
        preds = np.argmax(logits, axis=-1)
        y_true_all.append(y_batch.numpy().reshape(-1))
        y_pred_all.append(preds.reshape(-1))

    y_true = np.concatenate(y_true_all)
    y_pred = np.concatenate(y_pred_all)

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
    ).map(lambda x, y: _add_sample_weights(x, y, psg_w)).prefetch(10)
    psg_val_ds = build_dataset(
        [EDF_DIR], {EDF_DIR: edf_vl}, psg_mean_by_dir, psg_std_by_dir, PSG_WINDOW, PSG_CHANNELS, PSG_STRIDE, shuffle=False
    ).prefetch(10)

    accel_train_ds = build_dataset(
        [BID_DIR, ACCEL_DIR], {BID_DIR: bid_tr, ACCEL_DIR: acc_tr}, accel_mean_by_dir, accel_std_by_dir,
        ACCEL_WINDOW, ACCEL_CHANNELS, ACCEL_STRIDE, augment=True
    ).map(lambda x, y: _add_sample_weights(x, y, acc_w)).prefetch(10)
    accel_val_ds = build_dataset(
        [BID_DIR, ACCEL_DIR], {BID_DIR: bid_vl, ACCEL_DIR: acc_vl}, accel_mean_by_dir, accel_std_by_dir,
        ACCEL_WINDOW, ACCEL_CHANNELS, ACCEL_STRIDE, shuffle=False
    ).prefetch(10)

    # Model Build
    models = build_dual_domain_model(context_len=CONTEXT_LEN, n_classes=N_CLASSES)
    psg_infer_model = models["psg_infer_model"]
    accel_infer_model = models["accel_infer_model"]
    context_body = models["context_body"]

    # ============================================================
    # Stage 1: PSG(EEG/EOG/EMG) 사전학습 — 공유 컨텍스트 트랜스포머를 형성합니다.
    # ============================================================
    print("\n===== Stage 1: PSG 사전학습 (공유 컨텍스트 트랜스포머 형성) =====")
    stage1_lr = WarmupCosineDecay(
        peak_lr=4e-4, warmup_steps=STEPS_PER_EPOCH * 2, decay_steps=STEPS_PER_EPOCH * PSG_PRETRAIN_EPOCHS
    )
    psg_infer_model.compile(
        optimizer=tf.keras.optimizers.AdamW(learning_rate=stage1_lr, weight_decay=1e-4, clipnorm=1.0),
        loss=weighted_focal_loss(gamma=FOCAL_GAMMA),
    )
    stage1_cbs = [
        tf.keras.callbacks.EarlyStopping(monitor="val_loss", patience=8, restore_best_weights=True),
        tf.keras.callbacks.CSVLogger(str(OUTPUT_DIR / "train_log_stage1_psg.csv")),
    ]
    psg_infer_model.fit(
        psg_train_ds, validation_data=psg_val_ds,
        epochs=PSG_PRETRAIN_EPOCHS, steps_per_epoch=STEPS_PER_EPOCH, validation_steps=VAL_STEPS,
        callbacks=stage1_cbs,
    )
    psg_infer_model.save(OUTPUT_DIR / "psg_inference_model.keras")

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

    stage2_lr = WarmupCosineDecay(
        peak_lr=4e-4, warmup_steps=STEPS_PER_EPOCH * 2, decay_steps=STEPS_PER_EPOCH * ACCEL_FINETUNE_EPOCHS
    )
    accel_infer_model.compile(
        optimizer=tf.keras.optimizers.AdamW(learning_rate=stage2_lr, weight_decay=1e-4, clipnorm=1.0),
        loss=weighted_focal_loss(gamma=FOCAL_GAMMA),
    )
    stage2_cbs = [
        tf.keras.callbacks.EarlyStopping(monitor="val_loss", patience=12, restore_best_weights=True),
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

    evaluate_domain(psg_infer_model, test_psg_ds, psg_test_steps, "psg")
    evaluate_domain(accel_infer_model, test_accel_ds, accel_test_steps, "accel")

if __name__ == "__main__":
    main()
