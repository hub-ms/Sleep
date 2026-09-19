import numpy as np
from pathlib import Path
from scipy.io import loadmat
from datetime import datetime
from zoneinfo import ZoneInfo
import mne

mne.set_log_level("ERROR")


SAMPLE_RATE = 50
WINDOW      = 1500          # 50Hz * 30s
EPOCH_SEC   = 30
N_CLASSES   = 4

# 💡 cwd(현재 작업 디렉토리)가 로컬/Colab에서 다를 수 있어(ml/, ml/script/ 등) cwd에 의존하지
# 않도록 이 파일(ml/script/build_dataset_hybrid.py) 자신의 위치를 기준으로 ml/ 폴더를 찾습니다.
BASE_DIR = Path(__file__).resolve().parent.parent

SLEEP_ACCEL_DIR = BASE_DIR / "data" / "sleep_accel"
BIDSLEEP_DIR    = BASE_DIR / "data" / "bidsleep"
EDF_DIR = BASE_DIR / "data" / "sleep_edf"

SLEEP_ACCEL_SAVE_DIR = SLEEP_ACCEL_DIR / "subjects"
BIDSLEEP_SAVE_DIR    = BIDSLEEP_DIR / "subjects"
EDF_SAVE_DIR = EDF_DIR / "subjects"

SLEEP_ACCEL_SAVE_DIR.mkdir(parents=True, exist_ok=True)
BIDSLEEP_SAVE_DIR.mkdir(parents=True, exist_ok=True)
EDF_SAVE_DIR.mkdir(parents=True, exist_ok=True)

SLEEP_ACCEL_LABEL_MAP = {0: 0, 1: 1, 2: 1, 3: 2, 5: 3}
BIDSLEEP_LABEL_MAP = {0: 0, 1: 1, 2: 1, 3: 2, 4: 3}
EDF_LABEL_MAP = {
    "Sleep stage W": 0,
    "Sleep stage 1": 1,
    "Sleep stage 2": 1,
    "Sleep stage 3": 2,
    "Sleep stage 4": 2,
    "Sleep stage R": 3,
    "Movement time": 0,          # 깨어있음으로 처리
    "Sleep stage ?": 0,          # 불명확 → W로 처리
    "Sleep stage undefined": 0,  # 불명확 → W로 처리
}

EDF_EEG_CHANNELS = ["EEG Fpz-Cz", "EEG Pz-Oz"]
EDF_EOG_CHANNEL = "EOG horizontal"
EDF_EMG_CHANNEL = "EMG submental"
RECSTART_FORMATS = ["%Y-%m-%d %H:%M:%S", "%d-%b-%Y %H:%M:%S", "%Y-%m-%dT%H:%M:%S"]


def resample_to_grid(t_src, values, t_grid):
    """불규칙 샘플링된 (t_src, values)를 균일 50Hz 그리드로 선형보간."""
    if len(t_src) < 2:
        fill = values[0] if len(values) else 0.0
        return np.full(len(t_grid), fill, dtype=np.float32)
    return np.interp(t_grid, t_src, values).astype(np.float32)
def compute_hrv_proxy(hr_t, hr_v, t_start, t_end):
    """epoch 구간 심박수의 RMSSD 근사치. REM epoch에서 값이 커지는 경향이 있음."""
    mask = (hr_t >= t_start - 60) & (hr_t <= t_end + 60)
    v = hr_v[mask]
    if len(v) < 3:
        return 0.0
    diffs = np.diff(v)
    return float(np.sqrt(np.mean(diffs ** 2)))

def robust_loadtxt(path, ncols, delimiter=None, skiprows=0):
    """np.loadtxt는 파일 중간에 컬럼 수가 바뀌면(깨진 줄, 잘린 줄 등) 통째로 예외를 던지고 멈춥니다.
    Apple Watch 원본 데이터는 이런 깨진 줄이 종종 섞여 있으므로,
    먼저 빠른 np.loadtxt를 시도하고 실패하면 한 줄씩 읽어 컬럼 수가 맞는 행만 모아 반환합니다."""
    try:
        arr = np.loadtxt(path, delimiter=delimiter, skiprows=skiprows, ndmin=2)
        if arr.shape[1] == ncols:
            return arr
    except Exception:
        pass

    rows, n_bad = [], 0
    with open(path, "r") as f:
        for i, line in enumerate(f):
            if i < skiprows:
                continue
            line = line.strip()
            if not line:
                continue
            parts = [p for p in (line.split(delimiter) if delimiter else line.split()) if p != ""]
            if len(parts) != ncols:
                n_bad += 1
                continue
            try:
                rows.append([float(p) for p in parts])
            except ValueError:
                n_bad += 1
                continue

    if n_bad:
        print(f"  경고: {path.name} 에서 형식이 깨진 행 {n_bad}개를 건너뜀")
    return np.array(rows, dtype=np.float64) if rows else np.empty((0, ncols), dtype=np.float64)


