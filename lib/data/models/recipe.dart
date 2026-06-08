class RecipeStep {
  final String text;
  final String? imageUrl;
  RecipeStep({required this.text, this.imageUrl});
}

class Recipe {
  final int id;
  final String title;
  final String? imageUrl;
  final int readyInMinutes;
  final int servings;
  final List<String> usedIngredients;
  final List<String> missedIngredients;
  final double matchScore;
  final String? sourceUrl;
  final String? summary;
  final List<RecipeStep> steps;
  final String? ingredientsText; // 재료 원문
  final String? category; // 요리 종류 (반찬, 국 등)
  final String? cookMethod; // 조리 방법 (굽기, 끓이기 등)

  Recipe({
    required this.id,
    required this.title,
    this.imageUrl,
    required this.readyInMinutes,
    required this.servings,
    required this.usedIngredients,
    required this.missedIngredients,
    required this.matchScore,
    this.sourceUrl,
    this.summary,
    this.steps = const [],
    this.ingredientsText,
    this.category,
    this.cookMethod,
  });

  /// 식품안전처 COOKRCP01 응답 매핑
  factory Recipe.fromFoodSafety(
    Map<String, dynamic> map, {
    required List<String> userIngredients,
  }) {
    final parts = (map['RCP_PARTS_DTLS'] as String? ?? '').trim();

    // 조리 단계 (MANUAL01~MANUAL20 + MANUAL_IMG01~20)
    final steps = <RecipeStep>[];
    for (var i = 1; i <= 20; i++) {
      final key = 'MANUAL${i.toString().padLeft(2, '0')}';
      final imgKey = 'MANUAL_IMG${i.toString().padLeft(2, '0')}';
      final raw = (map[key] as String? ?? '').trim();
      if (raw.isEmpty) continue;
      // 앞쪽 "1. " 같은 번호 제거
      final text = raw.replaceFirst(RegExp(r'^\s*\d+\.?\s*'), '').trim();
      final img = (map[imgKey] as String? ?? '').trim();
      steps.add(RecipeStep(text: text, imageUrl: img.isEmpty ? null : img));
    }

    // 보유/필요 재료 매칭 (재료 원문에 사용자 식재료가 포함되는지)
    final used = <String>[];
    for (final ing in userIngredients) {
      if (parts.contains(ing)) used.add(ing);
    }
    final score = userIngredients.isEmpty
        ? 0.0
        : used.length / userIngredients.length;

    final mainImg = (map['ATT_FILE_NO_MK'] as String? ?? '').trim();
    final subImg = (map['ATT_FILE_NO_MAIN'] as String? ?? '').trim();
    final image = mainImg.isNotEmpty ? mainImg : (subImg.isNotEmpty ? subImg : null);

    return Recipe(
      id: int.tryParse(map['RCP_SEQ']?.toString() ?? '') ?? map.hashCode,
      title: (map['RCP_NM'] as String? ?? '이름 없음').trim(),
      imageUrl: image,
      readyInMinutes: 30,
      servings: 2,
      usedIngredients: used,
      missedIngredients: const [],
      matchScore: score,
      steps: steps,
      ingredientsText: parts.isEmpty ? null : parts,
      category: (map['RCP_PAT2'] as String?)?.trim(),
      cookMethod: (map['RCP_WAY2'] as String?)?.trim(),
      summary: null,
    );
  }
}
