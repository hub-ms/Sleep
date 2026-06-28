import tensorflow as tf

def build_cnn_transformer(channels=8):
    # 인풋 셰이프: (Batch, 1500, 8)
    inputs = tf.keras.layers.Input(shape=(1500, channels))

    # 1D CNN 필터 구간
    x = tf.keras.layers.Conv1D(64, 7, strides=2, activation="relu", padding="same")(inputs)
    x = tf.keras.layers.BatchNormalization()(x)
    x = tf.keras.layers.Conv1D(128, 5, strides=2, activation="relu", padding="same")(x)
    x = tf.keras.layers.BatchNormalization()(x)

    # Sequence Patchify 레이어 변환 (1500 -> 375 -> 25 patches)
    patch_size = 15
    x = tf.keras.layers.Reshape(((1500 // 4) // patch_size, patch_size, 128))(x)
    x = tf.keras.layers.Reshape((-1, 128))(x)

    # Attention 인코더 블록
    x = tf.keras.layers.MultiHeadAttention(num_heads=4, key_dim=32, dropout=0.1)(x, x)
    x = tf.keras.layers.LayerNormalization()(x)

    # 풀링 및 밀집 신경망 매핑 출력 (5개 수면 클래스)
    x = tf.keras.layers.GlobalAveragePooling1D()(x)
    x = tf.keras.layers.Dropout(0.3)(x)
    x = tf.keras.layers.Dense(128, activation="relu")(x)
    outputs = tf.keras.layers.Dense(5, activation="softmax")(x)

    return tf.keras.Model(inputs, outputs)