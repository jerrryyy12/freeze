import 'dart:io';

import 'package:flutter/material.dart';

import 'adb_actions.dart';
import 'adb_auto_diagnostics.dart';
import 'sensor_labels.dart';

/// adb 경로 해석.
/// 1) 설치본: 실행파일 옆 platform-tools\adb (Windows 동봉본)
/// 2) macOS/Linux: GUI 앱은 셸 PATH가 제한적이라 흔한 설치 경로를 직접 탐색
///    (brew / Android SDK 위치) — 그래야 맥에서 시스템 adb 를 찾음
/// 3) 최후: 시스템 PATH 의 adb
String resolveAdbPath() {
  final sep = Platform.pathSeparator;
  // 1) 실행파일 옆 동봉 adb
  try {
    final exeDir = File(Platform.resolvedExecutable).parent.path;
    final exe = Platform.isWindows ? 'adb.exe' : 'adb';
    final bundled = '$exeDir${sep}platform-tools$sep$exe';
    if (File(bundled).existsSync()) return bundled;
  } catch (_) {}

  // 2) macOS/Linux 흔한 설치 경로 (GUI 앱 PATH 문제 회피)
  if (!Platform.isWindows) {
    final home = Platform.environment['HOME'] ?? '';
    final candidates = [
      '/opt/homebrew/bin/adb', // macOS Apple Silicon (brew)
      '/usr/local/bin/adb', // macOS Intel (brew)
      '$home/Library/Android/sdk/platform-tools/adb', // macOS Android SDK
      '$home/Android/Sdk/platform-tools/adb', // Linux Android SDK
      '/usr/bin/adb',
    ];
    for (final c in candidates) {
      if (c.isNotEmpty && File(c).existsSync()) return c;
    }
  }

  return 'adb'; // 최후: 시스템 PATH
}

/// 폰 브라우저로 여는 검사 페이지 (액정 색상·스피커 사이렌·터치).
/// GitHub Pages 로 호스팅됨 (repo 의 docs/inspect.html).
const String kInspectUrl = 'https://jerrryyy12.github.io/freeze/inspect.html';

void main() {
  runApp(const InspectorApp());
}

class InspectorApp extends StatelessWidget {
  const InspectorApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: '폰 검수',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorSchemeSeed: const Color(0xFF1D9E75),
        useMaterial3: true,
      ),
      home: const DashboardPage(),
    );
  }
}

/// 육안 검수 항목 정의.
class ManualItem {
  const ManualItem(this.key, this.label, {this.hint});
  final String key;
  final String label;
  final String? hint;
}

/// 모든 폰 공통 육안 항목. (S펜은 지원 기종에만 별도 추가)
const List<ManualItem> kBaseManualItems = [
  ManualItem('lcd', '액정', hint: '데드픽셀·잔상(번인)·멍·줄'),
  ManualItem('camera', '카메라', hint: '전·후면 렌즈, 촬영 화질'),
  ManualItem('fingerprint', '지문 인식', hint: '등록·인식 동작'),
  ManualItem('speaker', '스피커', hint: '소리 재생·잡음'),
  ManualItem('mic', '마이크·수화부', hint: '녹음·통화 음성'),
  ManualItem('volume_buttons', '볼륨 버튼', hint: '위·아래 물리 버튼'),
];

const ManualItem kSpenItem = ManualItem('spen', 'S펜', hint: '필압·그리기·버튼');

/// 폰 한 대의 검사 상태.
class DeviceEntry {
  final String serial;
  String model;
  bool testing;
  List<Map<String, dynamic>> results; // adb 자동 검사 결과
  bool? spenSupported; // S펜 지원 기종 여부 (null = 미확인)
  Map<String, String> manual; // 육안 판정: key -> 'pass' | 'fail'

  DeviceEntry({
    required this.serial,
    this.model = '',
    this.testing = false,
    this.results = const [],
    this.spenSupported,
    Map<String, String>? manual,
  }) : manual = manual ?? {};

  /// 이 기기에 적용되는 육안 항목 (S펜 지원 시 S펜 포함).
  List<ManualItem> get manualItems => [
        ...kBaseManualItems,
        if (spenSupported == true) kSpenItem,
      ];

