"""평가 공용 모듈 — 지표, 후처리(다수결/Viterbi/이동평균), 야간 전체 추론, 앙상블, 체크포인트 재평가.

옛 metrics.py + postprocess.py + evaluation.py + aggregate_folds.py + evaluate_checkpoint.py를
합쳤습니다. metrics.py/postprocess.py 부분은 numpy/sklearn만 써서 로컬에서 바로 테스트할 수
있고(test_evaluate.py 참고), evaluate_subjects/evaluate_checkpoint 부분은 Keras 모델의
.predict()를 호출하므로 TensorFlow가 필요합니다(이 환경에는 없어 그 부분은 실행 검증을
못했습니다 — 옛 evaluation.py/evaluate_checkpoint.py에서 그대로 포팅한 로직입니다).

신규: moving_average_filter — 소프트맥스 확률 시퀀스에 이동평균을 적용하는 후처리(사용자가
명시적으로 요청한 후처리 기법). mode_filter(하드 라벨 다수결)와 달리 확률 공간에서 평균을 낸
뒤 argmax하므로, 확신도가 낮은 순간적 흔들림을 더 매끄럽게 눌러줍니다.
"""
import argparse
import json
import re
from pathlib import Path

import numpy as np
from sklearn.metrics import (
    accuracy_score,
    balanced_accuracy_score,
    classification_report,
    cohen_kappa_score,
    confusion_matrix,
    f1_score,
)

BASE_DIR = Path(__file__).resolve().parent.parent
OUTPUT_DIR = BASE_DIR / "output"

CLASS_NAMES = ["Wake", "Light", "Deep", "REM"]
N_CLASSES = 4


# ============================================================
# 1. 후처리 (옛 postprocess.py + 이동평균 신규 추가)
# ============================================================
def mode_filter(seq, window=5):
    """하드 라벨 시퀀스에 슬라이딩 윈도우 다수결을 적용합니다."""
    seq = np.asarray(seq)
    half = window // 2
    padded = np.pad(seq, (half, half), mode="edge")
    out = np.empty_like(seq)
    for i in range(len(seq)):
        vals, counts = np.unique(padded[i:i + window], return_counts=True)
        out[i] = vals[np.argmax(counts)]
    return out


def moving_average_filter(probs, window=5):
    """소프트맥스 확률 시퀀스(T, K)에 중심 이동평균을 적용한 뒤 다시 argmax합니다(신규).

    mode_filter는 이미 argmax된 **하드 라벨**에 다수결을 적용하지만, 이 함수는 argmax
    이전의 **확률**을 평균합니다 — 예를 들어 [Wake 0.51, Light 0.49]처럼 확신이 낮은
    프레임들이 연속될 때, 하드 라벨 다수결보다 더 부드럽게 흔들림을 눌러줍니다. 가장자리는
    mode_filter와 마찬가지로 edge 패딩을 씁니다(경계에서 창이 짧아지지 않도록).
    """
    probs = np.asarray(probs, dtype=np.float64)
    t_len, n_classes = probs.shape
    half = window // 2
    padded = np.pad(probs, ((half, half), (0, 0)), mode="edge")
    smoothed = np.empty_like(probs)
    for i in range(t_len):
        smoothed[i] = padded[i:i + window].mean(axis=0)
    return np.argmax(smoothed, axis=-1)


