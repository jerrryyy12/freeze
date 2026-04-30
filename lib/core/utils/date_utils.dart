import 'package:intl/intl.dart';
import '../constants/app_constants.dart';

class AppDateUtils {
  static final DateFormat _displayFormat = DateFormat('yyyy.MM.dd');
  static final DateFormat _dbFormat = DateFormat('yyyy-MM-dd');

  static String toDisplay(DateTime date) => _displayFormat.format(date);
  static String toDb(DateTime date) => _dbFormat.format(date);

  static DateTime fromDb(String dateStr) => DateTime.parse(dateStr);

  static int daysUntilExpiry(DateTime expiryDate) {
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);
    final expiry = DateTime(expiryDate.year, expiryDate.month, expiryDate.day);
    return expiry.difference(today).inDays;
  }

  static ExpiryStatus getExpiryStatus(DateTime expiryDate) {
    final days = daysUntilExpiry(expiryDate);
    if (days < 0) return ExpiryStatus.expired;
    if (days <= AppConstants.expiryDangerDays) return ExpiryStatus.danger;
    if (days <= AppConstants.expiryWarningDays) return ExpiryStatus.warning;
    return ExpiryStatus.good;
  }

  static String getExpiryLabel(DateTime expiryDate) {
    final days = daysUntilExpiry(expiryDate);
    if (days < 0) return '${days.abs()}일 지남';
    if (days == 0) return '오늘 만료';
    if (days == 1) return '내일 만료';
    return 'D-$days';
  }
}

enum ExpiryStatus { good, warning, danger, expired }
