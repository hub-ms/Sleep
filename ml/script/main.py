"""전체 파이프라인의 CLI 진입점 — preprocess → train → evaluate → convert.

옛 run_folds.py의 "여러 fold를 순서대로 돌리고 집계한다"는 오케스트레이션 역할을 **서브프로세스
대신 같은 프로세스 안에서** 수행합니다(계획 문서의 결정 — K-fold 교차검증 자체는 더 이상
기본값이 아니고, 필요할 때만 --folds로 선택적으로 돌리는 가벼운 루프가 됩니다).

사용 예:
    python main.py preprocess build-features
    python main.py preprocess stats
    python main.py train --stage accel_core --folds 5
    python main.py train --stage accel_core --folds 5 --fold-loop   # 5개 fold를 전부 순서대로
    python main.py evaluate summarize --pattern "*accelcore*of5"
    python main.py convert --variant accel_only
    python main.py pipeline                                         # 전체를 기본 설정으로 한 번에
"""
import argparse
import sys

import evaluate as evaluate_module
import preprocess as preprocess_module


def cmd_preprocess(args):
    if args.action == "build-features":
        preprocess_module.run_build_features(force=args.force)
    elif args.action == "bands":
        preprocess_module.run_build_bands(force=args.force)
    elif args.action == "stats":
        preprocess_module.run_compute_stats(domain=args.domain)
    elif args.action == "fixture":
        preprocess_module.run_export_parity_fixture()


def cmd_train(args):
    # train.py는 모듈 레벨에서 tensorflow를 import하므로, 여기서 늦게(lazy) import해 preprocess/
    # evaluate 서브커맨드만 쓰는 사용자가 TensorFlow 없이도 main.py를 쓸 수 있게 합니다.
    import train as train_module

    # 모듈 전역을 덮어쓴다. train.py 쪽은 이 값들을 **호출 시점에** 읽도록 고쳐져 있어야 한다
    # (예전엔 기본 인자로 def 시점에 묶여 --lambda-crf 가 조용히 무시됐다).
    train_module.LAMBDA_CRF = args.lambda_crf
    if args.focal_gamma is not None:
        train_module.FOCAL_GAMMA = args.focal_gamma
    if args.label_smoothing is not None:
        train_module.LABEL_SMOOTHING = args.label_smoothing
    if args.transition_prior_weight is not None:
        train_module.TRANSITION_PRIOR_WEIGHT = args.transition_prior_weight
    if args.no_class_weights:
        train_module.USE_CLASS_WEIGHTS = False
    if args.channel_set is not None:
        train_module.CHANNEL_SET = args.channel_set

    if not args.fold_loop:
        _dispatch_train_stage(train_module, args, args.fold_index)
        return

    # --fold-loop: 옛 run_folds.py가 서브프로세스로 하던 일을 같은 프로세스 안에서 반복합니다.
    print(f"\n{'=' * 70}\nfold 루프: 0..{args.folds - 1}\n{'=' * 70}")
    for k in range(args.folds):
        print(f"\n----- fold {k}/{args.folds} -----")
        _dispatch_train_stage(train_module, args, k)

    print(f"\n{'=' * 70}\n집계\n{'=' * 70}")
    evaluate_module.summarize(_fold_summary_pattern(args))


def _fold_summary_pattern(args):
    """run_dir 이름은 `{stamp}_{tag}` 이고, tag 기본값은 밑줄 없는 `accelcore_fold{k}of{n}` 이다
    (train.py run_accel_core). 예전 코드는 stage 이름(`accel_core`)을 그대로 넣어 `*accel_core*of5*`
    로 찾았기 때문에 어떤 디렉토리와도 매칭되지 않았다 — 집계가 항상 빈손으로 끝났다."""
    base = args.tag or args.stage.replace("_", "")
    return f"*{base}*of{args.folds}*"


def _dispatch_train_stage(train_module, args, fold_index):
    if args.stage == "accel_core":
        train_module.run_accel_core(args.folds, fold_index, args.tag, args.epochs,
                                    args.init_accel_encoder, args.init_context_body)
    elif args.stage == "psg_context_pretrain":
        train_module.run_psg_context_pretrain(args.folds, fold_index, args.tag, args.epochs)
    elif args.stage == "mesa_selfsup":
        train_module.run_mesa_selfsup(args.epochs)
    elif args.stage == "audio_denoise_aux":
        train_module.run_audio_denoise_aux(args.epochs)
    elif args.stage == "accel_selfsup_wisdm":
        train_module.run_accel_selfsup_wisdm(args.epochs)
    elif args.stage == "fusion_finetune":
        if not args.accel_checkpoint:
            print("fusion_finetune에는 --accel-checkpoint가 필요합니다.")
            return
        train_module.run_fusion_finetune(args.accel_checkpoint, args.audio_denoise_checkpoint, args.epochs)


