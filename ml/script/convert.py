"""학습된 Keras 모델을 온디바이스 배포용 TFLite로 변환합니다.

⚠️ TensorFlow가 필요합니다(이 환경에는 없어 실행 검증 불가). 옛 convert_to_tflite.py의
로직(커스텀 레이어 등록, Softmax 래핑, float16 양자화)을 그대로 포팅했고, 신규로 오디오+가속도
2-입력(fusion_infer_model) 변환과 입출력 메타데이터 JSON 저장을 추가했습니다.
"""
import argparse
import json
from pathlib import Path

import tensorflow as tf

import model as model_module  # noqa: F401  (PositionalEmbedding/ConstantDomainId/TransitionCRF 커스텀 레이어 등록을 위해 import 자체가 필요)

BASE_DIR = Path(__file__).resolve().parent.parent
OUTPUT_DIR = BASE_DIR / "output"
TFLITE_PATH = OUTPUT_DIR / "sleep_model.tflite"


def find_default_model(filename="accel_inference_model.keras"):
    """변환할 모델을 찾습니다. 기본값은 "가장 최근 실행 디렉토리의 모델"입니다.

    ⚠️ 어떤 fold/실행의 모델을 배포할지는 자동으로 정할 문제가 아닙니다 — 교차검증은 성능을
    **측정**하는 수단이고, 배포 모델은 보통 전체 데이터로 다시 학습하거나 fold 앙상블을
    distill해 만듭니다. 기본값에 의존하지 말고 --model로 명시하세요.
    """
    candidates = sorted(
        OUTPUT_DIR.glob(f"*/{filename}"), key=lambda p: p.stat().st_mtime, reverse=True
    )
    legacy = OUTPUT_DIR / filename
    if legacy.exists():
        candidates.append(legacy)
    return candidates[0] if candidates else None


def _wrap_with_softmax(keras_model):
    """로짓을 출력하는 모델을 소프트맥스 확률을 출력하는 모델로 감쌉니다(앱 쪽 사용 편의)."""
    inputs = keras_model.input
    logits = keras_model(inputs)
    outputs = tf.keras.layers.Softmax()(logits)
    return tf.keras.Model(inputs, outputs)


def _convert_and_save(full_model, tflite_path: Path, metadata: dict):
    converter = tf.lite.TFLiteConverter.from_keras_model(full_model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_types = [tf.float16]  # A100/최신 기기 최적화
    tflite_model = converter.convert()

    tflite_path.parent.mkdir(parents=True, exist_ok=True)
    with open(tflite_path, "wb") as f:
        f.write(tflite_model)

    # 신규: 입출력 shape/이름을 JSON으로 함께 남깁니다. 단일 입력(accel-only) 모델은 지금까지
    # Kotlin(SleepStageClassifier.kt)이 shape을 하드코딩해도 안전했지만, 2-입력(audio+accel)
    # 모델이 추가되면서 Kotlin 쪽 ByteBuffer 구성이 더 복잡해지므로 이 메타데이터가 그 근거가
    # 되도록 합니다(Phase 8, 안드로이드 쪽 구현은 아직 이 세션에서 진행하지 않았습니다).
    meta_path = tflite_path.with_suffix(".meta.json")
    meta_path.write_text(json.dumps(metadata, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"Success: TFLite 모델 저장 -> {tflite_path}")
    print(f"         메타데이터 저장 -> {meta_path}")


def convert_accel_only(model_path: Path = None, tflite_path: Path = TFLITE_PATH):
    """가속도 단독 추론 모델(accel_infer_model, [accel_input, time_bucket_id] 2-입력)을 변환합니다.
    현재 앱(SleepStageClassifier.kt)이 실제로 쓰는, accel-only 온디바이스 배포 경로입니다."""
    model_path = Path(model_path) if model_path else find_default_model("accel_inference_model.keras")
    if model_path is None:
        print(f"Error: 변환할 모델을 찾지 못했습니다 (검색 위치: {OUTPUT_DIR}/*/accel_inference_model.keras)")
        return
    if not model_path.exists():
        print(f"Error: Model not found at {model_path}")
        return
    print(f"변환 대상: {model_path}")

    keras_model = tf.keras.models.load_model(model_path, compile=False)
    full_model = _wrap_with_softmax(keras_model)

    metadata = {
        "inputs": [
            {"name": name, "shape": list(shape)}
            for name, shape in zip(keras_model.input_names, [t.shape for t in keras_model.inputs])
        ],
        "output": {"name": "softmax_probs", "shape": list(full_model.output_shape)},
        "variant": "accel_only",
    }
    _convert_and_save(full_model, tflite_path, metadata)


def convert_fusion(model_path: Path = None, tflite_path: Path = None):
    """오디오+가속도 2-입력 fusion_infer_model을 변환합니다(Phase 8 — 온디바이스 오디오 입력
    파이프라인이 Kotlin 쪽에 아직 구현되지 않아, 지금은 변환 자체만 지원합니다)."""
    model_path = Path(model_path) if model_path else find_default_model("fusion_finetune_base.keras")
    tflite_path = tflite_path or (OUTPUT_DIR / "sleep_model_fusion.tflite")
    if model_path is None:
        print(f"Error: 변환할 fusion 모델을 찾지 못했습니다 (검색 위치: {OUTPUT_DIR}/*/fusion_finetune_base.keras)")
        return
    if not model_path.exists():
        print(f"Error: Model not found at {model_path}")
        return
    print(f"변환 대상(fusion): {model_path}")

    keras_model = tf.keras.models.load_model(model_path, compile=False)
    # fusion_finetune_base.keras는 training_model(3출력: audio/accel/fusion) 전체이므로,
    # 배포에는 fusion_output 하나만 필요합니다 — 해당 출력만 떼어낸 서브모델을 만듭니다.
    fusion_output = keras_model.get_layer("fusion_output_act").output
    fusion_only = tf.keras.Model(keras_model.inputs, fusion_output)
    full_model = _wrap_with_softmax(fusion_only)

    metadata = {
        "inputs": [
            {"name": name, "shape": list(shape)}
            for name, shape in zip(fusion_only.input_names, [t.shape for t in fusion_only.inputs])
        ],
        "output": {"name": "softmax_probs", "shape": list(full_model.output_shape)},
        "variant": "audio_accel_fusion",
    }
    _convert_and_save(full_model, tflite_path, metadata)


def main():
    p = argparse.ArgumentParser(description="수면 단계 분류 모델을 TFLite로 변환")
    p.add_argument("--variant", choices=["accel_only", "fusion"], default="accel_only",
                   help="accel_only = 현재 배포 경로(가속도 단독). fusion = 오디오+가속도 2-입력(Phase 8 이후).")
    p.add_argument("--model", default=None, help="변환할 .keras 경로. 생략하면 가장 최근 실행의 모델을 씁니다.")
    p.add_argument("--out", default=None, help="출력 .tflite 경로")
    args = p.parse_args()

    if args.variant == "accel_only":
        convert_accel_only(args.model, Path(args.out) if args.out else TFLITE_PATH)
    else:
        convert_fusion(args.model, Path(args.out) if args.out else None)


if __name__ == "__main__":
    main()
