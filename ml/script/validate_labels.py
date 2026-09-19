"""
data/*/subjects/ 아래 y_*.npy 라벨 파일들을 스캔해서, 현재 4클래스(0=Wake, 1=Light, 2=Deep,
3=REM) 범위를 벗어난 값이 들어있는 파일을 찾아냅니다.

SKIP_EXISTING=True인 build_dataset_hybrid.py는 이미 존재하는 파일을 건너뛰므로, 예전(라벨
매핑이 다르거나 클래스 수가 달랐던) 버전으로 생성된 .npy가 섞여 있어도 자동으로 감지되지
않습니다. 이 스크립트로 문제 파일을 찾은 뒤, 지우고 build_dataset_hybrid.py를 다시 돌리면
현재 코드의 라벨 매핑으로 재생성됩니다.

실행: ml/script 디렉토리에서 `python validate_labels.py`
"""
from pathlib import Path
import numpy as np

BASE_DIR = Path(__file__).resolve().parent.parent
N_CLASSES = 4

DIRS = {
    "sleep_edf": BASE_DIR / "data" / "sleep_edf" / "subjects",
    "bidsleep": BASE_DIR / "data" / "bidsleep" / "subjects",
    "sleep_accel": BASE_DIR / "data" / "sleep_accel" / "subjects",
}


def main():
    total_bad = 0
    for domain, directory in DIRS.items():
        if not directory.exists():
            print(f"[{domain}] 건너뜀: {directory} 없음")
            continue

        y_files = sorted(directory.glob("y_*.npy"))
        print(f"\n[{domain}] {len(y_files)}개 라벨 파일 검사 중 ({directory})")

        bad_files = []
        for f in y_files:
            y = np.load(f)
            uniq = np.unique(y)
            if uniq.min() < 0 or uniq.max() >= N_CLASSES:
                bad_files.append((f, uniq))

        if bad_files:
            print(f"  ⚠️ 범위를 벗어난 라벨이 있는 파일 {len(bad_files)}개:")
            for f, uniq in bad_files:
                x_file = f.parent / f.name.replace("y_", "X_")
                print(f"    - {f.name}: 유니크 라벨={uniq.tolist()} (대응 X 파일: {x_file.name})")
            total_bad += len(bad_files)
        else:
            print(f"  정상: 모든 라벨이 0~{N_CLASSES-1} 범위 안에 있습니다.")

    print(f"\n===== 총 {total_bad}개 파일에 문제가 있습니다. =====")
    if total_bad > 0:
        print("해결: 위에 나열된 X_*.npy / y_*.npy 파일들을 삭제한 뒤 build_dataset_hybrid.py를")
        print("다시 실행하면(SKIP_EXISTING=True라도 삭제된 파일은 없으니 새로 생성됩니다) 현재")
        print("코드의 라벨 매핑으로 재생성됩니다.")


if __name__ == "__main__":
    main()
