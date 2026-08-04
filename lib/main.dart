import 'dart:io';

import 'package:flutter/material.dart';

import 'adb_auto_diagnostics.dart';

/// adb 경로 해석.
/// 설치본은 실행파일 옆 platform-tools\adb 를, 없으면 시스템 adb 를 사용.
String resolveAdbPath() {
  try {
    final exeDir = File(Platform.resolvedExecutable).parent.path;
    final sep = Platform.pathSeparator;
    final exe = Platform.isWindows ? 'adb.exe' : 'adb';
    final bundled = '$exeDir${sep}platform-tools$sep$exe';
    if (File(bundled).existsSync()) return bundled;
  } catch (_) {}
  return 'adb'; // 개발 중이거나 동봉 adb 없으면 시스템 adb
}

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

/// 폰 한 대의 검사 상태.
class DeviceEntry {
  final String serial;
  String model;
  bool testing;
  List<Map<String, dynamic>> results;

  DeviceEntry({
    required this.serial,
    this.model = '',
    this.testing = false,
    this.results = const [],
  });
}

class DashboardPage extends StatefulWidget {
  const DashboardPage({super.key});

  @override
  State<DashboardPage> createState() => _DashboardPageState();
}

class _DashboardPageState extends State<DashboardPage> {
  final String _adbPath = resolveAdbPath();
  late final AdbAutoDiagnostics _diag = AdbAutoDiagnostics(adbPath: _adbPath);
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
      found.add(DeviceEntry(serial: serial, model: model.replaceAll('_', ' ')));
    }
    setState(() {
      _devices = found;
      _scanning = false;
    });
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

  /// 전체 폰에 검사 실행.
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
              label: const Text('전체 검사'),
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
          maxCrossAxisExtent: 320,
          childAspectRatio: 1.1,
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
            const Divider(height: 20),
            Expanded(
              child: device.testing
                  ? const Center(
                      child: SizedBox(
                          width: 20,
                          height: 20,
                          child: CircularProgressIndicator(strokeWidth: 2)))
                  : device.results.isEmpty
                      ? Center(
                          child: OutlinedButton(
                            onPressed: () => _runTests(device),
                            child: const Text('검사 시작'),
                          ),
                        )
                      : _resultList(device.results),
            ),
          ],
        ),
      ),
    );
  }

  Widget _resultList(List<Map<String, dynamic>> results) {
    return ListView(
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
                    child: Icon(Icons.info_outline, size: 15, color: Colors.grey),
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

    // 센서 타입 영문 → 한글 라벨 (알기 쉬운 것만, 나머진 영문 그대로)
    const labels = {
      'accelerometer': '가속도',
      'gyroscope': '자이로',
      'magnetic_field': '지자기',
      'light': '조도',
      'proximity': '근접',
      'pressure': '기압',
      'gravity': '중력',
      'linear_acceleration': '선형가속도',
      'rotation_vector': '회전벡터',
      'step_counter': '걸음수',
      'significant_motion': '유의미한움직임',
      'game_rotation_vector': '게임회전벡터',
    };

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
                    final label = labels[t];
                    return ListTile(
                      dense: true,
                      leading: const Icon(Icons.sensors, size: 18, color: Colors.green),
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

  Widget _statusBadge(DeviceEntry device) {
    if (device.results.isEmpty) {
      return _badge('미검사', Colors.grey);
    }
    final allPass = device.results.every((r) => r['pass'] == true);
    return allPass
        ? _badge('통과', Colors.green)
        : _badge('불량', Colors.red);
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