def parse_rec_start(mat) -> float:
    """BIDSleep labels.mat의 recStart(미국 동부시간, 사람이 읽는 형식 문자열)를
    motion.csv/hr.csv와 동일한 기준인 UTC unix timestamp(초)로 변환."""
    raw = np.squeeze(mat["recStart"])
    raw_str = raw.item() if hasattr(raw, "item") else raw
    raw_str = str(raw_str).strip()

    last_err = None
    for fmt in RECSTART_FORMATS:
        try:
            dt_naive = datetime.strptime(raw_str, fmt)
            dt_eastern = dt_naive.replace(tzinfo=ZoneInfo("America/New_York"))
            return dt_eastern.timestamp()
        except ValueError as e:
            last_err = e
            continue
    raise ValueError(
        f"recStart 형식을 파싱하지 못했습니다: '{raw_str}'. "
        f"RECSTART_FORMATS에 실제 형식을 추가하세요."
    ) from last_err


def load_sleep_accel_subject(subject_id: str):
    motion_path = SLEEP_ACCEL_DIR / "motion" / f"{subject_id}_acceleration.txt"
    hr_path     = SLEEP_ACCEL_DIR / "heart_rate" / f"{subject_id}_heartrate.txt"
    label_path  = SLEEP_ACCEL_DIR / "labels" / f"{subject_id}_labeled_sleep.txt"
    if not (motion_path.exists() and label_path.exists()):
        return None, None

    # 가속도 로드
    # 💡 수정: np.loadtxt 대신 깨진 행을 건너뛰는 robust_loadtxt 사용 (컬럼 수 불일치 시 전체 실패 방지)
    motion = robust_loadtxt(motion_path, ncols=4)
    if motion.shape[0] < 2:
        return None, None

    # 시간 보정: 가속도 데이터의 마지막 시점을 라벨 데이터의 마지막 시점에 맞춤
    raw_labels = np.loadtxt(label_path)
    if len(raw_labels) == 0:
        return None, None

    last_label_time = raw_labels[-1, 0]
    t_acc_raw = motion[:, 0]
    t_acc = t_acc_raw - (t_acc_raw[-1] - last_label_time)
    ax, ay, az = motion[:, 1], motion[:, 2], motion[:, 3]

    hr_t, hr_v = np.array([]), np.array([])
    if hr_path.exists():
        hr = robust_loadtxt(hr_path, ncols=2, delimiter=",")
        if hr.shape[0] >= 2:
            hr_t_raw = hr[:, 0]
            hr_t = hr_t_raw - (t_acc_raw[-1] - last_label_time)
            hr_v = hr[:, 1]

    labels = raw_labels[raw_labels[:, 1] != -1]
    total_span = last_label_time

    X, y = [], []
    for onset, stage_raw in labels:
        stage_raw = int(stage_raw)
        if stage_raw not in SLEEP_ACCEL_LABEL_MAP:
            continue
        stage = SLEEP_ACCEL_LABEL_MAP[stage_raw]

        t_start, t_end = onset, onset + EPOCH_SEC
        t_grid = np.linspace(t_start, t_end, WINDOW, endpoint=False)

        mask = (t_acc >= t_start - 0.1) & (t_acc <= t_end + 0.1)
        if mask.sum() < 2:
            continue

        x = resample_to_grid(t_acc[mask], ax[mask], t_grid)
        yv = resample_to_grid(t_acc[mask], ay[mask], t_grid)
        z = resample_to_grid(t_acc[mask], az[mask], t_grid)

        avg_x, avg_y, avg_z = x.mean(), yv.mean(), z.mean()
        tilt = np.abs(x - avg_x) + np.abs(yv - avg_y) + np.abs(z - avg_z)

        if hr_t.size >= 2:
            hr_mask = (hr_t >= t_start - 30) & (hr_t <= t_end + 30)
            hr_val = (resample_to_grid(hr_t[hr_mask], hr_v[hr_mask], t_grid)
                      if hr_mask.sum() >= 1
                      else np.full(WINDOW, 68.5769, dtype=np.float32))
        else:
            hr_val = np.full(WINDOW, 68.5769, dtype=np.float32)

        hrv_val      = np.full(WINDOW, compute_hrv_proxy(hr_t, hr_v, t_start, t_end), dtype=np.float32)
        mfcc_energy  = np.full(WINDOW, 0.0,  dtype=np.float32)
        time_feature = np.full(WINDOW, onset / total_span, dtype=np.float32)

        window = np.column_stack([x, yv, z, tilt, hr_val, hrv_val, mfcc_energy, time_feature])
        X.append(window)
        y.append(stage)

    if not X:
        return None, None
    return np.array(X, dtype=np.float32), np.array(y, dtype=np.int32)


