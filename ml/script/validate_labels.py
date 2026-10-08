"""
data/*/subjects/ 아래 전처리 산출물(.npy)이 현재 파이프라인과 맞는지 검사합니다.

검사 항목:
  1. y_*.npy의 라벨이 4클래스(0=Wake, 1=Light, 2=Deep, 3=REM) 범위 안인지
  2. X_*.npy의 채널 수가 현재 N_CHANNELS(7)인지
     💡 Phase 1에서 심박 채널을 제거해 8 -> 7로 바뀌었습니다. 구버전 8채널 .npy가 섞여 있으면
     compute_stats의 정규화 통계가 오염되고 학습은 shape 오류로 죽습니다.
  3. X와 y가 짝으로 존재하고 길이가 같은지
     🐛 train.py의 load_subjects_lazy는 X와 y가 **둘 다** 있을 때만 캐시합니다. 과거에 BIDSleep이
     X 27개 / y 2개 상태였고, 그대로 학습을 돌리면 경고 없이 2명으로 학습됩니다.

build_dataset_hybrid.py는 SKIP_EXISTING=True라 이미 있는 파일을 건너뛰므로, 구버전 산출물이
섞여 있어도 자동으로는 감지되지 않습니다. 이 스크립트로 문제 파일을 찾아 지운 뒤
build_dataset_hybrid.py를 다시 돌리면 현재 코드로 재생성됩니다.

실행: ml/script 디렉토리에서 `python validate_labels.py`
"""
from pathlib import Path

import numpy as np

from preprocess import N_CHANNELS, N_CLASSES

BASE_DIR = Path(__file__).resolve().parent.parent

# accel/PSG 도메인은 X_*.npy (WINDOW, N_CHANNELS) 그리드, mesa는 M_*.npy 스칼라 특징 시퀀스,
# apsaa/aihub는 라벨 없는 A_*.npy — 모양이 다르므로 검사 분기도 다릅니다(아래 main() 참고).
# 💡 PSG-Audio는 더 이상 쓰지 않습니다(오디오+실제 수면단계 라벨이 동시에 있는 데이터가
# 전혀 없어졌으므로, "라벨 있는 오디오" 도메인 자체가 사라졌습니다 — audio_core 스테이지도
# train.py에서 함께 제거됨).
DIRS = {
    "sleep_edf": BASE_DIR / "data" / "sleep_edf" / "subjects",
    "bidsleep": BASE_DIR / "data" / "bidsleep" / "subjects",
    "sleep_accel": BASE_DIR / "data" / "sleep_accel" / "subjects",
}
# 라벨 없는 오디오(비지도 디노이징 과제 전용) — apsaa/aihub(audio_denoise_aux).
UNLABELED_AUDIO_DIRS = {
    "apsaa": BASE_DIR / "data" / "apsaa" / "subjects",
    "aihub": BASE_DIR / "data" / "aihub_selfsleep" / "subjects",
}
# MESA 액티그래피 특징 + 기기 자체 wake 라벨(2클래스) — mesa(mesa_selfsup).
MESA_DIRS = {
    "mesa": BASE_DIR / "data" / "mesa" / "subjects",
}
# 라벨 없는 격자 도메인(X_*.npy, 7채널이지만 y_*.npy 없음) — wisdm(accel_selfsup_wisdm, 개선 6).
UNLABELED_GRID_DIRS = {
    "wisdm": BASE_DIR / "data" / "wisdm" / "subjects",
}


