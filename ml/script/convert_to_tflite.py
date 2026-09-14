import tensorflow as tf
from pathlib import Path

BASE_DIR = Path("./")
MODEL_PATH = BASE_DIR / "output" / "accel_inference_model.keras"
TFLITE_PATH = BASE_DIR / "output" / "sleep_model.tflite"

def convert():
    if not MODEL_PATH.exists():
        print(f"Error: Model not found at {MODEL_PATH}")
        return

    # Load the inference model (logits output)
    model = tf.keras.models.load_model(MODEL_PATH, compile=False)

    # Wrap with Softmax for easier app-side usage
    inputs = model.input
    logits = model(inputs)
    outputs = tf.keras.layers.Softmax()(logits)
    full_model = tf.keras.Model(inputs, outputs)

    converter = tf.lite.TFLiteConverter.from_keras_model(full_model)
    
    # Optimization settings
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_types = [tf.float16] # A100/Modern devices optimization

    tflite_model = converter.convert()

    with open(TFLITE_PATH, "wb") as f:
        f.write(tflite_model)
    
    print(f"Success: TFLite model saved to {TFLITE_PATH}")

if __name__ == "__main__":
    convert()
