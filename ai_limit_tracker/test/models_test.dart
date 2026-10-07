import 'package:ai_limit_tracker/models.dart';
import 'package:ai_limit_tracker/ui/stats_page.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  final now = DateTime(2026, 10, 7, 12, 0); // 수요일

  test('nextPeriodic 은 기준점을 앞뒤로 굴려 now 직후 회차를 찾는다', () {
    expect(
      nextPeriodic(DateTime(2026, 9, 1, 9), kWeekLength, now),
      DateTime(2026, 10, 13, 9),
    );
    expect(
      nextPeriodic(DateTime(2026, 10, 20, 9), kWeekLength, now),
      DateTime(2026, 10, 13, 9),
    );
    expect(
      nextPeriodic(DateTime(2026, 9, 30, 12), kWeekLength, now),
      DateTime(2026, 10, 14, 12),
    );
  });

  test('nextClockTime 은 지난 시각이면 내일', () {
    expect(nextClockTime(15, 30, now), DateTime(2026, 10, 7, 15, 30));
    expect(nextClockTime(9, 0, now), DateTime(2026, 10, 8, 9, 0));
  });

  group('5시간 창', () {
    test('리셋 전엔 기록값, 리셋 후엔 100% 남음', () {
      final w = LimitWindow(WindowKind.session);
      w.record(
        usedPct: 40,
        resetAt: now.add(const Duration(hours: 3)),
        now: now,
      );
      expect(w.remainingNow(now), 60);
      expect(w.currentReset(now), now.add(const Duration(hours: 3)));
      final later = now.add(const Duration(hours: 3, minutes: 1));
      expect(w.remainingNow(later), 100);
      expect(w.currentReset(later), isNull);
    });

    test('페이스: 2시간 만에 60% 쓰면 리셋 전에 소진 예상', () {
      final w = LimitWindow(WindowKind.session);
      // 창 시작 10:00, 리셋 15:00, 지금 12:00 에 60% 사용
      w.record(usedPct: 60, resetAt: DateTime(2026, 10, 7, 15), now: now);
      final p = w.pace(now);
      expect(p.kind, PaceKind.runsOut);
      expect(p.at, DateTime(2026, 10, 7, 13, 20)); // 10:00 + 2h × 100/60
      expect(paceText(p, now), '13:20 소진 예상');
    });

    test('페이스: 천천히 쓰면 여유 있음, 100%면 모두 사용함', () {
      final w = LimitWindow(WindowKind.session);
      w.record(usedPct: 10, resetAt: DateTime(2026, 10, 7, 15), now: now);
      expect(w.pace(now).kind, PaceKind.plenty);
      w.record(usedPct: 100, now: now);
      expect(w.pace(now).kind, PaceKind.exhausted);
    });
  });

  group('주간 창', () {
    test('리셋이 지나면 사용량 0 으로 보고 다음 주 리셋을 보여준다', () {
      final w = LimitWindow(WindowKind.weekly);
      w.record(usedPct: 91, resetAt: DateTime(2026, 10, 8, 10), now: now);
      expect(w.remainingNow(now), 9);
      final after = DateTime(2026, 10, 8, 10, 1);
      expect(w.remainingNow(after), 100);
      expect(w.currentReset(after), DateTime(2026, 10, 15, 10));
    });

    test('페이스: 6일 중 하루 만에 50% 쓰면 소진 예상 시각 계산', () {
      final w = LimitWindow(WindowKind.weekly);
      // 리셋 10/13 12:00 → 창 시작 10/6 12:00, 지금 10/7 12:00 (1일 경과)
      w.record(usedPct: 50, resetAt: DateTime(2026, 10, 13, 12), now: now);
      expect(w.pace(now).at, DateTime(2026, 10, 8, 12));
    });
  });

  group('TrackerState', () {
    test('급한 순 정렬: 소진이 빠른 서비스가 먼저', () {
      final s = TrackerState.initial();
      s.record(
        'claude',
        WindowKind.session,
        usedPct: 80,
        resetAt: DateTime(2026, 10, 7, 15),
        now: now,
      );
      s.record(
        'codex',
        WindowKind.weekly,
        usedPct: 10,
        resetAt: DateTime(2026, 10, 13, 12),
        now: now,
      );
      expect(s.ordered(now).map((x) => x.id), ['claude', 'codex']);
      s.settings.urgentFirst = false;
      s.settings.customOrder = ['codex', 'claude'];
      expect(s.ordered(now).map((x) => x.id), ['codex', 'claude']);
    });

    test('주간 잔여 알림은 기준 이하일 때 창마다 한 번만', () {
      final s = TrackerState.initial();
      s.settings.alertFor('codex')
        ..enabled = true
        ..threshold = 10;
      s.record(
        'codex',
        WindowKind.weekly,
        usedPct: 85,
        resetAt: DateTime(2026, 10, 9, 10),
        now: now,
      );
      expect(s.takeThresholdAlerts(now), isEmpty);
      s.record('codex', WindowKind.weekly, usedPct: 92, now: now);
      final alerts = s.takeThresholdAlerts(now);
      expect(alerts.single.$2, 8);
      s.record('codex', WindowKind.weekly, usedPct: 95, now: now);
      expect(s.takeThresholdAlerts(now), isEmpty);
    });

    test('JSON 왕복, 옛 형식·깨진 값은 초기 상태', () {
      final s = TrackerState.initial();
      s.record(
        'claude',
        WindowKind.session,
        usedPct: 30,
        resetAt: DateTime(2026, 10, 7, 15),
        now: now,
      );
      s.service('claude').plan = 'Max 20x';
      s.settings.absoluteTime = true;
      final back = TrackerState.decode(s.encode());
      expect(back.service('claude').session.usedPct, 30);
      expect(back.service('claude').plan, 'Max 20x');
      expect(back.settings.absoluteTime, isTrue);
      expect(back.history.single.used, 30);
      expect(TrackerState.decode('{"v":1,"services":[]}').history, isEmpty);
      expect(TrackerState.decode('{oops').services.length, 2);
    });

    test('하루별 최고 사용량', () {
      final s = TrackerState.initial();
      s.record('claude', WindowKind.session, usedPct: 40, now: now);
      s.record('claude', WindowKind.session, usedPct: 100, now: now);
      s.record(
        'claude',
        WindowKind.session,
        usedPct: 20,
        now: now.subtract(const Duration(days: 2)),
      );
      final peaks = dailyPeaks(s.history, 'claude', WindowKind.session, now);
      expect(peaks, [null, null, null, null, 20, null, 100]);
    });
  });

  test('표시 형식', () {
    expect(formatRemaining(const Duration(hours: 2, minutes: 19)), '2시간 19분');
    expect(formatRemaining(const Duration(days: 1, hours: 14)), '1일 14시간');
    expect(formatClock(DateTime(2026, 10, 7, 15, 30), now), '15:30');
    expect(formatClock(DateTime(2026, 10, 8, 9, 5), now), '내일 09:05');
    expect(formatClock(DateTime(2026, 10, 10, 23, 0), now), '토 23:00');
    expect(
      formatReset(DateTime(2026, 10, 7, 14, 19), now, absolute: false),
      '2시간 19분 후 리셋',
    );
    expect(
      formatReset(DateTime(2026, 10, 7, 14, 19), now, absolute: true),
      '14:19 리셋',
    );
  });
}
