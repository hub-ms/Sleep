import json

path = "colab_preprocessing.ipynb"
nb = json.load(open(path, encoding="utf-8"))


def md(text):
    return {"cell_type": "markdown", "metadata": {}, "source": text.splitlines(keepends=True)}


def code(text):
    return {"cell_type": "code", "metadata": {}, "source": text.splitlines(keepends=True), "outputs": [], "execution_count": None}


# Step 3(build_dataset_hybrid.py) 코드 셀 바로 다음에 라벨 검증 단계를 끼워넣습니다.
step3_code_idx = None
for i, c in enumerate(nb["cells"]):
    src = "".join(c.get("source", []))
    if c["cell_type"] == "code" and "build_dataset_hybrid.py" in src:
        step3_code_idx = i
        break
assert step3_code_idx is not None

validate_md = md(
"""## Step 3b. 라벨 범위 검증 — `validate_labels.py`
`SKIP_EXISTING=True`라서 이전에 다른 라벨 매핑으로 생성된 `.npy`가 섞여 있어도 조용히
넘어갑니다. 학습 도중(특히 Stage 2, Accel) `tf.gather`가 "indices[...] = 4 is not in [0, 4)"
같은 에러로 죽는 건 대부분 이 문제입니다 — 학습을 다시 돌리기 전에 미리 확인하세요."""
)

validate_code = code(
"""!python validate_labels.py"""
)

fix_md = md(
"""위에서 문제 파일이 나왔다면, 아래처럼 해당 `X_*.npy`/`y_*.npy` 쌍을 삭제한 뒤 Step 3을
다시 실행하세요(`SKIP_EXISTING=True`여도 삭제된 파일은 없으므로 새로 생성됩니다)."""
)

fix_code = code(
"""# 예시 — validate_labels.py 출력에서 나온 파일명으로 바꿔서 실행하세요.
# import os
# os.remove("/content/ml/data/sleep_accel/subjects/X_SA_문제파일ID.npy")
# os.remove("/content/ml/data/sleep_accel/subjects/y_SA_문제파일ID.npy")"""
)

insert_at = step3_code_idx + 1
nb["cells"][insert_at:insert_at] = [validate_md, validate_code, fix_md, fix_code]

with open(path, "w", encoding="utf-8") as f:
    json.dump(nb, f, ensure_ascii=False, indent=1)

print("inserted at", insert_at, "-> new cell count:", len(nb["cells"]))