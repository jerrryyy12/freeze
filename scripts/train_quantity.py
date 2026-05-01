"""
AI Hub 양추정 데이터셋으로 수량 추정 모델 학습 (Google Colab용).

데이터 구조 (YOLO 포맷):
  labels/txt/
    01011001/   ← 음식 코드 폴더
      01_011_01011001_xxx.txt   ← 각 줄이 바운딩박스 1개
  images/
    01/011/01011001/
      01_011_01011001_xxx.jpg

각 txt 파일의 줄 수 = 해당 이미지의 음식 수량

사용법:
1. Google Drive에 AI Hub 데이터 업로드
2. Colab GPU 런타임으로 실행
3. 완료 후 quantity_model.tflite 다운로드 → assets/ml/
"""

# !pip install -q tensorflow==2.15 pillow

import os
import random
from pathlib import Path

import numpy as np
import tensorflow as tf
from tensorflow.keras import Model, layers
from tensorflow.keras.applications import MobileNetV2

IMG_SIZE = 224
BATCH = 32

# ============================================================
# Google Drive 마운트 (Colab)
# ============================================================
# from google.colab import drive
# drive.mount('/content/drive')

LABEL_TXT_DIR = "/content/drive/MyDrive/aihub/labels/txt"   # 바꾸기
IMAGE_BASE_DIR = "/content/drive/MyDrive/aihub/images"       # 바꾸기


# ============================================================
# 샘플 수집: (이미지경로, 수량) 쌍 만들기
# ============================================================
def collect_samples(label_txt_dir: str, image_base_dir: str):
    samples = []
    label_root = Path(label_txt_dir)

    for food_folder in label_root.iterdir():
        if not food_folder.is_dir():
            continue
        code = food_folder.name  # 예: 01011001

        for txt_file in food_folder.glob("*.txt"):
            # 수량 = 줄 수 (각 줄 = 바운딩박스 1개)
            lines = [l.strip() for l in txt_file.read_text().splitlines() if l.strip()]
            quantity = float(len(lines))
            if quantity == 0:
                continue

            # 대응하는 이미지 파일 경로 추정
            # 파일명: 01_011_01011001_xxx.txt → .jpg
            img_name = txt_file.stem + ".jpg"
            parts = code  # 01011001
            img_path = Path(image_base_dir) / parts[:2] / parts[2:5] / parts / img_name

            if img_path.exists():
                samples.append((str(img_path), quantity))

    return samples


print("샘플 수집 중...")
samples = collect_samples(LABEL_TXT_DIR, IMAGE_BASE_DIR)
print(f"총 샘플 수: {len(samples)}")
if samples:
    qtys = [s[1] for s in samples]
    print(f"수량 분포: min={min(qtys):.0f}, max={max(qtys):.0f}, avg={sum(qtys)/len(qtys):.1f}")


# ============================================================
# tf.data 파이프라인
# ============================================================
def make_dataset(samples, training=True):
    paths = [s[0] for s in samples]
    counts = [s[1] for s in samples]

    def load(path, count):
        raw = tf.io.read_file(path)
        img = tf.image.decode_jpeg(raw, channels=3)
        img = tf.image.resize(img, [IMG_SIZE, IMG_SIZE])
        img = (tf.cast(img, tf.float32) - 127.5) / 127.5
        if training:
            img = tf.image.random_flip_left_right(img)
            img = tf.image.random_brightness(img, 0.1)
        return img, count

    ds = tf.data.Dataset.from_tensor_slices((paths, counts))
    ds = ds.map(load, num_parallel_calls=tf.data.AUTOTUNE)
    if training:
        ds = ds.shuffle(2000)
    return ds.batch(BATCH).prefetch(tf.data.AUTOTUNE)


random.shuffle(samples)
split = int(len(samples) * 0.85)
train_ds = make_dataset(samples[:split], training=True)
val_ds   = make_dataset(samples[split:], training=False)


# ============================================================
# 모델: MobileNetV2 + 회귀 헤드
# ============================================================
base = MobileNetV2(input_shape=(IMG_SIZE, IMG_SIZE, 3), include_top=False, weights="imagenet")
base.trainable = False

inp = layers.Input(shape=(IMG_SIZE, IMG_SIZE, 3))
x = base(inp, training=False)
x = layers.GlobalAveragePooling2D()(x)
x = layers.Dense(128, activation="relu")(x)
x = layers.Dropout(0.3)(x)
out = layers.Dense(1, activation="relu")(x)

model = Model(inp, out)
model.compile(optimizer=tf.keras.optimizers.Adam(1e-3),
              loss="mean_absolute_error", metrics=["mae"])
model.summary()


# ============================================================
# 학습
# ============================================================
model.fit(train_ds, validation_data=val_ds, epochs=10,
          callbacks=[tf.keras.callbacks.EarlyStopping(patience=3, restore_best_weights=True)])

# Fine-tuning
base.trainable = True
for layer in base.layers[:-30]:
    layer.trainable = False
model.compile(optimizer=tf.keras.optimizers.Adam(1e-5),
              loss="mean_absolute_error", metrics=["mae"])
model.fit(train_ds, validation_data=val_ds, epochs=10,
          callbacks=[tf.keras.callbacks.EarlyStopping(patience=3, restore_best_weights=True)])


# ============================================================
# TFLite 변환 & 저장
# ============================================================
converter = tf.lite.TFLiteConverter.from_keras_model(model)
converter.optimizations = [tf.lite.Optimize.DEFAULT]
tflite_model = converter.convert()

with open("/content/quantity_model.tflite", "wb") as f:
    f.write(tflite_model)
print("저장:", os.path.getsize("/content/quantity_model.tflite"), "bytes")

# from google.colab import files
# files.download("/content/quantity_model.tflite")
