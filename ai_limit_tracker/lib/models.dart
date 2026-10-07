import 'dart:convert';
import 'dart:math' as math;

const Duration kSessionLength = Duration(hours: 5);
const Duration kWeekLength = Duration(days: 7);

enum WindowKind { session, weekly }

/// 한도 창 하나(5시간 세션 또는 주간).
///
/// 저장하는 건 "마지막으로 확인한 사용량과 그때의 리셋 시각"뿐이고,
/// 지금 남은 양·소진 예상 등은 현재 시각으로 매번 계산한다.
/// 안드로이드 위젯(TrackerCore.kt)도 같은 JSON·같은 규칙으로 계산하므로
/// 필드 이름이나 규칙을 바꾸면 Kotlin 쪽도 함께 바꿔야 한다.
class LimitWindow {
  LimitWindow(this.kind, {this.usedPct, this.resetAt, this.updatedAt});

  final WindowKind kind;

  /// 사용량(%) — 마지막 확인 시점 기준.
  int? usedPct;

  /// 리셋 시각. 주간은 7일마다 반복되는 기준점으로도 쓴다.
  DateTime? resetAt;

  /// usedPct 를 기록한 시각.
  DateTime? updatedAt;

  Duration get length =>
      kind == WindowKind.session ? kSessionLength : kWeekLength;

  bool get configured => usedPct != null || resetAt != null;

  /// 지금 진행 중인 창의 리셋 시각. 세션이 끝났거나 모르면 null.
  DateTime? currentReset(DateTime now) {
    final r = resetAt;
    if (r == null) return null;
    if (kind == WindowKind.session) return r.isAfter(now) ? r : null;
    return nextPeriodic(r, kWeekLength, now);
  }

  /// 지금 기준 사용량. 기록 이후 리셋됐으면 0.
  int? usedNow(DateTime now) {
    final used = usedPct;
    if (used == null) return null;
    final r = resetAt;
    if (r == null) return used;
    if (kind == WindowKind.session) return r.isAfter(now) ? used : 0;
    final reset = currentReset(now)!;
    final start = reset.subtract(kWeekLength);
    final at = updatedAt;
    if (at != null && at.isBefore(start)) return 0;
    return used;
  }

  int? remainingNow(DateTime now) {
    final u = usedNow(now);
    return u == null ? null : 100 - u;
  }

  /// 지금까지의 사용 속도로 계산한 페이스.
  Pace pace(DateTime now) {
    final reset = currentReset(now);
    final used = usedNow(now);
    final at = updatedAt;
    if (reset == null || used == null || at == null) {
      return const Pace.unknown();
    }
    if (used >= 100) return Pace.exhausted(reset);
    final start = reset.subtract(length);
    final elapsed = at.difference(start);
    if (used <= 0 || elapsed <= Duration.zero || at.isAfter(reset)) {
      return const Pace.plenty();
    }
    // 사용량은 창 시작 시점에 0 이었으므로 (사용량 / 경과시간) 이 평균 속도.
    final runOut = start.add(elapsed * (100 / used));
    if (runOut.isBefore(reset)) return Pace.runsOut(runOut);
    final projected = used * length.inSeconds / elapsed.inSeconds;
    return projected <= 70 ? const Pace.plenty() : const Pace.onPace();
  }

  /// 새로 확인한 값 기록.
  void record({
    required int usedPct,
    DateTime? resetAt,
    required DateTime now,
  }) {
    this.usedPct = usedPct.clamp(0, 100);
    if (resetAt != null) this.resetAt = resetAt;
    updatedAt = now;
  }

  void clear() {
    usedPct = null;
    resetAt = null;
    updatedAt = null;
  }

  Map<String, dynamic> toJson() => {
    'usedPct': usedPct,
    'resetAt': _ms(resetAt),
    'updatedAt': _ms(updatedAt),
  };

  static LimitWindow fromJson(WindowKind kind, Object? j) {
    if (j is! Map<String, dynamic>) return LimitWindow(kind);
    return LimitWindow(
      kind,
      usedPct: (j['usedPct'] as num?)?.toInt(),
      resetAt: _dt(j['resetAt']),
      updatedAt: _dt(j['updatedAt']),
    );
  }
}

enum PaceKind { unknown, plenty, onPace, runsOut, exhausted }

