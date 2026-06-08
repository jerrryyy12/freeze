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
  final List<String> instructions;

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
    this.instructions = const [],
  });

  Recipe copyWith({
    List<String>? instructions,
    String? summary,
    int? readyInMinutes,
    int? servings,
  }) {
    return Recipe(
      id: id,
      title: title,
      imageUrl: imageUrl,
      readyInMinutes: readyInMinutes ?? this.readyInMinutes,
      servings: servings ?? this.servings,
      usedIngredients: usedIngredients,
      missedIngredients: missedIngredients,
      matchScore: matchScore,
      sourceUrl: sourceUrl,
      summary: summary ?? this.summary,
      instructions: instructions ?? this.instructions,
    );
  }

  factory Recipe.fromSpoonacular(Map<String, dynamic> map) {
    final used = (map['usedIngredients'] as List? ?? [])
        .map((i) => i['name'] as String)
        .toList();
    final missed = (map['missedIngredients'] as List? ?? [])
        .map((i) => i['name'] as String)
        .toList();
    final total = used.length + missed.length;
    final score = total > 0 ? used.length / total : 0.0;

    return Recipe(
      id: map['id'] as int,
      title: map['title'] as String,
      imageUrl: map['image'] as String?,
      readyInMinutes: map['readyInMinutes'] as int? ?? 30,
      servings: map['servings'] as int? ?? 2,
      usedIngredients: used,
      missedIngredients: missed,
      matchScore: score,
      sourceUrl: map['sourceUrl'] as String?,
      summary: map['summary'] as String?,
    );
  }
}
