"""멀티모달(마이크+가속도) 수면 단계 분류 모델 아키텍처.

⚠️ 이 파일은 TensorFlow가 필요합니다. 이 개발 환경에는 TensorFlow가 설치되어 있지 않아
(ml/COLAB.md가 문서화한 대로 학습은 원래 Colab에서 돌립니다) 이 세션에서는 실제로 import해
실행 검증을 하지 못했습니다. 기존에 실제로 동작하던 로직(PositionalEmbedding,
ConstantDomainId, build_accel_epoch_encoder, 도메인 FiLM 기본 구조)은 옛 model.py에서
그대로(라인 단위로) 포팅했고, 신규 부분(오디오 인코더, 시간대 FiLM, CRF, late fusion)은
test_model.py에서 CRF의 핵심 수학(순전파 알고리즘/Viterbi)만 numpy로 분리해 브루트포스와
대조 검증했습니다 — TF 그래프로서의 동작 자체는 Colab에서 처음 실행될 때 확인이 필요합니다.

## 아키텍처 개요

- **오디오 인코더** (`build_audio_epoch_encoder`, 신규): 한 epoch(30초)의 멜스펙토그램을
  2D-CNN으로 인코딩합니다. ⚠️ PSG-Audio(오디오+실제 수면단계 라벨이 동기화된 유일한 소스)를
  데이터셋에서 제외했으므로, 이 인코더는 **지도학습되는 core 스테이지가 없습니다** —
  APSAA/AI-Hub로 비지도 디노이징 사전학습(`train.py`의 `audio_denoise_aux`)만 거치고,
  실제 수면단계 라벨은 `fusion_finetune`의 소량 동기화 샘플에서 처음이자 유일하게 봅니다.
- **가속도 인코더** (`build_accel_epoch_encoder`, 기존 그대로): 1D-CNN. 기존 3개 accel
  소스(Sleep-Accel, BIDSleep)로 단독 학습합니다(코어 학습) — MESA는 자기지도 사전학습
  보조용으로만 쓰고(train.py의 mesa_selfsup 스테이지), WISDM은 accel_epoch_encoder 자체의
  자기지도 사전학습용(accel_selfsup_wisdm)으로만 쓰며, 이 인코더의 지도학습 자체는 기존
  accel 소스만 씁니다(사용자 확정 사항).
- **컨텍스트 트랜스포머 바디** (`build_context_transformer_body`, 확장): 두 인코더가 공유하는
  야간 시퀀스 트랜스포머. 기존 도메인 FiLM(PSG/Accel 조건화를 Audio/Accel 조건화로 의미만
  바뀜)에 **시간대 FiLM**(밤 안에서 지금이 언제인지)을 추가했습니다.
- **TransitionCRF** (신규): postprocess.py의 고정 Laplace 전이행렬을 "학습 중 최적화되는
  파라미터"로 승격합니다. 초기값은 그 경험적 전이행렬이고, focal loss에 CRF
  음의 로그가능도를 더해 함께 학습합니다. 추론 시 Viterbi 디코딩도 이 레이어의 학습된
  행렬로 합니다.
- **Late Fusion 헤드** (신규): 오디오+가속도 임베딩을 모두 가진 경우(온디바이스 추론 시점,
  폰은 마이크+가속도를 동시에 가짐)에만 쓰는 결합 분류 헤드. 오디오 인코더는 실제 수면단계
  라벨을 한 번도 본 적이 없고(위 설명 참고) 가속도 인코더는 accel_core로 이미 학습되어
  있으므로, 이 헤드만 별도로(소량의 동기화된 샘플로) 미세조정합니다(train.py의
  fusion_finetune 스테이지) — 오디오 인코더가 수면단계 신호를 처음 보는 지점이기도 합니다.
"""
import numpy as np
import tensorflow as tf
import keras

tf.keras.mixed_precision.set_global_policy("mixed_bfloat16")


