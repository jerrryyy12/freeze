class AppConstants {
  static const String appName = 'Freeze';
  static const String dbName = 'freeze.db';
  static const int dbVersion = 1;

  // 식품안전처(식품안전나라) 조리식품 레시피 API
  // 키 발급: https://www.foodsafetykorea.go.kr/api/openApiInfo.do
  // 'sample' 키는 테스트용으로 일부 데이터만 반환됩니다.
  static const String recipeApiBase = 'http://openapi.foodsafetykorea.go.kr/api';
  static const String recipeApiKey = 'sample';
  static const String recipeServiceId = 'COOKRCP01';

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
