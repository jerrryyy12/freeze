import 'dart:convert';
import 'package:http/http.dart' as http;
import '../models/recipe.dart';
import '../../core/constants/app_constants.dart';

class RecipeService {
  static final RecipeService _instance = RecipeService._internal();
  factory RecipeService() => _instance;
  RecipeService._internal();

  Future<List<Recipe>> getRecipesByIngredients(List<String> ingredients) async {
    if (ingredients.isEmpty) return [];

    final ingredientStr = ingredients.join(',');
    final uri = Uri.parse(
      '${AppConstants.recipeBaseUrl}/recipes/findByIngredients'
      '?ingredients=$ingredientStr'
      '&number=20'
      '&ranking=2'
      '&ignorePantry=true'
      '&apiKey=${AppConstants.recipeApiKey}',
    );

    try {
      final response = await http.get(uri).timeout(const Duration(seconds: 10));
      if (response.statusCode == 200) {
        final List<dynamic> data = jsonDecode(response.body);
        return data.map((json) => Recipe.fromSpoonacular(json as Map<String, dynamic>)).toList();
      }
    } catch (_) {}

    return _getMockRecipes(ingredients);
  }

  /// 레시피 상세 조리법 조회 (Spoonacular recipe information)
  Future<Recipe> getRecipeDetail(Recipe recipe) async {
    // mock 레시피(id <= 3)는 내장 조리법 사용
    if (recipe.id <= 3) {
      return recipe.copyWith(instructions: _mockInstructions(recipe));
    }

    final uri = Uri.parse(
      '${AppConstants.recipeBaseUrl}/recipes/${recipe.id}/information'
      '?includeNutrition=false'
      '&apiKey=${AppConstants.recipeApiKey}',
    );

    try {
      final response = await http.get(uri).timeout(const Duration(seconds: 10));
      if (response.statusCode == 200) {
        final data = jsonDecode(response.body) as Map<String, dynamic>;
        final steps = <String>[];
        final analyzed = data['analyzedInstructions'] as List? ?? [];
        for (final block in analyzed) {
          for (final step in (block['steps'] as List? ?? [])) {
            final text = step['step'] as String?;
            if (text != null && text.trim().isNotEmpty) steps.add(text.trim());
          }
        }
        return recipe.copyWith(
          instructions: steps,
          summary: data['summary'] as String? ?? recipe.summary,
          readyInMinutes: data['readyInMinutes'] as int? ?? recipe.readyInMinutes,
          servings: data['servings'] as int? ?? recipe.servings,
        );
      }
    } catch (_) {}

    // 실패 시 mock 조리법
    return recipe.copyWith(instructions: _mockInstructions(recipe));
  }

  List<String> _mockInstructions(Recipe recipe) {
    final main = recipe.usedIngredients.isNotEmpty
        ? recipe.usedIngredients.join(', ')
        : '재료';
    return [
      '$main 을(를) 깨끗이 씻어 먹기 좋은 크기로 손질합니다.',
      '팬이나 냄비를 중불로 달군 뒤 손질한 재료를 넣습니다.',
      '소금, 후추 등으로 간을 맞추며 골고루 익혀줍니다.',
      '재료가 충분히 익으면 그릇에 담아 완성합니다.',
    ];
  }

  // API 키 없을 때 mock 데이터
  List<Recipe> _getMockRecipes(List<String> ingredients) {
    return [
      Recipe(
        id: 1,
        title: '${ingredients.isNotEmpty ? ingredients.first : "재료"} 볶음',
        imageUrl: null,
        readyInMinutes: 20,
        servings: 2,
        usedIngredients: ingredients.take(2).toList(),
        missedIngredients: ['소금', '후추'],
        matchScore: 0.8,
        summary: '간단하고 맛있는 볶음 요리입니다.',
      ),
      Recipe(
        id: 2,
        title: '냉장고 털기 국',
        imageUrl: null,
        readyInMinutes: 30,
        servings: 4,
        usedIngredients: ingredients.take(3).toList(),
        missedIngredients: ['육수'],
        matchScore: 0.7,
        summary: '남은 재료로 끓이는 구수한 국입니다.',
      ),
      Recipe(
        id: 3,
        title: '간단 샐러드',
        imageUrl: null,
        readyInMinutes: 10,
        servings: 2,
        usedIngredients: ingredients.take(2).toList(),
        missedIngredients: ['드레싱'],
        matchScore: 0.6,
        summary: '건강한 샐러드입니다.',
      ),
    ];
  }
}
