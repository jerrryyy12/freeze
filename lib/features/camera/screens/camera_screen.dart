import 'dart:io';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:google_mlkit_image_labeling/google_mlkit_image_labeling.dart';
import '../../../core/theme/app_theme.dart';
import '../../../data/models/ingredient.dart';
import '../../refrigerator/widgets/add_ingredient_sheet.dart';

const Map<String, _FoodInfo> _labelMap = {
  'apple': _FoodInfo('사과', '과일'),
  'orange': _FoodInfo('오렌지', '과일'),
  'banana': _FoodInfo('바나나', '과일'),
  'grape': _FoodInfo('포도', '과일'),
  'strawberry': _FoodInfo('딸기', '과일'),
  'watermelon': _FoodInfo('수박', '과일'),
  'peach': _FoodInfo('복숭아', '과일'),
  'pear': _FoodInfo('배', '과일'),
  'mango': _FoodInfo('망고', '과일'),
  'pineapple': _FoodInfo('파인애플', '과일'),
  'lemon': _FoodInfo('레몬', '과일'),
  'carrot': _FoodInfo('당근', '채소'),
  'broccoli': _FoodInfo('브로콜리', '채소'),
  'tomato': _FoodInfo('토마토', '채소'),
  'onion': _FoodInfo('양파', '채소'),
  'potato': _FoodInfo('감자', '채소'),
  'sweet potato': _FoodInfo('고구마', '채소'),
  'cucumber': _FoodInfo('오이', '채소'),
  'pepper': _FoodInfo('피망', '채소'),
  'cabbage': _FoodInfo('양배추', '채소'),
  'spinach': _FoodInfo('시금치', '채소'),
  'garlic': _FoodInfo('마늘', '채소'),
  'mushroom': _FoodInfo('버섯', '채소'),
  'corn': _FoodInfo('옥수수', '채소'),
  'eggplant': _FoodInfo('가지', '채소'),
  'zucchini': _FoodInfo('애호박', '채소'),
  'lettuce': _FoodInfo('상추', '채소'),
  'egg': _FoodInfo('계란', '유제품'),
  'milk': _FoodInfo('우유', '유제품'),
  'cheese': _FoodInfo('치즈', '유제품'),
  'butter': _FoodInfo('버터', '유제품'),
  'yogurt': _FoodInfo('요거트', '유제품'),
  'beef': _FoodInfo('소고기', '육류'),
  'pork': _FoodInfo('돼지고기', '육류'),
  'chicken': _FoodInfo('닭고기', '육류'),
  'fish': _FoodInfo('생선', '해산물'),
  'shrimp': _FoodInfo('새우', '해산물'),
  'squid': _FoodInfo('오징어', '해산물'),
  'crab': _FoodInfo('게', '해산물'),
  'rice': _FoodInfo('쌀', '곡류'),
  'bread': _FoodInfo('빵', '곡류'),
  'noodle': _FoodInfo('면', '곡류'),
  'tofu': _FoodInfo('두부', '기타'),
  'soy sauce': _FoodInfo('간장', '조미료'),
  'salt': _FoodInfo('소금', '조미료'),
  'sugar': _FoodInfo('설탕', '조미료'),
};

class _FoodInfo {
  final String koreanName;
  final String category;
  const _FoodInfo(this.koreanName, this.category);
}

class CameraScreen extends StatefulWidget {
  const CameraScreen({super.key});

  @override
  State<CameraScreen> createState() => _CameraScreenState();
}

class _CameraScreenState extends State<CameraScreen> {
  final ImagePicker _picker = ImagePicker();
  File? _selectedImage;
  bool _isAnalyzing = false;
  List<_DetectedItem> _detectedItems = [];

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
                    ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                    : const Icon(Icons.search),
                label: Text(_isAnalyzing ? '분석 중...' : 'AI 분석 시작'),
                style: ElevatedButton.styleFrom(
                  padding: const EdgeInsets.symmetric(vertical: 14),
                ),
              ),
            ],
            if (_detectedItems.isNotEmpty) ...[
              const SizedBox(height: 24),
              Text('인식된 식재료', style: Theme.of(context).textTheme.titleLarge),
              const SizedBox(height: 4),
              Text('추가할 식재료를 선택하세요', style: Theme.of(context).textTheme.bodyMedium),
              const SizedBox(height: 12),
              ..._detectedItems.map((item) => _DetectedItemTile(
                    item: item,
                    onAdd: () => _addToFridge(context, item),
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
        _detectedItems = [];
      });
    }
  }

  Future<void> _analyze() async {
    if (_selectedImage == null) return;
    setState(() => _isAnalyzing = true);

    try {
      final inputImage = InputImage.fromFile(_selectedImage!);
      final labeler = ImageLabeler(
        options: ImageLabelerOptions(confidenceThreshold: 0.5),
      );

      final labels = await labeler.processImage(inputImage);
      await labeler.close();

      final detected = <_DetectedItem>[];
      final seen = <String>{};

      for (final label in labels) {
        final key = label.label.toLowerCase();
        for (final entry in _labelMap.entries) {
          if (key.contains(entry.key) || entry.key.contains(key)) {
            if (!seen.contains(entry.value.koreanName)) {
              seen.add(entry.value.koreanName);
              detected.add(_DetectedItem(
                name: entry.value.koreanName,
                category: entry.value.category,
                confidence: label.confidence,
              ));
            }
            break;
          }
        }
      }

      setState(() => _detectedItems = detected);

      if (detected.isEmpty && mounted) {
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

    setState(() => _isAnalyzing = false);
  }

  void _addToFridge(BuildContext context, _DetectedItem item) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => AddIngredientSheet(
        ingredient: Ingredient(
          name: item.name,
          category: item.category,
          storageLocation: '냉장',
          quantity: 1,
          unit: '개',
          expiryDate: DateTime.now().add(const Duration(days: 7)),
        ),
      ),
    );
  }
}

class _DetectedItem {
  final String name;
  final double confidence;
  final String category;
  _DetectedItem({required this.name, required this.confidence, required this.category});
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
                          Text('AI 분석 중...', style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold)),
                        ],
                      ),
                    ),
                  ),
              ],
            ),
    );
  }
}

class _DetectedItemTile extends StatelessWidget {
  final _DetectedItem item;
  final VoidCallback onAdd;
  const _DetectedItemTile({required this.item, required this.onAdd});

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      child: ListTile(
        leading: CircleAvatar(
          backgroundColor: AppTheme.secondary.withOpacity(0.1),
          child: const Icon(Icons.eco, color: AppTheme.secondary),
        ),
        title: Text(item.name, style: Theme.of(context).textTheme.titleMedium),
        subtitle: Text('${item.category} · 신뢰도 ${(item.confidence * 100).toStringAsFixed(0)}%'),
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
              '한 번에 1~3개 식재료 촬영이 최적입니다',
              '배경이 단순할수록 인식률이 높아집니다',
              '인터넷 없이 온디바이스 AI로 동작합니다',
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
