import 'dart:async';
import 'dart:io';

/// 육안 검수를 돕는 adb 보조 동작 모음.
///
/// 폰에 앱을 설치하지 않으므로, PC의 adb 로 폰의 내장 기능을 호출해
/// 검수원이 실물을 확인할 수 있게 돕습니다.
/// - 삼성 하드웨어 테스트(*#0*#): 색상화면·스피커(사이렌)·진동·수화부 등 내장 검사
/// - 진동: 기종 무관 공용 진동
/// - 볼륨 버튼 감지: getevent 로 물리 버튼 입력 확인
/// - S펜 지원 여부: 디지타이저(S펜) 하드웨어 기능 존재 확인
class AdbActions {
  AdbActions({this.adbPath = 'adb'});
  final String adbPath;

  /// 삼성 하드웨어 테스트 메뉴(*#0*#) 열기.
  ///
  /// SECRET_CODE 브로드캐스트로 삼성 내장 종합 테스트를 띄웁니다.
  /// 여기서 액정 단색화면(빨강·초록·파랑 등), 스피커 사이렌음, 진동,
  /// 수화부(마이크/리시버), 터치 등을 검수원이 직접 확인할 수 있습니다.
  /// (삼성 One UI 기기 전용. 다른 제조사는 열리지 않을 수 있음)
  Future<AdbActionResult> samsungHardwareTest(String serial) async {
    final r = await _run(serial, [
      'shell', 'am', 'broadcast',
      '-a', 'android.provider.Telephony.SECRET_CODE',
      '-d', 'android_secret_code://0',
    ]);
    // 브로드캐스트가 정상 전달되면 result=0 (Broadcast completed) 이 찍힘
    final ok = r.contains('Broadcast completed') || r.contains('result=0');
    return AdbActionResult(
      ok: ok,
      message: ok
          ? '삼성 하드웨어 테스트를 폰 화면에 띄웠습니다.'
          : '테스트 메뉴를 열지 못했습니다. (삼성 기기가 아니거나 차단됨 — 폰에서 *#0*# 직접 입력)',
    );
  }

  /// 공용 진동 — 기종 무관. 진동 모터 동작 확인.
  Future<AdbActionResult> vibrate(String serial, {int ms = 800}) async {
    // Android 11+ : cmd vibrator vibrate <ms>
    final r = await _run(
        serial, ['shell', 'cmd', 'vibrator', 'vibrate', '-f', '$ms', '폰검수']);
    final blocked = r.contains('Error') || r.contains('Exception');
    return AdbActionResult(
      ok: !blocked,
      message: blocked ? '진동 명령이 차단되었습니다.' : '진동을 울렸습니다. (모터 동작 확인)',
    );
  }

  /// 볼륨 버튼 감지 — getevent 로 물리 버튼 입력을 [seconds]초간 감지.
  ///
  /// 검수원이 볼륨 위/아래 버튼을 누르면 감지됩니다.
  /// 반환: 감지된 버튼 집합('up'·'down').
  Future<VolumeKeyResult> detectVolumeKeys(String serial,
      {int seconds = 6}) async {
    final detected = <String>{};
    Process? proc;
    try {
      proc = await Process.start(
          adbPath, ['-s', serial, 'shell', 'getevent', '-lq']);
      final sub = proc.stdout.listen((data) {
        final text = String.fromCharCodes(data);
        if (text.contains('KEY_VOLUMEUP')) detected.add('up');
        if (text.contains('KEY_VOLUMEDOWN')) detected.add('down');
      });
      await Future.delayed(Duration(seconds: seconds));
      await sub.cancel();
    } catch (_) {
      // getevent 실패 — 권한/기기 문제
    } finally {
      proc?.kill();
    }
    return VolumeKeyResult(
      up: detected.contains('up'),
      down: detected.contains('down'),
    );
  }

  /// S펜(디지타이저) 지원 여부 — 하드웨어 기능 목록에서 확인.
  ///
  /// 삼성 S펜 기기는 pm 기능 목록에 'spen' 관련 feature 를 가집니다
  /// (예: com.samsung.android.feature.SPEN_USP).
  /// 이걸로 Note/Ultra 계열 등 S펜 내장 기종만 판별합니다.
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

class VolumeKeyResult {
  VolumeKeyResult({required this.up, required this.down});
  final bool up;
  final bool down;
  bool get bothOk => up && down;
}
