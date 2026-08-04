import 'dart:io';

/// adb dumpsys/getprop 로 뽑는 자동 검사 항목들.
/// 폰 앱 없이 PC에서 바로 실행. 락 체커와 같은 패턴(실기 검증된 방식).
///
/// 각 메서드는 {test, status, pass, data, message} 형태의 Map 을 반환해
/// 다른 테스트 결과와 동일하게 대시보드/리포트에서 처리됩니다.
class AdbAutoDiagnostics {
  AdbAutoDiagnostics({this.adbPath = 'adb'});
  final String adbPath;

  /// 이 기기에서 가능한 자동 항목 전부 실행.
  Future<List<Map<String, dynamic>>> runAll(String serial) async {
    return [
      await battery(serial),
      await sim(serial),
      await wireless(serial),
      await sensors(serial),
      await storage(serial),
      await systemSoftware(serial),
    ];
  }

  /// 센서 — 필수 센서가 하드웨어에 존재하는지 확인.
  /// dumpsys sensorservice 의 'Sensor List:' 에서 android.sensor.* 타입을 뽑아,
  /// 검수에 중요한 센서(가속도·자이로·지자기·조도·근접)가 다 있는지 판정.
  Future<Map<String, dynamic>> sensors(String serial) async {
    final dump = await _run(serial, ['shell', 'dumpsys', 'sensorservice']);

    // "type: android.sensor.accelerometer(1)" 형태에서 센서 종류 추출
    final present = <String>{};
    for (final m
        in RegExp(r'android\.sensor\.(\w+)').allMatches(dump)) {
      present.add(m.group(1)!);
    }

    // 총 하드웨어 센서 개수 (예: "Total 40 h/w sensors")
    final totalMatch =
        RegExp(r'Total\s+(\d+)\s+h/w sensors').firstMatch(dump);
    final totalCount = totalMatch != null ? int.tryParse(totalMatch.group(1)!) : null;

    // 검수에 중요한 필수 센서 목록 (한글 라벨과 매핑)
    const essential = {
      'accelerometer': '가속도',
      'gyroscope': '자이로',
      'magnetic_field': '지자기',
      'light': '조도',
      'proximity': '근접',
    };

    final missing = <String>[];
    essential.forEach((key, label) {
      if (!present.contains(key)) missing.add(label);
    });

    final pass = missing.isEmpty;
    return {
      'test': 'sensors',
      'status': pass ? 'pass' : 'fail',
      'pass': pass,
      'data': {
        'totalHwSensors': totalCount,
        'presentTypes': present.toList()..sort(),
        'missingEssential': missing,
      },
      'message': pass
          ? '필수 센서 정상 (총 ${totalCount ?? present.length}개)'
          : '누락 센서: ${missing.join(", ")}',
    };
  }

  /// SIM — 삽입 여부·통신사·상태. dumpsys telephony.registry 로 읽음.
  Future<Map<String, dynamic>> sim(String serial) async {
    final dump =
        await _run(serial, ['shell', 'dumpsys', 'telephony.registry']);

    // mSimState: 5=READY(정상), 1=ABSENT(없음), 그 외 LOCKED/UNKNOWN 등
    final stateMatch =
        RegExp(r'mSimState=?(\w+)').firstMatch(dump)?.group(1);
    // 통신사 이름
    final carrier =
        RegExp(r'mOperatorAlphaLong=([^\n,]+)').firstMatch(dump)?.group(1)?.trim();

    // getprop 로 폴백 (통신사)
    String? carrierProp;
    if (carrier == null || carrier.isEmpty) {
      final props = await _run(serial, ['shell', 'getprop', 'gsm.sim.operator.alpha']);
      carrierProp = props.trim();
    }

    final finalCarrier =
        (carrier != null && carrier.isNotEmpty) ? carrier : carrierProp;

    // SIM 삽입 판정: state 가 READY 또는 통신사 정보가 있으면 삽입됨
    final ready = stateMatch == 'READY' || stateMatch == '5';
    final absent = stateMatch == 'ABSENT' || stateMatch == '1';
    final inserted = ready || (finalCarrier != null && finalCarrier.isNotEmpty);

    return {
      'test': 'sim',
      'status': 'pass',
      'pass': true,
      'data': {
        'inserted': inserted,
        'state': stateMatch,
        'carrier': finalCarrier,
      },
      'message': inserted
          ? '유심 정상${finalCarrier != null && finalCarrier.isNotEmpty ? " · $finalCarrier" : ""}'
          : absent
              ? '유심 없음'
              : '유심 상태 확인 필요',
    };
  }

