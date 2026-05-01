"""
Kaggle 과일/채소 데이터셋으로 식재료 분류 모델 학습 (Google Colab용).

추천 데이터셋 (Kaggle에서 무료 다운로드):
  - "Fruits 360" : 131종 과일, 약 90,000장
  - "Vegetable Image Dataset" : 15종 채소, 약 21,000장
  두 개 합치면 한국 냉장고 식재료 대부분 커버

데이터셋 다운로드 방법 (Colab):
  !pip install kaggle
  # Kaggle API 키 설정 후:
  !kaggle datasets download -d moltean/fruits
  !kaggle datasets download -d misrakahmed/vegetable-image-dataset
  !unzip -q fruits.zip -d /content/data/fruits
  !unzip -q vegetable-image-dataset.zip -d /content/data/veggies

사용법:
1. Colab GPU 런타임 선택
2. 데이터셋 다운로드 후 실행
3. 완료 후 mobilenet_v2.tflite + labels.txt 다운로드 → assets/ml/
"""

# !pip install -q tensorflow==2.15 pillow kaggle

import os
import shutil
from pathlib import Path

import tensorflow as tf
from tensorflow.keras import Model, layers
from tensorflow.keras.applications import MobileNetV2
from tensorflow.keras.preprocessing.image import ImageDataGenerator

IMG_SIZE = 224
BATCH = 32

# ============================================================
# Kaggle 데이터셋 다운로드 (Colab에서 실행)
# ============================================================
# from google.colab import files
# files.upload()  # kaggle.json 업로드
# !mkdir -p ~/.kaggle && cp kaggle.json ~/.kaggle/ && chmod 600 ~/.kaggle/kaggle.json
# !kaggle datasets download -d moltean/fruits
# !kaggle datasets download -d misrakahmed/vegetable-image-dataset
# !unzip -q fruits.zip -d /tmp/fruits
# !unzip -q vegetable-image-dataset.zip -d /tmp/veggies

