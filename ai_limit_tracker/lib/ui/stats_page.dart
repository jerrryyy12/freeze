import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../app_model.dart';
import '../models.dart';
import 'common.dart';

const _weekdayShort = ['월', '화', '수', '목', '금', '토', '일'];

/// 최근 [days]일의 하루별 최고 사용량(%) — 오래된 날 → 오늘. 기록 없는 날은 null.
List<int?> dailyPeaks(
  List<Snapshot> history,
  String service,
  WindowKind kind,
  DateTime now, {
  int days = 7,
}) {
  final today = DateTime(now.year, now.month, now.day);
  final out = List<int?>.filled(days, null);
  for (final h in history) {
    if (h.service != service || h.kind != kind) continue;
    final d = DateTime(h.at.year, h.at.month, h.at.day);
    final idx = days - 1 - today.difference(d).inDays;
    if (idx < 0 || idx >= days) continue;
    out[idx] = math.max(out[idx] ?? 0, h.used);
  }
  return out;
}

class StatsPage extends StatelessWidget {
  const StatsPage({super.key, required this.model});

  final AppModel model;

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: model,
      builder: (context, _) {
        final s = model.state!;
        final now = DateTime.now();
        return ListView(
          padding: const EdgeInsets.fromLTRB(16, 52, 16, 120),
          children: [
            PageHeader(title: '통계', trailing: '기록 ${s.history.length}건'),
            for (final svc in s.ordered(now))
              _ServiceStats(state: s, svc: svc, now: now),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 8),
              child: Text(
                '사용량을 입력할 때마다 기록이 쌓여 통계에 반영돼요.',
                style: TextStyle(fontSize: 14, color: Palette.sub(context)),
              ),
            ),
          ],
        );
      },
    );
  }
}

class _ServiceStats extends StatelessWidget {
  const _ServiceStats({
    required this.state,
    required this.svc,
    required this.now,
  });

  final TrackerState state;
  final ServiceState svc;
  final DateTime now;

  @override
  Widget build(BuildContext context) {
    final text = Palette.text(context);
    final sub = Palette.sub(context);
    final weeklyUsed = svc.weekly.usedNow(now);
    final reset = svc.weekly.currentReset(now);
    String? perDay;
    if (weeklyUsed != null && reset != null) {
      final start = reset.subtract(kWeekLength);
      final days = math.max(1.0, now.difference(start).inMinutes / (24 * 60));
      perDay = '${(weeklyUsed / days).toStringAsFixed(1)}%';
    }
    final peaks = dailyPeaks(state.history, svc.id, WindowKind.session, now);
    final hitDays = peaks.where((p) => p != null && p >= 100).length;

    Widget stat(String label, String value) => Expanded(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: TextStyle(fontSize: 13, color: sub)),
          const SizedBox(height: 4),
          Text(
            value,
            style: TextStyle(
              fontSize: 22,
              fontWeight: FontWeight.w700,
              color: text,
            ),
          ),
        ],
      ),
    );

    return AppCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              ServiceIcon(svc.id, size: 24),
              const SizedBox(width: 12),
              Text(
                svc.name,
                style: TextStyle(
                  fontSize: 20,
                  fontWeight: FontWeight.w700,
                  color: text,
                ),
              ),
            ],
          ),
          const SizedBox(height: 18),
          Row(
            children: [
              stat('이번 주 사용', weeklyUsed == null ? '—' : '$weeklyUsed%'),
              stat('하루 평균', perDay ?? '—'),
              stat('한도 도달(7일)', '$hitDays일'),
            ],
          ),
          const SizedBox(height: 22),
          Text(
            '최근 7일 · 5시간 한도 최고 사용량',
            style: TextStyle(fontSize: 14, color: sub),
          ),
          const SizedBox(height: 12),
          SizedBox(
            height: 130,
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                for (var i = 0; i < peaks.length; i++)
                  Expanded(
                    child: _Bar(
                      value: peaks[i],
                      label:
                          _weekdayShort[now
                                  .subtract(
                                    Duration(days: peaks.length - 1 - i),
                                  )
                                  .weekday -
                              1],
                      today: i == peaks.length - 1,
                    ),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _Bar extends StatelessWidget {
  const _Bar({required this.value, required this.label, required this.today});

  final int? value;
  final String label;
  final bool today;

  @override
  Widget build(BuildContext context) {
    final v = value;
    final sub = Palette.sub(context);
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 5),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.end,
        children: [
          Text(
            v == null ? '' : '$v',
            style: TextStyle(fontSize: 11, color: sub),
          ),
          const SizedBox(height: 4),
          Container(
            height: v == null ? 4 : math.max(4, 80 * v / 100),
            decoration: BoxDecoration(
              color: v == null
                  ? Palette.track(context)
                  : Palette.forRemaining(100 - v),
              borderRadius: BorderRadius.circular(5),
            ),
          ),
          const SizedBox(height: 6),
          Text(
            label,
            style: TextStyle(
              fontSize: 12,
              fontWeight: today ? FontWeight.w700 : FontWeight.w400,
              color: today ? Palette.text(context) : sub,
            ),
          ),
        ],
      ),
    );
  }
}
