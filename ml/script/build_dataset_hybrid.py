import numpy as np
from pathlib import Path
from scipy.io import loadmat
from datetime import datetime
from zoneinfo import ZoneInfo
from collections import deque
import mne

mne.set_log_level("ERROR")


# 💡 채널 레이아웃과 에포크 특징 계산은 preprocess.py(옛 channels.py)에 모아 두었습니다 —
# preprocess.py가 mne 없이 import할 수 있어야 하고(compute_stats/validate_labels 역할 겸용),
# Kotlin(SleepFeatureBuilder.kt)과 같은 식을 써야 하는 계산이라 한 곳에서 관리합니다.
from preprocess import (  # noqa: E402
    AUDIO_SAMPLE_RATE,
    EPOCH_SEC,
    LOOKBACK_EPOCHS,
    N_CLASSES,
    WINDOW,
    activity_magnitude,
    causal_time_feature,
    compute_causal_trend_variability,
    compute_tilt,
)

# 💡 cwd(현재 작업 디렉토리)가 로컬/Colab에서 다를 수 있어(ml/, ml/script/ 등) cwd에 의존하지
# 않도록 이 파일(ml/script/build_dataset_hybrid.py) 자신의 위치를 기준으로 ml/ 폴더를 찾습니다.
BASE_DIR = Path(__file__).resolve().parent.parent

SLEEP_ACCEL_DIR = BASE_DIR / "data" / "sleep_accel"
BIDSLEEP_DIR    = BASE_DIR / "data" / "bidsleep"
EDF_DIR = BASE_DIR / "data" / "sleep_edf"
# 신규: MESA(NSRR, 가속도 보조 사전학습용) — 액티그래피(Actiwatch Spectrum)만 받아오면 되고
# PSG EDF는 필요 없다(아래 load_mesa_subject 참고 — 기기 자체의 raw 3축 가속도가 없어
# accel_core와 같은 (WINDOW,7) 그리드를 만들 수 없으므로, 기기가 이미 뽑아 주는 요약 지표
# (활동량 카운트 등)를 그대로 쓴다).
MESA_DIR = BASE_DIR / "data" / "mesa"
# 신규: APSAA(Zenodo 14096541, 공개) / AI-Hub 자가수면검사 — 둘 다 오디오 노이즈 강건성
# 보조 학습(audio_denoise_aux)에만 쓰고 라벨은 쓰지 않는다(세션에서 라벨 신뢰 불가로 판단).
APSAA_DIR = BASE_DIR / "data" / "apsaa"
AIHUB_DIR = BASE_DIR / "data" / "aihub_selfsleep"
# 신규(개선 6): WISDM(공개, 신청 불필요) — 수면 라벨이 전혀 없는 일상 동작(걷기/앉기 등) 폰
# 가속도계 데이터셋. accel_core 지도학습에는 쓸 수 없지만, BIDSleep/Sleep-Accel보다 훨씬
# 크고 다양한 원시 3축 가속도라서 accel_epoch_encoder를 먼저 자기지도(디노이징 재구성)로
# 사전학습하는 용도로만 쓴다(train.py의 accel_selfsup_wisdm 스테이지). MESA와 달리 원시
# x/y/z를 실제로 제공하므로 accel_core의 7채널 그리드와 같은 채널 레이아웃을 그대로
# 만들 수 있어, 사전학습된 인코더 가중치를 accel_core에 그대로 이어줄 수 있다(MESA는 불가능).
WISDM_DIR = BASE_DIR / "data" / "wisdm"

SLEEP_ACCEL_SAVE_DIR = SLEEP_ACCEL_DIR / "subjects"
BIDSLEEP_SAVE_DIR    = BIDSLEEP_DIR / "subjects"
EDF_SAVE_DIR = EDF_DIR / "subjects"
MESA_SAVE_DIR = MESA_DIR / "subjects"
APSAA_SAVE_DIR = APSAA_DIR / "subjects"
AIHUB_SAVE_DIR = AIHUB_DIR / "subjects"
WISDM_SAVE_DIR = WISDM_DIR / "subjects"

SLEEP_ACCEL_SAVE_DIR.mkdir(parents=True, exist_ok=True)
BIDSLEEP_SAVE_DIR.mkdir(parents=True, exist_ok=True)
EDF_SAVE_DIR.mkdir(parents=True, exist_ok=True)
MESA_SAVE_DIR.mkdir(parents=True, exist_ok=True)
APSAA_SAVE_DIR.mkdir(parents=True, exist_ok=True)
AIHUB_SAVE_DIR.mkdir(parents=True, exist_ok=True)
WISDM_SAVE_DIR.mkdir(parents=True, exist_ok=True)

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

