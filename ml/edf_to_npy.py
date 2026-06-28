import gc
import json
import mne
import numpy as np
import os
import warnings
from pathlib import Path

warnings.filterwarnings("ignore", category=RuntimeWarning)

SAMPLE_RATE = 50
WINDOW = 1500
STRIDE = 750
N_CLASSES = 5
CHANNELS = 8

EDF_DIR = Path("data/sleep_edf")
SAVE_DIR = Path("data/processed")
INDIVIDUAL_DIR = SAVE_DIR / "subjects"

for path in [SAVE_DIR, INDIVIDUAL_DIR]: path.mkdir(parents=True, exist_ok=True)

LABEL_MAP = {
    "Sleep stage W": 0,
    "Sleep stage 1": 1,
    "Sleep stage 2": 2,
    "Sleep stage 3": 3,
    "Sleep stage 4": 3,
    "Sleep stage R": 4,
}

def simulate_app_8channels(n_samples, stage, total_samp, start_indices):
    rng = np.random.default_rng()

    # 0, 1, 2번 채널: 가속도 X, Y, Z (중력가속도 기저선 9.8 적용)
    p_accel = {0: 0.2, 1: 0.05, 2: 0.02, 3: 0.01, 4: 0.08}
    accel = rng.normal(0, p_accel[stage], (n_samples, 3))
    accel[:, 2] += 9.80

    # 3번 채널: Tilt_Angle (평균값과의 편차 변동성 시뮬레이션)
    tilt_angle = rng.normal(p_accel[stage] * 1.5, 0.05, n_samples).clip(0, 2.0)

    # 4번 채널: Heart_Rate (워치 미착용 시 앱 Fallback 값인 68.57을 평균으로 지정)
    hr = rng.normal(68.5769, 1.5, n_samples)

    # 5, 6번 채널: 소음(Noise_RMS) 및 주파수 에너지(MFCC_Energy)
    p_mic = {0: 45, 1: 32, 2: 28, 3: 25, 4: 35}
    noise_rms = rng.normal(p_mic[stage], 5, n_samples).clip(20, 70)
    mfcc_energy = rng.normal(p_mic[stage] * 0.2, 2, n_samples).clip(0, 30)

    # 7번 채널: Time_Feature (0.0 ~ 1.0 정규화된 경과 시간 비율)
    time_feature = start_indices.astype(np.float32) / float(total_samp)

    return np.column_stack([accel, tilt_angle, hr, noise_rms, mfcc_energy, time_feature])


def process_subject(psg_path, hyp_path):
    raw = mne.io.read_raw_edf(str(psg_path), preload=False, verbose=False)
    hyp = mne.read_annotations(str(hyp_path))
    total_sec = raw.n_times / raw.info["sfreq"]
    total_samp = int(total_sec * SAMPLE_RATE)

    label_array = np.full(total_samp, -1, dtype=np.int32)
    for ann in hyp:
        if ann["description"] not in LABEL_MAP:
            continue
        s = int(ann["onset"] * SAMPLE_RATE)
        e = min(int((ann["onset"] + ann["duration"]) * SAMPLE_RATE), total_samp)
        if s < total_samp:
            label_array[s:e] = LABEL_MAP[ann["description"]]

    X, y = [], []
    for start in range(0, total_samp - WINDOW, STRIDE):
        lbl = label_array[start + WINDOW // 2]
        if lbl >= 0:
            indices = np.arange(start, start + WINDOW)
            X.append(simulate_app_8channels(WINDOW, lbl, total_samp, indices))
            y.append(lbl)

    gc.collect()
    return np.array(X, dtype=np.float32), np.array(y, dtype=np.int32)

def is_already_processed(subject_id: str) -> bool:
    """재실행 시 완료된 피험자 건너뜀"""
    x_file = INDIVIDUAL_DIR / f"X_{subject_id}.npy"
    y_file = INDIVIDUAL_DIR / f"y_{subject_id}.npy"
    if not (x_file.exists() and y_file.exists()):
        return False
    try:
        X = np.load(x_file, mmap_mode="r")
        return X.ndim == 3 and X.shape[0] > 0
    except Exception:
        return False


def merge_all():
    x_files = sorted(INDIVIDUAL_DIR.glob("X_*.npy"))
    y_files = sorted(INDIVIDUAL_DIR.glob("y_*.npy"))

    if not x_files: return

    x = [np.load(f) for f in x_files]
    y = [np.load(f) for f in y_files]

    X_all = np.concatenate(x)
    y_all = np.concatenate(y)

    np.save(SAVE_DIR / "X_all.npy", X_all)
    np.save(SAVE_DIR / "y_all.npy", y_all)
    print(f"\n✅ 병합 완료: X={X_all.shape}, y={y_all.shape} | 분포: {np.bincount(y_all, minlength=N_CLASSES)}")

def main():
    # 1. EDF 파일 검색 (rglob으로 하위 폴더까지 안전하게 검색)
    psg_files = sorted(EDF_DIR.rglob("*PSG*.edf"))
    if not psg_files:
        print(f"❌ EDF 파일 없음 — '{EDF_DIR}' 폴더에 파일이 있는지 확인하세요.")
        return

    for idx, psg in enumerate(psg_files):
        # 파일명에서 피험자 고유 코드 추출 (예: SC4001)
        subject_id = psg.stem.split("-")[0][:6]

        # ⚠️ 튜플 오타 제거 및 올바른 파일명 매칭
        x_file = INDIVIDUAL_DIR / f"X_{subject_id}.npy"
        y_file = INDIVIDUAL_DIR / f"y_{subject_id}.npy"

        # 이미 처리된 피험자라면 건너뛰기
        if x_file.exists() and y_file.exists():
            print(f"[{idx+1}/{len(psg_files)}] ⏭️  건너뜀: {subject_id}")
            continue

        # 2. 일치하는 짝꿍 Hypnogram 파일 찾기 (똑같이 rglob 사용 권장)
        hyp_files = sorted(EDF_DIR.rglob(f"{subject_id}*Hypnogram.edf"))
        if not hyp_files:
            print(f"[{idx+1}/{len(psg_files)}] ⚠️ Hypnogram 파일 없음: {subject_id}")
            continue

        # ⚠️ 변수명 sub_id -> subject_id로 수정
        print(f"[{idx+1}/{len(psg_files)}] 처리 중: {subject_id}", end=" → ", flush=True)
        try:
            X, y = process_subject(psg, hyp_files[0])
            if len(X) == 0:
                print("윈도우 데이터 부족으로 제외")
                continue

            np.save(x_file, X)
            np.save(y_file, y)

            # ⚠️ 변수명 d -> dist로 수정
            dist = np.bincount(y, minlength=N_CLASSES)
            print(f"{len(X)}윈도우 [W:{dist[0]} N1:{dist[1]} N2:{dist[2]} N3:{dist[3]} R:{dist[4]}]")
        except Exception as e:
            print(f"❌ 에러 발생: {e}")

    print("\n전체 병합 진행 중...")
    merge_all()

if __name__ == "__main__":
    main()
