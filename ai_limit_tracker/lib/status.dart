import 'dart:convert';
import 'dart:io';

/// 공식 상태 페이지(Statuspage 공개 API)에서 서비스 장애 현황을 가져온다.
class StatusSource {
  const StatusSource({
    required this.serviceId,
    required this.name,
    required this.pageUrl,
    required this.componentKeyword,
  });

  final String serviceId;
  final String name;

  /// 사람이 보는 상태 페이지. API 는 이 주소 + /api/v2/...
  final String pageUrl;

  /// 이 서비스에 해당하는 구성요소 이름에 들어가는 단어.
  final String componentKeyword;
}

const statusSources = [
  StatusSource(
    serviceId: 'codex',
    name: 'Codex',
    pageUrl: 'https://status.openai.com',
    componentKeyword: 'codex',
  ),
  StatusSource(
    serviceId: 'claude',
    name: 'Claude Code',
    pageUrl: 'https://status.claude.com',
    componentKeyword: 'claude code',
  ),
];

enum Health {
  operational,
  maintenance,
  degraded,
  partialOutage,
  majorOutage,
  unknown,
}

Health healthFromComponent(String? s) => switch (s) {
  'operational' => Health.operational,
  'under_maintenance' => Health.maintenance,
  'degraded_performance' => Health.degraded,
  'partial_outage' => Health.partialOutage,
  'major_outage' => Health.majorOutage,
  _ => Health.unknown,
};

/// 사건 영향도(impact) → 하루 칸 색 단계. 0: 없음, 1: 경미, 2: 주요, 3: 심각
int impactLevel(String? impact) => switch (impact) {
  'minor' => 1,
  'major' => 2,
  'critical' => 3,
  'maintenance' => 0,
  _ => 0,
};

String healthLabel(Health h) => switch (h) {
  Health.operational => '정상',
  Health.maintenance => '점검 중',
  Health.degraded => '성능 저하',
  Health.partialOutage => '부분 장애',
  Health.majorOutage => '주요 장애',
  Health.unknown => '확인 불가',
};

class Incident {
  Incident(this.name, this.impact, this.createdAt, this.resolved);
  final String name;
  final String impact;
  final DateTime createdAt;
  final bool resolved;
}

class ServiceStatus {
  ServiceStatus({
    required this.source,
    required this.health,
    required this.days,
    this.activeIncident,
    this.lastIncident,
    this.error,
  });

  final StatusSource source;
  final Health health;

  /// 최근 14일(오래된 날 → 오늘) 하루별 최악 영향도.
  final List<int> days;
  final Incident? activeIncident;
  final DateTime? lastIncident;
  final String? error;

  bool get hasIssue =>
      health != Health.operational &&
      health != Health.unknown &&
      health != Health.maintenance;
}

const statusDays = 14;

Future<Map<String, dynamic>> _getJson(HttpClient client, String url) async {
  final req = await client.getUrl(Uri.parse(url));
  req.headers.set(HttpHeaders.acceptHeader, 'application/json');
  final res = await req.close();
  if (res.statusCode != 200) {
    await res.drain<void>();
    throw HttpException('HTTP ${res.statusCode}');
  }
  final body = await res.transform(utf8.decoder).join();
  return jsonDecode(body) as Map<String, dynamic>;
}

Future<ServiceStatus> fetchStatus(StatusSource src, {DateTime? now}) async {
  final client = HttpClient()..connectionTimeout = const Duration(seconds: 10);
  try {
    final summary = await _getJson(
      client,
      '${src.pageUrl}/api/v2/summary.json',
    );
    Map<String, dynamic> incidentsJson;
    try {
      incidentsJson = await _getJson(
        client,
        '${src.pageUrl}/api/v2/incidents.json',
      );
    } catch (_) {
      incidentsJson = const {'incidents': []};
    }
    return parseStatus(src, summary, incidentsJson, now ?? DateTime.now());
  } catch (e) {
    return ServiceStatus(
      source: src,
      health: Health.unknown,
      days: List.filled(statusDays, 0),
      error: '$e',
    );
  } finally {
    client.close(force: true);
  }
}

bool _matches(StatusSource src, Object? components) {
  if (components is! List || components.isEmpty) return true; // 정보 없으면 포함
  return components.any(
    (c) =>
        c is Map &&
        (c['name'] as String? ?? '').toLowerCase().contains(
          src.componentKeyword,
        ),
  );
}

/// 순수 파싱(테스트 대상).
ServiceStatus parseStatus(
  StatusSource src,
  Map<String, dynamic> summary,
  Map<String, dynamic> incidentsJson,
  DateTime now,
) {
  // 1) 현재 상태: 해당 구성요소들 중 최악, 없으면 페이지 전체 지표.
  final comps = (summary['components'] as List? ?? [])
      .whereType<Map<String, dynamic>>()
      .where(
        (c) => (c['name'] as String? ?? '').toLowerCase().contains(
          src.componentKeyword,
        ),
      )
      .toList();
  Health health;
  if (comps.isNotEmpty) {
    health = comps
        .map((c) => healthFromComponent(c['status'] as String?))
        .reduce((a, b) => a.index >= b.index ? a : b);
  } else {
    final indicator = (summary['status'] as Map?)?['indicator'] as String?;
    health = switch (indicator) {
      'none' => Health.operational,
      'minor' => Health.degraded,
      'major' => Health.partialOutage,
      'critical' => Health.majorOutage,
      'maintenance' => Health.maintenance,
      _ => Health.unknown,
    };
  }

  // 2) 사건 목록 → 14일 칸, 진행 중 사건, 마지막 사건일.
  final today = DateTime(now.year, now.month, now.day);
  final days = List.filled(statusDays, 0);
  Incident? active;
  DateTime? last;
  final incidents = [
    ...(summary['incidents'] as List? ?? []),
    ...(incidentsJson['incidents'] as List? ?? []),
  ].whereType<Map<String, dynamic>>();
  final seen = <String>{};
  for (final i in incidents) {
    final id = '${i['id'] ?? i['name']}';
    if (!seen.add(id)) continue;
    if (!_matches(src, i['components'])) continue;
    final created = DateTime.tryParse(i['created_at'] as String? ?? '')
        ?.toLocal();
    if (created == null) continue;
    final impact = i['impact'] as String? ?? 'none';
    final status = i['status'] as String? ?? '';
    final resolved =
        status == 'resolved' || status == 'postmortem' || status == 'completed';
    final inc = Incident(i['name'] as String? ?? '', impact, created, resolved);
    if (!resolved && (active == null || created.isAfter(active.createdAt))) {
      active = inc;
    }
    if (impactLevel(impact) > 0 && (last == null || created.isAfter(last))) {
      last = created;
    }
    final day = DateTime(created.year, created.month, created.day);
    final idx = statusDays - 1 - today.difference(day).inDays;
    if (idx >= 0 && idx < statusDays) {
      final lv = impactLevel(impact);
      if (lv > days[idx]) days[idx] = lv;
    }
  }

  return ServiceStatus(
    source: src,
    health: health,
    days: days,
    activeIncident: active,
    lastIncident: last,
  );
}
