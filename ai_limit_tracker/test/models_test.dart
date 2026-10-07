import 'package:ai_limit_tracker/models.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  final now = DateTime(2026, 10, 7, 12, 0);

  group('nextPeriodic', () {
    test('과거 기준점은 앞으로 굴린다', () {
      final anchor = DateTime(2026, 9, 1, 9, 0); // 화요일
      final next = nextPeriodic(anchor, kWeek, now);
      expect(next, DateTime(2026, 10, 13, 9, 0));
    });

    test('미래 기준점은 now 직후 회차로 당긴다', () {
      final anchor = DateTime(2026, 10, 20, 9, 0);
      expect(nextPeriodic(anchor, kWeek, now), DateTime(2026, 10, 13, 9, 0));
    });

    test('정확히 리셋 시각이면 다음 주', () {
      final anchor = DateTime(2026, 9, 30, 12, 0);
      expect(nextPeriodic(anchor, kWeek, now), DateTime(2026, 10, 14, 12, 0));
    });
  });

  test('nextClockTime 은 지난 시각이면 내일', () {
    expect(nextClockTime(15, 30, now), DateTime(2026, 10, 7, 15, 30));
    expect(nextClockTime(9, 0, now), DateTime(2026, 10, 8, 9, 0));
  });

  group('세션', () {
    test('시작하면 5시간 뒤 리셋, 지나면 사용 가능', () {
      final s = ServiceState(id: 'claude', name: 'Claude Code');
      expect(s.availability(now), Availability.available);
      s.startSession(now);
      expect(s.sessionResetAt, now.add(const Duration(hours: 5)));
      expect(s.availability(now), Availability.inSession);
      s.setSessionLimited(true, now);
      expect(s.availability(now), Availability.sessionLimited);
      expect(s.blockedUntil(now), now.add(const Duration(hours: 5)));
      final later = now.add(const Duration(hours: 5, seconds: 1));
      expect(s.availability(later), Availability.available);
      expect(s.effectiveSessionPct(later), isNull);
    });

    test('사용량 입력 시 세션이 없으면 새로 시작', () {
      final s = ServiceState(id: 'codex', name: 'Codex');
      s.setSessionPct(40, now);
      expect(s.sessionActive(now), isTrue);
      expect(s.effectiveSessionPct(now), 40);
    });

    test('세션 끝난 뒤 리셋 시각 입력하면 이전 기록은 지운다', () {
      final s = ServiceState(id: 'codex', name: 'Codex');
      s.setSessionLimited(true, now);
      final later = now.add(const Duration(hours: 6));
      s.setSessionReset(later.add(const Duration(hours: 2)), later);
      expect(s.sessionLimited, isFalse);
      expect(s.sessionPct, isNull);
    });
  });

  group('주간', () {
    test('리셋 시각 없으면 한도 표시 불가', () {
      final s = ServiceState(id: 'claude', name: 'Claude Code');
      expect(s.setWeeklyLimited(true, now), isFalse);
      expect(s.setWeeklyPct(50, now), isFalse);
    });

    test('한도 도달은 다음 주간 리셋까지', () {
      final s = ServiceState(id: 'claude', name: 'Claude Code');
      s.setWeeklyReset(DateTime(2026, 10, 9, 10, 0), now);
      expect(s.setWeeklyLimited(true, now), isTrue);
      expect(s.availability(now), Availability.weeklyLimited);
      expect(s.blockedUntil(now), DateTime(2026, 10, 9, 10, 0));
      final after = DateTime(2026, 10, 9, 10, 1);
      expect(s.availability(after), Availability.available);
      expect(s.effectiveWeeklyPct(after), isNull);
      expect(s.nextWeeklyReset(after), DateTime(2026, 10, 16, 10, 0));
    });

    test('세션·주간 둘 다 막히면 늦게 풀리는 쪽', () {
      final s = ServiceState(id: 'claude', name: 'Claude Code');
      s.setWeeklyReset(DateTime(2026, 10, 7, 14, 0), now);
      s.setWeeklyLimited(true, now);
      s.setSessionLimited(true, now); // 17:00 리셋
      expect(s.blockedUntil(now), DateTime(2026, 10, 7, 17, 0));
    });
  });

  test('JSON 왕복 + 깨진 값은 초기 상태', () {
    final st = TrackerState.initial();
    st.services[0].startSession(now);
    st.services[0].setSessionPct(30, now);
    st.services[1].setWeeklyReset(DateTime(2026, 10, 10, 9, 0), now);
    final back = TrackerState.decode(st.encode());
    expect(back.services.map((s) => s.id), ['claude', 'codex']);
    expect(back.services[0].sessionResetAt, st.services[0].sessionResetAt);
    expect(back.services[0].sessionPct, 30);
    expect(back.services[1].weeklyAnchor, DateTime(2026, 10, 10, 9, 0));
    expect(TrackerState.decode('{oops').services.length, 2);
    expect(TrackerState.decode(null).services.length, 2);
  });

  test('남은 시간 표시', () {
    expect(formatRemaining(const Duration(hours: 2, minutes: 13)), '2시간 13분');
    expect(formatRemaining(const Duration(days: 3, hours: 4)), '3일 4시간');
    expect(formatRemaining(const Duration(seconds: 45)), '45초');
    expect(formatClock(DateTime(2026, 10, 7, 15, 30), now), '15:30');
    expect(formatClock(DateTime(2026, 10, 11, 9, 5), now), '10/11(일) 09:05');
  });
}
