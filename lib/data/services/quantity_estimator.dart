import 'dart:io';
import 'package:flutter/services.dart';
import 'package:image/image.dart' as img;
import 'package:tflite_flutter/tflite_flutter.dart';

class QuantityEstimator {
  static const String _modelPath = 'ml/quantity_model.tflite';
  static const int _inputSize = 224;

  Interpreter? _interpreter;

  Future<bool> get isAvailable async {
    try {
      await load();
      return true;
    } catch (_) {
      return false;
    }
  }

  Future<void> load() async {
    if (_interpreter != null) return;
    _interpreter = await Interpreter.fromAsset(_modelPath);
  }

  /// 이미지에서 식재료 개수 추정. 모델 없으면 null 반환.
  Future<double?> estimate(File imageFile) async {
    try {
      await load();
    } catch (_) {
      return null;
    }

    final raw = await imageFile.readAsBytes();
    final decoded = img.decodeImage(raw);
    if (decoded == null) return null;

    final resized = img.copyResize(decoded, width: _inputSize, height: _inputSize);

    final input = List.generate(
      1,
      (_) => List.generate(
        _inputSize,
        (y) => List.generate(_inputSize, (x) {
          final pixel = resized.getPixel(x, y);
          return [
            (pixel.r - 127.5) / 127.5,
            (pixel.g - 127.5) / 127.5,
            (pixel.b - 127.5) / 127.5,
          ];
        }),
      ),
    );

    final output = List.generate(1, (_) => List.filled(1, 0.0));
    _interpreter!.run(input, output);

    final raw_count = output[0][0];
    return (raw_count as double).clamp(0.5, 99.0);
  }

  void close() {
    _interpreter?.close();
    _interpreter = null;
  }
}