def viterbi_smooth(probs, transition_matrix, init_probs=None):
    """로그공간 Viterbi 디코딩(DP). transition_matrix는 고정 행렬(학습셋에서 계산된 경험적
    전이확률, 또는 model.py의 TransitionCRF가 학습한 행렬 — 어느 쪽이든 이 함수는 동일하게
    동작합니다)."""
    T, K = probs.shape
    logA = np.log(transition_matrix + 1e-12)
    logB = np.log(np.clip(probs, 1e-12, 1.0))

    if init_probs is None:
        init_probs = np.full(K, 1.0 / K)
    logpi = np.log(init_probs + 1e-12)

    delta = np.zeros((T, K))
    psi = np.zeros((T, K), dtype=np.int32)
    delta[0] = logpi + logB[0]

    for t in range(1, T):
        for k in range(K):
            scores = delta[t - 1] + logA[:, k]
            psi[t, k] = np.argmax(scores)
            delta[t, k] = np.max(scores) + logB[t, k]

    path = np.zeros(T, dtype=np.int32)
    path[-1] = np.argmax(delta[-1])
    for t in range(T - 2, -1, -1):
        path[t] = psi[t + 1, path[t + 1]]
    return path


def apply_all_smoothings(y_raw, probs, transition_matrix, mode_window=5, ma_window=5):
    return {
        "raw": y_raw,
        "mode": mode_filter(y_raw, window=mode_window),
        "moving_average": moving_average_filter(probs, window=ma_window),
        "viterbi": viterbi_smooth(probs, transition_matrix),
    }


# ============================================================
# 2. 지표 (옛 metrics.py)
# ============================================================
def epoch_metrics(y_true, y_pred, n_classes, class_names):
    y_true = np.asarray(y_true).reshape(-1)
    y_pred = np.asarray(y_pred).reshape(-1)
    labels = list(range(n_classes))

    cm = confusion_matrix(y_true, y_pred, labels=labels)
    row_sums = cm.sum(axis=1, keepdims=True)
    cm_normalized = np.divide(
        cm, row_sums, out=np.zeros(cm.shape, dtype=np.float64), where=row_sums > 0
    )

    return {
        "n_epochs": int(len(y_true)),
        "accuracy": float(accuracy_score(y_true, y_pred)),
        "balanced_accuracy": float(balanced_accuracy_score(y_true, y_pred)),
        "cohen_kappa": float(cohen_kappa_score(y_true, y_pred, labels=labels)),
        "macro_f1": float(f1_score(y_true, y_pred, labels=labels, average="macro", zero_division=0)),
        "weighted_f1": float(f1_score(y_true, y_pred, labels=labels, average="weighted", zero_division=0)),
        "per_class_f1": {
            name: float(score)
            for name, score in zip(
                class_names, f1_score(y_true, y_pred, labels=labels, average=None, zero_division=0)
            )
        },
        "confusion_matrix": cm.tolist(),
        "confusion_matrix_normalized": cm_normalized.tolist(),
        "sklearn_report": classification_report(
            y_true, y_pred, labels=labels, target_names=class_names, digits=4, zero_division=0
        ),
    }


def per_subject_metrics(subject_predictions, n_classes):
    labels = list(range(n_classes))
    rows = {}
    for sid, (y_true, y_pred) in sorted(subject_predictions.items()):
        y_true = np.asarray(y_true).reshape(-1)
        y_pred = np.asarray(y_pred).reshape(-1)
        if len(y_true) == 0:
            continue
        rows[sid] = {
            "n_epochs": int(len(y_true)),
            "accuracy": float(accuracy_score(y_true, y_pred)),
            "balanced_accuracy": float(balanced_accuracy_score(y_true, y_pred)),
            "cohen_kappa": float(cohen_kappa_score(y_true, y_pred, labels=labels)),
            "macro_f1": float(f1_score(y_true, y_pred, labels=labels, average="macro", zero_division=0)),
        }

    summary = {}
    for key in ("accuracy", "balanced_accuracy", "cohen_kappa", "macro_f1"):
        values = np.array([r[key] for r in rows.values()], dtype=np.float64)
        if len(values) == 0:
            summary[key] = {"mean": 0.0, "std": 0.0, "min": 0.0, "max": 0.0, "n_subjects": 0}
            continue
        summary[key] = {
            "mean": float(values.mean()),
            "std": float(values.std(ddof=1)) if len(values) > 1 else 0.0,
            "min": float(values.min()),
            "max": float(values.max()),
            "n_subjects": int(len(values)),
        }

    return {"per_subject": rows, "summary": summary}