def cmd_evaluate(args):
    if args.action == "summarize":
        evaluate_module.summarize(args.pattern)
    elif args.action == "compare":
        evaluate_module.compare(args.pattern_a, args.pattern_b)
    elif args.action == "ensemble":
        evaluate_module.ensemble(args.pattern)
    elif args.action == "checkpoint":
        import train as train_module
        from pathlib import Path

        folds, fold_index = evaluate_module.resolve_split(args.run_dir, args.folds, args.fold_index)
        if args.run_dir:
            run_dir = Path(args.run_dir)
            model_path = Path(args.model) if args.model else run_dir / "accel_inference_model.keras"
            out_dir = run_dir
        elif args.model:
            model_path = Path(args.model)
            out_dir = train_module.make_run_dir(f"reeval_fold{fold_index}of{folds}")
        else:
            print("--run-dir 또는 --model 중 하나는 필요합니다.")
            return
        evaluate_module.evaluate_accel_checkpoint(model_path, out_dir, folds, fold_index, args.label)
        print(f"\n산출물: {out_dir}")


def cmd_convert(args):
    import convert as convert_module
    from pathlib import Path

    if args.variant == "accel_only":
        convert_module.convert_accel_only(args.model, Path(args.out) if args.out else convert_module.TFLITE_PATH)
    else:
        convert_module.convert_fusion(args.model, Path(args.out) if args.out else None)


def cmd_pipeline(args):
    """preprocess(build-features, stats) → train(accel_core, 단일 분할) → evaluate(summarize)
    → convert(accel_only) 를 기본 설정으로 순서대로 실행합니다. 각 단계는 필요한 데이터가 없으면
    (이 저장소의 현재 상태) 명확한 메시지를 내고 다음 단계로 넘어가지 않고 멈춥니다 — 중간 산출물
    없이 다음 단계를 실행하면 의미 없는 실패만 반복되기 때문입니다.
    """
    print(f"\n{'=' * 70}\n[1/4] preprocess: build-features\n{'=' * 70}")
    preprocess_module.run_build_features(force=False)

    print(f"\n{'=' * 70}\n[2/4] preprocess: stats\n{'=' * 70}")
    preprocess_module.run_compute_stats()

    print(f"\n{'=' * 70}\n[3/4] train: accel_core (단일 분할)\n{'=' * 70}")
    import train as train_module
    run_dir = train_module.run_accel_core(folds=1, fold_index=0, tag=args.tag, epochs=args.epochs)
    if run_dir is None:
        print("\naccel_core 학습이 데이터 부족으로 실행되지 않았습니다 — 파이프라인을 중단합니다.")
        return

    print(f"\n{'=' * 70}\n[4/4] convert: accel_only\n{'=' * 70}")
    import convert as convert_module
    convert_module.convert_accel_only(run_dir / "accel_inference_model.keras")

    print(f"\n파이프라인 완료. 산출물: {run_dir}")


