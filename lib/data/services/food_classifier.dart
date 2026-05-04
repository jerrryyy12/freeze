import 'dart:io';
import 'package:flutter/services.dart';
import 'package:image/image.dart' as img;
import 'package:tflite_flutter/tflite_flutter.dart';

class FoodPrediction {
  final String label;
  final String koreanName;
  final String category;
  final double confidence;

  FoodPrediction({
    required this.label,
    required this.koreanName,
    required this.category,
    required this.confidence,
  });
}

class FoodClassifier {
  // tflite_flutter는 fromAsset에서 'assets/' 접두어를 자동으로 붙여 'ml/...'만 전달.
  static const String _modelPath = 'ml/mobilenet_v2.tflite';
  static const String _labelsPath = 'assets/ml/labels.txt';
  static const int _inputSize = 224;
  static const double _confidenceThreshold = 0.15;

  Interpreter? _interpreter;
  List<String> _labels = [];

  Future<void> load() async {
    if (_interpreter != null) return;
    _interpreter = await Interpreter.fromAsset(_modelPath);
    final raw = await rootBundle.loadString(_labelsPath);
    _labels = raw
        .split('\n')
        .map((e) => e.trim())
        .where((e) => e.isNotEmpty)
        .toList();
  }

  Future<List<FoodPrediction>> classify(File imageFile, {int topK = 3}) async {
    await load();
    final interpreter = _interpreter!;

    final raw = await imageFile.readAsBytes();
    final decoded = img.decodeImage(raw);
    if (decoded == null) return [];

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

    final outputTensor = interpreter.getOutputTensor(0);
    final outputSize = outputTensor.shape.last;
    final output = List.generate(1, (_) => List.filled(outputSize, 0.0));

    interpreter.run(input, output);

    final scores = output[0];
    final ranked = List<int>.generate(scores.length, (i) => i)
      ..sort((a, b) => scores[b].compareTo(scores[a]));

    final predictions = <FoodPrediction>[];
    for (final idx in ranked) {
      if (predictions.length >= topK) break;
      if (idx >= _labels.length) continue;
      final score = scores[idx].toDouble();
      if (score < _confidenceThreshold && predictions.isNotEmpty) break;

      final korean = _labels[idx];
      predictions.add(FoodPrediction(
        label: korean,
        koreanName: korean,
        category: _categoryOf(korean),
        confidence: score,
      ));
    }

    return predictions;
  }

  void close() {
    _interpreter?.close();
    _interpreter = null;
  }
}

String _categoryOf(String koreanName) {
  if (_fruits.contains(koreanName)) return '과일';
  if (_vegetables.contains(koreanName)) return '채소';
  return '기타';
}

const Set<String> _fruits = {
  '사과', '바나나', '오렌지', '딸기', '포도', '수박',
  '배', '복숭아', '망고', '파인애플', '레몬', '키위',
};

const Set<String> _vegetables = {
  '당근', '브로콜리', '토마토', '오이', '감자', '양파',
  '피망', '양배추', '시금치', '옥수수', '버섯', '생강',
  '마늘', '가지', '애호박', '상추',
};
