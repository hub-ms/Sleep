"""
학습을 다시 돌리지 않고, output/ 에 이미 저장된 체크포인트(accel_inference_model.keras,
psg_inference_model.keras)를 현재 데이터 분할 기준 held-out 테스트셋에서 평가해
baseline F1을 측정합니다.

이 체크포인트들은 train.py가 리팩터링되기 전 버전에서 저장된 것으로 보입니다
(output/에 best_model.keras가 아니라 best_dual_domain_model.keras라는 다른 이름의
파일이 함께 있는 것이 그 흔적입니다). 그래서 현재 model.py 구조와 정확히 호환되지
않을 수 있습니다. 로드에 실패하면 그 자체가 "재학습이 필요하다"는 결론이 되므로,
실패 시 원인을 최대한 자세히 출력합니다.

실행: ml/script 디렉토리에서 `python evaluate_checkpoint.py`
(numpy, tensorflow, scikit-learn 필요. norm_stats.npy가 없는 도메인은 건너뜁니다.)
"""
from pathlib import Path

import numpy as np
import tensorflow as tf

import model as model_module  # noqa: F401  (커스텀 레이어 등록을 위해 import 자체가 필요)
import train


def split_subjects(sids):
    """train.py main()의 split()과 동일한 70/15/15 분할 — 같은 테스트셋을 재현하기 위해 그대로 맞춤."""
    n = len(sids)
    return sids[:int(n * 0.7)], sids[int(n * 0.7):int(n * 0.85)], sids[int(n * 0.85):]


def load_checkpoint(path: Path):
    if not path.exists():
        print(f"체크포인트 없음: {path}")
        return None
    try:
        m = tf.keras.models.load_model(path, compile=False)
        print(f"로드 성공: {path.name}")
        m.summary()
        return m
    except Exception as e:
        print(f"체크포인트 로드 실패: {path.name} ({type(e).__name__}: {e})")
        print("→ 구버전 파이프라인 산출물이라 현재 model.py 구조와 입력/출력 형태가 다를 수 있습니다. "
              "이 경우 이 체크포인트로는 baseline을 낼 수 없고 재학습이 필요하다는 뜻입니다.")
        return None


def evaluate_accel_checkpoint():
    print("\n===== Accel 도메인 baseline 평가 =====")
    if not train.ACCEL_NORM_STATS_PATH.exists():
        print(f"norm_stats 없음: {train.ACCEL_NORM_STATS_PATH} — compute_stats.py를 먼저 실행하세요.")
        return
    stats = np.load(train.ACCEL_NORM_STATS_PATH, allow_pickle=True).item()
    mean, std = stats["mean"], stats["std"]

    bid_sids = sorted(p.name.replace("X_", "").replace(".npy", "") for p in train.BID_DIR.glob("X_*.npy"))
    acc_sids = sorted(p.name.replace("X_", "").replace(".npy", "") for p in train.ACCEL_DIR.glob("X_*.npy"))
    _, _, bid_te = split_subjects(bid_sids)
    _, _, acc_te = split_subjects(acc_sids)

    if not bid_te and not acc_te:
        print("테스트셋 subject가 없습니다(데이터가 너무 적거나 아직 전처리되지 않음).")
        return

    train.load_subjects_lazy(train.BID_DIR, bid_te)
    train.load_subjects_lazy(train.ACCEL_DIR, acc_te)

    model = load_checkpoint(train.OUTPUT_DIR / "accel_inference_model.keras")
    if model is None:
        return

    dataset = train.build_dataset(
        [train.BID_DIR, train.ACCEL_DIR], {train.BID_DIR: bid_te, train.ACCEL_DIR: acc_te},
        mean, std, train.ACCEL_WINDOW, train.ACCEL_CHANNELS, train.ACCEL_STRIDE, shuffle=False,
    )
    steps = max(1, train.count_context_windows(
        [train.BID_DIR, train.ACCEL_DIR], {train.BID_DIR: bid_te, train.ACCEL_DIR: acc_te}, train.ACCEL_STRIDE
    ) // train.BATCH_SIZE)

    train.evaluate_domain(model, dataset, steps, "accel_baseline_checkpoint")


def evaluate_psg_checkpoint():
    print("\n===== PSG 도메인 baseline 평가 =====")
    if not train.PSG_NORM_STATS_PATH.exists():
        print(
            f"norm_stats 없음: {train.PSG_NORM_STATS_PATH} — "
            f"build_dataset_hybrid.py + compute_stats.py를 먼저 실행하세요(PSG 전처리 필요)."
        )
        return
    stats = np.load(train.PSG_NORM_STATS_PATH, allow_pickle=True).item()
    mean, std = stats["mean"], stats["std"]

    edf_sids = sorted(p.name.replace("X_", "").replace(".npy", "") for p in train.EDF_DIR.glob("X_*.npy"))
    _, _, edf_te = split_subjects(edf_sids)
    if not edf_te:
        print("PSG 테스트셋 subject가 없습니다(전처리되지 않음).")
        return

    train.load_subjects_lazy(train.EDF_DIR, edf_te)

    model = load_checkpoint(train.OUTPUT_DIR / "psg_inference_model.keras")
    if model is None:
        return

    dataset = train.build_dataset(
        [train.EDF_DIR], {train.EDF_DIR: edf_te},
        mean, std, train.PSG_WINDOW, train.PSG_CHANNELS, train.PSG_STRIDE, shuffle=False,
    )
    steps = max(1, train.count_context_windows(
        [train.EDF_DIR], {train.EDF_DIR: edf_te}, train.PSG_STRIDE
    ) // train.BATCH_SIZE)

    train.evaluate_domain(model, dataset, steps, "psg_baseline_checkpoint")


if __name__ == "__main__":
    evaluate_accel_checkpoint()
    evaluate_psg_checkpoint()