class Pace {
  const Pace._(this.kind, [this.at]);
  const Pace.unknown() : this._(PaceKind.unknown);
  const Pace.plenty() : this._(PaceKind.plenty);
  const Pace.onPace() : this._(PaceKind.onPace);
  const Pace.runsOut(DateTime at) : this._(PaceKind.runsOut, at);
  const Pace.exhausted(DateTime resetAt) : this._(PaceKind.exhausted, resetAt);

  final PaceKind kind;

  /// runsOut: 소진 예상 시각, exhausted: 다시 풀리는 시각.
  final DateTime? at;

  bool get warning => kind == PaceKind.runsOut || kind == PaceKind.exhausted;
}

class ServiceState {
  ServiceState({
    required this.id,
    required this.name,
    this.plan,
    LimitWindow? session,
    LimitWindow? weekly,
  }) : session = session ?? LimitWindow(WindowKind.session),
       weekly = weekly ?? LimitWindow(WindowKind.weekly);

  final String id;
  final String name;
  String? plan;
  final LimitWindow session;
  final LimitWindow weekly;

  LimitWindow window(WindowKind k) =>
      k == WindowKind.session ? session : weekly;

  DateTime? get lastUpdated {
    final a = session.updatedAt, b = weekly.updatedAt;
    if (a == null) return b;
    if (b == null) return a;
    return a.isAfter(b) ? a : b;
  }

  /// 위젯·정렬에 쓰는 "더 빠듯한" 창 — 남은 양이 적은 쪽(같으면 주간).
  LimitWindow tighter(DateTime now) {
    final s = session.remainingNow(now), w = weekly.remainingNow(now);
    if (s == null) return weekly;
    if (w == null) return session;
    return s < w ? session : weekly;
  }

  /// 급한 순 정렬 기준: 소진 예상이 빠를수록, 남은 양이 적을수록 먼저.
  (int, int) urgency(DateTime now) {
    var soonest = 1 << 62;
    for (final w in [session, weekly]) {
      final p = w.pace(now);
      if (p.warning && p.at != null) {
        soonest = math.min(soonest, p.at!.millisecondsSinceEpoch);
      }
    }
    final rem = [
      session.remainingNow(now),
      weekly.remainingNow(now),
    ].whereType<int>().fold<int>(101, math.min);
    return (soonest, rem);
  }

  Map<String, dynamic> toJson() => {
    'id': id,
    'name': name,
    'plan': plan,
    'session': session.toJson(),
    'weekly': weekly.toJson(),
  };

  static ServiceState fromJson(Map<String, dynamic> j) => ServiceState(
    id: j['id'] as String,
    name: j['name'] as String,
    plan: j['plan'] as String?,
    session: LimitWindow.fromJson(WindowKind.session, j['session']),
    weekly: LimitWindow.fromJson(WindowKind.weekly, j['weekly']),
  );
}

class AlertSetting {
  AlertSetting({this.enabled = false, this.threshold = 10});

  bool enabled;

  /// 주간 잔여가 이 값(%) 이하로 떨어지면 알림.
  int threshold;

  /// 이미 알린 주간 창(그 창의 리셋 시각) — 같은 창에서 중복 알림 방지.
  int? alertedFor;

  Map<String, dynamic> toJson() => {
    'enabled': enabled,
    'threshold': threshold,
    'alertedFor': alertedFor,
  };

  static AlertSetting fromJson(Object? j) {
    if (j is! Map<String, dynamic>) return AlertSetting();
    return AlertSetting(
      enabled: j['enabled'] as bool? ?? false,
      threshold: (j['threshold'] as num?)?.toInt() ?? 10,
    )..alertedFor = (j['alertedFor'] as num?)?.toInt();
  }
}

class Settings {
  bool absoluteTime = false;

  /// true: 급한 순, false: 사용자 지정 순서(customOrder).
  bool urgentFirst = true;
  List<String> customOrder = ['codex', 'claude'];
  bool resetAlerts = true;
  Map<String, AlertSetting> alerts = {};

  AlertSetting alertFor(String id) => alerts.putIfAbsent(id, AlertSetting.new);

  Map<String, dynamic> toJson() => {
    'absoluteTime': absoluteTime,
    'urgentFirst': urgentFirst,
    'customOrder': customOrder,
    'resetAlerts': resetAlerts,
    'alerts': alerts.map((k, v) => MapEntry(k, v.toJson())),
  };