# ============================================================
# 1. 공용 레이어 (기존 — 그대로 포팅)
# ============================================================
@keras.saving.register_keras_serializable(package="model")
class PositionalEmbedding(tf.keras.layers.Layer):
    """학습 가능한 덧셈식 위치 임베딩. 모든 트랜스포머 블록(에포크 인코더, 컨텍스트 바디) 앞에 둡니다."""

    def __init__(self, seq_len, d_model, **kwargs):
        super().__init__(**kwargs)
        self.seq_len = seq_len
        self.d_model = d_model
        self.pos_emb = self.add_weight(
            name="pos_emb",
            shape=(1, seq_len, d_model),
            initializer="random_normal",
            trainable=True,
        )

    def call(self, x):
        return x + self.pos_emb

    def get_config(self):
        config = super().get_config()
        config.update({"seq_len": self.seq_len, "d_model": self.d_model})
        return config


@keras.saving.register_keras_serializable(package="model")
class ConstantDomainId(tf.keras.layers.Layer):
    """입력 배치 크기에 맞춰 고정된 domain_id(정수)를 채운 텐서를 만듭니다. FiLM 조건화 입력용."""

    def __init__(self, domain_id, **kwargs):
        super().__init__(**kwargs)
        self.domain_id = int(domain_id)

    def call(self, x):
        batch = tf.shape(x)[0]
        return tf.fill((batch,), tf.constant(self.domain_id, dtype=tf.int32))

    def get_config(self):
        config = super().get_config()
        config.update({"domain_id": self.domain_id})
        return config


@keras.saving.register_keras_serializable(package="model")
class TransitionCRF(tf.keras.layers.Layer):
    """클래스 간 전이 확률을 학습 가능한 파라미터로 표현하는 선형체인 CRF.

    postprocess.py의 viterbi_smooth()가 "고정된 Laplace 전이행렬"로 사후처리를 하던 것을,
    "학습 중 실제로 최적화되는 전이행렬"로 승격한 것입니다. init_transition_matrix로 넘긴
    경험적 전이행렬(train.compute_transition_matrix의 결과)을 초기값으로 쓰고, 학습 중
    forward-algorithm 기반 음의 로그가능도(neg_log_likelihood)를 focal loss에 더해 함께
    최적화합니다. 추론 시에는 같은 학습된 행렬로 Viterbi 디코딩합니다.

    call()은 입력을 그대로 통과시키기만 합니다(변환 없음) — Keras의 call()은 한 텐서를
    받아 한 텐서로 변환하는 용도라, 라벨/마스크가 함께 필요한 시퀀스 손실·디코딩에는 맞지
    않습니다. 실제 CRF 계산은 neg_log_likelihood()/viterbi_decode()를 명시적으로 호출합니다.
    """

    def __init__(self, n_classes, init_transition_matrix=None, **kwargs):
        super().__init__(**kwargs)
        self.n_classes = n_classes
        if init_transition_matrix is not None:
            self._init_matrix = np.asarray(init_transition_matrix, dtype=np.float32)
        else:
            self._init_matrix = np.full((n_classes, n_classes), 1.0 / n_classes, dtype=np.float32)

    def build(self, input_shape):
        # 로그 확률로 저장합니다 — forward 알고리즘/Viterbi 모두 로그공간에서 동작하므로
        # 매번 log()를 다시 계산하지 않고 바로 쓸 수 있고, 학습 중 음수 값도 자연스럽습니다.
        init_log = np.log(np.clip(self._init_matrix, 1e-6, 1.0)).astype("float32")
        self.log_transitions = self.add_weight(
            name="log_transitions", shape=(self.n_classes, self.n_classes),
            initializer=tf.keras.initializers.Constant(init_log), trainable=True,
        )
        super().build(input_shape)

    def call(self, logits):
        return logits

    def neg_log_likelihood(self, logits, labels):
        """focal loss에 더할 CRF 음의 로그가능도. logits: (B,T,K), labels: (B,T) int. 반환 (B,)."""
        return crf_neg_log_likelihood(logits, labels, self.log_transitions)

    def viterbi_decode(self, logits):
        """학습된 전이행렬로 가장 가능성 높은 라벨 시퀀스를 디코딩합니다. logits: (B,T,K) -> (B,T)."""
        return crf_viterbi_decode(logits, self.log_transitions)

    def get_config(self):
        config = super().get_config()
        config.update({"n_classes": self.n_classes, "init_transition_matrix": self._init_matrix.tolist()})
        return config


