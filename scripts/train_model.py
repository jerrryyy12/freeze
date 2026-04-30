"""
Google Colab에서 실행하는 식재료 분류 모델 학습 스크립트.

사용법:
1. https://colab.research.google.com 접속
2. 새 노트북 생성, 런타임 → 런타임 유형 변경 → GPU(T4) 선택
3. 셀에 이 파일 내용을 단계별로 복사해서 실행
4. 마지막에 model.tflite, labels.txt 다운로드
5. 프로젝트의 assets/ml/ 에 덮어쓰기

데이터셋: Roboflow Universe(무료) 의 food/ingredient 데이터셋
       또는 직접 카테고리별 폴더에 사진 넣어 zip 업로드
"""

# ============================================================
# 1. 환경 준비
# ============================================================
# !pip install -q tensorflow==2.15 pillow

import os
import shutil
import tensorflow as tf
from tensorflow.keras import layers, Model
from tensorflow.keras.applications import MobileNetV2
from tensorflow.keras.preprocessing.image import ImageDataGenerator

print("TF version:", tf.__version__)
print("GPU:", tf.config.list_physical_devices("GPU"))


# ============================================================
# 2. 데이터셋 준비
# ============================================================
# 방법 A) Roboflow Universe 에서 다운로드
#   https://universe.roboflow.com 접속, "fruits", "vegetables", "groceries" 등 검색
#   원하는 데이터셋에서 Download → Format: Folder → Show download code 복사
#   예시:
#
# !pip install roboflow
# from roboflow import Roboflow
# rf = Roboflow(api_key="YOUR_KEY")  # 무료 가입
# project = rf.workspace("xxx").project("yyy")
# dataset = project.version(1).download("folder")
# DATA_DIR = dataset.location
#
# 방법 B) 직접 만든 zip 업로드
#   from google.colab import files
#   files.upload()  # mydata.zip 업로드
#   !unzip -q mydata.zip -d /content/data
#   DATA_DIR = "/content/data"
#
# 폴더 구조 (어느 방법이든 결과는 이렇게):
#   /content/data/
#       train/
#           apple/   *.jpg
#           carrot/  *.jpg
#           egg/     *.jpg
#           ...
#       valid/
#           apple/  ...
#           ...

DATA_DIR = "/content/data"   # 실제 경로로 바꾸기
TRAIN_DIR = os.path.join(DATA_DIR, "train")
VAL_DIR = os.path.join(DATA_DIR, "valid")
IMG_SIZE = 224
BATCH = 32


# ============================================================
# 3. 데이터 로더
# ============================================================
train_aug = ImageDataGenerator(
    rescale=1.0 / 127.5,
    preprocessing_function=lambda x: x - 1.0,  # [-1, 1] 범위로
    rotation_range=20,
    width_shift_range=0.1,
    height_shift_range=0.1,
    horizontal_flip=True,
    zoom_range=0.15,
)
val_aug = ImageDataGenerator(
    rescale=1.0 / 127.5,
    preprocessing_function=lambda x: x - 1.0,
)

train_gen = train_aug.flow_from_directory(
    TRAIN_DIR, target_size=(IMG_SIZE, IMG_SIZE), batch_size=BATCH, class_mode="categorical"
)
val_gen = val_aug.flow_from_directory(
    VAL_DIR, target_size=(IMG_SIZE, IMG_SIZE), batch_size=BATCH, class_mode="categorical"
)

NUM_CLASSES = train_gen.num_classes
CLASS_NAMES = list(train_gen.class_indices.keys())
print("클래스 수:", NUM_CLASSES)
print("클래스 이름:", CLASS_NAMES)


# ============================================================
# 4. 모델 정의 (Transfer Learning - MobileNetV2)
# ============================================================
base = MobileNetV2(input_shape=(IMG_SIZE, IMG_SIZE, 3), include_top=False, weights="imagenet")
base.trainable = False  # 1차 학습은 backbone 고정

inputs = layers.Input(shape=(IMG_SIZE, IMG_SIZE, 3))
x = base(inputs, training=False)
x = layers.GlobalAveragePooling2D()(x)
x = layers.Dropout(0.2)(x)
outputs = layers.Dense(NUM_CLASSES, activation="softmax")(x)
model = Model(inputs, outputs)

model.compile(optimizer=tf.keras.optimizers.Adam(1e-3),
              loss="categorical_crossentropy",
              metrics=["accuracy"])
model.summary()


# ============================================================
# 5. 1차 학습 (head만)
# ============================================================
EPOCHS_HEAD = 8
model.fit(train_gen, validation_data=val_gen, epochs=EPOCHS_HEAD)


# ============================================================
# 6. 2차 학습 (fine-tuning)
# ============================================================
base.trainable = True
# 마지막 30개 레이어만 학습
for layer in base.layers[:-30]:
    layer.trainable = False

model.compile(optimizer=tf.keras.optimizers.Adam(1e-5),
              loss="categorical_crossentropy",
              metrics=["accuracy"])

EPOCHS_FT = 12
model.fit(train_gen, validation_data=val_gen, epochs=EPOCHS_FT)


# ============================================================
# 7. TFLite 변환 (양자화로 크기 축소)
# ============================================================
converter = tf.lite.TFLiteConverter.from_keras_model(model)
converter.optimizations = [tf.lite.Optimize.DEFAULT]
tflite_model = converter.convert()

with open("/content/mobilenet_v2.tflite", "wb") as f:
    f.write(tflite_model)

with open("/content/labels.txt", "w") as f:
    for name in CLASS_NAMES:
        f.write(name + "\n")

print("저장 완료:")
print("  /content/mobilenet_v2.tflite", os.path.getsize("/content/mobilenet_v2.tflite"), "bytes")
print("  /content/labels.txt")


# ============================================================
# 8. 다운로드 (Colab → 로컬)
# ============================================================
# from google.colab import files
# files.download("/content/mobilenet_v2.tflite")
# files.download("/content/labels.txt")
