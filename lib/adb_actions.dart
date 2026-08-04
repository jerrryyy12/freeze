import 'dart:async';
import 'dart:io';

/// 육안 검수를 돕는 adb 보조 동작 모음.
///
/// 폰에 앱을 설치하지 않으므로, PC의 adb 로 폰의 내장 기능을 호출해
/// 검수원이 실물을 확인할 수 있게 돕습니다.
/// - 삼성 하드웨어 테스트(*#0*#): 색상화면(액정)·수화부·터치 등 내장 검사
/// - 스피커 테스트(*#0289#): 삼성 멜로디 테스트로 스피커 소리 재생
/// - 진동: 진동 모터 동작 확인
/// - S펜 지원 여부: 디지타이저(S펜) 하드웨어 기능 존재 확인
class AdbActions {
  AdbActions({this.adbPath = 'adb'});
  final String adbPath;

  /// 삼성 하드웨어 테스트 메뉴(*#0*#) 열기.
  ///
  /// SECRET_CODE 브로드캐스트로 삼성 내장 종합 테스트를 띄웁니다.
  /// 액정 단색화면(빨강·초록·파랑 등), 수화부, 터치 등을 확인할 수 있습니다.
  Future<AdbActionResult> samsungHardwareTest(String serial) => _secretCode(
        serial,
        '0',
        '삼성 하드웨어 테스트를 폰 화면에 띄웠습니다.',
      );

  /// 스피커 소리 재생 — 미디어 볼륨을 최대로 올리고 기기 내장 벨소리를 재생.
  ///
  /// 삼성 SECRET_CODE(*#0289# 등)는 최신 One UI 에서 막혀서, 대신 기기에 이미
  /// 들어있는 벨소리(.ogg) 파일을 미디어 인텐트로 재생합니다. (앱 설치·에셋 불필요)
  /// 삼성 기기는 기본 음악 플레이어가 이 인텐트를 받아 자동 재생합니다.
  Future<AdbActionResult> speakerTest(String serial) async {
    // 1) 미디어 볼륨을 최대로 (best-effort — 명령 없으면 무시)
    await _run(serial, ['shell', 'media', 'volume', '--stream', '3', '--set', '15']);
    await _run(serial, ['shell', 'cmd', 'media_session', 'volume', '--stream', '3', '--set', '15']);

    // 2) 기기에 있는 벨소리/알림음 파일 하나 찾기
    String? sound;
    for (final dir in const [
      '/system/media/audio/ringtones',
      '/product/media/audio/ringtones',
      '/system/media/audio/notifications',
      '/system/media/audio/alarms',
    ]) {
      final ls = await _run(serial, ['shell', 'ls', dir]);
      final matches = ls
          .split('\n')
          .map((e) => e.trim())
          .where((e) =>
              e.endsWith('.ogg') || e.endsWith('.mp3') || e.endsWith('.wav'))
          .toList();
      if (matches.isNotEmpty) {
        sound = '$dir/${matches.first}';
        break;
      }
    }
    if (sound == null) {
      return AdbActionResult(
        ok: false,
        message: '재생할 소리 파일을 못 찾았습니다. 삼성 테스트(*#0*#) 메뉴의 스피커 항목을 이용하세요.',
      );
    }

    // 3) 미디어 뷰어로 재생 (삼성 뮤직 등이 자동 재생)
    final ext = sound.split('.').last.toLowerCase();
    final mime = ext == 'mp3'
        ? 'audio/mpeg'
        : ext == 'wav'
            ? 'audio/wav'
            : 'audio/ogg';
    final r = await _run(serial, [
      'shell', 'am', 'start',
      '-a', 'android.intent.action.VIEW',
      '-d', 'file://$sound',
      '-t', mime,
    ]);
    final ok = !_failed(r);
    return AdbActionResult(
      ok: ok,
      message: ok
          ? '스피커로 소리를 재생합니다. (안 들리면 폰 화면의 재생 버튼을 눌러주세요)'
          : '재생 실패: ${r.trim().isEmpty ? "미디어 앱을 열지 못함" : r.trim()}',
    );
  }

  Future<AdbActionResult> _secretCode(
      String serial, String code, String okMsg) async {
    final r = await _run(serial, [
      'shell', 'am', 'broadcast',
      '-a', 'android.provider.Telephony.SECRET_CODE',
      '-d', 'android_secret_code://$code',
    ]);
    // 브로드캐스트가 정상 전달되면 result=0 (Broadcast completed) 이 찍힘
    final ok = r.contains('Broadcast completed') || r.contains('result=0');
    return AdbActionResult(
      ok: ok,
      message: ok
          ? okMsg
          : '실행하지 못했습니다. (삼성 기기가 아니거나 차단됨 — 폰에서 *#$code# 직접 입력)',
    );
  }

  /// 진동 — 진동 모터 동작 확인.
  ///
  /// 최신 안드로이드(vibrator_manager) → 구형(vibrator) 순으로 시도.
  /// -f 는 무음/DND 상태여도 강제로 울리게 함.
  Future<AdbActionResult> vibrate(String serial, {int ms = 1000}) async {
    // 1) Android 12+ : cmd vibrator_manager synced -f oneshot <ms>
    var out = await _run(serial,
        ['shell', 'cmd', 'vibrator_manager', 'synced', '-f', 'oneshot', '$ms']);
    if (_failed(out)) {
      // 2) 구형 : cmd vibrator vibrate -f <ms>
      out = await _run(serial, ['shell', 'cmd', 'vibrator', 'vibrate', '-f', '$ms']);
    }
    final ok = !_failed(out);
    return AdbActionResult(
      ok: ok,
      message: ok
          ? '진동을 울렸습니다. (모터 동작 확인)'
          : '진동 실패: ${out.trim().isEmpty ? "명령이 차단되었습니다" : out.trim()}',
    );
  }

  /// 명령 출력에 오류 흔적이 있으면 실패로 간주. (성공 시 보통 출력이 없음)
  bool _failed(String out) {
    final o = out.toLowerCase();
    return o.contains('error') ||
        o.contains('exception') ||
        o.contains('unknown command') ||
        o.contains('usage:') ||
        o.contains('not found');
  }

  /// S펜(디지타이저) 지원 여부 — 하드웨어 기능 목록에서 확인.
  ///
  /// 삼성 S펜 기기는 pm 기능 목록에 'spen' 관련 feature 를 가집니다
  /// (예: com.samsung.android.feature.SPEN_USP).
  Future<bool> supportsSpen(String serial) async {
    final features = await _run(serial, ['shell', 'pm', 'list', 'features']);
    return features.toLowerCase().contains('spen');
  }

  Future<String> _run(String serial, List<String> args) async {
    try {
      final result = await Process.run(adbPath, ['-s', serial, ...args]);
      return '${result.stdout}${result.stderr}';
    } catch (e) {
      return '';
    }
  }
}

class AdbActionResult {
  AdbActionResult({required this.ok, required this.message});
  final bool ok;
  final String message;
}