  static Settings fromJson(Object? j) {
    final s = Settings();
    if (j is! Map<String, dynamic>) return s;
    s.absoluteTime = j['absoluteTime'] as bool? ?? false;
    s.urgentFirst = j['urgentFirst'] as bool? ?? true;
    s.customOrder =
        (j['customOrder'] as List?)?.cast<String>() ?? s.customOrder;
    s.resetAlerts = j['resetAlerts'] as bool? ?? true;
    final a = j['alerts'];
    if (a is Map<String, dynamic>) {
      s.alerts = a.map((k, v) => MapEntry(k, AlertSetting.fromJson(v)));
    }
    return s;
  }
}

/// 통계용 기록 한 줄.
class Snapshot {
  Snapshot(this.at, this.service, this.kind, this.used);

  final DateTime at;
  final String service;
  final WindowKind kind;
  final int used;

  Map<String, dynamic> toJson() => {
    't': at.millisecondsSinceEpoch,
    's': service,
    'w': kind == WindowKind.session ? 's' : 'w',
    'u': used,
  };

  static Snapshot? fromJson(Object? j) {
    if (j is! Map<String, dynamic>) return null;
    try {
      return Snapshot(
        DateTime.fromMillisecondsSinceEpoch((j['t'] as num).toInt()),
        j['s'] as String,
        j['w'] == 's' ? WindowKind.session : WindowKind.weekly,
        (j['u'] as num).toInt(),
      );
    } catch (_) {
      return null;
    }
  }
}

class TrackerState {
  TrackerState(this.services, this.settings, this.history);

  final List<ServiceState> services;
  final Settings settings;
  final List<Snapshot> history;

  static const maxHistory = 1000;

  static TrackerState initial() => TrackerState(
    [
      ServiceState(id: 'codex', name: 'Codex'),
      ServiceState(id: 'claude', name: 'Claude Code'),
    ],
    Settings(),
    [],
  );

  ServiceState service(String id) => services.firstWhere((s) => s.id == id);

  /// 설정에 따른 표시 순서.
  List<ServiceState> ordered(DateTime now) {
    final list = [...services];
    if (settings.urgentFirst) {
      list.sort((a, b) {
        final ua = a.urgency(now), ub = b.urgency(now);
        final c = ua.$1.compareTo(ub.$1);
        return c != 0 ? c : ua.$2.compareTo(ub.$2);
      });
    } else {
      int idx(ServiceState s) {
        final i = settings.customOrder.indexOf(s.id);
        return i < 0 ? 999 : i;
      }

      list.sort((a, b) => idx(a).compareTo(idx(b)));
    }
    return list;
  }

  DateTime? get lastUpdated => services
      .map((s) => s.lastUpdated)
      .whereType<DateTime>()
      .fold<DateTime?>(null, (a, b) => a == null || b.isAfter(a) ? b : a);

  /// 사용량 기록 + 통계용 스냅샷 추가.
  void record(
    String serviceId,
    WindowKind kind, {
    required int usedPct,
    DateTime? resetAt,
    required DateTime now,
  }) {
    service(serviceId)
        .window(kind)
        .record(usedPct: usedPct, resetAt: resetAt, now: now);
    history.add(Snapshot(now, serviceId, kind, usedPct.clamp(0, 100)));
    if (history.length > maxHistory) {
      history.removeRange(0, history.length - maxHistory);
    }
  }

  /// 주간 잔여 알림을 보내야 하는 서비스들 — 반환하면서 "알림 보냄"으로 표시.
  List<(ServiceState, int)> takeThresholdAlerts(DateTime now) {
    final out = <(ServiceState, int)>[];
    for (final s in services) {
      final a = settings.alertFor(s.id);
      final rem = s.weekly.remainingNow(now);
      final reset = s.weekly.currentReset(now);
      if (!a.enabled || rem == null || rem > a.threshold) continue;
      final key = reset?.millisecondsSinceEpoch ?? 0;
      if (a.alertedFor == key) continue;
      a.alertedFor = key;
      out.add((s, rem));
    }
    return out;
  }

  String encode() => jsonEncode({
    'v': 2,
    'services': services.map((s) => s.toJson()).toList(),
    'settings': settings.toJson(),
    'history': history.map((h) => h.toJson()).toList(),
  });