def crf_neg_log_likelihood(logits, labels, log_transitions):
    """선형체인 CRF의 음의 로그가능도. logits: (B,T,K), labels: (B,T) int32, log_transitions: (K,K).

    초기 분포는 균등(uniform)으로 가정합니다 — postprocess.viterbi_smooth의 기본값과 같은
    전제라, 사후처리(Viterbi)와 이 학습 손실이 같은 가정을 공유합니다.

    ⚠️ 가변 길이/패딩 마스킹은 아직 지원하지 않습니다. 현재 학습 경로(accel_core/fusion_finetune)는
    녹화가 context_len보다 길어 고정 길이 윈도우를 뽑으므로 패딩이 필요 없습니다.
    feature_dataset 스타일의 패딩 윈도우에 CRF를 적용하려면 마스크 지원이 먼저 필요합니다.
    """
    logits = tf.cast(logits, tf.float32)
    labels = tf.cast(labels, tf.int32)
    t_len = logits.shape[1]
    n_classes = logits.shape[2]

    # 정답 시퀀스의 점수 = 방출(emission) 점수 + 전이(transition) 점수
    one_hot_labels = tf.one_hot(labels, n_classes)
    emission_score = tf.reduce_sum(logits * one_hot_labels, axis=[1, 2])  # (B,)

    label_pairs = labels[:, :-1] * n_classes + labels[:, 1:]  # (B, T-1), 평탄화된 (i,j) 인덱스
    flat_transitions = tf.reshape(log_transitions, [-1])  # (K*K,)
    pair_scores = tf.gather(flat_transitions, label_pairs)  # (B, T-1)
    transition_score = tf.reduce_sum(pair_scores, axis=1)  # (B,)
    gold_score = emission_score + transition_score

    # 분배함수(partition function) logZ: forward 알고리즘(로그공간, logsumexp). T는 정적
    # (context_len 고정)이므로 Python for-loop로 펼쳐도 그래프가 한 번만 빌드됩니다.
    alpha = logits[:, 0, :]  # (B, K)
    for t in range(1, t_len):
        broadcast = alpha[:, :, None] + log_transitions[None, :, :]  # (B, K_prev, K_curr)
        alpha = logits[:, t, :] + tf.reduce_logsumexp(broadcast, axis=1)
    log_z = tf.reduce_logsumexp(alpha, axis=-1)  # (B,)

    return log_z - gold_score


def crf_viterbi_decode(logits, log_transitions):
    """학습된 전이행렬로 Viterbi 디코딩합니다. logits: (B,T,K) -> (B,T) int32 라벨.

    postprocess.viterbi_smooth()와 같은 알고리즘(로그공간 delta/psi DP)을 배치 단위로
    TF 연산으로 옮긴 것입니다. 그 함수는 "학습이 끝난 뒤 numpy 사후처리"에 쓰이고, 이 함수는
    "모델 안에 들어있는 학습된 전이행렬"로 같은 디코딩을 하기 위한 것입니다.
    """
    logits = tf.cast(logits, tf.float32)
    t_len = logits.shape[1]

    delta = logits[:, 0, :]  # (B, K)
    psi = []
    for t in range(1, t_len):
        broadcast = delta[:, :, None] + log_transitions[None, :, :]  # (B, K_prev, K_curr)
        best_prev = tf.argmax(broadcast, axis=1)  # (B, K_curr)
        best_val = tf.reduce_max(broadcast, axis=1)  # (B, K_curr)
        delta = logits[:, t, :] + best_val
        psi.append(best_prev)

    last = tf.argmax(delta, axis=-1)  # (B,)
    path = [last]
    for t in range(t_len - 2, -1, -1):
        last = tf.gather(psi[t], last, batch_dims=1)
        path.append(last)
    path.reverse()
    return tf.cast(tf.stack(path, axis=1), tf.int32)  # (B, T)


