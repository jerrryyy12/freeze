"""
Kaggle 노트북용 식재료 분류 모델 학습.

데이터셋 (Kaggle 노트북 "+ Add Input" 에서 추가):
  1. kritikseth/fruit-and-vegetable-image-recognition  ← 핵심 (실사 사진)
  2. moltean/fruits                                    ← 보조 (딸기·레몬·복숭아·애호박)
  3. misrakahmed/vegetable-image-dataset               ← 보조 (브로콜리·버섯)

파일 복사 없이 원본 경로에서 직접 tf.data로 학습합니다.
학습 완료 후 Output 탭에서 mobilenet_v2.tflite + labels.txt 다운로드.
"""

import os
import random
from pathlib import Path

import tensorflow as tf
from tensorflow.keras import Model, layers
from tensorflow.keras.applications import MobileNetV2

IMG_SIZE      = 224
BATCH         = 32
MAX_PER_CLASS = 400   # 클래스당 최대 이미지 수

# kritikseth 데이터셋에 있는 클래스 (실사) → 더 많이 허용
REAL_PHOTO_CAP   = MAX_PER_CLASS
# Fruits360처럼 흰배경 인공사진 → 실사 클래스에 이미 있으면 보조로만
SYNTHETIC_CAP    = 150
# 감자는 채소 데이터셋에서 편향이 심해 따로 제한
POTATO_CAP       = 120

KOREAN_MAP = {
    # kritikseth에 있는 것 (실사 우선)
    "Apple":       "사과",
    "Banana":      "바나나",
    "Orange":      "오렌지",
    "Grape":       "포도",
    "Watermelon":  "수박",
    "Pear":        "배",
    "Mango":       "망고",
    "Pineapple":   "파인애플",
    "Kiwi":        "키위",
    "Carrot":      "당근",
    "Tomato":      "토마토",
    "Cucumber":    "오이",
    "Potato":      "감자",
    "Onion":       "양파",
    "Cabbage":     "양배추",
    "Spinach":     "시금치",
    "Corn":        "옥수수",
    "Ginger":      "생강",
    "Garlic":      "마늘",
    "Eggplant":    "가지",
    "Lettuce":     "상추",
    "Paprika":     "피망",
    "Capsicum":    "피망",
    "Pepper":      "피망",
    # Fruits360·채소 데이터셋 보조 (kritikseth에 없는 것)
    "Strawberry":  "딸기",
    "Peach":       "복숭아",
    "Lemon":       "레몬",
    "Zucchini":    "애호박",
    "Broccoli":    "브로콜리",
    "Mushroom":    "버섯",
}

# kritikseth에 있는 클래스 (실사 데이터) 목록
KRITIKSETH_CLASSES = {
    "사과", "바나나", "오렌지", "포도", "수박", "배", "망고",
    "파인애플", "키위", "당근", "토마토", "오이", "감자", "양파",
    "양배추", "시금치", "옥수수", "생강", "마늘", "가지", "상추", "피망",
}

# ============================================================
# 소스 경로 탐색
# ============================================================
def find_all_src_dirs():
    found = []

    # 1순위: kritikseth 실사 데이터셋
    kritikseth_candidates = [
        "/kaggle/input/fruit-and-vegetable-image-recognition/train",
        "/kaggle/input/fruit-and-vegetable-image-recognition/test",
        "/kaggle/input/fruit-and-vegetable-image-recognition/validation",
        "/kaggle/input/datasets/kritikseth/fruit-and-vegetable-image-recognition/train",
        "/kaggle/input/datasets/kritikseth/fruit-and-vegetable-image-recognition/test",
        "/kaggle/input/datasets/kritikseth/fruit-and-vegetable-image-recognition/validation",
    ]
    for c in kritikseth_candidates:
        if Path(c).exists():
            found.append(("kritikseth", c))
            print(f"  [실사] 발견: {c}")

    # 2순위: Fruits360 (보조 - 복숭아·딸기·레몬 등)
    fruits360_candidates = [
        "/kaggle/input/datasets/moltean/fruits/fruits-360_100x100/fruits-360/Training",
        "/kaggle/input/datasets/moltean/fruits/fruits-360_100x100/fruits-360/Test",
        "/kaggle/input/fruits/fruits-360_100x100/fruits-360/Training",
    ]
    for c in fruits360_candidates:
        if Path(c).exists():
            found.append(("fruits360", c))
            print(f"  [보조] 발견: {c}")

    # 3순위: 채소 데이터셋 (브로콜리·버섯 보조)
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
            found.append(("vegetable", c))
            print(f"  [보조] 발견: {c}")

    return found


print("=== 경로 탐색 ===")
all_src = find_all_src_dirs()
if not all_src:
    raise RuntimeError("데이터셋을 찾지 못했습니다. Kaggle 노트북에 데이터셋을 추가하세요.")
print(f"총 {len(all_src)}개 경로 확인")

