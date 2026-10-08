"""검증 전용 스크립트 — 실제 학습 파이프라인(main.py)에는 포함되지 않습니다.

Kotlin 쪽 멀티입력(fusion) 추론 경로(SleepStageClassifier.kt의 runForMultipleInputsOutputs
분기)는 실제 audio_core+fusion_finetune 학습이 끝나기 전까지 한 번도 실행된 적이 없습니다.
이 스크립트는 **학습 없이** model.build_multimodal_model()을 랜덤 초기화 그대로 호출해
fusion_infer_model을 만들고 convert.convert_fusion()으로 TFLite까지 변환해, "모델 정확도"가
아니라 "입력 3개짜리 TFLite를 실기기 인터프리터가 shape 그대로 실행할 수 있는가"만 검증하기
위한 더미 산출물을 만듭니다.

⚠️ TensorFlow가 필요합니다(Colab에서 실행). 결과물(sleep_model_fusion_dummy.tflite)은 절대
실제 배포용이 아닙니다 — 검증이 끝나면 반드시 androidApp 에셋/정규화 상수를 원상복구하세요
(ml/TRAINING_GUIDE.md 또는 plan 문서의 "원상복구" 절차 참고).

실행: ml/script 디렉토리에서 `python make_dummy_fusion_model.py`
"""
from pathlib import Path

import model as model_module
import convert as convert_module
from preprocess import N_MEL_FRAMES, N_MELS, N_CLASSES

BASE_DIR = Path(__file__).resolve().parent.parent
OUTPUT_DIR = BASE_DIR / "output" / "dummy_fusion"
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

CONTEXT_LEN = 60
ACCEL_WINDOW, ACCEL_CHANNELS = 1500, 7
N_TIME_BUCKETS = 8


def main():
    print(f"더미 fusion 모델 조립 중 (학습 없음 — shape/실행 가능성만 검증)...")
    print(f"  audio: ({CONTEXT_LEN}, {N_MEL_FRAMES}, {N_MELS}, 1) / accel: ({CONTEXT_LEN}, {ACCEL_WINDOW}, {ACCEL_CHANNELS}) "
          f"/ time_bucket: scalar / n_classes: {N_CLASSES}")

    models = model_module.build_multimodal_model(
        audio_n_mel_frames=N_MEL_FRAMES,
        audio_n_mels=N_MELS,
        accel_window=ACCEL_WINDOW,
        accel_channels=ACCEL_CHANNELS,
        context_len=CONTEXT_LEN,
        n_classes=N_CLASSES,
        n_time_buckets=N_TIME_BUCKETS,
    )

    # convert.convert_fusion()은 training_model(3-출력) 전체를 로드해 fusion_output_act 레이어만
    # 떼어내는 방식이라(실제 fusion_finetune_base.keras와 같은 저장 형태), 여기서도 같은 이름으로
    # training_model을 저장합니다 — 변환 코드 경로까지 그대로 재사용해서 검증합니다.
    keras_path = OUTPUT_DIR / "fusion_finetune_base.keras"
    models["training_model"].save(keras_path)
    print(f"더미 모델(학습 안 됨) 저장: {keras_path}")

    tflite_path = OUTPUT_DIR / "sleep_model_fusion_dummy.tflite"
    convert_module.convert_fusion(keras_path, tflite_path)
    print(f"\n완료. {tflite_path}를 로컬로 받아 plan 문서의 B-2 절차대로 임시 검증한 뒤 반드시 원상복구하세요.")


if __name__ == "__main__":
    main()