# ============================================================
# 2. 에포크 인코더
# ============================================================
def build_multiscale_cnn(inputs, base_filters=32):
    """커널 크기가 다른 4개 Conv1D 가지를 합치는 멀티스케일 1D CNN(기존 그대로)."""
    branches = []
    for k in (3, 7, 15, 31):
        b = tf.keras.layers.Conv1D(base_filters, k, strides=2, padding="same")(inputs)
        b = tf.keras.layers.BatchNormalization()(b)
        b = tf.keras.layers.Activation("relu")(b)
        branches.append(b)
    x = tf.keras.layers.Concatenate()(branches)
    x = tf.keras.layers.Conv1D(128, 5, strides=2, padding="same")(x)
    x = tf.keras.layers.BatchNormalization()(x)
    x = tf.keras.layers.Activation("relu")(x)
    return x


def build_accel_epoch_encoder(window=1500, channels=7, d_model=128, name="accel_epoch_encoder"):
    """한 epoch(30초, WINDOW개 샘플)의 가속도 원시 파형을 d_model 벡터로 인코딩합니다(기존 그대로).
    3개의 plain Conv1D + BatchNorm + ReLU 뒤 GlobalAveragePooling으로 시간축을 압축합니다."""
    inp = tf.keras.layers.Input(shape=(window, channels))
    x = tf.keras.layers.Conv1D(32, 7, strides=2, padding="same")(inp)
    x = tf.keras.layers.BatchNormalization()(x)
    x = tf.keras.layers.Activation("relu")(x)
    for filters in (64, 128):
        x = tf.keras.layers.Conv1D(filters, 3, strides=2, padding="same")(x)
        x = tf.keras.layers.BatchNormalization()(x)
        x = tf.keras.layers.Activation("relu")(x)
    x = tf.keras.layers.GlobalAveragePooling1D()(x)
    x = tf.keras.layers.Dense(d_model, activation="relu")(x)
    return tf.keras.Model(inp, x, name=name)


def build_mesa_feature_encoder(n_features, d_model=128, name="mesa_feature_encoder"):
    """한 epoch(30초)의 MESA 액티그래피 요약 특징(activity/광량 등 스칼라 n_features개)을
    d_model 벡터로 인코딩합니다(신규, mesa_selfsup 보조 과제 전용).

    accel_epoch_encoder/audio_epoch_encoder는 "에포크 내 원시 시계열/스펙트로그램"을 Conv로
    압축하지만, MESA는 Actiwatch Spectrum 기기가 이미 30초 단위로 집계한 스칼라 값만 주므로
    압축할 시간축 자체가 없습니다. 그래서 Conv 대신 작은 MLP(Dense 2단)만 씁니다 — 입력 차원이
    accel_epoch_encoder(1500x7 원시 파형)와 전혀 달라 두 인코더는 가중치를 공유할 수 없습니다."""
    inp = tf.keras.layers.Input(shape=(n_features,))
    x = tf.keras.layers.Dense(32, activation="relu")(inp)
    x = tf.keras.layers.Dense(d_model, activation="relu")(x)
    return tf.keras.Model(inp, x, name=name)