def load_bidsleep_subject(subject_id: str):
    subj_dir = BIDSLEEP_DIR / subject_id
    if not subj_dir.exists():
        return None, None

    all_X, all_y = [], []

    # 밤 폴더는 "1","2",...,"7" 이므로 motion.csv 기준 재귀 탐색
    for motion_path in sorted(subj_dir.rglob("motion.csv")):
        night_dir = motion_path.parent
        hr_path    = night_dir / "hr.csv"
        label_path = night_dir / "labels.mat"
        if not (hr_path.exists() and label_path.exists()):
            continue

        # motion.csv: 헤더 있음 (Timestamp,x,y,z)
        # 💡 수정: np.loadtxt -> robust_loadtxt. 실제로 이 파일들에서 중간에 컬럼 수가
        #   4개->2개로 깨지는 행이 있어서 np.loadtxt가 통째로 죽는 문제가 있었음.
        motion = robust_loadtxt(motion_path, ncols=4, delimiter=",", skiprows=1)
        if motion.shape[0] < 2:
            print(f"  스킵: {night_dir} motion.csv 유효 데이터 부족")
            continue
        t_acc, ax, ay, az = motion[:, 0], motion[:, 1], motion[:, 2], motion[:, 3]

        # 💡 수정: hr.csv는 헤더가 없음 -> skiprows 제거 (있으면 첫 데이터 행이 날아감) + robust 로더
        hr = robust_loadtxt(hr_path, ncols=2, delimiter=",")
        if hr.shape[0] < 2:
            print(f"  스킵: {night_dir} hr.csv 유효 데이터 부족")
            continue
        hr_t, hr_v = hr[:, 0], hr[:, 1]

        mat = loadmat(label_path)
        # 💡 수정: recStart는 float가 아니라 사람이 읽는 시각 문자열(미국 동부시간) -> 파싱해서 UTC unix time으로 변환
        rec_start = parse_rec_start(mat)
        stage_seq = mat["expert_label"].squeeze().astype(int)  # 전문가 보정 라벨 우선 사용

        total_span = t_acc[-1] - rec_start if len(t_acc) else 1.0

        for k, stage_raw in enumerate(stage_seq):
            if stage_raw not in BIDSLEEP_LABEL_MAP:
                continue  # Unknown(5) 제외
            stage = BIDSLEEP_LABEL_MAP[stage_raw]

            t_start = rec_start + 30 * k
            t_end   = t_start + EPOCH_SEC
            t_grid  = np.linspace(t_start, t_end, WINDOW, endpoint=False)

            mask = (t_acc >= t_start - 1) & (t_acc <= t_end + 1)
            if mask.sum() < 2:
                continue

            x = resample_to_grid(t_acc[mask], ax[mask], t_grid)
            yv = resample_to_grid(t_acc[mask], ay[mask], t_grid)
            z = resample_to_grid(t_acc[mask], az[mask], t_grid)
            avg_x, avg_y, avg_z = x.mean(), yv.mean(), z.mean()
            tilt = np.abs(x - avg_x) + np.abs(yv - avg_y) + np.abs(z - avg_z)

            hr_mask = (hr_t >= t_start - 60) & (hr_t <= t_end + 60)
            hr_val = (resample_to_grid(hr_t[hr_mask], hr_v[hr_mask], t_grid)
                      if hr_mask.sum() >= 1
                      else np.full(WINDOW, 68.5769, dtype=np.float32))

            hrv_val = np.full(WINDOW, compute_hrv_proxy(hr_t, hr_v, t_start, t_end), dtype=np.float32)
            mfcc_energy  = np.full(WINDOW, 0.0,  dtype=np.float32)
            time_feature = np.full(WINDOW, (t_start - rec_start) / total_span, dtype=np.float32)

            window = np.column_stack([x, yv, z, tilt, hr_val, hrv_val, mfcc_energy, time_feature])
            all_X.append(window)
            all_y.append(stage)

    if not all_X:
        return None, None
    return np.array(all_X, dtype=np.float32), np.array(all_y, dtype=np.int32)