  bool get autoDone => results.isNotEmpty;
  bool get anyAutoFail => results.any((r) => r['pass'] != true);
  bool get anyManualFail => manual.values.any((v) => v == 'fail');
  bool get allManualDecided =>
      manualItems.every((it) => manual.containsKey(it.key));
  bool get allAutoPass => autoDone && !anyAutoFail;

  /// 종합 상태: none(미검사) / fail(불량) / pass(통과) / progress(진행중)
  String get overallStatus {
    if (!autoDone && manual.isEmpty) return 'none';
    if (anyAutoFail || anyManualFail) return 'fail';
    if (allAutoPass && allManualDecided) return 'pass';
    return 'progress';
  }
}

class DashboardPage extends StatefulWidget {
  const DashboardPage({super.key});

  @override
  State<DashboardPage> createState() => _DashboardPageState();
}

class _DashboardPageState extends State<DashboardPage> {
  final String _adbPath = resolveAdbPath();
  late final AdbAutoDiagnostics _diag = AdbAutoDiagnostics(adbPath: _adbPath);
  late final AdbActions _actions = AdbActions(adbPath: _adbPath);
  List<DeviceEntry> _devices = [];
  bool _scanning = false;

  @override
  void initState() {
    super.initState();
    _refreshDevices();
  }

  /// adb devices 로 연결된 폰 목록 갱신.
  Future<void> _refreshDevices() async {
    setState(() => _scanning = true);
    final result = await Process.run(_adbPath, ['devices', '-l']);
    final lines = (result.stdout as String).split('\n');
    final found = <DeviceEntry>[];
    for (final line in lines.skip(1)) {
      final trimmed = line.trim();
      if (trimmed.isEmpty) continue;
      if (!RegExp(r'\bdevice\b').hasMatch(trimmed)) continue;
      if (trimmed.contains('unauthorized') || trimmed.contains('offline')) {
        continue;
      }
      final serial = trimmed.split(RegExp(r'\s+')).first;
      final model =
          RegExp(r'model:(\S+)').firstMatch(trimmed)?.group(1) ?? serial;

      // 기존에 있던 기기면 검수 상태(자동·육안)를 유지
      final existing = _devices.where((d) => d.serial == serial);
      if (existing.isNotEmpty) {
        found.add(existing.first);
      } else {
        found.add(
            DeviceEntry(serial: serial, model: model.replaceAll('_', ' ')));
      }
    }
    setState(() {
      _devices = found;
      _scanning = false;
    });

    // S펜 지원 여부는 백그라운드로 확인 (아직 확인 안 된 기기만)
    for (final d in found) {
      if (d.spenSupported == null) {
        _actions.supportsSpen(d.serial).then((v) {
          if (mounted) setState(() => d.spenSupported = v);
        });
      }
    }
  }

  /// 특정 폰에 자동 검사 실행.
  Future<void> _runTests(DeviceEntry device) async {
    setState(() => device.testing = true);
    final results = await _diag.runAll(device.serial);
    setState(() {
      device.results = results;
      device.testing = false;
    });
  }