def build_audio_epoch_encoder(n_mel_frames, n_mels, d_model=128, name="audio_epoch_encoder"):
    """한 epoch(30초)의 멜스펙토그램(n_mel_frames, n_mels, 1)을 d_model 벡터로 인코딩합니다(신규).

    accel의 build_accel_epoch_encoder와 같은 "에포크 하나 -> 벡터" 역할이지만, 입력이 1D
    파형이 아니라 2D(시간 x 멜빈) 이미지형 데이터라 Conv2D를 씁니다. 커널/스트라이드는
    build_accel_epoch_encoder의 다운샘플링 깊이(3단계)에 맞췄습니다 — 실측 비교(Colab) 전까지는
    근거가 추정에 가까우므로, 연산 비용이 실제로 accel 인코더와 비슷한지 Phase 3에서 측정이
    필요합니다(계획 문서의 리스크 레지스터 참고).
    """
    inp = tf.keras.layers.Input(shape=(n_mel_frames, n_mels, 1))
    x = tf.keras.layers.Conv2D(16, (5, 5), strides=(2, 2), padding="same")(inp)
    x = tf.keras.layers.BatchNormalization()(x)
    x = tf.keras.layers.Activation("relu")(x)
    for filters in (32, 64):
        x = tf.keras.layers.Conv2D(filters, (3, 3), strides=(2, 2), padding="same")(x)
        x = tf.keras.layers.BatchNormalization()(x)
        x = tf.keras.layers.Activation("relu")(x)
    x = tf.keras.layers.GlobalAveragePooling2D()(x)
    x = tf.keras.layers.Dense(d_model, activation="relu")(x)
    return tf.keras.Model(inp, x, name=name)


# ============================================================
# 3. 컨텍스트 트랜스포머 바디 (기존 + 시간대 FiLM 확장)
# ============================================================
def build_context_transformer_body(
    context_len=60, d_model=128, num_heads=8, num_layers=4,
    n_domains=2, n_time_buckets=8, name="context_transformer_body",
):
    """오디오/가속도 에포크 인코더가 공유하는 야간 시퀀스 트랜스포머.

    도메인 FiLM(기존, 지금은 0=오디오/1=가속도 의미로 재사용)에 **시간대 FiLM**(신규)을
    추가했습니다 — 사용자가 확정한 "시간대별 가중치 결합"을, 기존에 이미 검증된 FiLM
    메커니즘을 그대로 재사용하는 방식(Option 2)으로 구현한 것입니다. 손실 재가중(Option 1)은
    쓰지 않습니다.
    """
    epoch_emb_in = tf.keras.layers.Input(shape=(context_len, d_model), name="epoch_embeddings")
    domain_in = tf.keras.layers.Input(shape=(), dtype="int32", name="domain_id")
    time_bucket_in = tf.keras.layers.Input(shape=(), dtype="int32", name="time_bucket_id")

    domain_gamma = tf.keras.layers.Embedding(n_domains, d_model, name="domain_gamma")(domain_in)
    domain_beta = tf.keras.layers.Embedding(n_domains, d_model, name="domain_beta")(domain_in)
    domain_gamma = tf.keras.layers.Reshape((1, d_model))(domain_gamma)
    domain_beta = tf.keras.layers.Reshape((1, d_model))(domain_beta)

    time_gamma = tf.keras.layers.Embedding(n_time_buckets, d_model, name="time_gamma")(time_bucket_in)
    time_beta = tf.keras.layers.Embedding(n_time_buckets, d_model, name="time_beta")(time_bucket_in)
    time_gamma = tf.keras.layers.Reshape((1, d_model))(time_gamma)
    time_beta = tf.keras.layers.Reshape((1, d_model))(time_beta)

    # 두 FiLM을 더해서 함께 적용합니다(domain과 time_bucket은 서로 독립적인 조건이므로 합산).
    x = epoch_emb_in * (1.0 + domain_gamma + time_gamma) + (domain_beta + time_beta)
    x = PositionalEmbedding(context_len, d_model)(x)

    for _ in range(num_layers):
        attn = tf.keras.layers.MultiHeadAttention(
            num_heads=num_heads, key_dim=d_model // num_heads, dropout=0.1
        )(x, x)
        x = tf.keras.layers.LayerNormalization()(x + attn)
        ff = tf.keras.layers.Dense(d_model * 4, activation="relu")(x)
        ff = tf.keras.layers.Dense(d_model)(ff)
        x = tf.keras.layers.LayerNormalization()(x + ff)

    return tf.keras.Model(
        inputs=[epoch_emb_in, domain_in, time_bucket_in], outputs=x, name=name
    )


