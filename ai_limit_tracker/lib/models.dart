import 'dart:convert';

/// 주간 한도 주기.
const Duration kWeek = Duration(days: 7);

/// 추적 대상 하나(Claude Code / Codex)의 한도 상태.
///
/// 저장되는 값은 "사실"(리셋 시각, 기록한 사용량 등)뿐이고,
/// 지금 사용 가능한지·남은 시간 같은 건 매번 현재 시각으로 계산한다.
/// 안드로이드 위젯(Kotlin)도 같은 JSON 을 읽어 같은 규칙으로 계산하므로
/// 필드 이름을 바꾸면 LimitWidgetProvider.kt 도 함께 바꿔야 한다.
class ServiceState {
  ServiceState({
    required this.id,
    required this.name,
    this.sessionHours = 5,
    this.sessionResetAt,
    this.sessionPct,
    this.sessionLimited = false,
    this.weeklyAnchor,
    this.weeklyPct,
    this.weeklyPctUntil,
    this.weeklyLimitedUntil,
  });

  final String id;
  final String name;

  /// 세션(롤링) 한도 길이. 두 서비스 모두 5시간.
  final int sessionHours;

  /// 현재 세션이 리셋되는 시각. null 이면 세션 시작 전.
  DateTime? sessionResetAt;

  /// 세션 사용량(%) — 사용자가 /usage, /status 보고 입력.
  int? sessionPct;

  /// 세션 한도 도달 여부(현재 세션에만 유효).
  bool sessionLimited;

  /// 알고 있는 주간 리셋 시각 하나. 7일 주기로 앞뒤로 굴려서 다음 리셋을 구한다.
  DateTime? weeklyAnchor;

  /// 주간 사용량(%)과, 그 값이 유효한 기한(기록 당시의 다음 주간 리셋).
  int? weeklyPct;
  DateTime? weeklyPctUntil;

  /// 주간 한도 도달 시 막혀 있는 기한.
  DateTime? weeklyLimitedUntil;

  // ───────────── 계산 ─────────────

  bool sessionActive(DateTime now) =>
      sessionResetAt != null && now.isBefore(sessionResetAt!);

  bool sessionBlocked(DateTime now) => sessionActive(now) && sessionLimited;

  /// 이번 세션 사용량. 세션이 끝났으면 null.
  int? effectiveSessionPct(DateTime now) =>
      sessionActive(now) ? sessionPct : null;

  DateTime? nextWeeklyReset(DateTime now) =>
      weeklyAnchor == null ? null : nextPeriodic(weeklyAnchor!, kWeek, now);

  int? effectiveWeeklyPct(DateTime now) =>
      (weeklyPct != null &&
          weeklyPctUntil != null &&
          now.isBefore(weeklyPctUntil!))
      ? weeklyPct
      : null;

  bool weeklyBlocked(DateTime now) =>
      weeklyLimitedUntil != null && now.isBefore(weeklyLimitedUntil!);

  /// 지금 막혀 있다면 풀리는 시각(둘 다 막혔으면 더 늦은 쪽). 사용 가능하면 null.
  DateTime? blockedUntil(DateTime now) {
    DateTime? until;
    if (weeklyBlocked(now)) until = weeklyLimitedUntil;
    if (sessionBlocked(now) &&
        (until == null || sessionResetAt!.isAfter(until))) {
      until = sessionResetAt;
    }
    return until;
  }

  Availability availability(DateTime now) {
    if (weeklyBlocked(now)) return Availability.weeklyLimited;
    if (sessionBlocked(now)) return Availability.sessionLimited;
    if (sessionActive(now)) return Availability.inSession;
    return Availability.available;
  }

  // ───────────── 조작 ─────────────

  /// 지금부터 세션 시작(첫 메시지를 보낸 시점).
  void startSession(DateTime now) {
    sessionResetAt = now.add(Duration(hours: sessionHours));
    sessionPct = null;
    sessionLimited = false;
  }

  /// CLI 에 표시된 세션 리셋 시각을 직접 입력.
  void setSessionReset(DateTime resetAt, DateTime now) {
    final wasActive = sessionActive(now);
    sessionResetAt = resetAt;
    if (!wasActive) {
      sessionPct = null;
      sessionLimited = false;
    }
  }

  void clearSession() {
    sessionResetAt = null;
    sessionPct = null;
    sessionLimited = false;
  }

