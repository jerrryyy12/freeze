import 'package:sqflite/sqflite.dart';
import 'package:path/path.dart';
import '../models/ingredient.dart';
import '../../core/constants/app_constants.dart';

class DatabaseService {
  static final DatabaseService _instance = DatabaseService._internal();
  factory DatabaseService() => _instance;
  DatabaseService._internal();

  Database? _db;

  Future<Database> get database async {
    _db ??= await _initDatabase();
    return _db!;
  }

  Future<Database> _initDatabase() async {
    final dbPath = await getDatabasesPath();
    final path = join(dbPath, AppConstants.dbName);

    return openDatabase(
      path,
      version: AppConstants.dbVersion,
      onCreate: _onCreate,
    );
  }

  Future<void> _onCreate(Database db, int version) async {
    await db.execute('''
      CREATE TABLE ingredients (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL,
        category TEXT NOT NULL,
        storage_location TEXT NOT NULL,
        quantity REAL NOT NULL,
        unit TEXT NOT NULL,
        expiry_date TEXT NOT NULL,
        added_date TEXT NOT NULL,
        image_path TEXT,
        memo TEXT
      )
    ''');
  }

  // 식재료 추가
  Future<int> insertIngredient(Ingredient ingredient) async {
    final db = await database;
    return db.insert('ingredients', ingredient.toMap());
  }

  // 전체 식재료 조회
  Future<List<Ingredient>> getAllIngredients() async {
    final db = await database;
    final maps = await db.query('ingredients', orderBy: 'expiry_date ASC');
    return maps.map(Ingredient.fromMap).toList();
  }

  // 카테고리별 조회
  Future<List<Ingredient>> getIngredientsByCategory(String category) async {
    final db = await database;
    final maps = await db.query(
      'ingredients',
      where: 'category = ?',
      whereArgs: [category],
      orderBy: 'expiry_date ASC',
    );
    return maps.map(Ingredient.fromMap).toList();
  }

  // 보관 위치별 조회
  Future<List<Ingredient>> getIngredientsByStorage(String location) async {
    final db = await database;
    final maps = await db.query(
      'ingredients',
      where: 'storage_location = ?',
      whereArgs: [location],
      orderBy: 'expiry_date ASC',
    );
    return maps.map(Ingredient.fromMap).toList();
  }

  // 유통기한 임박 식재료 (n일 이내)
  Future<List<Ingredient>> getExpiringIngredients(int days) async {
    final db = await database;
    final now = DateTime.now();
    final limit = now.add(Duration(days: days));
    final maps = await db.query(
      'ingredients',
      where: 'expiry_date <= ?',
      whereArgs: [limit.toIso8601String().substring(0, 10)],
      orderBy: 'expiry_date ASC',
    );
    return maps.map(Ingredient.fromMap).toList();
  }

  // 식재료 수정
  Future<int> updateIngredient(Ingredient ingredient) async {
    final db = await database;
    return db.update(
      'ingredients',
      ingredient.toMap(),
      where: 'id = ?',
      whereArgs: [ingredient.id],
    );
  }

  // 식재료 삭제
  Future<int> deleteIngredient(int id) async {
    final db = await database;
    return db.delete('ingredients', where: 'id = ?', whereArgs: [id]);
  }

  // 이름으로 검색
  Future<List<Ingredient>> searchIngredients(String query) async {
    final db = await database;
    final maps = await db.query(
      'ingredients',
      where: 'name LIKE ?',
      whereArgs: ['%$query%'],
      orderBy: 'expiry_date ASC',
    );
    return maps.map(Ingredient.fromMap).toList();
  }

  // 통계: 카테고리별 개수
  Future<Map<String, int>> getCategoryStats() async {
    final db = await database;
    final result = await db.rawQuery(
      'SELECT category, COUNT(*) as count FROM ingredients GROUP BY category',
    );
    return {for (var row in result) row['category'] as String: row['count'] as int};
  }
}
