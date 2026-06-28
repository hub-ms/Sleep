# download_mne.py
import mne
import os
import shutil

SAVE_DIR = "data/sleep_edf"
os.makedirs(SAVE_DIR, exist_ok=True)

# 20명 피험자 인덱스 (0~9 = 10명 × 2박 = 20개 세션)
subjects  = list(range(10))   # 0~9번 피험자
recording = [1, 2]             # 각 피험자 1박, 2박

print("Sleep-EDF 20명 다운로드 시작...")

paths = mne.datasets.sleep_physionet.age.fetch_data(
    subjects=subjects,
    recording=recording,
    on_missing="warn"
)

# MNE 기본 저장 위치 → ml/data/sleep_edf 로 복사
for src in paths:
    fname = os.path.basename(src)
    dst   = os.path.join(SAVE_DIR, fname)
    if not os.path.exists(dst):
        shutil.copy2(src, dst)
        print(f"✅ {fname} ({os.path.getsize(dst)//1024//1024}MB)")
    else:
        print(f"⏭️  건너뜀: {fname}")

print(f"\n완료. 저장 위치: {os.path.abspath(SAVE_DIR)}")
print(f"파일 수: {len(os.listdir(SAVE_DIR))}개")