SKIP_EXISTING = True

def bandpower_proxy(sig, fs, band=None):
    """epoch 구간 신호의 표준편차 기반 파워 근사(경량)."""
    if sig is None or len(sig) < 3:
        return 0.0
    return float(np.std(sig))


def load_sleepedf_subject(psg_path: Path):
    prefix = psg_path.stem.replace("-PSG", "")
    hyp_candidates = list(psg_path.parent.glob(f"{prefix[:-1]}*-Hypnogram*.edf"))
    if not hyp_candidates:
        print(f"Hypnogram 파일 없음: {psg_path.name}")
        return None, None

    hyp_path = hyp_candidates[0]
    try:
        raw = mne.io.read_raw_edf(psg_path, preload=True, verbose=False)
        annot = mne.read_annotations(hyp_path)
        if len(annot) == 0:
            print(f"Hypnogram annotation 없음: {hyp_path.name}")
            return None, None
        raw.set_annotations(annot, emit_warning=False)
    except Exception as e:
        print(f"  에러(EDF 로드 실패): {psg_path.name} ({type(e).__name__}: {e})")
        return None, None

    sfreq = raw.info["sfreq"]  # 보통 100Hz
    ch_names = raw.ch_names

    def get_channel(name):
        if name not in ch_names:
            return None
        return raw.get_data(picks=[name])[0]

    eeg1 = get_channel(EDF_EEG_CHANNELS[0])
    eeg2 = get_channel(EDF_EEG_CHANNELS[1]) if len(EDF_EEG_CHANNELS) > 1 else None
    eog = get_channel(EDF_EOG_CHANNEL)
    emg = get_channel(EDF_EMG_CHANNEL)

    if eeg1 is None:
        return None, None

    n_samples = len(eeg1)
    samples_per_epoch = int(EPOCH_SEC * sfreq)

    X, y = [], []
    for ann in raw.annotations:
        desc = ann["description"]
        if desc not in EDF_LABEL_MAP:
            continue
        stage = EDF_LABEL_MAP[desc]

        onset_sample = int(ann["onset"] * sfreq)
        dur_epochs = max(1, int(round(ann["duration"] / EPOCH_SEC)))

        for e in range(dur_epochs):
            s = onset_sample + e * samples_per_epoch
            en = s + samples_per_epoch
            if en > n_samples:
                break

            eeg1_seg = eeg1[s:en]
            eeg2_seg = eeg2[s:en] if eeg2 is not None else eeg1_seg
            eog_seg = eog[s:en] if eog is not None else np.zeros(samples_per_epoch)
            emg_seg = emg[s:en] if emg is not None else np.zeros(samples_per_epoch)

            # 원본 EDF 샘플레이트(100Hz)를 프로젝트 표준(50Hz, WINDOW=1500)로 리샘플
            t_src = np.linspace(0, EPOCH_SEC, len(eeg1_seg), endpoint=False)
            t_grid = np.linspace(0, EPOCH_SEC, WINDOW, endpoint=False)

            eeg1_r = resample_to_grid(t_src, eeg1_seg, t_grid)
            eeg2_r = resample_to_grid(t_src, eeg2_seg, t_grid)
            eog_r = resample_to_grid(t_src, eog_seg, t_grid)
            emg_r = resample_to_grid(t_src, emg_seg, t_grid)

            # PSG 채널 구성: eeg1, eeg2, eog, emg, hr(placeholder), hrv(placeholder), mfcc(placeholder), time
            hr_val = np.full(WINDOW, 68.5769, dtype=np.float32)  # Accel과 채널 정렬용 placeholder
            hrv_val = np.full(WINDOW, 0.0, dtype=np.float32)
            time_feature = np.full(WINDOW, ann["onset"] / (n_samples / sfreq), dtype=np.float32)

            window = np.column_stack([eeg1_r, eeg2_r, eog_r, emg_r, hr_val, hrv_val, time_feature,
                                      np.zeros(WINDOW, dtype=np.float32)])
            X.append(window)
            y.append(stage)

    if not X:
        return None, None
    return np.array(X, dtype=np.float32), np.array(y, dtype=np.int32)