# ── AI-Hub 자가 수면 검사 ──
# ⚠️ Phase 0 스파이크 필요: 채널명은 추정값입니다. 실제 파일에서
# mne.io.read_raw_edf(edf_path, preload=False).ch_names 로 확인이 필요합니다.
AIHUB_SOUND_CHANNEL = "Sound"

# ── WISDM(가속도 자기지도 사전학습) ──
# 2019년판 WISDM(Smartphone and Smartwatch Activity and Biometrics)의 폰 가속도계 샘플링
# 주기. 에포크당 "최소 몇 개 샘플이 있어야 쓸 만한 에포크인가"를 정하는 데만 씁니다
# (출력 그리드는 다른 소스와 동일하게 WINDOW=1500, 50Hz로 리샘플링됩니다).
WISDM_NATIVE_HZ = 20
# 30초 에포크에서 기대 샘플 수(WISDM_NATIVE_HZ * EPOCH_SEC) 대비 이 비율 이상 실제 샘플이
# 있어야 에포크로 채택합니다. load_wisdm_subject의 주석 참고.
MIN_EPOCH_COVERAGE_RATIO = 0.5

# ── 오디오 전용 소스(APSAA / AI-Hub, audio_denoise_aux) ──
# 피험자 한 명에서 저장할 최대 에포크 수. None이면 제한 없음.
#
# 💡 왜 필요한가: _chunk_audio_to_epochs는 원본을 AUDIO_SAMPLE_RATE(16kHz)로 업샘플링하므로
# 에포크 1개가 480,000 float32 = 1.92MB입니다. APSAA는 32명 × 평균 880 에포크(1인 7.1시간,
# 원본은 4kHz mono)라 전량 저장하면 약 54GB가 되고, Colab으로 올릴 tar를 만들 수조차 없습니다.
# 반면 train.py의 run_audio_denoise_aux는 매 스텝 파일 하나에서 에포크 **1개**만 무작위로
# 뽑는 비지도 디노이징 과제라(학습 전체에 걸쳐 수만 번 추출) 한 사람의 밤 전체를 들고 있을
# 필요가 없습니다. 밤 전체에 고르게 퍼진 60개면 소음 상황(코골이/뒤척임/정적)의 다양성은
# 유지되면서 32명 합계가 3.7GB로 떨어집니다.
MAX_AUDIO_EPOCHS_PER_SUBJECT = 60


def resample_to_grid(t_src, values, t_grid):
    """불규칙 샘플링된 (t_src, values)를 균일 50Hz 그리드로 선형보간."""
    if len(t_src) < 2:
        fill = values[0] if len(values) else 0.0
        return np.full(len(t_grid), fill, dtype=np.float32)
    return np.interp(t_grid, t_src, values).astype(np.float32)
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


def _parse_wisdm_raw(path: Path):
    """WISDM_ar_v1.1_raw.txt(사용자 전체가 한 파일에 합쳐진 원본)를 읽어
    {user_id: (t_sec, x, y, z)} 딕셔너리로 묶습니다. 줄 형식이 "user,activity,timestamp,x,y,z;"
    인데 세미콜론 유무/trailing comma 등 깨진 줄이 섞여 있어 한 줄씩 방어적으로 파싱합니다
    (robust_loadtxt와 같은 이유). activity 라벨은 이 스테이지(자기지도 사전학습)에서
    쓰지 않으므로 버립니다."""
    by_user = {}
    n_bad = 0
    with open(path, "r", errors="ignore") as f:
        for line in f:
            line = line.strip().rstrip(";")
            if not line:
                continue
            parts = line.split(",")
            if len(parts) != 6:
                n_bad += 1
                continue
            try:
                user_id = int(parts[0])
                timestamp_sec = float(parts[2]) / 1e9  # 나노초 -> 초
                x, y, z = float(parts[3]), float(parts[4]), float(parts[5])
            except ValueError:
                n_bad += 1
                continue
            by_user.setdefault(user_id, []).append((timestamp_sec, x, y, z))

    if n_bad:
        print(f"  경고: {path.name}에서 형식이 깨진 행 {n_bad}개를 건너뜀")

    result = {}
    for user_id, rows in by_user.items():
        rows.sort(key=lambda r: r[0])
        arr = np.array(rows, dtype=np.float64)
        t, x, y, z = arr[:, 0], arr[:, 1], arr[:, 2], arr[:, 3]
        valid = t > 0  # 일부 WISDM 배포본에 알려진 타임스탬프 0/오류 행 제거
        if valid.sum() < WINDOW:
            continue
        result[user_id] = (t[valid], x[valid], y[valid], z[valid])
    return result


