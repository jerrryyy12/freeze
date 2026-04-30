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
  static const String _modelPath = 'assets/ml/mobilenet_v2.tflite';
  static const String _labelsPath = 'assets/ml/labels.txt';
  static const int _inputSize = 224;

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

  Future<List<FoodPrediction>> classify(File imageFile, {int topK = 5}) async {
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
    final seenKorean = <String>{};

    for (final idx in ranked) {
      if (predictions.length >= topK) break;
      final labelIndex = _labels.length == outputSize ? idx : idx - 1;
      if (labelIndex < 0 || labelIndex >= _labels.length) continue;

      final rawLabel = _labels[labelIndex].toLowerCase();
      final mapped = _matchKorean(rawLabel);
      if (mapped == null) continue;
      if (seenKorean.contains(mapped.$1)) continue;
      seenKorean.add(mapped.$1);

      predictions.add(FoodPrediction(
        label: rawLabel,
        koreanName: mapped.$1,
        category: mapped.$2,
        confidence: scores[idx].toDouble(),
      ));
    }

    return predictions;
  }

  void close() {
    _interpreter?.close();
    _interpreter = null;
  }

  (String, String)? _matchKorean(String rawLabel) {
    for (final entry in _labelToKorean.entries) {
      if (rawLabel.contains(entry.key)) {
        return (entry.value.$1, entry.value.$2);
      }
    }
    return null;
  }
}

const Map<String, (String, String)> _labelToKorean = {
  // 과일
  'granny smith': ('사과', '과일'),
  'apple': ('사과', '과일'),
  'orange': ('오렌지', '과일'),
  'lemon': ('레몬', '과일'),
  'banana': ('바나나', '과일'),
  'pineapple': ('파인애플', '과일'),
  'strawberry': ('딸기', '과일'),
  'pomegranate': ('석류', '과일'),
  'fig': ('무화과', '과일'),
  'pear': ('배', '과일'),
  'jackfruit': ('잭프루트', '과일'),
  'custard apple': ('커스타드애플', '과일'),
  // ImageNet에는 복숭아가 없어 색·형태가 비슷한 후보를 매핑
  'peach': ('복숭아', '과일'),
  'apricot': ('살구', '과일'),
  'nectarine': ('천도복숭아', '과일'),
  // 채소
  'broccoli': ('브로콜리', '채소'),
  'cauliflower': ('콜리플라워', '채소'),
  'cucumber': ('오이', '채소'),
  'zucchini': ('애호박', '채소'),
  'bell pepper': ('피망', '채소'),
  'cardoon': ('아티초크', '채소'),
  'artichoke': ('아티초크', '채소'),
  'mushroom': ('버섯', '채소'),
  'corn': ('옥수수', '채소'),
  'acorn squash': ('단호박', '채소'),
  'butternut squash': ('단호박', '채소'),
  'spaghetti squash': ('호박', '채소'),
  'head cabbage': ('양배추', '채소'),
  'cabbage': ('양배추', '채소'),
  // 곡류/가공
  'french loaf': ('빵', '곡류'),
  'bagel': ('베이글', '곡류'),
  'pretzel': ('프레첼', '곡류'),
  // 단백질·해산물
  'rotisserie': ('닭고기', '육류'),
};
