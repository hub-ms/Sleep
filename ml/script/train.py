import gc
import time
from pathlib import Path
import numpy as np
import tensorflow as tf
import keras
from sklearn.metrics import classification_report
from model import build_dual_domain_model

tf.keras.mixed_precision.set_global_policy("mixed_bfloat16")

# 경로 설정
BASE_DIR = Path("./")
OUTPUT_DIR = BASE_DIR / "output"
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

EDF_DIR    = BASE_DIR / "data" / "sleep_edf" / "subjects"
BID_DIR    = BASE_DIR / "data" / "bidsleep" / "subjects"
ACCEL_DIR  = BASE_DIR / "data" / "sleep_accel" / "subjects"

PSG_NORM_STATS_PATH   = BASE_DIR / "data" / "sleep_edf" / "norm_stats.npy"
ACCEL_NORM_STATS_PATH = BASE_DIR / "data" / "accel_domain" / "norm_stats.npy"

# 하이퍼파라미터
PSG_WINDOW, PSG_CHANNELS = 1500, 8
ACCEL_WINDOW, ACCEL_CHANNELS = 1500, 8
N_CLASSES = 4
CONTEXT_LEN = 60
PSG_STRIDE = 3
ACCEL_STRIDE = 3

BATCH_SIZE = 32
STEPS_PER_EPOCH = 600
EPOCHS = 60
VAL_STEPS = 40

ACCEL_LOSS_WEIGHT = 0.5  # 0.8 -> 0.5 (백본 안정화)
FOCAL_GAMMA = 1.0
LABEL_SMOOTHING = 0.1
SELF_TRANSITION_BOOST = 1.5

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

def create_generator(directories, subject_ids_by_dir, mean, std, stride, augment=False, shuffle=True):
    def gen():
        pairs = [(d, sid) for d in directories for sid in subject_ids_by_dir.get(d, [])]
        while True:
            if shuffle: np.random.shuffle(pairs)
            for d, sid in pairs:
                X, y = _MEM_CACHE.get((str(d), sid), (None, None))
                if X is None: continue
                starts = list(range(0, len(X) - CONTEXT_LEN + 1, stride))
                if shuffle: np.random.shuffle(starts)
                for s in starts:
                    x_slice = np.asarray(X[s:s+CONTEXT_LEN], dtype=np.float32)
                    x_slice = (x_slice - mean) / std
                    if augment: x_slice = augment_accel(x_slice)
                    yield x_slice, y[s:s+CONTEXT_LEN]
    return gen

def build_dataset(directories, subject_ids_by_dir, mean, std, window, channels, stride, augment=False, shuffle=True):
    gen_fn = create_generator(directories, subject_ids_by_dir, mean, std, stride, augment, shuffle)
    return tf.data.Dataset.from_generator(
        gen_fn,
        output_signature=(
            tf.TensorSpec(shape=(CONTEXT_LEN, window, channels), dtype=tf.float32),
            tf.TensorSpec(shape=(CONTEXT_LEN,), dtype=tf.int32),
        )
    ).batch(BATCH_SIZE, drop_remainder=True)

def _combine_with_weights(psg_batch, accel_batch, psg_w, accel_w):
    psg_x, psg_y = psg_batch
    accel_x, accel_y = accel_batch
    psg_sw = tf.gather(tf.constant(psg_w, dtype=tf.float32), psg_y)
    accel_sw = tf.gather(tf.constant(accel_w, dtype=tf.float32), accel_y)
    return (
        {"psg_input": psg_x, "accel_input": accel_x},
        {"psg_output": psg_y, "accel_output": accel_y},
        {"psg_output": psg_sw, "accel_output": accel_sw}
    )