def _check_grid_domain(domain, directory, problems):
    """accel/PSG 도메인(X_*.npy, 고정 채널 수)의 라벨/shape 정합성을 검사합니다."""
    x_files = {f.name[len("X_"):-len(".npy")]: f for f in sorted(directory.glob("X_*.npy"))}
    y_files = {f.name[len("y_"):-len(".npy")]: f for f in sorted(directory.glob("y_*.npy"))}
    print(f"\n[{domain}] X {len(x_files)}개 / y {len(y_files)}개 ({directory})")

    for sid in sorted(set(x_files) - set(y_files)):
        problems.append((domain, sid, f"y_{sid}.npy 없음 → 학습에서 조용히 제외됩니다"))
    for sid in sorted(set(y_files) - set(x_files)):
        problems.append((domain, sid, f"X_{sid}.npy 없음"))

    for sid in sorted(set(x_files) & set(y_files)):
        X = np.load(x_files[sid], mmap_mode="r")
        y = np.load(y_files[sid])

        if X.ndim != 3 or X.shape[-1] != N_CHANNELS:
            problems.append((domain, sid, f"채널 수 {X.shape[-1] if X.ndim == 3 else '?'} != {N_CHANNELS} (shape={X.shape})"))
        if len(X) != len(y):
            problems.append((domain, sid, f"에포크 수 불일치: X {len(X)} != y {len(y)}"))
        if len(y):
            uniq = np.unique(y)
            if uniq.min() < 0 or uniq.max() >= N_CLASSES:
                problems.append((domain, sid, f"라벨 범위 벗어남: {uniq.tolist()}"))
        else:
            problems.append((domain, sid, "라벨이 비어 있음"))

    if not any(p[0] == domain for p in problems):
        print(f"  정상: 라벨 0~{N_CLASSES - 1}, 채널 {N_CHANNELS}, X/y 짝 및 길이 일치")


def _check_unlabeled_audio_domain(domain, directory, problems):
    """라벨 없는 오디오 도메인(apsaa/aihub, A_*.npy만 — y_*.npy는 아예 만들지 않음)의 shape만 검사합니다.
    audio_denoise_aux는 비지도 디노이징 과제라서 라벨이 필요 없으므로, y_*.npy가 없다고
    문제로 보고하지 않습니다(그게 정상입니다). 대신 혹시 과거에 잘못 만들어진 y_*.npy가
    남아 있으면 그 사실만 알려줍니다."""
    a_files = {f.name[len("A_"):-len(".npy")]: f for f in sorted(directory.glob("A_*.npy"))}
    y_files = {f.name[len("y_"):-len(".npy")]: f for f in sorted(directory.glob("y_*.npy"))}
    print(f"\n[{domain}] A {len(a_files)}개 (라벨 없는 비지도 과제 전용 — y_*.npy는 불필요)")

    if y_files:
        print(f"  참고: y_*.npy {len(y_files)}개가 존재함 — {domain}은 라벨을 쓰지 않으므로 무시됩니다.")

    for sid, f in a_files.items():
        A = np.load(f, mmap_mode="r")
        if A.ndim != 2:
            problems.append((domain, sid, f"오디오 배열이 2차원(에포크, 샘플)이 아님: shape={A.shape}"))

    if not any(p[0] == domain for p in problems):
        print(f"  정상: 모든 에포크가 2차원(에포크, 샘플) 배열")


def _check_mesa_domain(domain, directory, problems):
    """MESA 액티그래피 도메인(M_*.npy — 2차원 스칼라 특징 시퀀스, y_*.npy — 기기 자체 wake 2클래스)을 검사합니다.
    accel/PSG의 (에포크, WINDOW, N_CHANNELS) 원시 파형 그리드가 아니라 (에포크, 특징수) 요약 특징
    벡터이므로 N_CHANNELS 검사 대신 ndim==2만 확인하고, 라벨 범위도 N_CLASSES(4)가 아니라 0/1(2클래스)로 검사합니다."""
    m_files = {f.name[len("M_"):-len(".npy")]: f for f in sorted(directory.glob("M_*.npy"))}
    y_files = {f.name[len("y_"):-len(".npy")]: f for f in sorted(directory.glob("y_*.npy"))}
    print(f"\n[{domain}] M {len(m_files)}개 / y {len(y_files)}개 ({directory})")

    for sid in sorted(set(m_files) - set(y_files)):
        problems.append((domain, sid, f"y_{sid}.npy 없음 → 학습에서 조용히 제외됩니다"))
    for sid in sorted(set(y_files) - set(m_files)):
        problems.append((domain, sid, f"M_{sid}.npy 없음"))

    for sid in sorted(set(m_files) & set(y_files)):
        M = np.load(m_files[sid], mmap_mode="r")
        y = np.load(y_files[sid])

        if M.ndim != 2:
            problems.append((domain, sid, f"특징 배열이 2차원(에포크, 특징수)이 아님: shape={M.shape}"))
        if len(M) != len(y):
            problems.append((domain, sid, f"에포크 수 불일치: M {len(M)} != y {len(y)}"))
        if len(y):
            uniq = np.unique(y)
            if uniq.min() < 0 or uniq.max() > 1:
                problems.append((domain, sid, f"wake 라벨 범위 벗어남(0/1 기대): {uniq.tolist()}"))
        else:
            problems.append((domain, sid, "라벨이 비어 있음"))

    if not any(p[0] == domain for p in problems):
        print(f"  정상: wake 라벨 0~1, M/y 짝 및 길이 일치")