  /// 저장된 JSON 을 읽는다. 없거나 깨졌거나 옛 형식이면 초기 상태.
  static TrackerState decode(String? raw) {
    final base = initial();
    if (raw == null || raw.isEmpty) return base;
    try {
      final j = jsonDecode(raw) as Map<String, dynamic>;
      if (j['v'] != 2) return base;
      final loaded = {
        for (final s in (j['services'] as List))
          (s as Map<String, dynamic>)['id'] as String: ServiceState.fromJson(s),
      };
      return TrackerState(
        [for (final s in base.services) loaded[s.id] ?? s],
        Settings.fromJson(j['settings']),
        [for (final h in (j['history'] as List? ?? [])) ?Snapshot.fromJson(h)],
      );
    } catch (_) {
      return base;
    }
  }
}

/// anchor 에서 period 간격으로 반복되는 시각들 중 now 보다 뒤인 첫 시각.
DateTime nextPeriodic(DateTime anchor, Duration period, DateTime now) {
  final p = period.inMilliseconds;
  final diff = now.millisecondsSinceEpoch - anchor.millisecondsSinceEpoch;
  final m = diff % p; // Dart 의 % 는 항상 0 이상
  return DateTime.fromMillisecondsSinceEpoch(
    now.millisecondsSinceEpoch - m + p,
  );
}

/// 시:분만 아는 리셋 시각(예: "resets 3pm")을 now 이후 가장 가까운 시각으로.
DateTime nextClockTime(int hour, int minute, DateTime now) {
  var t = DateTime(now.year, now.month, now.day, hour, minute);
  if (!t.isAfter(now)) {
    t = DateTime(now.year, now.month, now.day + 1, hour, minute);
  }
  return t;
}

int? _ms(DateTime? d) => d?.millisecondsSinceEpoch;
DateTime? _dt(Object? v) =>
    v == null ? null : DateTime.fromMillisecondsSinceEpoch((v as num).toInt());

// ───────────── 표시용 ─────────────

/// "2시간 19분", "1일 14시간", "45초"
String formatRemaining(Duration d) {
  if (d.isNegative) d = Duration.zero;
  final days = d.inDays;
  final hours = d.inHours % 24;
  final minutes = d.inMinutes % 60;
  if (days > 0) return hours > 0 ? '$days일 $hours시간' : '$days일';
  if (d.inHours > 0) {
    return minutes > 0 ? '${d.inHours}시간 $minutes분' : '${d.inHours}시간';
  }
  if (d.inMinutes > 0) return '${d.inMinutes}분';
  return '${d.inSeconds}초';
}

const _weekdays = ['월', '화', '수', '목', '금', '토', '일'];

String _two(int n) => n.toString().padLeft(2, '0');

/// 오늘이면 "15:30", 내일이면 "내일 15:30", 일주일 안이면 "수 10:27", 그 밖엔 "10/12 10:27"
String formatClock(DateTime t, DateTime now) {
  final hm = '${_two(t.hour)}:${_two(t.minute)}';
  final today = DateTime(now.year, now.month, now.day);
  final day = DateTime(t.year, t.month, t.day);
  final days = day.difference(today).inDays;
  if (days == 0) return hm;
  if (days == 1) return '내일 $hm';
  if (days > 1 && days < 7) return '${_weekdays[t.weekday - 1]} $hm';
  return '${t.month}/${t.day} $hm';
}

/// "2시간 19분 후 리셋" 또는 (절대 시각 설정 시) "수 10:27 리셋"
String formatReset(DateTime reset, DateTime now, {required bool absolute}) =>
    absolute
    ? '${formatClock(reset, now)} 리셋'
    : '${formatRemaining(reset.difference(now))} 후 리셋';

/// "방금 전", "5분 전", "3시간 전", "2일 전"
String formatAgo(DateTime t, DateTime now) {
  final d = now.difference(t);
  if (d.inMinutes < 1) return '방금 전';
  if (d.inHours < 1) return '${d.inMinutes}분 전';
  if (d.inDays < 1) return '${d.inHours}시간 전';
  return '${d.inDays}일 전';
}

String paceText(Pace p, DateTime now) => switch (p.kind) {
  PaceKind.unknown => '',
  PaceKind.plenty => '여유 있음',
  PaceKind.onPace => '적정 페이스',
  PaceKind.runsOut => '${formatClock(p.at!, now)} 소진 예상',
  PaceKind.exhausted => '모두 사용함',
};
