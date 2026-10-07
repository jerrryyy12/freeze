import 'dart:async';

import 'package:flutter/material.dart';

import '../models.dart';
import '../status.dart';
import '../storage.dart';
import 'common.dart';

/// 서비스 상태 — 공식 상태 페이지를 2분마다 확인.
class StatusPage extends StatefulWidget {
  const StatusPage({super.key, this.fetcher = fetchStatus});

  final Future<ServiceStatus> Function(StatusSource) fetcher;

  @override
  State<StatusPage> createState() => _StatusPageState();
}

class _StatusPageState extends State<StatusPage> {
  List<ServiceStatus>? _list;
  DateTime? _checked;
  bool _loading = false;
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    _refresh();
    _timer = Timer.periodic(const Duration(minutes: 2), (_) => _refresh());
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  Future<void> _refresh() async {
    if (_loading) return;
    setState(() => _loading = true);
    final list = await Future.wait(statusSources.map(widget.fetcher));
    if (!mounted) return;
    setState(() {
      _list = list;
      _checked = DateTime.now();
      _loading = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    final list = _list;
    final now = DateTime.now();
    final checked = _checked;
    return RefreshIndicator(
      onRefresh: _refresh,
      child: ListView(
        padding: const EdgeInsets.fromLTRB(16, 52, 16, 120),
        children: [
          PageHeader(
            title: '상태',
            trailing: checked == null
                ? '확인 중…'
                : '${formatClock(checked, now)} 확인',
          ),
          if (list == null)
            const Padding(
              padding: EdgeInsets.all(40),
              child: Center(child: CircularProgressIndicator()),
            )
          else
            _body(context, list, now),
        ],
      ),
    );
  }

  Widget _body(BuildContext context, List<ServiceStatus> list, DateTime now) {
    final issues = list.where((s) => s.hasIssue).toList();
    final failed = list.where((s) => s.error != null).toList();
    final text = Palette.text(context);
    final sub = Palette.sub(context);

    final String badge;
    final Color badgeColor;
    if (issues.isNotEmpty) {
      badge = '${issues.length}개 서비스에 문제 보고';
      badgeColor = Palette.amber;
    } else if (failed.length == list.length) {
      badge = '상태를 불러오지 못했어요';
      badgeColor = Palette.sub(context);
    } else {
      badge = '모든 서비스 정상';
      badgeColor = Palette.green;
    }

    final headline = issues.isNotEmpty
        ? (issues.first.activeIncident?.name ??
              '${issues.first.source.name}에 ${healthLabel(issues.first.health)}')
        : failed.length == list.length
        ? '네트워크를 확인하고 아래로 당겨 새로고침하세요'
        : 'Codex 와 Claude Code 모두 정상 운영 중이에요';

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 7),
          decoration: BoxDecoration(
            color: badgeColor.withValues(alpha: 0.14),
            borderRadius: BorderRadius.circular(20),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.circle, size: 10, color: badgeColor),
              const SizedBox(width: 8),
              Text(
                badge,
                style: TextStyle(
                  fontSize: 15,
                  fontWeight: FontWeight.w600,
                  color: text,
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 14),
        Text(
          headline,
          style: TextStyle(
            fontSize: 30,
            height: 1.2,
            fontWeight: FontWeight.w800,
            letterSpacing: -0.5,
            color: text,
          ),
        ),
        const SizedBox(height: 20),
        for (final s in list) ...[
          Divider(height: 1, color: Palette.line(context)),
          _ServiceStatusRow(status: s, now: now),
        ],
        Divider(height: 1, color: Palette.line(context)),
        const SizedBox(height: 18),
        Text(
          '공식 상태 페이지 기준 · 2분마다 확인',
          style: TextStyle(fontSize: 14, color: sub),
        ),
      ],
    );
  }
}

class _ServiceStatusRow extends StatelessWidget {
  const _ServiceStatusRow({required this.status, required this.now});

  final ServiceStatus status;
  final DateTime now;

  Color _healthColor(Health h) => switch (h) {
    Health.operational => Palette.green,
    Health.maintenance => Palette.codex,
    Health.degraded => Palette.amber,
    Health.partialOutage || Health.majorOutage => Palette.red,
    Health.unknown => Colors.grey,
  };

  Color _dayColor(BuildContext c, int lv) => switch (lv) {
    0 => Palette.track(c),
    1 => Palette.amber,
    2 => const Color(0xFFE0782F),
    _ => Palette.red,
  };

  @override
  Widget build(BuildContext context) {
    final s = status;
    final text = Palette.text(context);
    final sub = Palette.sub(context);
    final color = _healthColor(s.health);
    final last = s.lastIncident;
    return InkWell(
      onTap: () => Storage.openUrl(s.source.pageUrl),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 18),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                ServiceIcon(s.source.serviceId, size: 24),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    s.source.name,
                    style: TextStyle(
                      fontSize: 20,
                      fontWeight: FontWeight.w700,
                      color: text,
                    ),
                  ),
                ),
                Icon(Icons.circle, size: 10, color: color),
                const SizedBox(width: 6),
                Text(
                  healthLabel(s.health),
                  style: TextStyle(fontSize: 16, color: color),
                ),
              ],
            ),
            const SizedBox(height: 14),
            Row(
              children: [
                for (var i = 0; i < s.days.length; i++) ...[
                  if (i > 0) const SizedBox(width: 3),
                  Expanded(
                    child: Container(
                      height: 30,
                      decoration: BoxDecoration(
                        color: _dayColor(context, s.days[i]),
                        borderRadius: BorderRadius.circular(3),
                      ),
                    ),
                  ),
                ],
              ],
            ),
            const SizedBox(height: 8),
            Row(
              children: [
                Text(
                  s.error != null
                      ? '불러오기 실패 · 눌러서 공식 페이지 열기'
                      : last == null
                      ? '최근 장애 없음'
                      : '마지막 장애 ${last.month}/${last.day}',
                  style: TextStyle(fontSize: 14, color: sub),
                ),
                const Spacer(),
                Text('오늘', style: TextStyle(fontSize: 14, color: sub)),
              ],
            ),
            if (s.activeIncident != null) ...[
              const SizedBox(height: 10),
              Text(
                s.activeIncident!.name,
                style: const TextStyle(
                  fontSize: 15,
                  fontWeight: FontWeight.w600,
                  color: Palette.amber,
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
