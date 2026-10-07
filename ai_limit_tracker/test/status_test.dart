import 'package:ai_limit_tracker/status.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  final now = DateTime(2026, 10, 7, 12);
  final codex = statusSources.firstWhere((s) => s.serviceId == 'codex');

  test('구성요소 상태와 14일 사건 칸을 계산한다', () {
    final summary = {
      'status': {'indicator': 'minor'},
      'components': [
        {'name': 'Codex', 'status': 'degraded_performance'},
        {'name': 'ChatGPT', 'status': 'operational'},
      ],
      'incidents': [
        {
          'id': 'a',
          'name': 'Codex in ChatGPT Desktop degraded',
          'impact': 'minor',
          'status': 'investigating',
          'created_at': '2026-10-07T01:00:00Z',
          'components': [
            {'name': 'Codex'},
          ],
        },
      ],
    };
    final incidents = {
      'incidents': [
        {
          'id': 'b',
          'name': 'Elevated errors',
          'impact': 'critical',
          'status': 'resolved',
          'created_at': '2026-10-04T12:00:00Z',
          'components': [
            {'name': 'Codex CLI'},
          ],
        },
        {
          'id': 'c',
          'name': 'Images down',
          'impact': 'major',
          'status': 'resolved',
          'created_at': '2026-10-05T12:00:00Z',
          'components': [
            {'name': 'Image Generation'},
          ],
        },
      ],
    };
    final st = parseStatus(codex, summary, incidents, now);
    expect(st.health, Health.degraded);
    expect(st.hasIssue, isTrue);
    expect(st.activeIncident?.name, 'Codex in ChatGPT Desktop degraded');
    expect(st.days.length, statusDays);
    expect(st.days.last, 1); // 오늘 경미
    expect(st.days[statusDays - 4], 3); // 3일 전 심각
    expect(st.days[statusDays - 3], 0); // 다른 구성요소 사건은 제외
  });

  test('구성요소가 없으면 페이지 전체 지표를 쓴다', () {
    final st = parseStatus(
      codex,
      {
        'status': {'indicator': 'none'},
      },
      {'incidents': []},
      now,
    );
    expect(st.health, Health.operational);
    expect(st.hasIssue, isFalse);
    expect(st.lastIncident, isNull);
  });
}