def format_metrics_report(label, metrics, subject_metrics=None, class_names=None):
    lines = [
        f"=== {label} ===",
        f"에포크 수(중복 없음): {metrics['n_epochs']}",
        "",
        f"accuracy          : {metrics['accuracy']:.4f}",
        f"balanced accuracy : {metrics['balanced_accuracy']:.4f}",
        f"Cohen's kappa     : {metrics['cohen_kappa']:.4f}",
        f"macro F1          : {metrics['macro_f1']:.4f}",
        f"weighted F1       : {metrics['weighted_f1']:.4f}",
        "",
        "클래스별 F1:",
    ]
    for name, score in metrics["per_class_f1"].items():
        lines.append(f"  {name:<6}: {score:.4f}")

    names = class_names or list(metrics["per_class_f1"].keys())
    lines += ["", "Confusion matrix (행=정답, 열=예측):"]
    header = "          " + "".join(f"{n:>10}" for n in names)
    lines.append(header)
    for name, row in zip(names, metrics["confusion_matrix"]):
        lines.append(f"{name:>10}" + "".join(f"{v:>10}" for v in row))

    lines += ["", "정규화 confusion matrix (행 기준 = 클래스별 recall 분해):"]
    lines.append(header)
    for name, row in zip(names, metrics["confusion_matrix_normalized"]):
        lines.append(f"{name:>10}" + "".join(f"{v:>10.4f}" for v in row))

    if subject_metrics:
        lines += ["", f"subject별 지표 ({subject_metrics['summary']['macro_f1']['n_subjects']}명):"]
        for key, label_ko in (
            ("accuracy", "accuracy"), ("balanced_accuracy", "balanced acc"),
            ("cohen_kappa", "kappa"), ("macro_f1", "macro F1"),
        ):
            s = subject_metrics["summary"][key]
            lines.append(
                f"  {label_ko:<13}: {s['mean']:.4f} ± {s['std']:.4f}  (min {s['min']:.4f} / max {s['max']:.4f})"
            )
        lines += ["", "  subject별 상세:"]
        for sid, r in subject_metrics["per_subject"].items():
            lines.append(
                f"    {sid:<24} n={r['n_epochs']:>6}  acc={r['accuracy']:.4f}  "
                f"kappa={r['cohen_kappa']:.4f}  macroF1={r['macro_f1']:.4f}"
            )

    lines += ["", "--- sklearn classification_report ---", metrics["sklearn_report"]]
    return "\n".join(lines)