def build_parser():
    p = argparse.ArgumentParser(description="수면 단계 분류 ML 파이프라인")
    sub = p.add_subparsers(dest="command", required=True)

    pp = sub.add_parser("preprocess", help="전처리 유틸리티")
    pp_sub = pp.add_subparsers(dest="action", required=True)
    bf = pp_sub.add_parser("build-features", help="X_*.npy -> F_*.npy")
    bf.add_argument("--force", action="store_true")
    bd = pp_sub.add_parser("bands", help="X_*.npy -> B_*.npy (epoch별 대역 파워, --channel-set full 용)")
    bd.add_argument("--force", action="store_true", help="이미 있는 B_*.npy 도 다시 계산")
    st = pp_sub.add_parser("stats", help="정규화 통계 + Kotlin parity 검사")
    st.add_argument("--domain", choices=["accel", "edf", "audio", "all"], default="accel",
                    help="어느 도메인의 통계를 계산할지. 기본 accel — 앱이 쓰는 유일한 도메인이고, "
                         "edf는 27GB를 전부 읽으므로 필요할 때만 따로 돌린다.")
    pp_sub.add_parser("fixture", help="Kotlin parity fixture 생성")
    pp.set_defaults(func=cmd_preprocess)

    tr = sub.add_parser("train", help="모델 학습")
    tr.add_argument("--stage", required=True,
                     choices=["accel_core", "psg_context_pretrain", "mesa_selfsup",
                              "audio_denoise_aux", "accel_selfsup_wisdm", "fusion_finetune"])
    tr.add_argument("--folds", type=int, default=5)
    tr.add_argument("--fold-index", type=int, default=0)
    tr.add_argument("--fold-loop", action="store_true",
                     help="0..folds-1 전체를 순서대로 돌리고 끝에 집계(옛 run_folds.py 대체)")
    tr.add_argument("--tag", type=str, default=None)
    tr.add_argument("--epochs", type=int, default=40)
    tr.add_argument("--lambda-crf", type=float, default=1.0)
    tr.add_argument("--focal-gamma", type=float, default=None,
                     help="focal loss의 gamma(기본 1.0). 소수 클래스(Wake/Deep)에 더 집중시키려면 1.5~2.0")
    tr.add_argument("--label-smoothing", type=float, default=None,
                     help="라벨 스무딩(기본 0.1). 0.1은 확신 상한을 0.925로 묶는다 — 0.05와 비교해 볼 것")
    tr.add_argument("--transition-prior-weight", type=float, default=None,
                     help="CRF 전이행렬 초기값에서 생리학적 prior를 몇 개의 가상 관측치로 셀지"
                          "(기본 10 — 실측 19만 전이 앞에서는 사실상 무영향. 수천 단위를 권장)")
    tr.add_argument("--no-class-weights", action="store_true",
                     help="클래스 가중치를 손실에서 뺀다(ablation용). 기본은 적용")
    tr.add_argument("--channel-set", choices=["base", "circadian", "full"], default=None,
                     help="모델 입력 채널 구성. base=기존 7채널(기본), "
                          "circadian=+수면주기 sin/cos(9채널), "
                          "full=+대역파워 3채널(12채널, `preprocess bands` 선행 필요). "
                          "base 가 아닌 모델을 앱에 넣으려면 Kotlin AccelChannels 도 같이 바꿀 것")
    tr.add_argument("--accel-checkpoint", default=None)
    tr.add_argument("--audio-denoise-checkpoint", default=None,
                     help="fusion_finetune용 audio_denoise_aux 사전학습 인코더 체크포인트(선택)")
    tr.add_argument("--init-accel-encoder", default=None,
                     help="accel_core 시작 전 accel_epoch_encoder를 초기화할 .keras 경로 "
                          "(accel_selfsup_wisdm이 저장한 사전학습 인코더)")
    tr.add_argument("--init-context-body", default=None,
                     help="accel_core 시작 전 컨텍스트 트랜스포머를 초기화할 .keras 경로 "
                          "(psg_context_pretrain이 저장한 context_body_psg_pretrained.keras)")
    tr.set_defaults(func=cmd_train)

    ev = sub.add_parser("evaluate", help="평가/집계")
    ev_sub = ev.add_subparsers(dest="action", required=True)
    s1 = ev_sub.add_parser("summarize")
    s1.add_argument("--pattern", default="*")
    s2 = ev_sub.add_parser("compare")
    s2.add_argument("pattern_a")
    s2.add_argument("pattern_b")
    s3 = ev_sub.add_parser("ensemble")
    s3.add_argument("pattern")
    s4 = ev_sub.add_parser("checkpoint")
    s4.add_argument("--run-dir", default=None)
    s4.add_argument("--model", default=None)
    s4.add_argument("--folds", type=int, default=5)
    s4.add_argument("--fold-index", type=int, default=0)
    s4.add_argument("--label", default="accel_checkpoint")
    ev.set_defaults(func=cmd_evaluate)

    cv = sub.add_parser("convert", help="TFLite 변환")
    cv.add_argument("--variant", choices=["accel_only", "fusion"], default="accel_only")
    cv.add_argument("--model", default=None)
    cv.add_argument("--out", default=None)
    cv.set_defaults(func=cmd_convert)

    pl = sub.add_parser("pipeline", help="preprocess -> train(accel_core) -> convert 를 기본 설정으로 한 번에")
    pl.add_argument("--tag", default=None)
    pl.add_argument("--epochs", type=int, default=40)
    pl.set_defaults(func=cmd_pipeline)

    return p


def main():
    parser = build_parser()
    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    sys.exit(main() or 0)
