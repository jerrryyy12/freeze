import 'package:flutter/material.dart';
import '../models/ingredient.dart';
import 'ingredient_repository.dart';

class IngredientProvider extends ChangeNotifier {
  final IngredientRepository _repo = IngredientRepository();

  List<Ingredient> _ingredients = [];
  String _selectedCategory = '전체';
  String _selectedStorage = '전체';
  String _searchQuery = '';
  bool _isLoading = false;

  List<Ingredient> get ingredients => _filteredIngredients();
  String get selectedCategory => _selectedCategory;
  String get selectedStorage => _selectedStorage;
  bool get isLoading => _isLoading;

  List<Ingredient> get expiringIngredients =>
      _ingredients.where((i) => i.daysLeft <= 3).toList()
        ..sort((a, b) => a.daysLeft.compareTo(b.daysLeft));

  List<String> get ingredientNames =>
      _ingredients.map((i) => i.name).toSet().toList();

  List<Ingredient> _filteredIngredients() {
    var list = _ingredients;

    if (_selectedCategory != '전체') {
      list = list.where((i) => i.category == _selectedCategory).toList();
    }
    if (_selectedStorage != '전체') {
      list = list.where((i) => i.storageLocation == _selectedStorage).toList();
    }
    if (_searchQuery.isNotEmpty) {
      list = list
          .where((i) => i.name.contains(_searchQuery))
          .toList();
    }
    return list;
  }

  Future<void> loadIngredients() async {
    _isLoading = true;
    notifyListeners();
    _ingredients = await _repo.getAll();
    _isLoading = false;
    notifyListeners();
  }

  Future<void> addIngredient(Ingredient ingredient) async {
    await _repo.add(ingredient);
    await loadIngredients();
  }

  Future<void> updateIngredient(Ingredient ingredient) async {
    await _repo.update(ingredient);
    await loadIngredients();
  }

  Future<void> deleteIngredient(int id) async {
    await _repo.delete(id);
    await loadIngredients();
  }

  void setCategory(String category) {
    _selectedCategory = category;
    notifyListeners();
  }

  void setStorage(String storage) {
    _selectedStorage = storage;
    notifyListeners();
  }

  void setSearchQuery(String query) {
    _searchQuery = query;
    notifyListeners();
  }

  Future<Map<String, int>> getCategoryStats() => _repo.getCategoryStats();
}