# ============================================================
# 샘플 수집
# ============================================================
def collect_samples(src_list):
    """
    (dataset_type, path) 목록에서 (path, label) 수집.
    실사 데이터(kritikseth)를 먼저 쌓고, 보조 데이터는 클래스 한도가
    남은 경우에만 추가해 실사 이미지를 우선한다.
    """
    class_to_paths: dict[str, list[str]] = {}

    # 실사 데이터 먼저 수집
    for dtype, src in src_list:
        if dtype != "kritikseth":
            continue
        for folder in Path(src).iterdir():
            if not folder.is_dir():
                continue
            label = _match_label(folder.name)
            if label is None:
                continue
            imgs = _collect_images(folder)
            if imgs:
                class_to_paths.setdefault(label, []).extend(imgs)

    # 실사 클래스별 캡 적용
    for lbl in list(class_to_paths.keys()):
        random.shuffle(class_to_paths[lbl])
        cap = POTATO_CAP if lbl == "감자" else REAL_PHOTO_CAP
        class_to_paths[lbl] = class_to_paths[lbl][:cap]

    # 보조 데이터: kritikseth에 없거나 부족한 클래스만 채우기
    for dtype, src in src_list:
        if dtype == "kritikseth":
            continue
        for folder in Path(src).iterdir():
            if not folder.is_dir():
                continue
            label = _match_label(folder.name)
            if label is None:
                continue

            # kritikseth 실사 데이터가 있는 클래스는 보조 캡 적용
            current = len(class_to_paths.get(label, []))
            cap = POTATO_CAP if label == "감자" else (
                SYNTHETIC_CAP if label in KRITIKSETH_CLASSES else REAL_PHOTO_CAP
            )
            if current >= cap:
                continue

            imgs = _collect_images(folder)
            random.shuffle(imgs)
            need = cap - current
            class_to_paths.setdefault(label, []).extend(imgs[:need])

    return class_to_paths


def _match_label(folder_name: str):
    for eng, kor in KOREAN_MAP.items():
        if eng.lower() in folder_name.lower():
            return kor
    return None


def _collect_images(folder: Path) -> list[str]:
    imgs = list(folder.glob("*.jpg")) + list(folder.glob("*.png")) + list(folder.glob("*.jpeg"))
    if not imgs:
        for sub in folder.iterdir():
            if sub.is_dir():
                imgs += list(sub.glob("*.jpg")) + list(sub.glob("*.png")) + list(sub.glob("*.jpeg"))
    return [str(p) for p in imgs]


print("\n=== 샘플 수집 ===")
class_to_paths = collect_samples(all_src)
CLASS_NAMES = sorted(class_to_paths.keys())
NUM_CLASSES = len(CLASS_NAMES)
label_to_idx = {lbl: i for i, lbl in enumerate(CLASS_NAMES)}

print(f"클래스 {NUM_CLASSES}개:")
for lbl in CLASS_NAMES:
    tag = "[실사]" if lbl in KRITIKSETH_CLASSES else "[보조]"
    print(f"  {tag} {lbl}: {len(class_to_paths[lbl])}장")

if NUM_CLASSES == 0:
    raise RuntimeError("클래스를 찾지 못했습니다.")

# (path, one_hot) 목록
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
# tf.data 파이프라인
# ============================================================
def make_dataset(data, training=True):
    paths  = [d[0] for d in data]
    labels = [d[1] for d in data]

    def load_and_preprocess(path, label):
        raw = tf.io.read_file(path)
        img = tf.image.decode_image(raw, channels=3, expand_animations=False)
        if training:
            img = tf.image.resize(img, [IMG_SIZE + 32, IMG_SIZE + 32])
            img = tf.image.random_crop(img, [IMG_SIZE, IMG_SIZE, 3])
            img = tf.image.random_flip_left_right(img)
            img = tf.image.random_flip_up_down(img)
            img = tf.image.random_brightness(img, 0.4)
            img = tf.image.random_contrast(img, 0.6, 1.4)
            img = tf.image.random_saturation(img, 0.6, 1.4)
            img = tf.image.random_hue(img, 0.08)
        else:
            img = tf.image.resize(img, [IMG_SIZE, IMG_SIZE])
        img = (tf.cast(img, tf.float32) - 127.5) / 127.5
        img = tf.clip_by_value(img, -1.0, 1.0)
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
x   = layers.Dropout(0.4)(x)
out = layers.Dense(NUM_CLASSES, activation="softmax")(x)

model = Model(inp, out)
model.compile(
    optimizer=tf.keras.optimizers.Adam(1e-3),
    loss="categorical_crossentropy",
    metrics=["accuracy"],
)

cb = [
    tf.keras.callbacks.EarlyStopping(patience=5, restore_best_weights=True),
    tf.keras.callbacks.ModelCheckpoint(
        "/kaggle/working/best_checkpoint.keras",
        save_best_only=True, monitor="val_accuracy",
    ),
]

print("\n=== 1단계: 헤드 학습 ===")
model.fit(train_ds, validation_data=val_ds, epochs=15, callbacks=cb)

print("\n=== 2단계: Fine-tuning ===")
base.trainable = True
for layer in base.layers[:-60]:
    layer.trainable = False
model.compile(
    optimizer=tf.keras.optimizers.Adam(1e-5),
    loss="categorical_crossentropy",
    metrics=["accuracy"],
)
model.fit(train_ds, validation_data=val_ds, epochs=12, callbacks=cb)

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