  /// 세션 한도 도달 표시. 세션이 없으면 지금부터 세션을 잡는다.
  void setSessionLimited(bool limited, DateTime now) {
    if (limited && !sessionActive(now)) startSession(now);
    sessionLimited = limited;
    if (limited) sessionPct = 100;
  }

  void setSessionPct(int pct, DateTime now) {
    if (!sessionActive(now)) startSession(now);
    sessionPct = pct.clamp(0, 100);
  }

  void setWeeklyReset(DateTime resetAt, DateTime now) {
    weeklyAnchor = resetAt;
    // 리셋 시각이 바뀌면 그 기준으로 기록해 둔 기한도 맞춘다.
    final next = nextWeeklyReset(now)!;
    if (weeklyPct != null) weeklyPctUntil = next;
    if (weeklyBlocked(now)) weeklyLimitedUntil = next;
  }

  /// 주간 리셋 시각을 모르면 false 를 반환(리셋 시각 설정이 먼저 필요).
  bool setWeeklyPct(int pct, DateTime now) {
    final next = nextWeeklyReset(now);
    if (next == null) return false;
    weeklyPct = pct.clamp(0, 100);
    weeklyPctUntil = next;
    return true;
  }

  bool setWeeklyLimited(bool limited, DateTime now) {
    if (!limited) {
      weeklyLimitedUntil = null;
      return true;
    }
    final next = nextWeeklyReset(now);
    if (next == null) return false;
    weeklyLimitedUntil = next;
    weeklyPct = 100;
    weeklyPctUntil = next;
    return true;
  }

  // ───────────── 직렬화 ─────────────

  Map<String, dynamic> toJson() => {
    'id': id,
    'name': name,
    'sessionHours': sessionHours,
    'sessionResetAt': _ms(sessionResetAt),
    'sessionPct': sessionPct,
    'sessionLimited': sessionLimited,
    'weeklyAnchor': _ms(weeklyAnchor),
    'weeklyPct': weeklyPct,
    'weeklyPctUntil': _ms(weeklyPctUntil),
    'weeklyLimitedUntil': _ms(weeklyLimitedUntil),
  };

  factory ServiceState.fromJson(Map<String, dynamic> j) => ServiceState(
    id: j['id'] as String,
    name: j['name'] as String,
    sessionHours: (j['sessionHours'] as num?)?.toInt() ?? 5,
    sessionResetAt: _dt(j['sessionResetAt']),
    sessionPct: (j['sessionPct'] as num?)?.toInt(),
    sessionLimited: j['sessionLimited'] as bool? ?? false,
    weeklyAnchor: _dt(j['weeklyAnchor']),
    weeklyPct: (j['weeklyPct'] as num?)?.toInt(),
    weeklyPctUntil: _dt(j['weeklyPctUntil']),
    weeklyLimitedUntil: _dt(j['weeklyLimitedUntil']),
  );
}

enum Availability { available, inSession, sessionLimited, weeklyLimited }

class TrackerState {
  TrackerState(this.services);

  final List<ServiceState> services;

  static TrackerState initial() => TrackerState([
    ServiceState(id: 'claude', name: 'Claude Code'),
    ServiceState(id: 'codex', name: 'Codex'),
  ]);

  String encode() => jsonEncode({
    'v': 1,
    'services': services.map((s) => s.toJson()).toList(),
  });

  /// 저장된 JSON 을 읽는다. 없거나 깨졌으면 초기 상태.
  /// 기본 두 서비스는 항상 존재하도록 보정한다.
  static TrackerState decode(String? raw) {
    final base = initial();
    if (raw == null || raw.isEmpty) return base;
    try {
      final j = jsonDecode(raw) as Map<String, dynamic>;
      final loaded = {
        for (final s in (j['services'] as List))
          (s as Map<String, dynamic>)['id'] as String: ServiceState.fromJson(s),
      };
      return TrackerState([for (final s in base.services) loaded[s.id] ?? s]);
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

/// "2시간 13분", "3일 4시간", "45초"
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

/// 오늘이면 "15:30", 아니면 "10/12(일) 15:30"
String formatClock(DateTime t, DateTime now) {
  final hm = '${_two(t.hour)}:${_two(t.minute)}';
  final sameDay =
      t.year == now.year && t.month == now.month && t.day == now.day;
  if (sameDay) return hm;
  return '${t.month}/${t.day}(${_weekdays[t.weekday - 1]}) $hm';
}