# ============================================================
# 한국 냉장고에 필요한 식재료만 필터링
# ============================================================
KOREAN_MAP = {
    # 과일
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
    # 채소
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

DATA_DIR   = "/kaggle/working/dataset"
TRAIN_DIR  = f"{DATA_DIR}/train"
VAL_DIR    = f"{DATA_DIR}/val"
MAX_PER_CLASS = 500

# Kaggle 데이터셋 실제 경로 (하드코딩)
SRC_DIRS = [
    "/kaggle/input/datasets/moltean/fruits/fruits-360_100x100/fruits-360/Training",
    "/kaggle/input/datasets/moltean/fruits/fruits-360_100x100/fruits-360/Test",
    # 채소 데이터셋 - 실제 경로 자동 탐색
]

def find_vegetable_dir():
    """채소 데이터셋 경로 자동 탐색."""
    candidates = [
        "/kaggle/input/vegetable-image-dataset",
        "/kaggle/input/datasets/misrakahmed/vegetable-image-dataset/Vegetable Images/train",
        "/kaggle/input/datasets/misrakahmed/vegetable-image-dataset/Vegetable Images/test",
        "/kaggle/input/datasets/misrakahmed/vegetable-image-dataset/Vegetable Images/validation",
    ]
    found = []
    for c in candidates:
        p = Path(c)
        if p.exists():
            found.append(str(p))
            print(f"  채소 경로 발견: {c}")
    if not found:
        # 전체 탐색
        base = Path("/kaggle/input")
        for p in base.rglob("*"):
            if p.is_dir() and any(
                kw in p.name.lower() for kw in ("vegetable", "veggie", "carrot", "broccoli")
            ):
                found.append(str(p))
                print(f"  채소 경로 발견 (탐색): {p}")
    return found


def build_filtered_dataset(src_dirs: list[str]):
    """여러 소스 폴더에서 원하는 클래스만 추려서 train/val 구성."""
    import random
    for split in ("train", "val"):
        os.makedirs(f"{DATA_DIR}/{split}", exist_ok=True)

    copied = 0
    for src in src_dirs:
        src_path = Path(src)
        if not src_path.exists():
            print(f"  경로 없음 (건너뜀): {src}")
            continue
        # 직접 하위 폴더만 확인 (rglob 대신 iterdir 사용해 중복 방지)
        for folder in src_path.iterdir():
            if not folder.is_dir():
                continue
            matched = None
            for eng, kor in KOREAN_MAP.items():
                if eng.lower() in folder.name.lower():
                    matched = kor
                    break
            if not matched:
                continue

            images = list(folder.glob("*.jpg")) + list(folder.glob("*.png"))
            if not images:
                # 한 단계 더 들어가기 (일부 데이터셋은 하위 폴더 구조)
                for sub in folder.iterdir():
                    if sub.is_dir():
                        images += list(sub.glob("*.jpg")) + list(sub.glob("*.png"))

            random.shuffle(images)
            images = images[:MAX_PER_CLASS]
            split_idx = int(len(images) * 0.85)

            for i, img_path in enumerate(images):
                split = "train" if i < split_idx else "val"
                dest = Path(DATA_DIR) / split / matched
                dest.mkdir(parents=True, exist_ok=True)
                dst_file = dest / img_path.name
                if not dst_file.exists():
                    shutil.copy2(img_path, dst_file)
                    copied += 1

    print(f"데이터셋 구성 완료 (총 {copied}개 복사)")
    for split in ("train", "val"):
        split_path = Path(DATA_DIR) / split
        if split_path.exists():
            classes = [d for d in split_path.iterdir() if d.is_dir()]
            print(f"  {split}: {len(classes)}개 클래스 → {[c.name for c in classes]}")


all_dirs = SRC_DIRS + find_vegetable_dir()
print(f"사용할 소스 경로 {len(all_dirs)}개:")
for d in all_dirs:
    print(f"  {d}")
build_filtered_dataset(all_dirs)


# ============================================================
# 데이터 로더
# ============================================================
train_gen = ImageDataGenerator(
    rescale=1./127.5,
    preprocessing_function=lambda x: x - 1.0,
    rotation_range=20,
    width_shift_range=0.1,
    height_shift_range=0.1,
    horizontal_flip=True,
    zoom_range=0.15,
).flow_from_directory(TRAIN_DIR, target_size=(IMG_SIZE, IMG_SIZE),
                      batch_size=BATCH, class_mode="categorical")

val_gen = ImageDataGenerator(
    rescale=1./127.5,
    preprocessing_function=lambda x: x - 1.0,
).flow_from_directory(VAL_DIR, target_size=(IMG_SIZE, IMG_SIZE),
                      batch_size=BATCH, class_mode="categorical")

NUM_CLASSES = train_gen.num_classes
CLASS_NAMES = list(train_gen.class_indices.keys())
print(f"클래스 {NUM_CLASSES}개:", CLASS_NAMES)


# ============================================================
# 모델: MobileNetV2 + 분류 헤드
# ============================================================
base = MobileNetV2(input_shape=(IMG_SIZE, IMG_SIZE, 3), include_top=False, weights="imagenet")
base.trainable = False

inp = layers.Input(shape=(IMG_SIZE, IMG_SIZE, 3))
x = base(inp, training=False)
x = layers.GlobalAveragePooling2D()(x)
x = layers.Dropout(0.2)(x)
out = layers.Dense(NUM_CLASSES, activation="softmax")(x)

model = Model(inp, out)
model.compile(optimizer=tf.keras.optimizers.Adam(1e-3),
              loss="categorical_crossentropy", metrics=["accuracy"])

cb = [tf.keras.callbacks.EarlyStopping(patience=4, restore_best_weights=True)]
model.fit(train_gen, validation_data=val_gen, epochs=15, callbacks=cb)

# Fine-tuning
base.trainable = True
for layer in base.layers[:-30]:
    layer.trainable = False
model.compile(optimizer=tf.keras.optimizers.Adam(1e-5),
              loss="categorical_crossentropy", metrics=["accuracy"])
model.fit(train_gen, validation_data=val_gen, epochs=10, callbacks=cb)


# ============================================================
# TFLite 변환 & 라벨 저장
# ============================================================
converter = tf.lite.TFLiteConverter.from_keras_model(model)
converter.optimizations = [tf.lite.Optimize.DEFAULT]
tflite_model = converter.convert()

OUT_MODEL  = "/kaggle/working/mobilenet_v2.tflite"
OUT_LABELS = "/kaggle/working/labels.txt"

with open(OUT_MODEL, "wb") as f:
    f.write(tflite_model)
with open(OUT_LABELS, "w", encoding="utf-8") as f:
    f.write("\n".join(CLASS_NAMES))

print("완료!")
print(f"  {OUT_MODEL}: {os.path.getsize(OUT_MODEL):,} bytes")
print(f"  labels.txt 클래스: {CLASS_NAMES}")