def load_wisdm_subject(t, x, y, z):
    """WISDM 한 사용자의 연속 가속도 시계열을 30초 에포크로 잘라 accel_core와 같은 7채널
    그리드로 만듭니다(라벨 없음 — 자기지도 사전학습 전용, train.py의 accel_selfsup_wisdm).

    accel_core 로더들(load_sleep_accel_subject 등)과 똑같은 tilt/variability/trend 계산을
    그대로 재사용해, 사전학습된 accel_epoch_encoder 가중치가 실제 수면 데이터로 그대로
    이어질 수 있게 채널 의미를 맞춥니다. 다만 "수면 세션 시작 이후 경과 시간"이라는
    time_feature의 정의는 WISDM(짧은 일상 동작 녹화)에는 의미가 없으므로 중립값 0.5로
    고정합니다 — 이 스테이지는 FiLM/CRF/분류 헤드가 아니라 accel_epoch_encoder의 디노이징
    재구성만 학습하므로, time_feature 채널 하나의 값이 거칠어도 치명적이지 않습니다."""
    activity_mag = activity_magnitude(x, y, z)
    recent_epoch_means = deque(maxlen=LOOKBACK_EPOCHS)

    t0 = t[0]
    n_epochs = int((t[-1] - t0) // EPOCH_SEC)

    # 💡 에포크당 최소 샘플 수. 예전에는 `mask.sum() < 2`만 걸러서, 30초 구간에 샘플이 2개만
    # 있어도 resample_to_grid가 그 2점을 WINDOW(1500)개로 선형보간해 "직선에 가까운" 가짜
    # 에포크를 만들었습니다. 2019년판 WISDM은 활동 세션 사이에 기기 클럭이 크게 점프해
    # (51명 중 9명, 최대 14,286분) 이런 희박한 구간이 실제로 생깁니다 — 자기지도 디노이징
    # 재구성 과제에 이런 에포크가 섞이면 "복원하기 쉬운 직선"만 학습하게 되므로 제외합니다.
    min_samples = max(2, int(WISDM_NATIVE_HZ * EPOCH_SEC * MIN_EPOCH_COVERAGE_RATIO))

    X = []
    prev_accepted_epoch = None
    for e in range(n_epochs):
        t_start, t_end = t0 + e * EPOCH_SEC, t0 + (e + 1) * EPOCH_SEC

        # ⚡ 예전에는 에포크마다 `(t >= t_start) & (t <= t_end)`로 전체 배열 길이의 불리언
        # 마스크를 만들었습니다. 위에서 말한 타임스탬프 점프 때문에 n_epochs가 실제 녹화
        # 길이와 무관하게 커지는데(한 사용자는 28,709개), 그 전부에 64k 샘플 마스크를 씌우면
        # 한 사람만으로 약 18억 회 연산을 헛돕니다. t는 _parse_wisdm_raw에서 이미 정렬돼
        # 들어오므로 searchsorted로 구간 인덱스만 O(log n)에 찾습니다.
        lo = int(np.searchsorted(t, t_start - 0.1, side="left"))
        hi = int(np.searchsorted(t, t_end + 0.1, side="right"))
        if hi - lo < min_samples:
            continue

        # 타임스탬프 공백으로 에포크를 건너뛰었다면 여기서부터는 시간상 이어지지 않는 새
        # 구간입니다. recent_epoch_means(최근 3분 추세/변동성)는 "연속한 과거 에포크"를
        # 전제하므로, 끊긴 지점에서 비워 공백 양쪽 값이 섞이지 않게 합니다.
        if prev_accepted_epoch is not None and e != prev_accepted_epoch + 1:
            recent_epoch_means.clear()
        prev_accepted_epoch = e

        t_grid = np.linspace(t_start, t_end, WINDOW, endpoint=False)
        t_seg = t[lo:hi]

        xg = resample_to_grid(t_seg, x[lo:hi], t_grid)
        yg = resample_to_grid(t_seg, y[lo:hi], t_grid)
        zg = resample_to_grid(t_seg, z[lo:hi], t_grid)
        tilt = compute_tilt(xg, yg, zg)

        current_epoch_mean = float(activity_mag[lo:hi].mean())
        trend, variability = compute_causal_trend_variability(recent_epoch_means, current_epoch_mean)
        recent_epoch_means.append(current_epoch_mean)
        variability_ch = np.full(WINDOW, variability, dtype=np.float32)
        trend_ch = np.full(WINDOW, trend, dtype=np.float32)
        time_feature = np.full(WINDOW, 0.5, dtype=np.float32)  # 위 docstring 참고

        window = np.column_stack([xg, yg, zg, tilt, variability_ch, trend_ch, time_feature])
        X.append(window)

    if not X:
        return None
    return np.array(X, dtype=np.float32)


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
    """Apple Watch Sleep-Accel 데이터셋 한 명을 읽어 (X, y)를 만듭니다.
    가속도 텍스트 파일 + 라벨 텍스트 파일을 시간축으로 맞춘 뒤 30초 에포크 그리드(WINDOW개
    샘플)로 리샘플링하고, 인과적 활동량 추세/변동성 채널을 함께 계산합니다."""
    motion_path = SLEEP_ACCEL_DIR / "motion" / f"{subject_id}_acceleration.txt"
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
    # 추세/변동성 채널의 기반 신호(가속도 벡터 크기)
    activity_mag = activity_magnitude(ax, ay, az)

    # 💡 Phase 1: 심박(heart_rate) 채널을 더 이상 읽지 않습니다 — 앱이 수집하지 않는 신호라
    # 학습에만 쓰면 서빙에서 상수로 대체되어 불일치가 됩니다. heart_rate 파일이 없는 subject도
    # 이제 정상적으로 처리됩니다.
    labels = raw_labels[raw_labels[:, 1] != -1]

    # 💡 Phase 1: epoch 시간순으로 최근 활동량 평균만 누적하는 인과적 버퍼(미래 데이터 사용 안 함).
    recent_epoch_means = deque(maxlen=LOOKBACK_EPOCHS)

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

        tilt = compute_tilt(x, yv, z)

        # 이 epoch을 포함해 과거 LOOKBACK_EPOCHS개(최근 3분)만 보는 인과적 추세/변동성입니다.
        current_epoch_mean = float(activity_mag[mask].mean())
        trend, variability = compute_causal_trend_variability(recent_epoch_means, current_epoch_mean)
        recent_epoch_means.append(current_epoch_mean)
        variability_ch = np.full(WINDOW, variability, dtype=np.float32)
        trend_ch       = np.full(WINDOW, trend, dtype=np.float32)
        # onset은 녹화 시작 이후 경과 초입니다(t_acc를 라벨 시각에 맞춰 보정했으므로).
        time_feature = np.full(WINDOW, causal_time_feature(onset), dtype=np.float32)

        window = np.column_stack([x, yv, z, tilt, variability_ch, trend_ch, time_feature])
        X.append(window)
        y.append(stage)

    if not X:
        return None, None
    return np.array(X, dtype=np.float32), np.array(y, dtype=np.int32)


def load_bidsleep_subject(subject_id: str):
    """BIDSleep 데이터셋 한 명(여러 night 폴더를 가질 수 있음)을 읽어 (X, y)를 만듭니다.
    night마다 motion.csv + labels.mat을 각각 처리하고 인과적 버퍼도 night마다 새로 시작하며,
    모든 night의 에포크를 이어붙여 반환합니다."""
    subj_dir = BIDSLEEP_DIR / subject_id
    if not subj_dir.exists():
        return None, None

    all_X, all_y = [], []

    # 밤 폴더는 "1","2",...,"7" 이므로 motion.csv 기준 재귀 탐색
    for motion_path in sorted(subj_dir.rglob("motion.csv")):
        night_dir = motion_path.parent
        label_path = night_dir / "labels.mat"
        # 💡 Phase 1: 심박을 쓰지 않으므로 hr.csv 존재를 더 이상 요구하지 않습니다. 기존에는
        # hr.csv가 없거나 유효 행이 2개 미만이면 그 밤을 통째로 버렸는데(아래 두 군데),
        # 심박이 입력에서 빠진 지금은 버릴 이유가 없습니다 — 사용 가능한 밤이 늘어납니다.
        if not label_path.exists():
            continue

        # motion.csv: 헤더 있음 (Timestamp,x,y,z)
        # 💡 수정: np.loadtxt -> robust_loadtxt. 실제로 이 파일들에서 중간에 컬럼 수가
        #   4개->2개로 깨지는 행이 있어서 np.loadtxt가 통째로 죽는 문제가 있었음.
        motion = robust_loadtxt(motion_path, ncols=4, delimiter=",", skiprows=1)
        if motion.shape[0] < 2:
            print(f"  스킵: {night_dir} motion.csv 유효 데이터 부족")
            continue
        t_acc, ax, ay, az = motion[:, 0], motion[:, 1], motion[:, 2], motion[:, 3]
        # 추세/변동성 채널의 기반 신호(가속도 벡터 크기)
        activity_mag = activity_magnitude(ax, ay, az)

        mat = loadmat(label_path)
        # 💡 수정: recStart는 float가 아니라 사람이 읽는 시각 문자열(미국 동부시간) -> 파싱해서 UTC unix time으로 변환
        rec_start = parse_rec_start(mat)
        stage_seq = mat["expert_label"].squeeze().astype(int)  # 전문가 보정 라벨 우선 사용

        # 💡 Phase 1: 밤(수면 세션)마다 새로 시작하는 인과적 버퍼(미래 데이터 사용 안 함).
        recent_epoch_means = deque(maxlen=LOOKBACK_EPOCHS)

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
            tilt = compute_tilt(x, yv, z)

            # 이 epoch을 포함해 과거 LOOKBACK_EPOCHS개(최근 3분)만 보는 인과적 추세/변동성입니다.
            current_epoch_mean = float(activity_mag[mask].mean())
            trend, variability = compute_causal_trend_variability(recent_epoch_means, current_epoch_mean)
            recent_epoch_means.append(current_epoch_mean)
            variability_ch = np.full(WINDOW, variability, dtype=np.float32)
            trend_ch       = np.full(WINDOW, trend, dtype=np.float32)
            time_feature = np.full(WINDOW, causal_time_feature(t_start - rec_start), dtype=np.float32)

            window = np.column_stack([x, yv, z, tilt, variability_ch, trend_ch, time_feature])
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
    """Sleep-EDF Expanded 한 명(PSG .edf + 짝이 되는 Hypnogram .edf)을 읽어 (X, y)를 만듭니다.
    mne로 EDF를 로드하고, annotation(수면단계 구간)마다 30초 에포크로 잘라 원본 샘플레이트에서
    프로젝트 표준 그리드(50Hz, WINDOW개)로 리샘플링합니다. 연구용 참고 도메인(앱 미배포)."""
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

            # PSG 채널 구성(PSG_CHANNEL_NAMES와 일치): eeg1, eeg2, eog, emg, reserved, reserved, time
            # 💡 Phase 1: 심박 placeholder(상수 68.5769)를 없애고 Accel과 같은 7채널로 맞췄습니다.
            # time_feature가 두 도메인 모두 인덱스 6으로 정렬됩니다(이전에는 PSG 6 / Accel 7).
            reserved = np.zeros(WINDOW, dtype=np.float32)
            time_feature = np.full(WINDOW, causal_time_feature(ann["onset"] + e * EPOCH_SEC), dtype=np.float32)

            window = np.column_stack([eeg1_r, eeg2_r, eog_r, emg_r, reserved, reserved, time_feature])
            X.append(window)
            y.append(stage)

    if not X:
        return None, None
    return np.array(X, dtype=np.float32), np.array(y, dtype=np.int32)


def load_mesa_subject(csv_path: Path):
    """MESA 액티그래피 EBE(epoch-by-epoch) CSV 한 명을 읽어 (특징, 라벨)을 만듭니다.

    💡 중요한 설계 결정(세션에서 실제로 조사해 확인): MESA가 쓰는 Actiwatch Spectrum 기기는
    **원시 3축 가속도를 전혀 출력하지 않습니다** — 30초 에포크당 "활동량 카운트" 등 요약
    지표만 냅니다. 그래서 BIDSleep/Sleep-Accel처럼 (WINDOW=1500, N_CHANNELS=7) raw 파형
    그리드를 만들 수 없고, accel_core의 CNN 인코더에 그대로 넣을 수도 없습니다. 대신 CSV가
    이미 제공하는 스칼라 컬럼들을 에포크 특징 벡터로 쓰고, train.py의 mesa_selfsup은 이
    저차원 특징 전용의 별도 인코더로 학습합니다(accel_core의 가중치를 직접 재사용하지 않음
    — 입력 표현이 근본적으로 다르기 때문입니다).

    라벨도 PSG가 아니라 **기기 자체의 wake 지표**(0=sleep, 1=wake)를 씁니다 — 이 보조
    학습의 목적이 "가속도 유사 신호만으로 수면/각성을 구분하는 표현"을 사전학습하는
    것이므로, 훨씬 더 무거운 PSG EDF까지 받아올 필요가 없습니다(다운로드량도 훨씬 작음 —
    NSRR에서 `actigraphy/` 폴더만 받으면 됩니다).

    ⚠️ Phase 0 확인 필요: 아래 컬럼명(activity/whitelight/.../wake/offwrist)은 NSRR 문서에
    근거한 추정입니다. 실제 CSV 헤더와 다르면 이 함수의 컬럼명만 맞추면 됩니다.
    """
    import csv as csv_module

    if not csv_path.exists():
        return None, None

    try:
        with open(csv_path, newline="", encoding="utf-8") as f:
            rows = list(csv_module.DictReader(f))
    except Exception as e:
        print(f"  에러(CSV 로드 실패): {csv_path.name} ({type(e).__name__}: {e})")
        return None, None

    def _f(row, key, default=0.0):
        try:
            v = row.get(key, default)
            return float(v) if v not in (None, "") else default
        except ValueError:
            return default

    M, y = [], []
    for row in rows:
        # offwrist(손목에서 벗겨짐) 구간은 신호가 무의미하므로 제외합니다.
        if _f(row, "offwrist", 0.0) >= 1.0:
            continue
        features = [
            _f(row, "activity"),
            _f(row, "whitelight"),
            _f(row, "redlight"),
            _f(row, "greenlight"),
            _f(row, "bluelight"),
        ]
        M.append(features)
        y.append(1 if _f(row, "wake", 0.0) >= 1.0 else 0)

    if not M:
        return None, None
    return np.array(M, dtype=np.float32), np.array(y, dtype=np.int32)


def _load_wav_mono(wav_path: Path):
    """WAV 파일을 모노 float32([-1,1])로 읽습니다. 정수 PCM이면 정규화합니다."""
    from scipy.io import wavfile

    src_sr, audio = wavfile.read(wav_path)
    if audio.ndim > 1:
        audio = audio.mean(axis=1)
    if np.issubdtype(audio.dtype, np.integer):
        audio = audio.astype(np.float32) / 32768.0
    else:
        audio = audio.astype(np.float32)
    return src_sr, audio


def _chunk_audio_to_epochs(audio, src_sr, max_epochs=MAX_AUDIO_EPOCHS_PER_SUBJECT):
    """1차원 오디오를 30초 에포크로 잘라 16kHz(AUDIO_SAMPLE_RATE)로 리샘플링합니다.
    라벨이 없는 오디오 전용 소스(APSAA/AI-Hub)가 공통으로 쓰는 헬퍼입니다.

    [max_epochs]개를 넘으면 **밤 전체에 고르게 퍼진** 그만큼의 에포크만 고릅니다
    (MAX_AUDIO_EPOCHS_PER_SUBJECT의 주석 참고 — 용량이 1인당 1.6GB에서 115MB로 줄어듭니다).
    고른 에포크만 리샘플링하는 순서가 중요합니다: 전량을 리샘플링한 뒤 자르면 1인당
    880회 np.interp(480,000 포인트)를 그대로 돌게 되어 전처리 시간이 약 15배 길어집니다."""
    epoch_samples_src = int(src_sr * EPOCH_SEC)
    n_epochs = len(audio) // epoch_samples_src
    if n_epochs == 0:
        return None

    if max_epochs is not None and n_epochs > max_epochs:
        # linspace(endpoint=False)로 처음~끝을 균등 분할 — 밤의 앞/중간/뒤 소음 상황이
        # 고르게 섞이게 합니다(앞 60개만 자르면 잠들기 직전 구간만 남습니다).
        indices = np.unique(np.linspace(0, n_epochs, max_epochs, endpoint=False).astype(int))
    else:
        indices = np.arange(n_epochs)

    epoch_samples_dst = int(AUDIO_SAMPLE_RATE * EPOCH_SEC)
    t_dst = np.linspace(0, EPOCH_SEC, epoch_samples_dst, endpoint=False)
    X = []
    for e in indices:
        seg = audio[e * epoch_samples_src:(e + 1) * epoch_samples_src]
        t_src = np.linspace(0, EPOCH_SEC, len(seg), endpoint=False)
        X.append(resample_to_grid(t_src, seg, t_dst))
    return np.array(X, dtype=np.float32)


def load_apsaa_subject(wav_path: Path):
    """APSAA의 WAV 오디오 파일 하나를 30초 에포크 원시 오디오로 자릅니다. 라벨은 만들지
    않습니다 — audio_denoise_aux는 비지도 디노이징 과제라 라벨이 필요 없고, APSAA의 주석은
    호흡(무호흡/저호흡) 이벤트 위주라 수면단계 라벨로 신뢰할 수 없다고 세션에서 판단했습니다."""
    if not wav_path.exists():
        return None
    try:
        src_sr, audio = _load_wav_mono(wav_path)
    except Exception as e:
        print(f"  에러(WAV 로드 실패): {wav_path.name} ({type(e).__name__}: {e})")
        return None
    return _chunk_audio_to_epochs(audio, src_sr)


def load_aihub_subject(edf_path: Path):
    """AI-Hub 자가 수면 검사 데이터셋의 단일 EDF(ECG+actigraphy+sound 결합, 250Hz)에서
    사운드 채널만 분리 로드해 30초 에포크로 자릅니다. 라벨은 만들지 않습니다 — 이 데이터셋의
    PSG는 검사 시점과 6개월 전/후에 별도로 시행되어 에포크 단위 수면단계 라벨로 쓸 수
    없다고 세션에서 확인했습니다(audio_denoise_aux 비지도 과제에만 사용)."""
    if not edf_path.exists():
        return None
    try:
        raw = mne.io.read_raw_edf(edf_path, preload=False, verbose=False)
        if AIHUB_SOUND_CHANNEL not in raw.ch_names:
            print(f"  스킵: {edf_path.name} — '{AIHUB_SOUND_CHANNEL}' 채널 없음 "
                  f"(실제 채널: {raw.ch_names})")
            return None
        raw.load_data(picks=[AIHUB_SOUND_CHANNEL])
        audio = raw.get_data(picks=[AIHUB_SOUND_CHANNEL])[0]
        src_sr = float(raw.info["sfreq"])
    except Exception as e:
        print(f"  에러(EDF 로드 실패): {edf_path.name} ({type(e).__name__}: {e})")
        return None
    return _chunk_audio_to_epochs(audio, src_sr)


# 소스별 (저장 접두어, 저장 디렉토리, 라벨 존재 여부). "라벨 존재 여부"가 False인 소스는
# audio_denoise_aux처럼 비지도 과제에만 쓰이는 오디오 전용 소스입니다(y_*.npy를 만들지 않음).
_SOURCE_SPEC = {
    "sleep_edf":   ("X", "EDF",    EDF_SAVE_DIR,        True),
    "sleep_accel": ("X", "SA",     SLEEP_ACCEL_SAVE_DIR, True),
    "bidsleep":    ("X", "BID",    BIDSLEEP_SAVE_DIR,   True),
    "mesa":        ("M", "MESA",   MESA_SAVE_DIR,       True),
    "apsaa":       ("A", "APSAA",  APSAA_SAVE_DIR,      False),
    "aihub":       ("A", "AIHUB",  AIHUB_SAVE_DIR,      False),
    "wisdm":       ("X", "WISDM",  WISDM_SAVE_DIR,      False),
}


def merge_and_save(subject_id: str, source: str, psg_path=None):
    """한 subject를 적절한 로더로 처리해 (X|A|M)_*.npy / (있으면) y_*.npy로 저장합니다.
    SKIP_EXISTING=True면 필요한 파일이 **모두** 이미 있는 경우에만 건너뜁니다(라벨이 없는
    소스는 데이터 파일만, 있는 소스는 데이터+라벨 둘 다 확인).

    ⚠️ source="wisdm"일 때는 psg_path가 Path가 아니라 (t, x, y, z) 튜플입니다 — WISDM은
    다른 소스처럼 "원본 파일 하나 = subject 하나"가 아니라 사용자 전체가 한 파일에 합쳐져
    있어서(_parse_wisdm_raw), main()에서 사용자별로 미리 잘라낸 배열을 바로 넘겨줍니다."""
    x_prefix, tag, save_dir, has_label = _SOURCE_SPEC[source]
    x_path = save_dir / f"{x_prefix}_{tag}_{subject_id}.npy"
    y_path = save_dir / f"y_{tag}_{subject_id}.npy"

    # 🐛 버그 수정: 이 분기는 아직 로드하지도 않은 지역변수(motion, raw_labels)를 출력하려 해서
    # 실제로 건너뛸 때마다 NameError로 죽었습니다(디버그용 print가 남은 것으로 보임).
    # 또한 X만 보고 건너뛰었기 때문에, y가 없는(전처리가 중간에 끊긴) subject를 영원히 건너뛰어
    # 학습에서 조용히 제외되는 원인이 됐습니다 — train.py의 load_subjects_lazy는 X와 y가 모두
    # 있을 때만 캐시합니다. 라벨이 있는 소스는 둘 다, 없는 소스는 데이터 파일만 확인합니다.
    required_ready = x_path.exists() and (y_path.exists() if has_label else True)
    if SKIP_EXISTING and required_ready:
        print(f"이미 처리됨, 건너뜀: {tag}_{subject_id}")
        return
    if SKIP_EXISTING and x_path.exists() and not required_ready:
        print(f"데이터만 있고 라벨이 없어 다시 처리합니다: {tag}_{subject_id}")

    try:
        if source == "sleep_edf":
            X, y = load_sleepedf_subject(psg_path)
        elif source == "mesa":
            X, y = load_mesa_subject(psg_path)
        elif source == "apsaa":
            X, y = load_apsaa_subject(psg_path), None
        elif source == "aihub":
            X, y = load_aihub_subject(psg_path), None
        elif source == "wisdm":
            t, x, y_axis, z = psg_path
            X, y = load_wisdm_subject(t, x, y_axis, z), None
        else:
            loader = load_sleep_accel_subject if source == "sleep_accel" else load_bidsleep_subject
            X, y = loader(subject_id)
    except Exception as e:
        print(f"에러로 스킵: {source}:{subject_id} ({type(e).__name__}: {e})")
        return
    if X is None:
        print(f"스킵: {source}:{subject_id} (데이터 없음)")
        return

    np.save(x_path, X)
    if has_label:
        np.save(y_path, y)
        dist = np.bincount(y, minlength=N_CLASSES if source != "mesa" else 2)
        print(f"{tag}_{subject_id}: {len(X)}윈도우 분포={dist.tolist()}")
    else:
        print(f"{tag}_{subject_id}: {len(X)}윈도우 (라벨 없음 — 비지도 과제 전용)")




def main():
    """7개 데이터 소스(sleep_accel, bidsleep, sleep_edf, mesa, apsaa, aihub, wisdm)를
    원본에서 찾아 전부 merge_and_save()에 넘기고, 끝나면 소스별 산출물 개수를 요약합니다."""
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

    # 신규: MESA 처리 (가속도 보조 사전학습, mesa_selfsup).
    # ⚠️ PSG EDF는 받지 않고 액티그래피 CSV만 씁니다(load_mesa_subject 참고 — 이 기기는
    # 원시 가속도를 제공하지 않음). NSRR에서 `nsrr download mesa/actigraphy`로 받은
    # `*.csv`(또는 `.csv.gz`를 풀어 둔) 파일들을 mesa/raw/ 에 그대로 두면 됩니다.
    mesa_raw = MESA_DIR / "raw"
    if mesa_raw.exists():
        csv_files = sorted(mesa_raw.glob("*.csv"))
        print(f"[mesa] 원본에서 {len(csv_files)}개 액티그래피 CSV 발견 ({mesa_raw})")
        for csv_path in csv_files:
            sid = csv_path.stem
            merge_and_save(sid, "mesa", psg_path=csv_path)
    else:
        print(f"[mesa] 건너뜀: {mesa_raw} 폴더 없음 (원본 데이터 미업로드 또는 NSRR 승인 전)")

    # 신규: APSAA 처리 (오디오 노이즈 강건성 보조, audio_denoise_aux). 라벨 없음.
    apsaa_raw = APSAA_DIR / "raw"
    if apsaa_raw.exists():
        wav_files = sorted(apsaa_raw.glob("*.wav"))
        print(f"[apsaa] 원본에서 {len(wav_files)}개 WAV 파일 발견 ({apsaa_raw})")
        for wav_path in wav_files:
            sid = wav_path.stem
            merge_and_save(sid, "apsaa", psg_path=wav_path)
    else:
        print(f"[apsaa] 건너뜀: {apsaa_raw} 폴더 없음 (원본 데이터 미업로드)")

    # 신규: AI-Hub 자가수면검사 처리 (오디오 노이즈 강건성 보조, audio_denoise_aux). 라벨 없음.
    aihub_raw = AIHUB_DIR / "raw"
    if aihub_raw.exists():
        edf_files = sorted(aihub_raw.glob("*.edf"))
        print(f"[aihub] 원본에서 {len(edf_files)}개 EDF 파일 발견 ({aihub_raw})")
        for edf_path in edf_files:
            sid = edf_path.stem
            merge_and_save(sid, "aihub", psg_path=edf_path)
    else:
        print(f"[aihub] 건너뜀: {aihub_raw} 폴더 없음 (원본 데이터 미업로드)")

    # 신규(개선 6): WISDM 처리 (accel_epoch_encoder 자기지도 사전학습, accel_selfsup_wisdm).
    # 라벨 없음. 다른 소스와 달리 "원본 파일 하나 = subject 하나"가 아니라, 공개 배포
    # 원본(WISDM_ar_v1.1_raw.txt) 하나에 모든 사용자가 합쳐져 있어서 _parse_wisdm_raw로
    # user id별로 먼저 쪼갠 뒤, 사용자마다 merge_and_save를 호출합니다.
    wisdm_raw = WISDM_DIR / "raw" / "WISDM_ar_v1.1_raw.txt"
    if wisdm_raw.exists():
        users = _parse_wisdm_raw(wisdm_raw)
        print(f"[wisdm] 원본에서 사용자 {len(users)}명 발견 ({wisdm_raw})")
        for user_id, (t, x, y_axis, z) in users.items():
            merge_and_save(f"user{user_id}", "wisdm", psg_path=(t, x, y_axis, z))
    else:
        print(f"[wisdm] 건너뜀: {wisdm_raw} 없음 (원본 데이터 미다운로드)")

    # 💡 끝나고 나서 SAVE_DIR별 실제 npy 파일 개수를 요약해, 어느 폴더가 비어있는지 한눈에 확인합니다.
    print("\n===== 결과 요약 =====")
    for name, save_dir, prefix in [
        ("sleep_accel", SLEEP_ACCEL_SAVE_DIR, "X"),
        ("bidsleep", BIDSLEEP_SAVE_DIR, "X"),
        ("sleep_edf", EDF_SAVE_DIR, "X"),
        ("mesa", MESA_SAVE_DIR, "M"),
        ("apsaa", APSAA_SAVE_DIR, "A"),
        ("aihub", AIHUB_SAVE_DIR, "A"),
        ("wisdm", WISDM_SAVE_DIR, "X"),
    ]:
        n_x = len(list(save_dir.glob(f"{prefix}_*.npy")))
        print(f"  {name}: {save_dir} 에 {prefix}_*.npy {n_x}개")

    print("\n완료.")



if __name__ == "__main__":
    main()