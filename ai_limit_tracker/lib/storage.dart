import 'package:flutter/services.dart';

import 'models.dart';

/// 안드로이드 네이티브(MainActivity.kt)와의 연결.
///
/// 상태 JSON 을 SharedPreferences 에 저장하면 네이티브가
/// 홈 화면 위젯을 갱신하고, 다음 리셋 시각에 알림 알람을 다시 건다.
class Storage {
  static const _channel = MethodChannel('ai_limit_tracker/native');

  /// 위젯 미리보기·테스트 등 네이티브가 없을 때를 위한 메모리 사본.
  static String? _fallback;

  static Future<TrackerState> load() async {
    try {
      final raw = await _channel.invokeMethod<String>('load');
      return TrackerState.decode(raw);
    } on MissingPluginException {
      return TrackerState.decode(_fallback);
    }
  }

  static Future<void> save(TrackerState state) async {
    final raw = state.encode();
    try {
      await _channel.invokeMethod<void>('save', raw);
    } on MissingPluginException {
      _fallback = raw;
    }
  }

  /// 안드로이드 13+ 알림 권한 요청. 이미 허용됐거나 불필요하면 true.
  static Future<bool> requestNotificationPermission() async {
    try {
      return await _channel.invokeMethod<bool>(
            'requestNotificationPermission',
          ) ??
          true;
    } on MissingPluginException {
      return true;
    }
  }

  /// 런처에 위젯 추가 요청(지원하는 런처에서만). 요청창이 떴으면 true.
  static Future<bool> requestPinWidget() async {
    try {
      return await _channel.invokeMethod<bool>('requestPinWidget') ?? false;
    } on MissingPluginException {
      return false;
    }
  }
}