  /// 전체 폰에 자동 검사 실행.
  Future<void> _runAll() async {
    for (final d in _devices) {
      await _runTests(d);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('폰 검수 대시보드'),
        actions: [
          Padding(
            padding: const EdgeInsets.only(right: 8),
            child: Center(
              child: Text('USB 연결 ${_devices.length}대',
                  style: const TextStyle(fontSize: 13)),
            ),
          ),
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: '기기 새로고침',
            onPressed: _scanning ? null : _refreshDevices,
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12),
            child: FilledButton.icon(
              onPressed: _devices.isEmpty ? null : _runAll,
              icon: const Icon(Icons.play_arrow),
              label: const Text('전체 자동검사'),
            ),
          ),
        ],
      ),
      body: _scanning
          ? const Center(child: CircularProgressIndicator())
          : _devices.isEmpty
              ? _emptyState()
              : _deviceGrid(),
    );
  }

  Widget _emptyState() {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.usb_off, size: 48, color: Colors.grey),
          const SizedBox(height: 12),
          const Text('연결된 폰이 없습니다.'),
          const SizedBox(height: 4),
          const Text('USB로 연결하고 디버깅을 허용한 뒤 새로고침하세요.',
              style: TextStyle(color: Colors.grey, fontSize: 13)),
          const SizedBox(height: 16),
          OutlinedButton(
              onPressed: _refreshDevices, child: const Text('새로고침')),
        ],
      ),
    );
  }

  Widget _deviceGrid() {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: GridView.builder(
        gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
          maxCrossAxisExtent: 340,
          childAspectRatio: 0.92,
          crossAxisSpacing: 12,
          mainAxisSpacing: 12,
        ),
        itemCount: _devices.length,
        itemBuilder: (_, i) => _deviceCard(_devices[i]),
      ),
    );
  }

  Widget _deviceCard(DeviceEntry device) {
    return Card(
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: BorderSide(color: Colors.grey.shade300),
      ),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.smartphone, size: 22),
                const SizedBox(width: 8),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(device.model,
                          style: const TextStyle(
                              fontWeight: FontWeight.w500, fontSize: 14)),
                      Text(device.serial,
                          style: const TextStyle(
                              color: Colors.grey, fontSize: 11)),
                    ],
                  ),
                ),
                _statusBadge(device),
              ],
            ),
            const Divider(height: 18),
            Expanded(
              child: device.testing
                  ? const Center(
                      child: SizedBox(
                          width: 20,
                          height: 20,
                          child: CircularProgressIndicator(strokeWidth: 2)))
                  : device.results.isEmpty
                      ? Center(
                          child: Column(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              OutlinedButton(
                                onPressed: () => _runTests(device),
                                child: const Text('자동 검사 시작'),
                              ),
                              const SizedBox(height: 8),
                              const Text('배터리·유심·무선·센서·저장소·시스템',
                                  style: TextStyle(
                                      color: Colors.grey, fontSize: 11)),
                            ],
                          ),
                        )
                      : _resultList(device.results),
            ),
            const SizedBox(height: 8),
            Row(
              children: [
                if (device.results.isNotEmpty)
                  Expanded(
                    child: OutlinedButton.icon(
                      onPressed: () => _runTests(device),
                      icon: const Icon(Icons.refresh, size: 16),
                      label: const Text('재검사'),
                      style: OutlinedButton.styleFrom(
                          padding: const EdgeInsets.symmetric(vertical: 8)),
                    ),
                  ),
                if (device.results.isNotEmpty) const SizedBox(width: 8),
                Expanded(
                  child: FilledButton.tonalIcon(
                    onPressed: () => _openInspection(device),
                    icon: const Icon(Icons.checklist, size: 16),
                    label: Text(_manualSummaryLabel(device)),
                    style: FilledButton.styleFrom(
                        padding: const EdgeInsets.symmetric(vertical: 8)),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  /// 육안 버튼에 표시할 진행 요약 (예: "육안 2/7").
  String _manualSummaryLabel(DeviceEntry device) {
    final total = device.manualItems.length;
    final done = device.manual.length;
    return done == 0 ? '육안 검수' : '육안 $done/$total';
  }

  Widget _resultList(List<Map<String, dynamic>> results) {
    return ListView(
      padding: EdgeInsets.zero,
      children: results.map((r) {
        final pass = r['pass'] == true;
        final isSensor = r['test'] == 'sensors';
        return Padding(
          padding: const EdgeInsets.symmetric(vertical: 3),
          child: Row(
            children: [
              Icon(pass ? Icons.check_circle : Icons.error,
                  size: 15, color: pass ? Colors.green : Colors.red),
              const SizedBox(width: 6),
              Expanded(
                child: Text(r['message']?.toString() ?? '',
                    style: const TextStyle(fontSize: 12),
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis),
              ),
              if (isSensor)
                InkWell(
                  onTap: () => _showSensorDetail(r),
                  child: const Padding(
                    padding: EdgeInsets.only(left: 4),
                    child:
                        Icon(Icons.info_outline, size: 15, color: Colors.grey),
                  ),
                ),
            ],
          ),
        );
      }).toList(),
    );
  }

  /// 센서 전체 목록 다이얼로그.
  void _showSensorDetail(Map<String, dynamic> sensorResult) {
    final data = sensorResult['data'] as Map<String, dynamic>? ?? {};
    final types = (data['presentTypes'] as List?)?.cast<String>() ?? [];
    final total = data['totalHwSensors'];
    final missing = (data['missingEssential'] as List?)?.cast<String>() ?? [];

    showDialog(
      context: context,
      builder: (_) => AlertDialog(
        title: Text('센서 목록 (총 ${total ?? types.length}개)'),
        content: SizedBox(
          width: 360,
          height: 420,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              if (missing.isNotEmpty)
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(8),
                  margin: const EdgeInsets.only(bottom: 8),
                  decoration: BoxDecoration(
                    color: Colors.red.withOpacity(0.08),
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: Text('누락 필수 센서: ${missing.join(", ")}',
                      style: const TextStyle(color: Colors.red, fontSize: 13)),
                ),
              Expanded(
                child: ListView.separated(
                  itemCount: types.length,
                  separatorBuilder: (_, __) => const Divider(height: 1),
                  itemBuilder: (_, i) {
                    final t = types[i];
                    final label = kSensorLabels[t];
                    return ListTile(
                      dense: true,
                      leading: const Icon(Icons.sensors,
                          size: 18, color: Colors.green),
                      title: Text(label != null ? '$label ($t)' : t,
                          style: const TextStyle(fontSize: 13)),
                    );
                  },
                ),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('닫기')),
        ],
      ),
    );
  }

  /// 육안 검수 상세 다이얼로그 — 항목별 정상/불량 판정 + adb 보조 동작.
  void _openInspection(DeviceEntry device) {
    // 도우미 동작 결과를 다이얼로그 '안'에 표시 (SnackBar 는 다이얼로그 뒤에 떠서 안 보임)
    String? actionStatus;
    bool actionOk = true;
    bool actionBusy = false;

    showDialog(
      context: context,
      builder: (dialogContext) {
        return StatefulBuilder(
          builder: (context, setDialogState) {
            void setManual(String key, String value) {
              // 같은 값 다시 누르면 판정 취소(미검사로)
              setState(() {
                if (device.manual[key] == value) {
                  device.manual.remove(key);
                } else {
                  device.manual[key] = value;
                }
              });
              setDialogState(() {});
            }

            Future<void> runAction(
                Future<AdbActionResult> Function() action) async {
              setDialogState(() {
                actionBusy = true;
                actionStatus = '실행 중…';
              });
              final r = await action();
              setDialogState(() {
                actionBusy = false;
                actionOk = r.ok;
                actionStatus = r.message;
              });
            }

            return AlertDialog(
              title: Row(
                children: [
                  const Icon(Icons.checklist, size: 20),
                  const SizedBox(width: 8),
                  Expanded(child: Text('육안 검수 — ${device.model}')),
                  _statusBadge(device),
                ],
              ),
              content: SizedBox(
                width: 460,
                child: SingleChildScrollView(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      // 도우미 동작 결과 표시 (다이얼로그 안)
                      if (actionStatus != null)
                        Container(
                          width: double.infinity,
                          margin: const EdgeInsets.only(bottom: 12),
                          padding: const EdgeInsets.symmetric(
                              horizontal: 12, vertical: 10),
                          decoration: BoxDecoration(
                            color: (actionBusy
                                    ? Colors.blueGrey
                                    : actionOk
                                        ? Colors.green
                                        : Colors.red)
                                .withOpacity(0.10),
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: Row(
                            children: [
                              if (actionBusy)
                                const SizedBox(
                                    width: 14,
                                    height: 14,
                                    child: CircularProgressIndicator(
                                        strokeWidth: 2))
                              else
                                Icon(
                                    actionOk
                                        ? Icons.check_circle
                                        : Icons.error,
                                    size: 16,
                                    color: actionOk
                                        ? Colors.green
                                        : Colors.red),
                              const SizedBox(width: 8),
                              Expanded(
                                child: Text(actionStatus!,
                                    style: const TextStyle(fontSize: 12)),
                              ),
                            ],
                          ),
                        ),
                      // adb 보조 동작
                      _sectionTitle('검수 도우미 (폰 화면·기능 띄우기)'),
                      Wrap(
                        spacing: 8,
                        runSpacing: 8,
                        children: [
                          OutlinedButton.icon(
                            onPressed: () => runAction(() => _actions
                                .openWebInspector(device.serial, kInspectUrl)),
                            icon: const Icon(Icons.smartphone, size: 16),
                            label: const Text('폰 화면·소리 검사'),
                          ),
                          OutlinedButton.icon(
                            onPressed: () => runAction(
                                () => _actions.vibrate(device.serial)),
                            icon: const Icon(Icons.vibration, size: 16),
                            label: const Text('진동'),
                          ),
                        ],
                      ),
                      const SizedBox(height: 6),
                      Text(
                        '폰 화면·소리 검사: 폰 브라우저에 검사 페이지가 열립니다 → 색상화면(액정)·사이렌(스피커)·터치를 폰에서 직접 확인. (검수장 와이파이 인터넷 필요)',
                        style: TextStyle(
                            fontSize: 11, color: Colors.grey.shade600),
                      ),
                      const Divider(height: 24),

                      // 육안 판정 항목
                      _sectionTitle('육안 판정'),
                      ...device.manualItems.map(
                          (item) => _manualRow(item, device, setManual)),
                    ],
                  ),
                ),
              ),
              actions: [
                TextButton(
                  onPressed: () {
                    setState(() => device.manual.clear());
                    setDialogState(() {});
                  },
                  child: const Text('육안 초기화'),
                ),
                FilledButton(
                  onPressed: () => Navigator.pop(dialogContext),
                  child: const Text('완료'),
                ),
              ],
            );
          },
        );
      },
    );
  }

  Widget _sectionTitle(String text) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Text(text,
          style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13)),
    );
  }

  /// 육안 항목 한 줄: 라벨 + 힌트 + 정상/불량 토글.
  Widget _manualRow(ManualItem item, DeviceEntry device,
      void Function(String key, String value) setManual) {
    final value = device.manual[item.key];
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 5),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(item.label,
                    style: const TextStyle(
                        fontSize: 14, fontWeight: FontWeight.w500)),
                if (item.hint != null)
                  Text(item.hint!,
                      style: TextStyle(
                          fontSize: 11, color: Colors.grey.shade600)),
              ],
            ),
          ),
          _choiceButton(
            label: '정상',
            selected: value == 'pass',
            color: Colors.green,
            onTap: () => setManual(item.key, 'pass'),
          ),
          const SizedBox(width: 6),
          _choiceButton(
            label: '불량',
            selected: value == 'fail',
            color: Colors.red,
            onTap: () => setManual(item.key, 'fail'),
          ),
        ],
      ),
    );
  }

  Widget _choiceButton({
    required String label,
    required bool selected,
    required Color color,
    required VoidCallback onTap,
  }) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(8),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
        decoration: BoxDecoration(
          color: selected ? color.withOpacity(0.15) : null,
          border: Border.all(
              color: selected ? color : Colors.grey.shade400,
              width: selected ? 1.5 : 1),
          borderRadius: BorderRadius.circular(8),
        ),
        child: Text(label,
            style: TextStyle(
                fontSize: 13,
                color: selected ? color : Colors.grey.shade700,
                fontWeight:
                    selected ? FontWeight.w600 : FontWeight.normal)),
      ),
    );
  }

  Widget _statusBadge(DeviceEntry device) {
    switch (device.overallStatus) {
      case 'fail':
        return _badge('불량', Colors.red);
      case 'pass':
        return _badge('통과', Colors.green);
      case 'progress':
        return _badge('진행중', Colors.orange);
      default:
        return _badge('미검사', Colors.grey);
    }
  }

  Widget _badge(String text, Color color) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: color.withOpacity(0.12),
        borderRadius: BorderRadius.circular(6),
      ),
      child: Text(text,
          style: TextStyle(
              color: color, fontSize: 11, fontWeight: FontWeight.w500)),
    );
  }
}
