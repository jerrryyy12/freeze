import '../models/ingredient.dart';
import '../services/database_service.dart';

class IngredientRepository {
  final DatabaseService _db = DatabaseService();

  Future<int> add(Ingredient ingredient) => _db.insertIngredient(ingredient);
  Future<List<Ingredient>> getAll() => _db.getAllIngredients();
  Future<List<Ingredient>> getByCategory(String category) => _db.getIngredientsByCategory(category);
  Future<List<Ingredient>> getByStorage(String location) => _db.getIngredientsByStorage(location);
  Future<List<Ingredient>> getExpiring(int days) => _db.getExpiringIngredients(days);
  Future<int> update(Ingredient ingredient) => _db.updateIngredient(ingredient);
  Future<int> delete(int id) => _db.deleteIngredient(id);
  Future<List<Ingredient>> search(String query) => _db.searchIngredients(query);
  Future<Map<String, int>> getCategoryStats() => _db.getCategoryStats();
}
