import tensorflow as tf
from pathlib import Path

OUTPUT_DIR = Path("/content/drive/MyDrive/ml/output")
model = tf.keras.models.load_model(str(OUTPUT_DIR / "sleep_model_final.keras"))

converter = tf.lite.TFLiteConverter.from_keras_model(model)
converter.optimizations = [tf.lite.Optimize.DEFAULT]
converter.target_spec.supported_types = [tf.float16]
converter.target_spec.supported_ops = [
    tf.lite.OpsSet.TFLITE_BUILTINS,
    tf.lite.OpsSet.SELECT_TF_OPS
]

tflite_model = converter.convert()
with open(str(OUTPUT_DIR / "sleep_model.tflite"), "wb") as f:
    f.write(tflite_model)
print("✅ 안드로이드 모바일 기기 배포용 8채널 가속 모델 컴파일 완성")
