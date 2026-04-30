class AppConstants {
  static const String appName = 'Freeze';
  static const String dbName = 'freeze.db';
  static const int dbVersion = 1;

  // 외부 레시피 API (Spoonacular)
  static const String recipeBaseUrl = 'https://api.spoonacular.com';
  static const String recipeApiKey = 'YOUR_SPOONACULAR_API_KEY';

  // 유통기한 경고 기준 (일)
  static const int expiryWarningDays = 3;
  static const int expiryDangerDays = 1;

  // 식재료 카테고리
  static const List<String> categories = [
    '전체',
    '채소',
    '과일',
    '육류',
    '해산물',
    '유제품',
    '음료',
    '조미료',
    '기타',
  ];

  static const List<String> storageLocations = [
    '냉장',
    '냉동',
    '실온',
  ];
}