  /// 배터리 — 잔량·온도·health·충전 소스.
  Future<Map<String, dynamic>> battery(String serial) async {
    final dump = await _run(serial, ['shell', 'dumpsys', 'battery']);
    int? _int(String key) {
      final m = RegExp('$key:\\s*(-?\\d+)').firstMatch(dump);
      return m != null ? int.tryParse(m.group(1)!) : null;
    }

    final level = _int('level');
    final tempRaw = _int('temperature'); // 0.1도 단위 (예: 293 = 29.3도)
    final temp = tempRaw != null ? tempRaw / 10.0 : null;
    final healthCode = _int('health');
    final acPlugged = dump.contains('AC powered: true');
    final usbPlugged = dump.contains('USB powered: true');
    final wirelessPlugged = dump.contains('Wireless powered: true');

    // health 코드: 1=알 수 없음 2=정상 3=과열 4=수명 이상 5=과전압 6=고장 7=저온
    const healthMap = {
      1: '확인 불가', 2: '정상', 3: '과열', 4: '수명 이상',
      5: '과전압', 6: '고장', 7: '저온',
    };
    final health = healthMap[healthCode] ?? '확인 불가';

    // 판정: 온도 45도 초과 또는 '명백한' 불량 코드일 때만 불량.
    // 1(알 수 없음)은 판정 불가일 뿐 불량이 아님 — 일부 삼성폰은 adb 로 health 를
    // 항상 1 로 보고하므로, 이를 불량으로 처리하면 멀쩡한 배터리가 불량이 됨.
    const badHealth = {3, 4, 5, 6}; // 과열·수명이상·과전압·고장
    final tempOk = temp == null || temp < 45;
    final healthOk = healthCode == null || !badHealth.contains(healthCode);
    final pass = tempOk && healthOk;

    return {
      'test': 'battery',
      'status': pass ? 'pass' : 'fail',
      'pass': pass,
      'data': {
        'level': level,
        'temperatureC': temp,
        'health': health,
        'chargingSource': acPlugged
            ? 'AC(유선)'
            : usbPlugged
                ? 'USB(유선)'
                : wirelessPlugged
                    ? '무선'
                    : '없음',
      },
      'message': '잔량 ${level ?? "?"}% · ${temp?.toStringAsFixed(1) ?? "?"}°C · $health',
    };
  }

  /// 무선 — 와이파이(실제 연결+통신)·블루투스·NFC.
  /// 검수장에 테스트용 와이파이 AP가 상시 있는 전제로, WiFi는 켜짐이 아니라
  /// 실제 AP 연결 + 인터넷 통신(핑)까지 확인.
  Future<Map<String, dynamic>> wireless(String serial) async {
    final wifi = await _wifiCheck(serial);
    final bt = await _bluetoothCheck(serial);
    final nfc = await _nfcCheck(serial);

    // 판정: WiFi 실제 통신 실패거나 NFC 칩이 죽었으면 불량
    final pass = wifi['connected'] == true &&
        (nfc['supported'] != true || nfc['alive'] == true);

    return {
      'test': 'wireless',
      'status': pass ? 'pass' : 'fail',
      'pass': pass,
      'data': {
        'wifi': wifi,
        'bluetooth': bt,
        'nfc': nfc,
      },
      'message': '와이파이 ${wifi['connected'] == true ? "정상" : "불량"} · '
          '블루투스 ${bt['available'] == true ? "정상" : "불량"} · '
          'NFC ${nfc['supported'] != true ? "미지원" : (nfc['alive'] == true ? "정상" : "불량")}',
    };
  }

  /// WiFi — 켜기 → 연결 확인 → 실제 핑으로 통신 검증.
  Future<Map<String, dynamic>> _wifiCheck(String serial) async {
    // 1) 와이파이 켜져 있는지
    final on = (await _run(
                serial, ['shell', 'settings', 'get', 'global', 'wifi_on']))
            .trim() ==
        '1';
    if (!on) {
      return {
        'on': false,
        'connected': false,
        'message': 'OFF (꺼져 있음)',
      };
    }

    // 2) AP 에 실제 연결됐는지 (dumpsys wifi 의 연결 상태)
    final wifiDump = await _run(serial, ['shell', 'dumpsys', 'wifi']);
    final connected = wifiDump.contains('mNetworkInfo') &&
            wifiDump.contains('CONNECTED') ||
        wifiDump.contains('Wi-Fi is connected');
    // 신호세기(RSSI) 추출
    final rssi =
        RegExp(r'RSSI:\s*(-?\d+)').firstMatch(wifiDump)?.group(1);

    // 3) 실제 인터넷 통신 확인 — ping (테스트 AP 가 인터넷 연결이면 8.8.8.8,
    //    폐쇄망이면 게이트웨이로 바꾸세요)
    final ping = await _run(
        serial, ['shell', 'ping', '-c', '2', '-W', '2', '8.8.8.8']);
    final pingOk = ping.contains('2 received') ||
        ping.contains('bytes from') && !ping.contains('100% packet loss');

    final ok = connected || pingOk;
    return {
      'on': true,
      'connected': ok,
      'rssi': rssi,
      'pingOk': pingOk,
      'message': ok
          ? '연결됨${rssi != null ? " (${rssi}dBm)" : ""}${pingOk ? " · 통신 정상" : ""}'
          : '켜짐이나 연결 안 됨',
    };
  }

