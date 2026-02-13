import glob
import os
import mne
import numpy as np
import pyedflib
import tensorflow as tf
from scipy.signal import butter, resample, lfilter
from sklearn.model_selection import train_test_split
from tensorflow.keras.layers import Input, Conv1D, MaxPooling1D, LSTM, Dense,Dropout, BatchNormalization
from tensorflow.keras.models import Model

edf_path = 'C:\\Users\\dream\\study\\Sleep\\sleep-edf-database-expanded\\SC4001E0-PSG.edf'
with pyedflib.EdfReader(edf_path) as edf:
  print("채널 수:", edf.signals_in_file)
  print("채널 이름:", edf.getSignalLabels())
  print("샘플링 레이트:",
        [edf.getSampleFrequency(i) for i in range(edf.signals_in_file)])
  print("기록 시작 시간:", edf.getStartdatetime())
  print("기록 길이(초):", edf.file_duration)
  print("헤더 정보:", edf.getHeader())


# 데이터 로딩 및 전처리 함수
def load_and_preprocess_sleep_data(dataset_dir: str, test_size: float = 0.2, batch_size: int = 32):
  """
  데이터셋 디렉토리에서 PSG EDF 파일과 Hypnogram 파일을 읽어
  EEG 신호를 전처리하고, Epoch 단위로 분할 및 정규화하여
  학습/테스트 데이터로 반환합니다.
  """
  EEG_CHANNEL = 'EEG Fpz-Cz'  # 사용할 EEG 채널 이름
  EPOCH_SEC = 30  # 각 Epoch의 길이(초)
  ORIGINAL_SAMPLING_RATE = 100  # 원본 샘플링 레이트(Hz)
  TARGET_SAMPLING_RATE = 100  # 목표 샘플링 레이트(Hz)
  LOWCUT = 0.5  # 밴드패스 필터 하한 주파수(Hz)
  HIGHCUT = 45  # 밴드패스 필터 상한 주파수(Hz)

  ANNOTATION_MAP = {"Sleep stage W": 0, "Sleep stage 1": 1, "Sleep stage 2": 2,
                    "Sleep stage 3": 3, "Sleep stage 4": 3, "Sleep stage R": 4, }  # 수면 단계 매핑

  all_epochs = []  # 모든 Epoch 저장
  all_labels = []  # 모든 레이블 저장

  print("데이터 로딩 및 전처리를 시작합니다...")

  edf_files = glob.glob(os.path.join(dataset_dir, '*PSG.edf'))  # PSG EDF 파일 경로
  if not edf_files:  # EDF 파일이 없으면 오류 발생
    raise FileNotFoundError(
      f"'{dataset_dir}' 경로에서 EDF 파일을 찾을 수 없습니다. 경로를 확인해주세요.")

  for i, edf_path in enumerate(edf_files):  # 각 EDF 파일 처리
    print(f"파일 처리 중 ({i + 1}/{len(edf_files)}):{os.path.basename(edf_path)}")
    hypnogram_glob = os.path.join(dataset_dir, os.path.basename(edf_path)[
      :7] + '*Hypnogram.edf')
    hypnogram_paths = glob.glob(hypnogram_glob)
    print("hypnogram_paths:", hypnogram_paths)
    if not hypnogram_paths:  # 주석 파일이 없으면 경고 출력 후 건너뜀
      print(f"경고: 해당 파일의 주석 파일(.edf)을 찾을 수 없습니다: {os.path.basename(edf_path)}")
      continue
    hypnogram_path = hypnogram_paths[0]  # 주석 파일 경로

    # EEG 신호 pyEDFlib로 읽기
    with pyedflib.EdfReader(edf_path) as edf:
      channel_names = edf.getSignalLabels()
      try:
        ch_idx = channel_names.index(EEG_CHANNEL)
      except ValueError:
        print(f"경고: '{EEG_CHANNEL}' 채널을 찾을 수 없습니다.")
        continue
      eeg_signal = edf.readSignal(ch_idx)

    # Hypnogram 주석을 MNE로 읽기
    annotations = mne.read_annotations(hypnogram_path)

    # EEG 신호에 밴드패스 필터 적용
    filtered_eeg = bandpass_filter(eeg_signal, LOWCUT, HIGHCUT,ORIGINAL_SAMPLING_RATE)

    # 목표 샘플링 레이트로 리샘플링
    if ORIGINAL_SAMPLING_RATE != TARGET_SAMPLING_RATE:
      num_points = int(
        len(filtered_eeg) * (TARGET_SAMPLING_RATE / ORIGINAL_SAMPLING_RATE))
      resampled_eeg = resample(filtered_eeg, num_points)
    else:
      resampled_eeg = filtered_eeg

    # Epoch 분할 및 정규화
    epoch_points = EPOCH_SEC * TARGET_SAMPLING_RATE

    # 각 주석에 대해 Epoch 생성
    for onset_sec, duration_sec, label_str in zip(annotations.onset, annotations.duration, annotations.description):
      if label_str in ANNOTATION_MAP:
        label = ANNOTATION_MAP[label_str]
        start_point = int(onset_sec * TARGET_SAMPLING_RATE)
        end_point = start_point + epoch_points
        if end_point > len(resampled_eeg):
          continue
        epoch = resampled_eeg[start_point:end_point]
        if len(epoch) != epoch_points:
          continue
        # Epoch 신호 정규화
        epoch = (epoch - np.mean(epoch)) / np.std(epoch)

        all_epochs.append(epoch)
        all_labels.append(label)

  # 모든 파일 처리 후 데이터 확인
  if not all_epochs:
    raise ValueError("처리할 수 있는 데이터가 없습니다. 채널 이름이나 파일 구조를 확인해주세요.")

  X = np.array(all_epochs)
  y = np.array(all_labels)
  X = np.expand_dims(X, axis=-1)
  print(f"전처리 완료. 총 {len(X)}개의 Epoch 생성.")

  # 학습/테스트 데이터 분리 (계층적 분할)
  if len(np.unique(y)) < 2:
    raise ValueError("데이터셋에 클래스가 하나뿐이거나 없습니다. 계층적 분할(stratify)을 할 수 없습니다.")
  X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=test_size, random_state=42, stratify=y)

  print(f"데이터 분리 완료. 학습 데이터: {X_train.shape}, 테스트 데이터: {X_test.shape}")

  # tf.data.Dataset 파이프라인 적용
  train_ds = tf.data.Dataset.from_tensor_slices((X_train, y_train))
  train_ds = train_ds.shuffle(buffer_size=len(X_train)).batch(batch_size).prefetch(tf.data.AUTOTUNE)

  test_ds = tf.data.Dataset.from_tensor_slices((X_test, y_test))
  test_ds = test_ds.batch(batch_size).prefetch(tf.data.AUTOTUNE)

  return train_ds, test_ds, X_train.shape[1:]


