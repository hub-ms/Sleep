import gc
import numpy as np
import tensorflow as tf
from pathlib import Path
from sklearn.utils.class_weight import compute_class_weight
from sklearn.metrics import classification_report, confusion_matrix
from model import build_cnn_transformer

SAVE_DIR       = Path("data/processed")
INDIVIDUAL_DIR = SAVE_DIR / "subjects"
OUTPUT_DIR     = Path("output")
OUTPUT_DIR.mkdir(exist_ok=True)

WINDOW     = 1500
CHANNELS   = 8
N_CLASSES  = 5
BATCH_SIZE = 64
EPOCHS     = 50

TRAIN_SUBJECTS = ["SC4001","SC4002","SC4011","SC4012","SC4021","SC4022","SC4031","SC4032","SC4041","SC4042","SC4051","SC4052","SC4061","SC4062","SC4071","SC4072"]
VAL_SUBJECTS   = ["SC4081","SC4082"]
TEST_SUBJECTS  = ["SC4091","SC4092"]

def make_dataset(subject_ids: list[str], stats: dict, batch_size: int, shuffle: bool = True) -> tf.data.Dataset:
    mean = stats["mean"].astype(np.float32)
    std  = stats["std"].astype(np.float32)

    def generator():
        ids = subject_ids.copy()
        if shuffle: np.random.shuffle(ids)
        for sid in ids:
            x_path, y_path = INDIVIDUAL_DIR / f"X_{sid}.npy", INDIVIDUAL_DIR / f"y_{sid}.npy"
            if not (x_path.exists() and y_path.exists()): continue

            X = np.load(x_path).astype(np.float32)
            y = np.load(y_path).astype(np.int32)
            X = (X - mean) / std  # 시계열 Z-score 계산

            if shuffle:
                idx = np.random.permutation(len(X))
                X, y = X[idx], y[idx]

            for xi, yi in zip(X, y):
                yield xi, yi
            del X, y
            gc.collect()

    ds = tf.data.Dataset.from_generator(
        generator,
        output_signature=(
            tf.TensorSpec(shape=(WINDOW, CHANNELS), dtype=tf.float32),
            tf.TensorSpec(shape=(),                 dtype=tf.int32)
        )
    )
    if shuffle: ds = ds.shuffle(buffer_size=2000)
    return ds.batch(batch_size).prefetch(tf.data.AUTOTUNE)

def main():
    stats_path = SAVE_DIR / "norm_stats.npy"
    stats = np.load(stats_path, allow_pickle=True).item()

    train_ds = make_dataset(TRAIN_SUBJECTS, stats, BATCH_SIZE, shuffle=True)
    val_ds   = make_dataset(VAL_SUBJECTS,   stats, BATCH_SIZE, shuffle=False)

    # 8개 커스텀 구조 모델 생성 및 컴파일
    model = build_cnn_transformer(channels=CHANNELS)
    model.compile(
        optimizer=tf.keras.optimizers.Adam(1e-3),
        loss="sparse_categorical_crossentropy",
        metrics=["accuracy"]
    )

    # 학습 시작
    model.fit(
        train_ds, validation_data=val_ds, epochs=EPOCHS,
        callbacks=[
            tf.keras.callbacks.EarlyStopping(monitor="val_accuracy", patience=8, restore_best_weights=True),
            tf.keras.callbacks.ModelCheckpoint(str(OUTPUT_DIR / "best_sleep_model.keras"), save_best_only=True, monitor="val_accuracy")
        ]
    )

    # 최종 상세 검증 리포트 출력
    test_ds = make_dataset(TEST_SUBJECTS, stats, BATCH_SIZE, shuffle=False)
    y_pred_list, y_true_list = [], []
    for x_b, y_b in test_ds:
        preds = model.predict(x_b, verbose=0)
        y_pred_list.append(np.argmax(preds, axis=1))
        y_true_list.append(y_b.numpy())

    y_true, y_pred = np.concatenate(y_true_list), np.concatenate(y_pred_list)
    print(classification_report(y_true, y_pred, target_names=["Wake(0)", "N1(1)", "N2(2)", "N3(3)", "REM(4)"], digits=3))

    model.save(str(OUTPUT_DIR / "sleep_model_final.keras"))

if __name__ == "__main__":
    main()