# ============================================================
# 4. 전체 모델 조립
# ============================================================
def build_multimodal_model(
    audio_n_mel_frames, audio_n_mels,
    accel_window=1500, accel_channels=7,
    context_len=60, n_classes=4, d_model=128,
    n_time_buckets=8,
    ctx_num_heads=8, ctx_num_layers=4,
    init_transition_matrix=None,
):
    """오디오 인코더 + 가속도 인코더 + 공유 컨텍스트 바디 + CRF + late fusion 헤드를 조립합니다.

    오디오/가속도 인코더는 **서로 다른 방식으로 학습**됩니다 — 가속도=기존 3개 소스로
    지도학습(accel_core), 오디오=APSAA/AI-Hub로 비지도 사전학습만(audio_denoise_aux, 라벨
    없음). 오디오+실제 수면단계 라벨이 동시에 있는 데이터가 전혀 없기 때문입니다(PSG-Audio를
    데이터셋에서 제외한 결과 — 사용자 확정 사항). late fusion 헤드만 "동기화가 보장된 소량
    샘플"로 별도 미세조정합니다(train.py의 fusion_finetune 스테이지) — 오디오 인코더가
    수면단계 라벨을 처음이자 유일하게 보는 지점입니다.

    반환 dict:
      training_model        — 세 출력(audio/accel/fusion)을 모두 가진 학습용 모델(스테이지별로
                               필요한 출력의 손실만 계산하면 됨).
      audio_infer_model     — 오디오 단독 추론(지도학습 스테이지 없음 — 구조/변환 경로 검증용).
      accel_infer_model     — 가속도 단독 추론(가속도 core 학습/평가용, 온디바이스 배포용 —
                               단일 출력이라 convert.py/TFLite/Kotlin과 그대로 호환).
      accel_train_model     — accel_infer_model과 같은 레이어를 공유하지만 보조 과제(전이
                               탐지, accel_transition_output)가 추가로 붙은 2-출력 모델(개선
                               5). accel_core 학습은 이 모델로 fit하고, 배포/평가는 가중치를
                               공유하는 accel_infer_model을 씁니다.
      fusion_infer_model    — 오디오+가속도 동시 입력 -> 결합 추론(fusion_finetune 이후 배포용).
      context_body          — 공유 트랜스포머(2단계 학습에서 trainable 조작용).
      crf                   — TransitionCRF 레이어(학습 손실/추론 디코딩에서 재사용).
      accel_encoder          — accel_epoch_encoder 서브모델 자체(개선 6: WISDM 자기지도
                               사전학습 가중치를 accel_core 시작 전에 주입하는 용도 —
                               accel_infer_model/accel_train_model이 참조하는 것과 같은
                               레이어 객체라, 이걸 set_weights()하면 두 모델 모두에 반영됩니다).
      audio_encoder          — audio_epoch_encoder 서브모델 자체(같은 이유로 노출).
    """
    audio_enc = build_audio_epoch_encoder(audio_n_mel_frames, audio_n_mels, d_model)
    accel_enc = build_accel_epoch_encoder(accel_window, accel_channels, d_model)
    ctx_body = build_context_transformer_body(
        context_len, d_model, ctx_num_heads, ctx_num_layers, n_domains=2, n_time_buckets=n_time_buckets
    )
    crf = TransitionCRF(n_classes, init_transition_matrix, name="transition_crf")

    audio_in = tf.keras.layers.Input(
        shape=(context_len, audio_n_mel_frames, audio_n_mels, 1), name="audio_input"
    )
    accel_in = tf.keras.layers.Input(
        shape=(context_len, accel_window, accel_channels), name="accel_input"
    )
    time_bucket_in = tf.keras.layers.Input(shape=(), dtype="int32", name="time_bucket_id")

    audio_emb = tf.keras.layers.TimeDistributed(audio_enc)(audio_in)
    accel_emb = tf.keras.layers.TimeDistributed(accel_enc)(accel_in)

    audio_emb = tf.keras.layers.Dense(d_model, activation="relu", name="audio_proj")(audio_emb)
    accel_emb = tf.keras.layers.Dense(d_model, activation="relu", name="accel_proj")(accel_emb)

    audio_dom = ConstantDomainId(0, name="audio_dom")(audio_in)
    accel_dom = ConstantDomainId(1, name="accel_dom")(accel_in)

    audio_ctx = ctx_body([audio_emb, audio_dom, time_bucket_in])
    accel_ctx = ctx_body([accel_emb, accel_dom, time_bucket_in])

    audio_head = tf.keras.layers.Dense(n_classes, activation=None, name="audio_logits")
    accel_head = tf.keras.layers.Dense(n_classes, activation=None, name="accel_logits")

    audio_out = tf.keras.layers.Activation("linear", dtype="float32", name="audio_output_act")(
        audio_head(audio_ctx)
    )
    accel_out = tf.keras.layers.Activation("linear", dtype="float32", name="accel_output_act")(
        accel_head(accel_ctx)
    )
    # crf(logits)는 통과시키기만 하지만, 레이어를 그래프에 연결해 두어야 training_model이
    # CRF의 log_transitions를 자신의 학습 가능 변수 목록에 포함시킵니다.
    audio_out = crf(audio_out)
    accel_out = crf(accel_out)

    # Late Fusion: 두 컨텍스트 임베딩(분류 헤드 이전의 d_model 벡터)을 이어붙여 별도 헤드로 분류.
    fusion_concat = tf.keras.layers.Concatenate(name="fusion_concat")([audio_ctx, accel_ctx])
    fusion_hidden = tf.keras.layers.Dense(d_model, activation="relu", name="fusion_hidden")(fusion_concat)
    fusion_logits = tf.keras.layers.Dense(n_classes, activation=None, name="fusion_logits")(fusion_hidden)
    fusion_out = tf.keras.layers.Activation("linear", dtype="float32", name="fusion_output_act")(
        fusion_logits
    )

    # 개선 5: accel_ctx(분류 헤드 이전의 컨텍스트 트랜스포머 출력)에서 "이 에포크가 직전
    # 에포크와 다른 수면단계로 막 바뀐 시점인가"를 예측하는 보조 이진 분류 헤드를 추가로
    # 답니다. 새 라벨/데이터 없이 기존 4-class 라벨에서 파생되는 신호(train._transition_labels)
    # 로 학습되고, REM/Light 같은 애매한 경계에 더 민감한 표현을 배우도록 유도하는 정규화
    # 역할입니다. 배포(accel_infer_model)에는 영향이 없도록 별도 모델(accel_train_model)에만
    # 이 출력을 포함시킵니다.
    accel_transition_logit = tf.keras.layers.Dense(1, activation=None, name="accel_transition_logit")(accel_ctx)
    accel_transition_out = tf.keras.layers.Activation(
        "sigmoid", dtype="float32", name="accel_transition_output_act"
    )(accel_transition_logit)

    training_model = tf.keras.Model(
        inputs={"audio_input": audio_in, "accel_input": accel_in, "time_bucket_id": time_bucket_in},
        outputs={"audio_output": audio_out, "accel_output": accel_out, "fusion_output": fusion_out},
    )

    accel_infer_model = tf.keras.Model([accel_in, time_bucket_in], accel_out)
    accel_train_model = tf.keras.Model(
        [accel_in, time_bucket_in],
        {"accel_output": accel_out, "accel_transition_output": accel_transition_out},
    )

    return {
        "training_model": training_model,
        "audio_infer_model": tf.keras.Model([audio_in, time_bucket_in], audio_out),
        "accel_infer_model": accel_infer_model,
        "accel_train_model": accel_train_model,
        "fusion_infer_model": tf.keras.Model([audio_in, accel_in, time_bucket_in], fusion_out),
        "context_body": ctx_body,
        "crf": crf,
        "accel_encoder": accel_enc,
        "audio_encoder": audio_enc,
    }
