import 'dart:io';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:provider/provider.dart';
import '../../../core/theme/app_theme.dart';
import '../../../data/models/ingredient.dart';
import '../../../data/repositories/ingredient_provider.dart';
import '../../../data/services/food_classifier.dart';
import '../../../data/services/quantity_estimator.dart';
import '../../refrigerator/widgets/add_ingredient_sheet.dart';

class CameraScreen extends StatefulWidget {
  const CameraScreen({super.key});

  @override
  State<CameraScreen> createState() => _CameraScreenState();
}

class _CameraScreenState extends State<CameraScreen> {
  final ImagePicker _picker = ImagePicker();
  final FoodClassifier _classifier = FoodClassifier();
  final QuantityEstimator _estimator = QuantityEstimator();
  File? _selectedImage;
  bool _isAnalyzing = false;
  List<FoodPrediction> _predictions = [];
  Map<String, double?> _quantities = {};

  @override
  void initState() {
    super.initState();
    _classifier.load();
    _estimator.load().catchError((_) {});
  }

  @override
  void dispose() {
    _estimator.close();
    _classifier.close();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(title: const Text('식재료 인식')),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            _ImagePreview(image: _selectedImage, isAnalyzing: _isAnalyzing),
            const SizedBox(height: 20),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: () => _pickImage(ImageSource.camera),
                    icon: const Icon(Icons.camera_alt),
                    label: const Text('카메라'),
                    style: OutlinedButton.styleFrom(
                      padding: const EdgeInsets.symmetric(vertical: 14),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: () => _pickImage(ImageSource.gallery),
                    icon: const Icon(Icons.photo_library),
                    label: const Text('갤러리'),
                    style: OutlinedButton.styleFrom(
                      padding: const EdgeInsets.symmetric(vertical: 14),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                    ),
                  ),
                ),
              ],
            ),
            if (_selectedImage != null) ...[
              const SizedBox(height: 12),
              ElevatedButton.icon(
                onPressed: _isAnalyzing ? null : _analyze,
                icon: _isAnalyzing
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                      )
                    : const Icon(Icons.search),
                label: Text(_isAnalyzing ? '분석 중...' : 'AI 분석 시작 (온디바이스)'),
                style: ElevatedButton.styleFrom(
                  padding: const EdgeInsets.symmetric(vertical: 14),
                ),
              ),
            ],
            if (_predictions.isNotEmpty) ...[
              const SizedBox(height: 24),
              Text('인식된 식재료', style: Theme.of(context).textTheme.titleLarge),
              const SizedBox(height: 4),
              Text('추가할 식재료를 선택하세요', style: Theme.of(context).textTheme.bodyMedium),
              const SizedBox(height: 12),
              ..._predictions.map((p) => _PredictionTile(
                    prediction: p,
                    quantity: _quantities[p.koreanName],
                    onAdd: () => _addToFridge(context, p),
                  )),
            ],
            const SizedBox(height: 24),
            _GuideCard(),
          ],
        ),
      ),
    );
  }

  Future<void> _pickImage(ImageSource source) async {
    final picked = await _picker.pickImage(source: source, imageQuality: 85);
    if (picked != null) {
      setState(() {
        _selectedImage = File(picked.path);
        _predictions = [];
      });
    }
  }

  Future<void> _analyze() async {
    if (_selectedImage == null) return;
    setState(() => _isAnalyzing = true);

    try {
      final results = await _classifier.classify(_selectedImage!);

      // 양추정 모델이 있으면 각 식재료 수량 추정
      final quantities = <String, double?>{};
      for (final p in results) {
        quantities[p.koreanName] = await _estimator.estimate(_selectedImage!);
      }

      setState(() {
        _predictions = results;
        _quantities = quantities;
      });

      if (results.isEmpty && mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('식재료를 인식하지 못했습니다. 다시 촬영해보세요.')),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('분석 오류: $e')),
        );
      }
    }

    if (mounted) setState(() => _isAnalyzing = false);
  }

  Future<void> _addToFridge(BuildContext context, FoodPrediction p) async {
    final qty = _quantities[p.koreanName];
    final provider = context.read<IngredientProvider>();
    final ingredient = Ingredient(
      name: p.koreanName,
      category: p.category,
      storageLocation: '냉장',
      quantity: qty ?? 1.0,
      unit: '개',
      expiryDate: DateTime.now().add(const Duration(days: 7)),
    );
    await provider.addIngredient(ingredient);
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('${p.koreanName} 냉장고에 추가됐습니다')),
      );
    }
  }
}

class _ImagePreview extends StatelessWidget {
  final File? image;
  final bool isAnalyzing;
  const _ImagePreview({required this.image, required this.isAnalyzing});

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 240,
      decoration: BoxDecoration(
        color: AppTheme.divider,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppTheme.divider),
      ),
      clipBehavior: Clip.antiAlias,
      child: image == null
          ? const Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(Icons.add_a_photo_outlined, size: 48, color: AppTheme.textSecondary),
                SizedBox(height: 12),
                Text('사진을 선택하세요', style: TextStyle(color: AppTheme.textSecondary)),
              ],
            )
          : Stack(
              fit: StackFit.expand,
              children: [
                Image.file(image!, fit: BoxFit.cover),
                if (isAnalyzing)
                  Container(
                    color: Colors.black45,
                    child: const Center(
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          CircularProgressIndicator(color: Colors.white),
                          SizedBox(height: 12),
                          Text(
                            '온디바이스 분석 중...',
                            style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold),
                          ),
                        ],
                      ),
                    ),
                  ),
              ],
            ),
    );
  }
}

class _PredictionTile extends StatelessWidget {
  final FoodPrediction prediction;
  final double? quantity;
  final VoidCallback onAdd;
  const _PredictionTile({required this.prediction, required this.onAdd, this.quantity});

  @override
  Widget build(BuildContext context) {
    final qtyText = quantity != null ? ' · 약 ${quantity!.toStringAsFixed(1)}개' : '';
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      child: ListTile(
        leading: CircleAvatar(
          backgroundColor: AppTheme.secondary.withOpacity(0.1),
          child: const Icon(Icons.eco, color: AppTheme.secondary),
        ),
        title: Text(prediction.koreanName, style: Theme.of(context).textTheme.titleMedium),
        subtitle: Text(
          '${prediction.category} · 신뢰도 ${(prediction.confidence * 100).toStringAsFixed(0)}%$qtyText',
        ),
        trailing: ElevatedButton(
          onPressed: onAdd,
          style: ElevatedButton.styleFrom(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
            minimumSize: Size.zero,
          ),
          child: const Text('추가'),
        ),
      ),
    );
  }
}

class _GuideCard extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.tips_and_updates, color: AppTheme.warning, size: 20),
                const SizedBox(width: 8),
                Text('촬영 팁', style: Theme.of(context).textTheme.titleMedium),
              ],
            ),
            const SizedBox(height: 12),
            ...[
              '식재료를 밝은 곳에서 촬영하세요',
              '한 번에 1개 식재료가 가장 정확합니다',
              '배경이 단순할수록 인식률이 높아집니다',
              '인터넷 없이 TFLite 온디바이스 AI로 동작합니다',
            ].map((tip) => Padding(
                  padding: const EdgeInsets.only(bottom: 6),
                  child: Row(
                    children: [
                      const Icon(Icons.check_circle_outline, size: 16, color: AppTheme.secondary),
                      const SizedBox(width: 8),
                      Expanded(child: Text(tip, style: Theme.of(context).textTheme.bodyMedium)),
                    ],
                  ),
                )),
          ],
        ),
      ),
    );
  }
}