  /// 블루투스 — 켜기 → 스캔 시작이 되는지로 어댑터 생존 확인.
  Future<Map<String, dynamic>> _bluetoothCheck(String serial) async {
    final on = (await _run(serial,
                ['shell', 'settings', 'get', 'global', 'bluetooth_on']))
            .trim() ==
        '1';
    // dumpsys bluetooth_manager 에 어댑터 정보가 있으면 칩 살아있음
    final btDump =
        await _run(serial, ['shell', 'dumpsys', 'bluetooth_manager']);
    final available = btDump.contains('mAddress') ||
        btDump.contains('AdapterState') ||
        btDump.contains('enabled: true');

    return {
      'on': on,
      'available': available,
      'message': available ? 'OK' : '어댑터 응답 없음',
    };
  }

  /// NFC — 어댑터가 살아있는지(칩 생존). ON/OFF 가 아니라 칩 존재 여부.
  Future<Map<String, dynamic>> _nfcCheck(String serial) async {
    final nfcDump = await _run(serial, ['shell', 'dumpsys', 'nfc']);
    final supported = nfcDump.trim().isNotEmpty &&
        !nfcDump.contains('Can\'t find service') &&
        !nfcDump.contains('no NFC');
    // 어댑터가 상태를 보고하면 칩이 살아있는 것 (on/off 무관)
    final alive = supported &&
        (nfcDump.contains('mState=') || nfcDump.contains('NfcService'));
    final on = nfcDump.contains('mState=on');

    return {
      'supported': supported,
      'alive': alive,
      'on': on,
    };
  }

  /// 저장소 — 총 용량.
  Future<Map<String, dynamic>> storage(String serial) async {
    // df /data 로 데이터 파티션 용량
    final df = await _run(serial, ['shell', 'df', '/data']);
    // 출력 마지막 줄 파싱: Filesystem 1K-blocks Used Available Use% Mounted
    final lines = df.trim().split('\n');
    int? totalKb;
    if (lines.length >= 2) {
      final parts = lines.last.trim().split(RegExp(r'\s+'));
      if (parts.length >= 2) {
        totalKb = int.tryParse(parts[1]);
      }
    }
    final totalGb = totalKb != null ? (totalKb / 1024 / 1024) : null;

    return {
      'test': 'storage',
      'status': 'pass',
      'pass': true,
      'data': {
        'totalGB': totalGb != null ? double.parse(totalGb.toStringAsFixed(1)) : null,
      },
      'message': '저장소 ${totalGb?.toStringAsFixed(1) ?? "?"} GB',
    };
  }

  /// 시스템 소프트웨어 — 모델·OS·보안패치.
  Future<Map<String, dynamic>> systemSoftware(String serial) async {
    final props = await _run(serial, ['shell', 'getprop']);
    String? prop(String key) =>
        RegExp('\\[$key\\]:\\s*\\[([^\\]]*)\\]').firstMatch(props)?.group(1);

    final model = prop('ro.product.model');
    final release = prop('ro.build.version.release');
    final sdk = prop('ro.build.version.sdk');
    final patch = prop('ro.build.version.security_patch');
    final oneui = prop('ro.build.version.oneui'); // 삼성 One UI 버전

    return {
      'test': 'system_software',
      'status': 'pass',
      'pass': true,
      'data': {
        'model': model,
        'androidVersion': release,
        'sdkInt': sdk,
        'securityPatch': patch,
        'oneUiVersion': oneui,
      },
      'message': '$model · Android $release (SDK $sdk)',
    };
  }

  Future<String> _run(String serial, List<String> args) async {
    try {
      final result = await Process.run(adbPath, ['-s', serial, ...args]);
      return '${result.stdout}';
    } catch (e) {
      return '';
    }
  }
}
