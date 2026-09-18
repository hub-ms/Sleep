import numpy as np
from pathlib import Path
import os

BASE_DIR = Path("./")
CHANNELS = 8

def to_kotlin_array(name, arr):
    return f"actual val {name} = floatArrayOf(" + \
        ", ".join([f"{x:.6f}f" for x in arr]) + ")"


def compute_domain_stats_multi(directories: list, n_channels: int, channel_names: list, save_path: Path):
    x_files = []
    for directory in directories:
        x_files.extend(sorted(directory.glob("X_*.npy")))

    if not x_files:
        print(f"경고: {directories}에 분석할 파일이 없습니다.")
        return

    n_total = 0
    mean = np.zeros(n_channels, dtype=np.float64)
    M2 = np.zeros(n_channels, dtype=np.float64)

    for f in x_files:
        X = np.load(f)
        if X.shape[-1] != n_channels:
            print(f"경고: {f.name} 채널 수 불일치 ({X.shape[-1]} != {n_channels})")
            continue
        flat = X.reshape(-1, n_channels).astype(np.float64)
        del X
        n_b = flat.shape[0]
        if n_b == 0:
            continue
        mean_b = flat.mean(axis=0)
        M2_b = flat.var(axis=0) * n_b
        n_new = n_total + n_b
        delta = mean_b - mean
        mean = mean + delta * (n_b / n_new)
        M2 = M2 + M2_b + delta ** 2 * (n_total * n_b / n_new)
        n_total = n_new
        del flat

    if n_total == 0:
        raise ValueError(f"{directories}: 통계 계산할 샘플 없음")

    std = np.sqrt(M2 / n_total).astype(np.float32)
    std = np.where(std < 1e-6, 1.0, std)
    stats = {"mean": mean.astype(np.float32), "std": std, "channel_names": channel_names}
    save_path.parent.mkdir(parents=True, exist_ok=True)
    np.save(save_path, stats)
    print(f"성공: {save_path} 저장됨 (n={n_total:,} samples, files={len(x_files)})")


def compute_domain_stats(directory: Path, n_channels: int, channel_names: list, save_path: Path):
    compute_domain_stats_multi([directory], n_channels, channel_names, save_path)


def main():
    # 순수 EEG/EOG/EMG 도메인 (Sleep-EDF Expanded만 해당)
    edf_channel_names = ["eeg1", "eeg2", "eog", "emg", "heart_rate", "hrv", "time_feature", "reserved"]

    # 가속도+심박 도메인 채널 레이아웃 (BIDSleep과 Sleep-Accel이 동일한 8채널 구성)
    accel_channel_names = ["accel_x", "accel_y", "accel_z", "tilt", "heart_rate", "hrv", "mfcc_energy", "time_feature"]

    edf_norm_path = BASE_DIR / "data" / "sleep_edf" / "norm_stats.npy"
    bid_norm_path = BASE_DIR / "data" / "bidsleep" / "norm_stats.npy"
    sleep_accel_norm_path = BASE_DIR / "data" / "sleep_accel" / "norm_stats.npy"

    compute_domain_stats(
        BASE_DIR / "data" / "sleep_edf" / "subjects", 8, edf_channel_names, edf_norm_path
    )
    compute_domain_stats(
        BASE_DIR / "data" / "bidsleep" / "subjects", 8, accel_channel_names, bid_norm_path
    )
    compute_domain_stats(
        BASE_DIR / "data" / "sleep_accel" / "subjects", 8, accel_channel_names, sleep_accel_norm_path
    )

    # 💡 입력 .npy가 아예 없는 도메인은 compute_domain_stats*가 경고만 찍고 norm_stats.npy를
    # 저장하지 않은 채 조용히 반환합니다. 예전에는 이후 np.load가 바로 FileNotFoundError로
    # 죽었는데, 어느 도메인이 준비됐는지 봐가며 있는 도메인만 이어서 출력하도록 바꿨습니다.
    edf_stats = np.load(edf_norm_path, allow_pickle=True).item() if edf_norm_path.exists() else None
    bid_stats = np.load(bid_norm_path, allow_pickle=True).item() if bid_norm_path.exists() else None
    sleep_accel_stats = np.load(sleep_accel_norm_path, allow_pickle=True).item() if sleep_accel_norm_path.exists() else None

    print("// Sleep-EDF Expanded")
    print(to_kotlin_array("EDF_CHANNEL_MEAN", edf_stats["mean"]))
    print(to_kotlin_array("EDF_CHANNEL_STD", edf_stats["std"]))

    print("\n// Accel Domain (BIDSleep + Sleep-Accel)")
    print(to_kotlin_array("ACCEL_CHANNEL_MEAN", accel_stats["mean"]))
    print(to_kotlin_array("ACCEL_CHANNEL_STD", accel_stats["std"]))

    combined_mean = np.concatenate([edf_stats["mean"], accel_stats["mean"]])
    combined_std = np.concatenate([edf_stats["std"], accel_stats["std"]])


    
    print(to_kotlin_array("COMBINED_CHANNEL_MEAN", combined_mean))
    print(to_kotlin_array("COMBINED_CHANNEL_STD", combined_std))

if __name__ == "__main__":
    main()