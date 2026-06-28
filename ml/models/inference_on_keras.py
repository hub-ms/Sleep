# models/inference_on_keras.py

import os
import numpy as np
import tensorflow as tf
from pathlib import Path

WINDOW   = 1500
CHANNELS = 8
N_CLASSES = 5

OUTPUT_DIR = Path("/content/drive/MyDrive/ml/output")


def load_best_model():
    model = tf.keras.models.load_model(str(OUTPUT_DIR / "best_sleep_model.keras"))
    print(model.summary())
    return model


def infer_sample(model, x: np.ndarray):
    pred = model.predict(x, verbose=0)
    return np.argmax(pred, axis=1)[0]

if __name__ == "__main__":
    model = tf.keras.models.load_model(str(OUTPUT_DIR / "best_sleep_model.keras"))
    print(model.summary())

    mock_x = np.random.normal(0, 1, (1, WINDOW, CHANNELS)).astype(np.float32)
    print("Keras 가상 8채널 데이터 추론 클래스 인덱스:", infer_sample(model, mock_x))
