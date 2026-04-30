"""
Google Colab 양추정 모델 학습 스크립트.

AI Hub 양추정 데이터셋을 기반으로 MobileNetV2 회귀 모델 학습.
입력: 224x224 이미지
출력: 추정 개수 (소수점 포함, 예: 2.5개)

사용법:
1. Colab에서 drive 마운트 후 실행
2. 학습 완료 후 quantity_model.tflite 다운로드
3. assets/ml/ 에 추가
"""

# ============================================================
# 1. 환경 준비
# ============================================================
# !pip install -q tensorflow==2.15 pillow

import json
import os
from pathlib import Path

import numpy as np
import tensorflow as tf
from tensorflow.keras import Model, layers
from tensorflow.keras.applications import MobileNetV2

IMG_SIZE = 224
BATCH = 32


# ============================================================
# 2. AI Hub 양추정 라벨 파싱
# ============================================================
def parse_quantity_labels(label_dir: str, image_dir: str):
    """
    AI Hub 양추정 JSON에서 (이미지경로, 수량) 쌍 추출.
    수량 = bbox 개수로 추정 (count-based approach).
    """
    samples = []
    for json_path in Path(label_dir).rglob("*.json"):
        try:
            with open(json_path, encoding="utf-8") as f:
                data = json.load(f)

            if "images" in data and "annotations" in data:
                id_to_file = {img["id"]: img["file_name"] for img in data["images"]}
                from collections import Counter
                count_per_image = Counter(
                    ann["image_id"] for ann in data["annotations"]
                )
                for img_id, count in count_per_image.items():
                    fname = id_to_file.get(img_id, "")
                    if not fname:
                        continue
                    full_path = os.path.join(image_dir, fname)
                    if os.path.exists(full_path):
                        samples.append((full_path, float(count)))
        except Exception:
            continue

    return samples


# ============================================================
# 3. tf.data 파이프라인
# ============================================================
def make_dataset(samples, training=True):
    paths = [s[0] for s in samples]
    counts = [s[1] for s in samples]

    def load_img(path, count):
        raw = tf.io.read_file(path)
        img = tf.image.decode_jpeg(raw, channels=3)
        img = tf.image.resize(img, [IMG_SIZE, IMG_SIZE])
        img = (tf.cast(img, tf.float32) - 127.5) / 127.5
        if training:
            img = tf.image.random_flip_left_right(img)
            img = tf.image.random_brightness(img, 0.1)
        return img, count

    ds = tf.data.Dataset.from_tensor_slices((paths, counts))
    ds = ds.map(load_img, num_parallel_calls=tf.data.AUTOTUNE)
    if training:
        ds = ds.shuffle(1000)
    return ds.batch(BATCH).prefetch(tf.data.AUTOTUNE)


# ============================================================
# 4. 모델 정의 (회귀)
# ============================================================
def build_quantity_model():
    base = MobileNetV2(input_shape=(IMG_SIZE, IMG_SIZE, 3), include_top=False, weights="imagenet")
    base.trainable = False

    inputs = layers.Input(shape=(IMG_SIZE, IMG_SIZE, 3))
    x = base(inputs, training=False)
    x = layers.GlobalAveragePooling2D()(x)
    x = layers.Dense(128, activation="relu")(x)
    x = layers.Dropout(0.3)(x)
    output = layers.Dense(1, activation="relu")(x)  # 수량은 0 이상

    return Model(inputs, output)


# ============================================================
# 5. 학습
# ============================================================
LABEL_DIR = "/content/drive/MyDrive/quantity_labels"   # 바꾸기
IMAGE_DIR = "/content/drive/MyDrive/quantity_images"   # 바꾸기

print("라벨 파싱 중...")
samples = parse_quantity_labels(LABEL_DIR, IMAGE_DIR)
print(f"  샘플 수: {len(samples)}")

split = int(len(samples) * 0.8)
import random; random.shuffle(samples)
train_ds = make_dataset(samples[:split], training=True)
val_ds   = make_dataset(samples[split:], training=False)

model = build_quantity_model()
model.compile(
    optimizer=tf.keras.optimizers.Adam(1e-3),
    loss="mean_absolute_error",
    metrics=["mae"],
)
model.summary()

model.fit(train_ds, validation_data=val_ds, epochs=10)

# Fine-tuning
model.layers[1].trainable = True
for layer in model.layers[1].layers[:-20]:
    layer.trainable = False
model.compile(optimizer=tf.keras.optimizers.Adam(1e-5), loss="mae", metrics=["mae"])
model.fit(train_ds, validation_data=val_ds, epochs=8)


# ============================================================
# 6. TFLite 변환
# ============================================================
converter = tf.lite.TFLiteConverter.from_keras_model(model)
converter.optimizations = [tf.lite.Optimize.DEFAULT]
tflite_model = converter.convert()

with open("/content/quantity_model.tflite", "wb") as f:
    f.write(tflite_model)

print("저장 완료: /content/quantity_model.tflite")
print("크기:", os.path.getsize("/content/quantity_model.tflite"), "bytes")

# from google.colab import files
# files.download("/content/quantity_model.tflite")
