#!/bin/bash
# MobileNetV2 ImageNet TFLite 모델 다운로드
# 약 14MB, 1000개 클래스 (과일/채소 다수 포함)

set -e

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ML_DIR="$ROOT/assets/ml"
mkdir -p "$ML_DIR"

echo "==> Downloading MobileNetV2 model..."
curl -L -o /tmp/mobilenet.tgz \
  "https://storage.googleapis.com/download.tensorflow.org/models/tflite_11_05_08/mobilenet_v2_1.0_224.tgz"

echo "==> Extracting..."
tar -xzf /tmp/mobilenet.tgz -C /tmp/
cp /tmp/mobilenet_v2_1.0_224.tflite "$ML_DIR/mobilenet_v2.tflite"

echo "==> Downloading ImageNet labels..."
curl -L -o "$ML_DIR/labels.txt" \
  "https://storage.googleapis.com/download.tensorflow.org/data/ImageNetLabels.txt"

echo "==> Done."
echo "    $ML_DIR/mobilenet_v2.tflite"
echo "    $ML_DIR/labels.txt"
