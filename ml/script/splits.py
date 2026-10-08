"""subject-wise 분할 — TensorFlow에 의존하지 않는 부분만 분리했습니다.

💡 Phase 0: 기존 분할은 `sorted(...)` 결과를 그대로 70/15/15로 자르는 **알파벳 순 고정 분할**
이었습니다(train.py의 옛 `split()`). 셔플도 층화도 교차검증도 없어서 테스트셋 구성이 파일명으로
결정됐고, subject가 10명 내외라 그 한 번의 분할 운에 지표가 ±0.05~0.08 흔들렸습니다. 지금까지
기록된 개선폭(앙상블 +0.06pp 등)은 전부 그 노이즈 범위 안입니다.

train.py에서 떼어낸 이유는 테스트 가능성입니다 — train.py는 import 시점에 TensorFlow를 올리기
때문에, 분할 규칙만 확인하려 해도 TF가 설치된 환경이 필요했습니다.
"""
import numpy as np

SPLIT_SEED = 20260101


def list_subject_pairs(directories):
    """[(directory, subject_id), ...] — 여러 소스 디렉토리의 subject를 하나의 목록으로 모읍니다.

    💡 BIDSleep은 subject 하나가 3~7 night을 가지지만 .npy는 subject 단위로 저장되므로
    (X_BID_BidslabXX.npy), 이 목록의 원소 하나가 곧 하나의 group입니다 — 같은 사람의 다른 night이
    train과 test로 나뉘는 누수는 구조적으로 발생하지 않습니다.
    """
    pairs = []
    for d in directories:
        for p in sorted(d.glob("X_*.npy")):
            pairs.append((d, p.name.replace("X_", "").replace(".npy", "")))
    return pairs


def split_subject_pairs(pairs, folds=5, fold_index=0, seed=SPLIT_SEED):
    """subject-wise 분할. folds>=2면 교차검증의 fold_index번째 분할을 반환합니다.

    fold k:  test = 그룹 k,  val = 그룹 (k+1) % folds,  train = 나머지
    folds<=1이면 시드 고정 셔플 후 70/15/15로 자릅니다.

    같은 seed면 fold_index만 바꿔도 그룹 구성이 동일하므로, 5번 돌리면 모든 subject가 정확히
    한 번씩 테스트셋에 들어갑니다. aggregate_folds.py로 평균 ± 표준편차를 봐야 개선인지
    노이즈인지 구분됩니다.
    """
    pairs = list(pairs)
    rng = np.random.default_rng(seed)
    rng.shuffle(pairs)

    if folds <= 1:
        n = len(pairs)
        return pairs[: int(n * 0.7)], pairs[int(n * 0.7) : int(n * 0.85)], pairs[int(n * 0.85) :]

    fold_index %= folds
    val_index = (fold_index + 1) % folds
    groups = [[pairs[i] for i in g] for g in np.array_split(np.arange(len(pairs)), folds)]
    test = groups[fold_index]
    val = groups[val_index]
    train = [p for k, g in enumerate(groups) if k not in (fold_index, val_index) for p in g]
    return train, val, test


def pairs_to_by_dir(pairs):
    """[(dir, sid), ...] -> {dir: [sid, ...]} (데이터셋 생성 함수들이 기대하는 형태)"""
    by_dir = {}
    for d, sid in pairs:
        by_dir.setdefault(d, []).append(sid)
    return by_dir
