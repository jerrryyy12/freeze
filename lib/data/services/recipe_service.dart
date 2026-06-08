import 'dart:convert';
import 'package:http/http.dart' as http;
import '../models/recipe.dart';
import '../../core/constants/app_constants.dart';

class RecipeService {
  static final RecipeService _instance = RecipeService._internal();
  factory RecipeService() => _instance;
  RecipeService._internal();

  /// 보유 식재료 기반 레시피 검색 (식품안전처 COOKRCP01)
  /// 재료별로 조회 후 중복 제거 + 매칭도 정렬
  Future<List<Recipe>> getRecipesByIngredients(List<String> ingredients) async {
    if (ingredients.isEmpty) return [];

    final byId = <int, Recipe>{};

    // 식재료당 한 번씩 RCP_PARTS_DTLS 필터로 조회 (최대 5개까지만)
    for (final ing in ingredients.take(5)) {
      final recipes = await _fetch(partsKeyword: ing, userIngredients: ingredients);
      for (final r in recipes) {
        // 더 높은 매칭도로 갱신
        final existing = byId[r.id];
        if (existing == null || r.matchScore > existing.matchScore) {
          byId[r.id] = r;
        }
      }
    }

    final result = byId.values.toList()
      ..sort((a, b) => b.matchScore.compareTo(a.matchScore));

    if (result.isEmpty) return _getMockRecipes(ingredients);
    return result;
  }

  /// 메뉴명 키워드로 레시피 검색
  Future<List<Recipe>> searchByName(String query) async {
    if (query.trim().isEmpty) return [];
    return _fetch(nameKeyword: query.trim(), userIngredients: const []);
  }

  Future<List<Recipe>> _fetch({
    String? partsKeyword,
    String? nameKeyword,
    required List<String> userIngredients,
    int start = 1,
    int end = 30,
  }) async {
    // URL 구성: /api/{KEY}/COOKRCP01/json/{start}/{end}/{필터}
    final buffer = StringBuffer()
      ..write(AppConstants.recipeApiBase)
      ..write('/${AppConstants.recipeApiKey}')
      ..write('/${AppConstants.recipeServiceId}')
      ..write('/json/$start/$end');

    if (nameKeyword != null) {
      buffer.write('/RCP_NM=${Uri.encodeComponent(nameKeyword)}');
    } else if (partsKeyword != null) {
      buffer.write('/RCP_PARTS_DTLS=${Uri.encodeComponent(partsKeyword)}');
    }

    final uri = Uri.parse(buffer.toString());

    try {
      final response = await http.get(uri).timeout(const Duration(seconds: 12));
      if (response.statusCode != 200) return [];

      final decoded = jsonDecode(utf8.decode(response.bodyBytes));
      final service = decoded[AppConstants.recipeServiceId];
      if (service == null) return [];

      // RESULT.CODE 확인 (INFO-000 성공)
      final rows = service['row'] as List?;
      if (rows == null) return [];

      return rows
          .map((row) => Recipe.fromFoodSafety(
                row as Map<String, dynamic>,
                userIngredients: userIngredients,
              ))
          .toList();
    } catch (_) {
      return [];
    }
  }

  /// API 실패 시 mock 데이터
  List<Recipe> _getMockRecipes(List<String> ingredients) {
    final main = ingredients.isNotEmpty ? ingredients.first : '재료';
    return [
      Recipe(
        id: 1,
        title: '$main 볶음',
        readyInMinutes: 20,
        servings: 2,
        usedIngredients: ingredients.take(2).toList(),
        missedIngredients: const ['소금', '후추'],
        matchScore: 0.8,
        ingredientsText: '${ingredients.take(3).join(', ')}, 소금, 후추, 식용유',
        steps: [
          RecipeStep(text: '$main 을(를) 깨끗이 씻어 먹기 좋은 크기로 손질합니다.'),
          RecipeStep(text: '팬에 식용유를 두르고 중불로 달굽니다.'),
          RecipeStep(text: '손질한 재료를 넣고 소금, 후추로 간하며 볶습니다.'),
          RecipeStep(text: '재료가 충분히 익으면 그릇에 담아 완성합니다.'),
        ],
      ),
    ];
  }
}
