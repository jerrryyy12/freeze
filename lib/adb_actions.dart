import 'dart:async';
import 'dart:io';

/// 육안 검수를 돕는 adb 보조 동작 모음.
///
/// 최신 삼성(One UI)은 adb 의 SECRET_CODE(*#0*#) 브로드캐스트를 막아서
/// 내장 하드웨어 테스트를 adb 로 띄울 수 없습니다. 그래서 액정 색상화면·
/// 스피커 사이렌·터치 검사는 '폰 브라우저로 검사 페이지를 여는' 방식으로 처리합니다.
/// - 검사 페이지 열기: am start 로 폰 기본 브라우저에 검사 페이지(URL) 표시
/// - 진동: cmd vibrator (기종 무관, adb 로 직접 동작)
/// - S펜 지원 여부: 디지타이저(S펜) 하드웨어 기능 존재 확인
class AdbActions {
  AdbActions({this.adbPath = 'adb'});
  final String adbPath;

  /// 폰 브라우저로 검사 페이지 열기 (액정 색상·스피커 소리·터치 검사).
  ///
  /// PC 로컬 서버(InspectServer)가 [port] 에 검사 페이지를 제공하고 있고,
  /// adb reverse 로 폰의 localhost:port 를 그 서버로 터널링한 뒤, 폰 브라우저를
  /// http://localhost:port 로 엽니다. 인터넷·와이파이·GitHub Pages 없이 USB 만으로
  /// 동작하며, localhost 라 웹오디오(사이렌)도 정상 재생됩니다.
  Future<AdbActionResult> openLocalInspector(String serial, int port) async {
    // 1) 폰 localhost:port → PC localhost:port (USB 터널)
    final rev = await _run(serial, ['reverse', 'tcp:$port', 'tcp:$port']);
    if (_failed(rev)) {
      return AdbActionResult(
        ok: false,
        message: 'USB 터널 연결 실패: ${rev.trim()}',
      );
    }

    // 2) 스피커 사이렌이 크게 들리도록 미디어 볼륨 최대 (best-effort)
    await _run(
        serial, ['shell', 'media', 'volume', '--stream', '3', '--set', '15']);

    // 3) 폰 기본 브라우저로 검사 페이지 열기
    final r = await _run(serial, [
      'shell', 'am', 'start',
      '-a', 'android.intent.action.VIEW',
      '-d', 'http://localhost:$port/',
    ]);
    final ok = r.contains('Starting') || (!_failed(r) && !r.contains('Error'));
    return AdbActionResult(
      ok: ok,
      message: ok
          ? '폰 브라우저에 검사 페이지를 열었습니다. 폰 화면에서 색상·스피커·터치를 확인하세요.'
          : '페이지를 열지 못했습니다: ${r.trim().isEmpty ? "브라우저 실행 실패" : r.trim()}',
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
      out = await _run(
          serial, ['shell', 'cmd', 'vibrator', 'vibrate', '-f', '$ms']);
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