def save_metrics(out_dir, label, metrics, subject_metrics=None, class_names=None):
    out_dir.mkdir(parents=True, exist_ok=True)
    report = format_metrics_report(label, metrics, subject_metrics, class_names)
    (out_dir / f"{label}_classification_report.txt").write_text(report, encoding="utf-8")

    payload = {k: v for k, v in metrics.items() if k != "sklearn_report"}
    if subject_metrics:
        payload["per_subject"] = subject_metrics["per_subject"]
        payload["per_subject_summary"] = subject_metrics["summary"]
    (out_dir / f"{label}_metrics.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(f"\n{report}\n저장: {out_dir / f'{label}_classification_report.txt'}")
    return report


# ============================================================
# 3. 야간 전체 추론 + 에포크 1회 집계 (옛 evaluation.py) — TF 필요
# ============================================================
def _tile_starts(n_epochs, context_len):
    starts = list(range(0, max(1, n_epochs - context_len + 1), context_len))
    covered = starts[-1] + context_len
    if covered < n_epochs:
        starts.append(n_epochs - context_len)
    return starts


def _normalize(x_slice, mean, std):
    return (np.asarray(x_slice, dtype=np.float32) - mean) / std


def _predict_windows(infer_model, windows, batch_size):
    out = []
    for i in range(0, len(windows), batch_size):
        batch = np.stack(windows[i: i + batch_size], axis=0)
        logits = infer_model.predict(batch, verbose=0).astype(np.float64)
        logits -= logits.max(axis=-1, keepdims=True)
        exp = np.exp(logits)
        out.append(exp / exp.sum(axis=-1, keepdims=True))
    return np.concatenate(out, axis=0) if out else np.zeros((0, 0, 0))


def predict_night_probs(infer_model, X, mean, std, context_len, n_classes, batch_size=8, overlap_stride=None):
    """야간 전체에 대해 에포크당 정확히 하나의 확률 분포를 반환합니다. shape (n_epochs, n_classes)."""
    n_epochs = len(X)
    if n_epochs == 0:
        return np.zeros((0, n_classes), dtype=np.float64)

    if n_epochs < context_len:
        pad = context_len - n_epochs
        window = np.concatenate(
            [np.repeat(np.asarray(X[0:1]), pad, axis=0), np.asarray(X[:])], axis=0
        )
        probs = _predict_windows(infer_model, [_normalize(window, mean, std)], batch_size)[0]
        return probs[pad:]

    if overlap_stride is None:
        starts = _tile_starts(n_epochs, context_len)
        windows = [_normalize(X[s: s + context_len], mean, std) for s in starts]
        preds = _predict_windows(infer_model, windows, batch_size)

        probs = np.zeros((n_epochs, n_classes), dtype=np.float64)
        filled = np.zeros(n_epochs, dtype=bool)
        for s, win_probs in zip(starts, preds):
            idx = np.arange(s, s + context_len)
            take = ~filled[idx]
            probs[idx[take]] = win_probs[take]
            filled[idx[take]] = True
        assert filled.all(), "모든 에포크가 정확히 한 번 예측되어야 합니다."
        return probs

    starts = list(range(0, n_epochs - context_len + 1, overlap_stride))
    if starts[-1] + context_len < n_epochs:
        starts.append(n_epochs - context_len)
    windows = [_normalize(X[s: s + context_len], mean, std) for s in starts]
    preds = _predict_windows(infer_model, windows, batch_size)

    acc = np.zeros((n_epochs, n_classes), dtype=np.float64)
    cnt = np.zeros((n_epochs, 1), dtype=np.float64)
    for s, win_probs in zip(starts, preds):
        acc[s: s + context_len] += win_probs
        cnt[s: s + context_len] += 1.0
    return acc / np.maximum(cnt, 1.0)


def evaluate_subjects(
    infer_model, subjects, context_len, n_classes, class_names, out_dir, label,
    transition_matrix=None, mode_window=5, ma_window=5, batch_size=8, overlap_stride=None, save_probs=True,
):
    """subject별 야간 전체 추론 → 후처리(raw/mode/moving_average/viterbi) → 에포크 1회 집계 지표.

    subjects: [(subject_id, X, y, mean, std), ...]
    반환: {variant: metrics dict}
    """
    variants = ["raw", "mode", "moving_average"] + (["viterbi"] if transition_matrix is not None else [])
    concat = {v: {"true": [], "pred": []} for v in variants}
    by_subject = {v: {} for v in variants}
    prob_store = {}

    for sid, X, y, mean, std in subjects:
        y = np.asarray(y).reshape(-1)
        probs = predict_night_probs(
            infer_model, X, mean, std, context_len, n_classes,
            batch_size=batch_size, overlap_stride=overlap_stride,
        )
        n = min(len(y), len(probs))
        if n == 0:
            print(f"  건너뜀(에포크 없음): {sid}")
            continue
        probs, y_true = probs[:n], y[:n]

        preds = {"raw": np.argmax(probs, axis=-1)}
        preds["mode"] = mode_filter(preds["raw"], window=mode_window)
        preds["moving_average"] = moving_average_filter(probs, window=ma_window)
        if transition_matrix is not None:
            preds["viterbi"] = viterbi_smooth(probs, transition_matrix)

        for v in variants:
            concat[v]["true"].append(y_true)
            concat[v]["pred"].append(preds[v])
            by_subject[v][sid] = (y_true, preds[v])

        if save_probs:
            prob_store[f"probs_{sid}"] = probs.astype(np.float32)
            prob_store[f"true_{sid}"] = y_true.astype(np.int32)

        print(f"  {sid}: {n} 에포크")

    results = {}
    for v in variants:
        if not concat[v]["true"]:
            print(f"평가할 subject가 없습니다: {label}")
            return results
        y_true = np.concatenate(concat[v]["true"])
        y_pred = np.concatenate(concat[v]["pred"])
        m = epoch_metrics(y_true, y_pred, n_classes, class_names)
        sm = per_subject_metrics(by_subject[v], n_classes)
        variant_label = label if v == "raw" else f"{label}_{v}_smoothed"
        save_metrics(out_dir, variant_label, m, sm, class_names)
        results[v] = m

    if save_probs and prob_store:
        np.savez_compressed(out_dir / f"{label}_subject_probs.npz", **prob_store)

    lines = [f"{'variant':<14}{'accuracy':>10}{'bal.acc':>10}{'kappa':>10}{'macroF1':>10}{'REM F1':>10}"]
    for v, m in results.items():
        lines.append(
            f"{v:<14}{m['accuracy']:>10.4f}{m['balanced_accuracy']:>10.4f}"
            f"{m['cohen_kappa']:>10.4f}{m['macro_f1']:>10.4f}{m['per_class_f1'].get('REM', 0.0):>10.4f}"
        )
    summary = "\n".join(lines)
    (out_dir / f"{label}_postprocess_comparison.txt").write_text(summary, encoding="utf-8")
    print(f"\n=== {label} 후처리 비교 ===\n{summary}")
    return results


# ============================================================
# 4. 여러 실행(fold/seed) 집계 + 진짜 앙상블 (옛 aggregate_folds.py)
# ============================================================
VARIANTS = ["accel", "accel_mode_smoothed", "accel_moving_average_smoothed", "accel_viterbi_smoothed"]
METRIC_KEYS = ["accuracy", "balanced_accuracy", "cohen_kappa", "macro_f1"]


def find_runs(pattern):
    runs = sorted(d for d in OUTPUT_DIR.glob(pattern) if d.is_dir())
    return [d for d in runs if any(d.glob("*_metrics.json"))]


def load_metrics(run_dir, variant):
    path = run_dir / f"{variant}_metrics.json"
    if not path.exists():
        return None
    return json.loads(path.read_text(encoding="utf-8"))


def fold_index_of(run_dir):
    cfg = run_dir / "config.json"
    if cfg.exists():
        try:
            return json.loads(cfg.read_text(encoding="utf-8")).get("fold_index")
        except json.JSONDecodeError:
            pass
    m = re.search(r"fold(\d+)of", run_dir.name)
    return int(m.group(1)) if m else None


def summarize(pattern):
    runs = find_runs(pattern)
    if not runs:
        print(f"'{pattern}'에 맞는 실행 디렉토리가 없습니다 (검색 위치: {OUTPUT_DIR})")
        return {}

    print(f"\n대상 실행 {len(runs)}개:")
    for r in runs:
        print(f"  - {r.name} (fold={fold_index_of(r)})")

    collected = {}
    for variant in VARIANTS:
        rows = [(r, load_metrics(r, variant)) for r in runs]
        rows = [(r, m) for r, m in rows if m]
        if not rows:
            continue
        collected[variant] = rows

        print(f"\n=== {variant} ===")
        header = f"{'run':<34}" + "".join(f"{k:>14}" for k in METRIC_KEYS) + f"{'REM F1':>10}"
        print(header)
        for r, m in rows:
            print(
                f"{r.name:<34}" + "".join(f"{m[k]:>14.4f}" for k in METRIC_KEYS)
                + f"{m['per_class_f1'].get('REM', 0.0):>10.4f}"
            )
        print("-" * len(header))
        for k in METRIC_KEYS + ["REM_f1"]:
            values = np.array(
                [m["per_class_f1"]["REM"] if k == "REM_f1" else m[k] for _, m in rows], dtype=np.float64
            )
            std = values.std(ddof=1) if len(values) > 1 else 0.0
            print(f"{k:<24}: {values.mean():.4f} ± {std:.4f}  (n={len(values)})")

    return collected


def compare(pattern_a, pattern_b):
    """두 설정을 fold 단위로 쌍대 비교하고 Wilcoxon signed-rank 검정을 수행합니다."""
    from scipy.stats import wilcoxon

    runs_a = {fold_index_of(r): r for r in find_runs(pattern_a)}
    runs_b = {fold_index_of(r): r for r in find_runs(pattern_b)}
    shared = sorted(f for f in runs_a if f is not None and f in runs_b)
    if not shared:
        print(f"비교할 공통 fold가 없습니다. A={sorted(runs_a)} B={sorted(runs_b)}")
        return

    for variant in VARIANTS:
        pairs = []
        for f in shared:
            ma, mb = load_metrics(runs_a[f], variant), load_metrics(runs_b[f], variant)
            if ma and mb:
                pairs.append((f, ma, mb))
        if not pairs:
            continue

        print(f"\n=== {variant}: A='{pattern_a}' vs B='{pattern_b}' (공통 fold {len(pairs)}개) ===")
        for key in METRIC_KEYS:
            a = np.array([ma[key] for _, ma, _ in pairs], dtype=np.float64)
            b = np.array([mb[key] for _, _, mb in pairs], dtype=np.float64)
            diff = b - a
            line = (
                f"  {key:<20} A {a.mean():.4f} → B {b.mean():.4f}  "
                f"(차이 {diff.mean():+.4f} ± {diff.std(ddof=1) if len(diff) > 1 else 0.0:.4f})"
            )
            n_pairs = int(np.sum(diff != 0))
            if len(diff) >= 3 and n_pairs > 0:
                try:
                    stat, pval = wilcoxon(a, b)
                    line += f"  Wilcoxon p={pval:.4f}"
                    floor = 2.0 / (2 ** n_pairs)
                    if pval <= floor + 1e-12:
                        line += (
                            f" (= n={n_pairs}에서 가능한 최솟값 {floor:.4f}, 모든 fold에서 같은 방향 — "
                            f"이 fold 수로 얻을 수 있는 가장 강한 증거"
                        )
                        if floor > 0.05:
                            line += f"; p<0.05를 원하면 fold {int(np.ceil(np.log2(2 / 0.05)))}개 이상 필요"
                        line += ")"
                    elif pval >= 0.05:
                        line += " (유의하지 않음 — 개선이라 말할 수 없음)"
                except ValueError as e:
                    line += f"  Wilcoxon 불가({e})"
            else:
                line += "  Wilcoxon 불가(fold가 3개 미만이거나 차이가 전부 0)"
            print(line)


def ensemble(pattern, out_tag="ensemble"):
    """같은 테스트 subject를 공유하는 실행들의 **확률을 평균**해 진짜 앙상블을 만듭니다
    (여러 독립 학습 run의 확률 평균 — 같은 모델의 raw/후처리 변형을 다수결하는 방식이 아닙니다)."""
    runs = [r for r in find_runs(pattern) if (r / "accel_subject_probs.npz").exists()]
    if len(runs) < 2:
        print(f"앙상블에는 accel_subject_probs.npz를 가진 실행이 2개 이상 필요합니다 (찾음: {len(runs)}개).")
        return

    stores = [np.load(r / "accel_subject_probs.npz") for r in runs]
    subject_ids = set(k[len("probs_"):] for k in stores[0].files if k.startswith("probs_"))
    for s in stores[1:]:
        subject_ids &= set(k[len("probs_"):] for k in s.files if k.startswith("probs_"))
    if not subject_ids:
        print("실행들이 공유하는 테스트 subject가 없습니다 — 같은 fold의 실행끼리만 앙상블할 수 있습니다.")
        return

    print(f"\n앙상블: 실행 {len(runs)}개 × subject {len(subject_ids)}명")
    for r in runs:
        print(f"  - {r.name}")

    by_subject, trues, preds = {}, [], []
    for sid in sorted(subject_ids):
        n = min(len(s[f"probs_{sid}"]) for s in stores)
        avg = np.mean([s[f"probs_{sid}"][:n] for s in stores], axis=0)
        y_true = stores[0][f"true_{sid}"][:n]
        y_pred = np.argmax(avg, axis=-1)
        by_subject[sid] = (y_true, y_pred)
        trues.append(y_true)
        preds.append(y_pred)

    y_true, y_pred = np.concatenate(trues), np.concatenate(preds)
    m = epoch_metrics(y_true, y_pred, N_CLASSES, CLASS_NAMES)
    sm = per_subject_metrics(by_subject, N_CLASSES)

    out_dir = OUTPUT_DIR / f"_{out_tag}_{len(runs)}runs"
    save_metrics(out_dir, "accel_prob_ensemble", m, sm, CLASS_NAMES)

    singles = [load_metrics(r, "accel") for r in runs]
    singles = [s for s in singles if s]
    if singles:
        print("\n개별 모델 대비:")
        for key in METRIC_KEYS:
            values = np.array([s[key] for s in singles], dtype=np.float64)
            print(f"  {key:<20} 개별 평균 {values.mean():.4f} (최고 {values.max():.4f}) → 앙상블 {m[key]:.4f}")


# ============================================================
# 5. 저장된 체크포인트 재평가 (옛 evaluate_checkpoint.py) — TF 필요
# ============================================================
def load_checkpoint(path: Path):
    import tensorflow as tf
    import model as model_module  # noqa: F401  (커스텀 레이어 등록을 위해 import 자체가 필요)

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
        return None


def check_architecture_compatibility(keras_model, expected_context_len, expected_classes):
    """체크포인트의 입력/출력 shape이 현재 파이프라인 기대값과 다르면 평가를 건너뛰도록 미리 확인합니다."""
    output_shape = tuple(keras_model.output_shape)
    problems = []
    if output_shape[-1] != expected_classes:
        problems.append(f"출력 클래스 수 {output_shape[-1]} != 현재 기대값 {expected_classes}")
    if len(output_shape) > 1 and output_shape[1] not in (None, expected_context_len):
        problems.append(f"컨텍스트 길이 {output_shape[1]} != 현재 기대값 {expected_context_len}")

    if problems:
        print("⚠️ 이 체크포인트는 현재 파이프라인과 아키텍처가 다릅니다:")
        for p in problems:
            print(f"  - {p}")
        return False
    return True


def resolve_split(run_dir, folds, fold_index):
    if run_dir is not None:
        cfg_path = Path(run_dir) / "config.json"
        if cfg_path.exists():
            cfg = json.loads(cfg_path.read_text(encoding="utf-8"))
            folds = cfg.get("folds", folds)
            fold_index = cfg.get("fold_index", fold_index)
            print(f"config.json에서 분할 복원: folds={folds}, fold_index={fold_index}")
        else:
            print(f"⚠️ config.json이 없습니다({cfg_path}) — 인자로 받은 folds/fold_index를 사용합니다.")
    return folds, fold_index


def evaluate_accel_checkpoint(model_path: Path, out_dir: Path, folds: int, fold_index: int, label: str):
    """train.py를 다시 돌리지 않고, 이미 저장된 accel_core 체크포인트를 그 실행의 테스트 분할로
    재평가합니다. train.py의 데이터 로딩/분할 함수를 재사용합니다."""
    import train

    if not train.ACCEL_NORM_STATS_PATH.exists():
        print(f"norm_stats 없음: {train.ACCEL_NORM_STATS_PATH} — preprocess.py stats를 먼저 실행하세요.")
        return

    accel_stats = np.load(train.ACCEL_NORM_STATS_PATH, allow_pickle=True).item()
    mean, std = accel_stats["mean"], accel_stats["std"]

    pairs = train.list_subject_pairs([train.BID_DIR, train.ACCEL_DIR])
    tr_p, _, te_p = train.split_subject_pairs(pairs, folds=folds, fold_index=fold_index)
    if not te_p:
        print("테스트셋 subject가 없습니다.")
        return

    train.load_subjects_lazy(train.BID_DIR, [sid for d, sid in pairs if d == train.BID_DIR])
    train.load_subjects_lazy(train.ACCEL_DIR, [sid for d, sid in pairs if d == train.ACCEL_DIR])

    m = load_checkpoint(model_path)
    if m is None:
        return
    if not check_architecture_compatibility(m, train.CONTEXT_LEN, N_CLASSES):
        return

    y_sequences = [train._MEM_CACHE[(str(d), sid)][1] for d, sid in tr_p if (str(d), sid) in train._MEM_CACHE]
    transition_matrix = train.compute_transition_matrix(y_sequences)

    wrapped = train._SingleInputEvalWrapper(m, train.N_TIME_BUCKETS)
    evaluate_subjects(
        wrapped, train.pairs_to_eval_subjects_accel(te_p, mean, std),
        train.CONTEXT_LEN, N_CLASSES, CLASS_NAMES, out_dir, label,
        transition_matrix=transition_matrix,
        batch_size=train.EVAL_BATCH_SIZE, overlap_stride=train.EVAL_OVERLAP_STRIDE,
    )


# ============================================================
# 6. CLI
# ============================================================
def main():
    p = argparse.ArgumentParser(description="평가/집계/체크포인트 재평가")
    sub = p.add_subparsers(dest="cmd", required=True)

    s1 = sub.add_parser("summarize", help="fold별 지표 + 평균 ± 표준편차")
    s1.add_argument("--pattern", default="*")

    s2 = sub.add_parser("compare", help="두 설정을 fold 단위로 쌍대 비교 + Wilcoxon")
    s2.add_argument("pattern_a")
    s2.add_argument("pattern_b")

    s3 = sub.add_parser("ensemble", help="확률 평균 앙상블")
    s3.add_argument("pattern")

    s4 = sub.add_parser("checkpoint", help="저장된 accel_core 체크포인트 재평가")
    s4.add_argument("--run-dir", default=None)
    s4.add_argument("--model", default=None)
    s4.add_argument("--folds", type=int, default=5)
    s4.add_argument("--fold-index", type=int, default=0)
    s4.add_argument("--label", default="accel_checkpoint")

    args = p.parse_args()
    if args.cmd == "summarize":
        summarize(args.pattern)
    elif args.cmd == "compare":
        compare(args.pattern_a, args.pattern_b)
    elif args.cmd == "ensemble":
        ensemble(args.pattern)
    elif args.cmd == "checkpoint":
        import train
        folds, fold_index = resolve_split(args.run_dir, args.folds, args.fold_index)
        if args.run_dir:
            run_dir = Path(args.run_dir)
            model_path = Path(args.model) if args.model else run_dir / "accel_inference_model.keras"
            out_dir = run_dir
        elif args.model:
            model_path = Path(args.model)
            out_dir = train.make_run_dir(f"reeval_fold{fold_index}of{folds}")
        else:
            p.error("--run-dir 또는 --model 중 하나는 필요합니다.")
            return
        evaluate_accel_checkpoint(model_path, out_dir, folds, fold_index, args.label)
        print(f"\n산출물: {out_dir}")


if __name__ == "__main__":
    main()
