"""
Kaggle 노트북용 식재료 분류 모델 학습.

파일을 복사하지 않고 원본 경로에서 직접 tf.data로 학습합니다.
디스크 사용량: 0 (working 디렉터리에 .tflite + labels.txt만 저장)

사용법:
1. Kaggle 노트북에 Fruits360 + Vegetable Image Dataset 추가
2. 이 스크립트 전체를 셀에 붙여넣고 실행
3. Output 탭에서 mobilenet_v2.tflite + labels.txt 다운로드
"""

import os
import random
from pathlib import Path

import numpy as np
import tensorflow as tf
from tensorflow.keras import Model, layers
from tensorflow.keras.applications import MobileNetV2

IMG_SIZE = 224
BATCH    = 32
MAX_PER_CLASS = 300   # 클래스당 최대 이미지 수

KOREAN_MAP = {
    "Apple":       "사과",
    "Banana":      "바나나",
    "Orange":      "오렌지",
    "Strawberry":  "딸기",
    "Grape":       "포도",
    "Watermelon":  "수박",
    "Pear":        "배",
    "Peach":       "복숭아",
    "Mango":       "망고",
    "Pineapple":   "파인애플",
    "Lemon":       "레몬",
    "Kiwi":        "키위",
    "Carrot":      "당근",
    "Broccoli":    "브로콜리",
    "Tomato":      "토마토",
    "Cucumber":    "오이",
    "Potato":      "감자",
    "Onion":       "양파",
    "Pepper":      "피망",
    "Cabbage":     "양배추",
    "Spinach":     "시금치",
    "Corn":        "옥수수",
    "Mushroom":    "버섯",
    "Ginger":      "생강",
    "Garlic":      "마늘",
    "Eggplant":    "가지",
    "Zucchini":    "애호박",
    "Lettuce":     "상추",
}

# ============================================================
# 소스 경로 탐색 (파일 복사 없음)
# ============================================================
SRC_DIRS = [
    "/kaggle/input/datasets/moltean/fruits/fruits-360_100x100/fruits-360/Training",
    "/kaggle/input/datasets/moltean/fruits/fruits-360_100x100/fruits-360/Test",
]

def find_extra_dirs():
    """채소 데이터셋 등 추가 경로 자동 탐색."""
    found = []
    veg_candidates = [
        "/kaggle/input/vegetable-image-dataset/train",
        "/kaggle/input/vegetable-image-dataset/test",
        "/kaggle/input/vegetable-image-dataset/validation",
        "/kaggle/input/datasets/misrakahmed/vegetable-image-dataset/Vegetable Images/train",
        "/kaggle/input/datasets/misrakahmed/vegetable-image-dataset/Vegetable Images/test",
        "/kaggle/input/datasets/misrakahmed/vegetable-image-dataset/Vegetable Images/validation",
    ]
    for c in veg_candidates:
        if Path(c).exists():
            found.append(c)
            print(f"  발견: {c}")
    return found

print("=== 경로 탐색 ===")
all_src = [d for d in SRC_DIRS if Path(d).exists()] + find_extra_dirs()
print(f"소스 경로 {len(all_src)}개 확인됨")

# ============================================================
# (path, label_index) 리스트 수집 - 파일 복사 없음
# ============================================================
def collect_samples(src_dirs):
    class_to_paths: dict[str, list[str]] = {}
    for src in src_dirs:
        for folder in Path(src).iterdir():
            if not folder.is_dir():
                continue
            label = None
            for eng, kor in KOREAN_MAP.items():
                if eng.lower() in folder.name.lower():
                    label = kor
                    break
            if label is None:
                continue
            imgs = list(folder.glob("*.jpg")) + list(folder.glob("*.png"))
            if not imgs:
                for sub in folder.iterdir():
                    if sub.is_dir():
                        imgs += list(sub.glob("*.jpg")) + list(sub.glob("*.png"))
            if imgs:
                class_to_paths.setdefault(label, []).extend([str(p) for p in imgs])

    # 클래스별 샘플 수 제한
    for lbl in class_to_paths:
        random.shuffle(class_to_paths[lbl])
        class_to_paths[lbl] = class_to_paths[lbl][:MAX_PER_CLASS]

    return class_to_paths


print("\n=== 샘플 수집 ===")
class_to_paths = collect_samples(all_src)
CLASS_NAMES = sorted(class_to_paths.keys())
NUM_CLASSES = len(CLASS_NAMES)
label_to_idx = {lbl: i for i, lbl in enumerate(CLASS_NAMES)}