# ============================================================
# 4. Main Execution
# ============================================================
def main():
    # Stats Load
    psg_stats = np.load(PSG_NORM_STATS_PATH, allow_pickle=True).item()
    accel_stats = np.load(ACCEL_NORM_STATS_PATH, allow_pickle=True).item()
    psg_m, psg_s = psg_stats["mean"], psg_stats["std"]
    accel_m, accel_s = accel_stats["mean"], accel_stats["std"]

    # Subject Split
    def get_sids(d): return sorted(p.name.replace("X_", "").replace(".npy", "") for p in d.glob("X_*.npy"))
    edf_sids, bid_sids, acc_sids = get_sids(EDF_DIR), get_sids(BID_DIR), get_sids(ACCEL_DIR)
    
    def split(s): return s[:int(len(s)*0.7)], s[int(len(s)*0.7):int(len(s)*0.85)], s[int(len(s)*0.85):]
    edf_tr, edf_vl, edf_te = split(edf_sids)
    bid_tr, bid_vl, bid_te = split(bid_sids)
    acc_tr, acc_vl, acc_te = split(acc_sids)

    load_subjects_lazy(EDF_DIR, edf_tr + edf_vl)
    load_subjects_lazy(BID_DIR, bid_tr + bid_vl)
    load_subjects_lazy(ACCEL_DIR, acc_tr + acc_vl)

    psg_w = compute_class_weights_multi([EDF_DIR], {EDF_DIR: edf_tr})
    acc_w = compute_class_weights_multi([BID_DIR, ACCEL_DIR], {BID_DIR: bid_tr, ACCEL_DIR: acc_tr})
    print(f"Weights - PSG: {psg_w}, Accel: {acc_w}")

    # Datasets
    train_ds = tf.data.Dataset.zip((
        build_dataset([EDF_DIR], {EDF_DIR: edf_tr}, psg_m, psg_s, PSG_WINDOW, PSG_CHANNELS, PSG_STRIDE, augment=False),
        build_dataset([BID_DIR, ACCEL_DIR], {BID_DIR: bid_tr, ACCEL_DIR: acc_tr}, accel_m, accel_s, ACCEL_WINDOW, ACCEL_CHANNELS, ACCEL_STRIDE, augment=True)
    )).map(lambda p, a: _combine_with_weights(p, a, psg_w, acc_w)).prefetch(10)

    val_ds = tf.data.Dataset.zip((
        build_dataset([EDF_DIR], {EDF_DIR: edf_vl}, psg_m, psg_s, PSG_WINDOW, PSG_CHANNELS, PSG_STRIDE, shuffle=False),
        build_dataset([BID_DIR, ACCEL_DIR], {BID_DIR: bid_vl, ACCEL_DIR: acc_vl}, accel_m, accel_s, ACCEL_WINDOW, ACCEL_CHANNELS, ACCEL_STRIDE, shuffle=False)
    )).map(lambda p, a: ({"psg_input": p[0], "accel_input": a[0]}, {"psg_output": p[1], "accel_output": a[1]})).prefetch(10)

    # Model Build
    models = build_dual_domain_model(context_len=CONTEXT_LEN, n_classes=N_CLASSES)
    train_model = models["training_model"]

    # Scheduler
    lr_sched = WarmupCosineDecay(peak_lr=4e-4, warmup_steps=STEPS_PER_EPOCH*2, decay_steps=STEPS_PER_EPOCH*EPOCHS)
    opt = tf.keras.optimizers.AdamW(learning_rate=lr_sched, weight_decay=1e-4, clipnorm=1.0)

    train_model.compile(
        optimizer=opt,
        loss={"psg_output": weighted_focal_loss(gamma=FOCAL_GAMMA), "accel_output": weighted_focal_loss(gamma=FOCAL_GAMMA)},
        loss_weights={"psg_output": 1.0, "accel_output": ACCEL_LOSS_WEIGHT}
    )

    cbs = [
        tf.keras.callbacks.EarlyStopping(monitor="val_loss", patience=12, restore_best_weights=True),
        tf.keras.callbacks.ModelCheckpoint(str(OUTPUT_DIR / "best_model.keras"), save_best_only=True)
    ]

    train_model.fit(train_ds, validation_data=val_ds, epochs=EPOCHS, steps_per_epoch=STEPS_PER_EPOCH, validation_steps=VAL_STEPS, callbacks=cbs)

    # Save
    models["psg_infer_model"].save(OUTPUT_DIR / "psg_inference_model.keras")
    models["accel_infer_model"].save(OUTPUT_DIR / "accel_inference_model.keras")

if __name__ == "__main__":
    main()
