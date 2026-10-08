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
    elif args.action == "stats":
        preprocess_module.run_compute_stats()
    elif args.action == "fixture":
        preprocess_module.run_export_parity_fixture()


def cmd_train(args):
    # train.py는 모듈 레벨에서 tensorflow를 import하므로, 여기서 늦게(lazy) import해 preprocess/
    # evaluate 서브커맨드만 쓰는 사용자가 TensorFlow 없이도 main.py를 쓸 수 있게 합니다.
    import train as train_module

    train_module.LAMBDA_CRF = args.lambda_crf

    if not args.fold_loop:
        _dispatch_train_stage(train_module, args, args.fold_index)
        return

    # --fold-loop: 옛 run_folds.py가 서브프로세스로 하던 일을 같은 프로세스 안에서 반복합니다.
    print(f"\n{'=' * 70}\nfold 루프: 0..{args.folds - 1}\n{'=' * 70}")
    for k in range(args.folds):
        print(f"\n----- fold {k}/{args.folds} -----")
        _dispatch_train_stage(train_module, args, k)

    print(f"\n{'=' * 70}\n집계\n{'=' * 70}")
    evaluate_module.summarize(f"*{args.tag or args.stage}*of{args.folds}*")


def _dispatch_train_stage(train_module, args, fold_index):
    if args.stage == "accel_core":
        train_module.run_accel_core(args.folds, fold_index, args.tag, args.epochs, args.init_accel_encoder)
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
    pp_sub.add_parser("stats", help="정규화 통계 + Kotlin parity 검사")
    pp_sub.add_parser("fixture", help="Kotlin parity fixture 생성")
    pp.set_defaults(func=cmd_preprocess)

    tr = sub.add_parser("train", help="모델 학습")
    tr.add_argument("--stage", required=True,
                     choices=["accel_core", "mesa_selfsup", "audio_denoise_aux",
                              "accel_selfsup_wisdm", "fusion_finetune"])
    tr.add_argument("--folds", type=int, default=5)
    tr.add_argument("--fold-index", type=int, default=0)
    tr.add_argument("--fold-loop", action="store_true",
                     help="0..folds-1 전체를 순서대로 돌리고 끝에 집계(옛 run_folds.py 대체)")
    tr.add_argument("--tag", type=str, default=None)
    tr.add_argument("--epochs", type=int, default=40)
    tr.add_argument("--lambda-crf", type=float, default=1.0)
    tr.add_argument("--accel-checkpoint", default=None)
    tr.add_argument("--audio-denoise-checkpoint", default=None,
                     help="fusion_finetune용 audio_denoise_aux 사전학습 인코더 체크포인트(선택)")
    tr.add_argument("--init-accel-encoder", default=None,
                     help="accel_core 시작 전 accel_epoch_encoder를 초기화할 .keras 경로 "
                          "(accel_selfsup_wisdm이 저장한 사전학습 인코더)")
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