def _check_unlabeled_grid_domain(domain, directory, problems):
    """라벨 없는 격자 도메인(wisdm, X_*.npy만 — y_*.npy는 아예 만들지 않음)의 shape만 검사합니다.
    accel_selfsup_wisdm은 비지도(디노이징 재구성) 사전학습 과제라서 라벨이 필요 없으므로,
    y_*.npy가 없다고 문제로 보고하지 않습니다(그게 정상입니다). 채널 수는 accel_core와 같은
    N_CHANNELS(7)여야 사전학습된 accel_epoch_encoder 가중치를 그대로 이어줄 수 있으므로 검사합니다."""
    x_files = {f.name[len("X_"):-len(".npy")]: f for f in sorted(directory.glob("X_*.npy"))}
    y_files = {f.name[len("y_"):-len(".npy")]: f for f in sorted(directory.glob("y_*.npy"))}
    print(f"\n[{domain}] X {len(x_files)}개 (라벨 없는 자기지도 과제 전용 — y_*.npy는 불필요)")

    if y_files:
        print(f"  참고: y_*.npy {len(y_files)}개가 존재함 — {domain}은 라벨을 쓰지 않으므로 무시됩니다.")

    for sid, f in x_files.items():
        X = np.load(f, mmap_mode="r")
        if X.ndim != 3 or X.shape[-1] != N_CHANNELS:
            problems.append((domain, sid, f"채널 수 {X.shape[-1] if X.ndim == 3 else '?'} != {N_CHANNELS} (shape={X.shape})"))

    if not any(p[0] == domain for p in problems):
        print(f"  정상: 모든 에포크가 채널 {N_CHANNELS}개인 (에포크, WINDOW, {N_CHANNELS}) 배열")


def main():
    """모든 도메인(격자 기반 3개 + MESA 1개 + 라벨 없는 오디오 2개 + 라벨 없는 격자 1개)을
    검사하고 문제 목록을 출력합니다."""
    problems = []

    for domain, directory in DIRS.items():
        if not directory.exists():
            print(f"[{domain}] 건너뜀: {directory} 없음")
            continue
        _check_grid_domain(domain, directory, problems)

    for domain, directory in MESA_DIRS.items():
        if not directory.exists():
            print(f"[{domain}] 건너뜀: {directory} 없음")
            continue
        _check_mesa_domain(domain, directory, problems)

    for domain, directory in UNLABELED_AUDIO_DIRS.items():
        if not directory.exists():
            print(f"[{domain}] 건너뜀: {directory} 없음")
            continue
        _check_unlabeled_audio_domain(domain, directory, problems)

    for domain, directory in UNLABELED_GRID_DIRS.items():
        if not directory.exists():
            print(f"[{domain}] 건너뜀: {directory} 없음")
            continue
        _check_unlabeled_grid_domain(domain, directory, problems)

    print(f"\n===== 문제 {len(problems)}건 =====")
    for domain, sid, msg in problems:
        print(f"  [{domain}] {sid}: {msg}")

    if problems:
        print("\n해결: 위 subject의 X_*.npy / y_*.npy를 삭제한 뒤 build_dataset_hybrid.py를 다시")
        print("실행하세요(SKIP_EXISTING=True여도 삭제된 파일은 새로 생성됩니다).")
        print("채널 수 불일치가 다수라면 Phase 1(8 -> 7채널) 이전 산출물이므로 전체 재생성이 필요합니다.")
    return len(problems)


if __name__ == "__main__":
    main()
