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