# 밴드패스 필터 함수
def bandpass_filter(data, lowcut, highcut, fs, order=5):
  """
  입력 신호에 밴드패스 필터를 적용하여
  지정한 주파수 범위의 신호만 통과시킵니다.
  """
  nyq = 0.5 * fs
  low = lowcut / nyq
  high = highcut / nyq
  [b, a] = butter(order, (low, high), btype='band')
  y = lfilter(b, a, data)
  return y


# CNN-LSTM 모델 정의 함수
def create_cnn_lstm_model(input_shape=(3000, 1), num_classes=5):
  """
  입력 데이터 형태와 클래스 수를 받아
  1D CNN-LSTM 기반 수면 단계 분류 모델을 생성합니다.
  """
  inputs = Input(shape=input_shape)
  x = Conv1D(filters=64, kernel_size=50, activation='relu', padding='same')(
    inputs)
  x = BatchNormalization()(x)
  x = MaxPooling1D(pool_size=8)(x)

  x = Conv1D(filters=128, kernel_size=12, activation='relu', padding='same')(x)
  x = BatchNormalization()(x)
  x = MaxPooling1D(pool_size=4)(x)

  x = LSTM(units=128, return_sequences=False)(x)
  x = Dropout(0.5)(x)

  x = Dense(64, activation='relu')(x)
  outputs = Dense(num_classes, activation='softmax')(x)

  model = Model(inputs=inputs, outputs=outputs)
  model.compile(optimizer='adam', loss='sparse_categorical_crossentropy',
                metrics=['accuracy'])

  return model


DATASET_PATH = "C:\\Users\\dream\\study\\Sleep\\sleep-edf-database-expanded"  # 데이터셋 경로
try:
  print("코어",os.cpu_count())
  # GPU를 비활성화하여 CPU만 사용
  tf.config.set_visible_devices([], 'GPU')
  # 멀티스레드 설정 (CPU 코어 수에 맞게 조정)
  tf.config.threading.set_intra_op_parallelism_threads(20)
  tf.config.threading.set_inter_op_parallelism_threads(20)
  # 데이터 로딩 및 전처리
  train_ds, test_ds, input_shape = load_and_preprocess_sleep_data(DATASET_PATH, batch_size=32)
  model = create_cnn_lstm_model(input_shape=input_shape, num_classes=5)
  model.summary()

  # 모델 학습 (tf.data.Dataset 사용)
  history = model.fit(train_ds, epochs=10, validation_data=test_ds)
  # 모델 저장
  model.save('sleep_stage_model.keras')

  print("모델 학습 및 저장 완료: sleep_stage_model.keras")
  # TFLite 변환
  converter = tf.lite.TFLiteConverter.from_keras_model(model)
  converter.optimizations = [tf.lite.Optimize.DEFAULT]
  converter.target_spec.supported_types = [tf.float16]
  converter.target_spec.supported_ops = [
    tf.lite.OpsSet.TFLITE_BUILTINS,
    tf.lite.OpsSet.SELECT_TF_OPS
  ]
  converter._experimental_lower_tensor_list_ops = False
  tflite_model = converter.convert()
  with open('sleep_stage_model.tflite', 'wb') as f:
    f.write(tflite_model)
  print("TFLite 변환 완료: sleep_stage_model.tflite")
except (FileNotFoundError, ValueError) as e:
  print(f"오류 발생: {e}")
