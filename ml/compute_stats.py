import numpy as np
from pathlib import Path

INDIVIDUAL_DIR = Path("data/processed/subjects")
SAVE_DIR = Path("data/processed")
CHANNELS = 8
CHANNEL_NAMES = ["accel_x", "accel_y", "accel_z", "tilt_angle", "heart_rate", "noise_rms",
                 "mfcc_energy", "time_feature"]


def compute_stats_streaming():
    x_files = sorted(INDIVIDUAL_DIR.glob("X_*.npy"))
    if not x_files:
        raise FileNotFoundError("subjects/ 아래 X_*.npy 없음")

    n_total = 0
    mean = np.zeros(CHANNELS, dtype=np.float64)
    M2 = np.zeros(CHANNELS, dtype=np.float64)

    for f in x_files:
        X = np.load(f)
        flat = X.reshape(-1, CHANNELS).astype(np.float64)
        del X

        for row in flat:
            n_total += 1
            delta = row - mean
            mean += delta / n_total
            delta2 = row - mean
            M2 += delta * delta2
        del flat

    std = np.sqrt(M2 / n_total).astype(np.float32)
    std = np.where(std < 1e-6, 1.0, std)

    stats = {"mean": mean.astype(np.float32), "std": std}
    np.save(SAVE_DIR / "norm_stats.npy", stats)

    print("\n── [Kotlin 동기화 전용 채널별 정규화 통계] ──")
    for i, name in enumerate(CHANNEL_NAMES):
        print(f"  {name:13s}: mean={mean[i]:7.4f}  std={std[i]:7.4f}")

    m_str = ", ".join([f"{v:.4f}f" for v in mean])
    s_str = ", ".join([f"{v:.4f}f" for v in std])
    print(f"\n// Kotlin 'SleepStageClassifier.actual' companion object 복사용")
    print(f"actual val CHANNEL_MEAN = floatArrayOf({m_str})")
    print(f"actual val CHANNEL_STD  = floatArrayOf({s_str})")
    return stats


if __name__ == "__main__":
    compute_stats_streaming()
