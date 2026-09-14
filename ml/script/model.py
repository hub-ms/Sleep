import tensorflow as tf
import keras

# 혼합 정밀도 설정 (A100 최적화)
tf.keras.mixed_precision.set_global_policy("mixed_bfloat16")

@keras.saving.register_keras_serializable(package="model")
class PositionalEmbedding(tf.keras.layers.Layer):
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


def build_multiscale_cnn(inputs, base_filters=32):
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


def build_psg_epoch_encoder(window=1500, channels=8, patch_size=15, d_model=128, num_heads=4, num_layers=2, name="psg_epoch_encoder"):
    inp = tf.keras.layers.Input(shape=(window, channels))
    x = build_multiscale_cnn(inp)
    n_patches = x.shape[1] // patch_size
    x = tf.keras.layers.Reshape((n_patches, patch_size * x.shape[-1]))(x)
    x = tf.keras.layers.Dense(d_model)(x)
    x = PositionalEmbedding(n_patches, d_model)(x)

    for _ in range(num_layers):
        attn = tf.keras.layers.MultiHeadAttention(num_heads=num_heads, key_dim=d_model // num_heads, dropout=0.1)(x, x)
        x = tf.keras.layers.LayerNormalization()(x + attn)
        ff = tf.keras.layers.Dense(d_model * 2, activation="relu")(x)
        ff = tf.keras.layers.Dense(d_model)(ff)
        x = tf.keras.layers.LayerNormalization()(x + ff)
    x = tf.keras.layers.GlobalAveragePooling1D()(x)
    return tf.keras.Model(inp, x, name=name)


def build_accel_epoch_encoder(window=1500, channels=8, d_model=128, name="accel_epoch_encoder"):
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


def build_context_transformer_body(context_len=60, d_model=128, num_heads=8, num_layers=4, n_domains=2, name="context_transformer_body"):
    epoch_emb_in = tf.keras.layers.Input(shape=(context_len, d_model), name="epoch_embeddings")
    domain_in = tf.keras.layers.Input(shape=(), dtype="int32", name="domain_id")

    # FiLM Style 도메인 조건화
    gamma = tf.keras.layers.Embedding(n_domains, d_model, name="domain_gamma")(domain_in)
    beta = tf.keras.layers.Embedding(n_domains, d_model, name="domain_beta")(domain_in)
    gamma = tf.keras.layers.Reshape((1, d_model))(gamma)
    beta = tf.keras.layers.Reshape((1, d_model))(beta)

    x = epoch_emb_in * (1.0 + gamma) + beta
    x = PositionalEmbedding(context_len, d_model)(x)

    for _ in range(num_layers):
        attn = tf.keras.layers.MultiHeadAttention(num_heads=num_heads, key_dim=d_model // num_heads, dropout=0.1)(x, x)
        x = tf.keras.layers.LayerNormalization()(x + attn)
        ff = tf.keras.layers.Dense(d_model * 4, activation="relu")(x)
        ff = tf.keras.layers.Dense(d_model)(ff)
        x = tf.keras.layers.LayerNormalization()(x + ff)

    return tf.keras.Model(inputs=[epoch_emb_in, domain_in], outputs=x, name=name)


def build_dual_domain_model(psg_window=1500, psg_channels=8, accel_window=1500, accel_channels=8,
                            context_len=60, n_classes=4, d_model=128, psg_patch_size=15,
                            psg_num_heads=4, psg_num_layers=2, ctx_num_heads=8, ctx_num_layers=4):

    psg_enc = build_psg_epoch_encoder(psg_window, psg_channels, psg_patch_size, d_model, psg_num_heads, psg_num_layers)
    acc_enc = build_accel_epoch_encoder(accel_window, accel_channels, d_model)
    ctx_body = build_context_transformer_body(context_len, d_model, ctx_num_heads, ctx_num_layers)

    psg_in = tf.keras.layers.Input(shape=(context_len, psg_window, psg_channels), name="psg_input")
    acc_in = tf.keras.layers.Input(shape=(context_len, accel_window, accel_channels), name="accel_input")

    psg_emb = tf.keras.layers.TimeDistributed(psg_enc)(psg_in)
    acc_emb = tf.keras.layers.TimeDistributed(acc_enc)(acc_in)

    # 도메인 투영 (각 인코더 출력을 공유 공간으로 정렬)
    psg_emb = tf.keras.layers.Dense(d_model, activation="relu", name="psg_proj")(psg_emb)
    acc_emb = tf.keras.layers.Dense(d_model, activation="relu", name="acc_proj")(acc_emb)

    psg_dom = ConstantDomainId(0, name="psg_dom")(psg_in)
    acc_dom = ConstantDomainId(1, name="acc_dom")(acc_in)

    psg_features = ctx_body([psg_emb, psg_dom])
    acc_features = ctx_body([acc_emb, acc_dom])

    # 도메인별 분리된 출력 헤드
    psg_head = tf.keras.layers.Dense(n_classes, activation=None, name="psg_logits")
    acc_head = tf.keras.layers.Dense(n_classes, activation=None, name="accel_logits")

    # Mixed Precision 사용 시 최종 출력은 float32로 캐스팅 권장
    psg_out = tf.keras.layers.Activation("linear", dtype="float32", name="psg_output_act")(psg_head(psg_features))
    acc_out = tf.keras.layers.Activation("linear", dtype="float32", name="accel_output_act")(acc_head(acc_features))

    training_model = tf.keras.Model(
        inputs={"psg_input": psg_in, "accel_input": acc_in},
        outputs={"psg_output": psg_out, "accel_output": acc_out}
    )

    return {
        "training_model": training_model,
        "psg_infer_model": tf.keras.Model(psg_in, psg_out),
        "accel_infer_model": tf.keras.Model(acc_in, acc_out)
    }