def merge_and_save(subject_id: str, source: str, psg_path: Path = None):
    if source == "sleep_edf":
        tag = "EDF"
        save_dir = EDF_SAVE_DIR
    else:
        tag = "SA" if source == "sleep_accel" else "BID"
        save_dir = SLEEP_ACCEL_SAVE_DIR if source == "sleep_accel" else BIDSLEEP_SAVE_DIR

    if SKIP_EXISTING and (save_dir / f"X_{tag}_{subject_id}.npy").exists():
        print(f"이미 처리됨, 건너뜀: {tag}_{subject_id}")
        return

    try:
        if source == "sleep_edf":
            X, y = load_sleepedf_subject(psg_path)
        else:
            loader = load_sleep_accel_subject if source == "sleep_accel" else load_bidsleep_subject
            X, y = loader(subject_id)
    except Exception as e:
        print(f"에러로 스킵: {source}:{subject_id} ({type(e).__name__}: {e})")
        return
    if X is None:
        print(f"스킵: {source}:{subject_id} (데이터 없음)")
        return

    np.save(save_dir / f"X_{tag}_{subject_id}.npy", X)
    np.save(save_dir / f"y_{tag}_{subject_id}.npy", y)
    dist = np.bincount(y, minlength=N_CLASSES)
    print(f"{tag}_{subject_id}: {len(X)}윈도우 [W:{dist[0]} Light:{dist[1]} Deep:{dist[2]} REM:{dist[3]}]")


def main():
    # 💡 원본 폴더가 없거나 비어있으면 예전에는 조용히 건너뛰어서, 왜 특정 도메인의 SAVE_DIR에
    # npy가 하나도 안 생기는지 알아채기 어려웠습니다. 도메인별로 원본에서 찾은 subject 수를
    # 먼저 출력해 어느 도메인이 원본 데이터 자체가 없는 건지 바로 보이게 합니다.
    if (SLEEP_ACCEL_DIR / "labels").exists():
        sa_ids = sorted({p.stem.split("_")[0] for p in (SLEEP_ACCEL_DIR / "labels").glob("*_labeled_sleep.txt")})
        print(f"[sleep_accel] 원본에서 {len(sa_ids)}명 발견 ({SLEEP_ACCEL_DIR / 'labels'})")
        for sid in sa_ids:
            merge_and_save(sid, "sleep_accel")
    else:
        print(f"[sleep_accel] 건너뜀: {SLEEP_ACCEL_DIR / 'labels'} 폴더 없음 (원본 데이터 미업로드)")

    if BIDSLEEP_DIR.exists():
        bid_ids = sorted([p.name for p in BIDSLEEP_DIR.iterdir() if p.is_dir() and p.name.startswith("Bidslab")])
        print(f"[bidsleep] 원본에서 {len(bid_ids)}명 발견 ({BIDSLEEP_DIR})")
        for sid in bid_ids:
            merge_and_save(sid, "bidsleep")
    else:
        print(f"[bidsleep] 건너뜀: {BIDSLEEP_DIR} 폴더 없음 (원본 데이터 미업로드)")

    # 💡 추가: Sleep-EDF Expanded 처리
    if EDF_DIR.exists():
        psg_files = sorted(EDF_DIR.rglob("*-PSG.edf"))
        print(f"[sleep_edf] 원본에서 {len(psg_files)}개 PSG 파일 발견 ({EDF_DIR})")
        for psg_path in psg_files:
            sid = psg_path.stem.replace("-PSG", "")
            merge_and_save(sid, "sleep_edf", psg_path=psg_path)
    else:
        print(f"[sleep_edf] 건너뜀: {EDF_DIR} 폴더 없음 (원본 데이터 미업로드)")

    # 💡 끝나고 나서 SAVE_DIR별 실제 npy 파일 개수를 요약해, 어느 폴더가 비어있는지 한눈에 확인합니다.
    print("\n===== 결과 요약 =====")
    for name, save_dir in [
        ("sleep_accel", SLEEP_ACCEL_SAVE_DIR),
        ("bidsleep", BIDSLEEP_SAVE_DIR),
        ("sleep_edf", EDF_SAVE_DIR),
    ]:
        n_x = len(list(save_dir.glob("X_*.npy")))
        print(f"  {name}: {save_dir} 에 X_*.npy {n_x}개")

    print("\n완료.")


if __name__ == "__main__":
    main()