print(f"클래스 {NUM_CLASSES}개: {CLASS_NAMES}")
for lbl in CLASS_NAMES:
    print(f"  {lbl}: {len(class_to_paths[lbl])}장")

if NUM_CLASSES == 0:
    raise RuntimeError("클래스를 찾지 못했습니다. 데이터셋 경로를 확인하세요.")

# (path, one_hot) 전체 목록
all_paths, all_labels = [], []
for lbl, paths in class_to_paths.items():
    idx = label_to_idx[lbl]
    one_hot = [0.0] * NUM_CLASSES
    one_hot[idx] = 1.0
    for p in paths:
        all_paths.append(p)
        all_labels.append(one_hot)

combined = list(zip(all_paths, all_labels))
random.shuffle(combined)
split_idx = int(len(combined) * 0.85)
train_data = combined[:split_idx]
val_data   = combined[split_idx:]
print(f"\ntrain: {len(train_data)}장 / val: {len(val_data)}장")

# ============================================================
# tf.data 파이프라인 (원본 경로에서 직접 읽기)
# ============================================================
def make_dataset(data, training=True):
    paths  = [d[0] for d in data]
    labels = [d[1] for d in data]

    def load_and_preprocess(path, label):
        raw = tf.io.read_file(path)
        img = tf.image.decode_image(raw, channels=3, expand_animations=False)
        img = tf.image.resize(img, [IMG_SIZE, IMG_SIZE])
        img = (tf.cast(img, tf.float32) - 127.5) / 127.5
        if training:
            img = tf.image.random_flip_left_right(img)
            img = tf.image.random_brightness(img, 0.1)
            img = tf.image.random_contrast(img, 0.9, 1.1)
        img.set_shape([IMG_SIZE, IMG_SIZE, 3])
        return img, label

    ds = tf.data.Dataset.from_tensor_slices((paths, labels))
    if training:
        ds = ds.shuffle(len(data), reshuffle_each_iteration=True)
    ds = ds.map(load_and_preprocess, num_parallel_calls=tf.data.AUTOTUNE)
    return ds.batch(BATCH).prefetch(tf.data.AUTOTUNE)


train_ds = make_dataset(train_data, training=True)
val_ds   = make_dataset(val_data,   training=False)

# ============================================================
# 모델: MobileNetV2 + 분류 헤드
# ============================================================
base = MobileNetV2(input_shape=(IMG_SIZE, IMG_SIZE, 3), include_top=False, weights="imagenet")
base.trainable = False

inp = layers.Input(shape=(IMG_SIZE, IMG_SIZE, 3))
x   = base(inp, training=False)
x   = layers.GlobalAveragePooling2D()(x)
x   = layers.Dropout(0.2)(x)
out = layers.Dense(NUM_CLASSES, activation="softmax")(x)

model = Model(inp, out)
model.compile(
    optimizer=tf.keras.optimizers.Adam(1e-3),
    loss="categorical_crossentropy",
    metrics=["accuracy"],
)

cb = [
    tf.keras.callbacks.EarlyStopping(patience=4, restore_best_weights=True),
    tf.keras.callbacks.ModelCheckpoint(
        "/kaggle/working/best_checkpoint.keras",
        save_best_only=True, monitor="val_accuracy",
    ),
]

print("\n=== 1단계: 헤드 학습 ===")
model.fit(train_ds, validation_data=val_ds, epochs=12, callbacks=cb)

print("\n=== 2단계: Fine-tuning ===")
base.trainable = True
for layer in base.layers[:-30]:
    layer.trainable = False
model.compile(
    optimizer=tf.keras.optimizers.Adam(1e-5),
    loss="categorical_crossentropy",
    metrics=["accuracy"],
)
model.fit(train_ds, validation_data=val_ds, epochs=8, callbacks=cb)

# ============================================================
# TFLite 변환
# ============================================================
converter = tf.lite.TFLiteConverter.from_keras_model(model)
converter.optimizations = [tf.lite.Optimize.DEFAULT]
tflite_model = converter.convert()

with open("/kaggle/working/mobilenet_v2.tflite", "wb") as f:
    f.write(tflite_model)
with open("/kaggle/working/labels.txt", "w", encoding="utf-8") as f:
    f.write("\n".join(CLASS_NAMES))

print("\n=== 완료 ===")
print(f"  mobilenet_v2.tflite: {os.path.getsize('/kaggle/working/mobilenet_v2.tflite'):,} bytes")
print(f"  labels.txt: {CLASS_NAMES}")
print("Output 탭에서 두 파일 다운로드 후 assets/ml/ 에 넣으세요.")
