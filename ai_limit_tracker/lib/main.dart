import 'dart:async';

import 'package:flutter/material.dart';

import 'models.dart';
import 'storage.dart';

void main() {
  runApp(const TrackerApp());
}

class TrackerApp extends StatelessWidget {
  const TrackerApp({super.key});

  @override
  Widget build(BuildContext context) {
    const seed = Color(0xFF6750A4);
    return MaterialApp(
      title: 'AI 리미터',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(colorSchemeSeed: seed, useMaterial3: true),
      darkTheme: ThemeData(
        colorSchemeSeed: seed,
        brightness: Brightness.dark,
        useMaterial3: true,
      ),
      home: const HomePage(),
    );
  }
}

/// 서비스별 표시 색.
const _accent = {'claude': Color(0xFFD97757), 'codex': Color(0xFF10A37F)};

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  TrackerState? _state;
  Timer? _ticker;

  @override
  void initState() {
    super.initState();
    _init();
    _ticker = Timer.periodic(const Duration(seconds: 1), (_) {
      if (mounted) setState(() {});
    });
  }

  Future<void> _init() async {
    final s = await Storage.load();
    if (!mounted) return;
    setState(() => _state = s);
    // 리셋 알림을 받으려면 안드로이드 13+ 에서 권한이 필요.
    await Storage.requestNotificationPermission();
  }

  @override
  void dispose() {
    _ticker?.cancel();
    super.dispose();
  }

  void _update(void Function(DateTime now) change) {
    setState(() => change(DateTime.now()));
    Storage.save(_state!);
  }

  Future<void> _addWidget() async {
    final shown = await Storage.requestPinWidget();
    if (!mounted || shown) return;
    showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('홈 화면에 위젯 추가'),
        content: const Text(
          '홈 화면 빈 곳을 길게 누름 → 위젯 → "AI 리미터" 를 찾아 끌어다 놓으세요.\n\n'
          '위젯은 남은 시간을 실시간으로 보여주고, 리셋 시각이 되면 자동으로 "사용 가능"으로 바뀝니다.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('확인'),
          ),
        ],
      ),
    );
  }

  void _showHelp() {
    showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      isScrollControlled: true,
      builder: (ctx) => const _HelpSheet(),
    );
  }

  @override
  Widget build(BuildContext context) {
    final state = _state;
    return Scaffold(
      appBar: AppBar(
        title: const Text('AI 리미터'),
        actions: [
          IconButton(
            tooltip: '홈 화면에 위젯 추가',
            icon: const Icon(Icons.widgets_outlined),
            onPressed: _addWidget,
          ),
          IconButton(
            tooltip: '사용법',
            icon: const Icon(Icons.help_outline),
            onPressed: _showHelp,
          ),
        ],
      ),
      body: state == null
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.fromLTRB(12, 4, 12, 24),
              children: [
                for (final s in state.services)
                  ServiceCard(
                    service: s,
                    now: DateTime.now(),
                    onChange: _update,
                  ),
              ],
            ),
    );
  }
}

class ServiceCard extends StatelessWidget {
  const ServiceCard({
    super.key,
    required this.service,
    required this.now,
    required this.onChange,
  });

  final ServiceState service;
  final DateTime now;
  final void Function(void Function(DateTime now) change) onChange;

  @override
  Widget build(BuildContext context) {
    final s = service;
    final theme = Theme.of(context);
    final accent = _accent[s.id] ?? theme.colorScheme.primary;
    final avail = s.availability(now);

    return Card(
      margin: const EdgeInsets.symmetric(vertical: 8),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 14, 16, 12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                CircleAvatar(
                  radius: 16,
                  backgroundColor: accent,
                  child: Text(
                    s.name.characters.first,
                    style: const TextStyle(
                      color: Colors.white,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    s.name,
                    style: theme.textTheme.titleLarge?.copyWith(
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
                _StatusChip(avail: avail),
              ],
            ),
            _blockedBanner(context),
            const SizedBox(height: 8),
            _sessionSection(context, accent),
            const Divider(height: 24),
            _weeklySection(context, accent),
          ],
        ),
      ),
    );
  }

  Widget _blockedBanner(BuildContext context) {
    final until = service.blockedUntil(now);
    if (until == null) return const SizedBox.shrink();
    final cs = Theme.of(context).colorScheme;
    return Container(
      width: double.infinity,
      margin: const EdgeInsets.only(top: 10),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: cs.errorContainer,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Text(
        '${formatClock(until, now)}에 다시 사용 가능 · ${formatRemaining(until.difference(now))} 남음',
        style: TextStyle(
          color: cs.onErrorContainer,
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }

  Widget _sessionSection(BuildContext context, Color accent) {
    final s = service;
    final theme = Theme.of(context);
    final active = s.sessionActive(now);
    final pct = s.effectiveSessionPct(now);

    final Widget body;
    if (active) {
      final reset = s.sessionResetAt!;
      final total = Duration(hours: s.sessionHours).inSeconds;
      final left = reset.difference(now).inSeconds;
      final elapsedFrac = (1 - left / total).clamp(0.0, 1.0);
      body = Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '${formatClock(reset, now)} 리셋 · ${formatRemaining(reset.difference(now))} 남음',
            style: theme.textTheme.bodyLarge,
          ),
          const SizedBox(height: 6),
          _Bar(
            value: pct != null ? pct / 100 : elapsedFrac,
            color: s.sessionLimited ? theme.colorScheme.error : accent,
            label: pct != null
                ? '사용량 $pct%'
                : '시간 경과 ${(elapsedFrac * 100).round()}%',
          ),
        ],
      );
    } else {
      body = Text(
        '진행 중인 세션 없음 — 첫 요청을 보낼 때 "지금 시작"',
        style: theme.textTheme.bodyMedium?.copyWith(
          color: theme.colorScheme.onSurfaceVariant,
        ),
      );
    }

    return _Section(
      title: '${s.sessionHours}시간 세션 한도',
      body: body,
      actions: [
        if (!active)
          FilledButton.tonalIcon(
            onPressed: () => onChange(s.startSession),
            icon: const Icon(Icons.play_arrow, size: 18),
            label: const Text('지금 시작'),
          ),
        OutlinedButton.icon(
          onPressed: () => _pickSessionReset(context),
          icon: const Icon(Icons.schedule, size: 18),
          label: const Text('리셋 시각'),
        ),
        OutlinedButton.icon(
          onPressed: () => _pickSessionPct(context),
          icon: const Icon(Icons.percent, size: 18),
          label: const Text('사용량'),
        ),
        FilterChip(
          label: const Text('한도 도달'),
          selected: s.sessionBlocked(now),
          onSelected: (v) => onChange((now) => s.setSessionLimited(v, now)),
        ),
        if (active)
          IconButton(
            tooltip: '세션 지우기',
            onPressed: () => onChange((_) => s.clearSession()),
            icon: const Icon(Icons.close),
          ),
      ],
    );
  }

  Widget _weeklySection(BuildContext context, Color accent) {
    final s = service;
    final theme = Theme.of(context);
    final next = s.nextWeeklyReset(now);
    final pct = s.effectiveWeeklyPct(now);

    final Widget body;
    if (next == null) {
      body = Text(
        '주간 리셋 시각을 한 번 설정하면 매주 자동으로 계산합니다',
        style: theme.textTheme.bodyMedium?.copyWith(
          color: theme.colorScheme.onSurfaceVariant,
        ),
      );
    } else {
      body = Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '${formatClock(next, now)} 리셋 · ${formatRemaining(next.difference(now))} 남음',
            style: theme.textTheme.bodyLarge,
          ),
          if (pct != null) ...[
            const SizedBox(height: 6),
            _Bar(
              value: pct / 100,
              color: s.weeklyBlocked(now) ? theme.colorScheme.error : accent,
              label: '사용량 $pct%',
            ),
          ],
        ],
      );
    }

    return _Section(
      title: '주간 한도',
      body: body,
      actions: [
        OutlinedButton.icon(
          onPressed: () => _pickWeeklyReset(context),
          icon: const Icon(Icons.event, size: 18),
          label: Text(next == null ? '리셋 시각 설정' : '리셋 시각'),
        ),
        if (next != null) ...[
          OutlinedButton.icon(
            onPressed: () => _pickWeeklyPct(context),
            icon: const Icon(Icons.percent, size: 18),
            label: const Text('사용량'),
          ),
          FilterChip(
            label: const Text('주간 한도 도달'),
            selected: s.weeklyBlocked(now),
            onSelected: (v) => onChange((now) => s.setWeeklyLimited(v, now)),
          ),
        ],
      ],
    );
  }

  Future<void> _pickSessionReset(BuildContext context) async {
    final s = service;
    final init = s.sessionActive(now)
        ? TimeOfDay.fromDateTime(s.sessionResetAt!)
        : TimeOfDay.fromDateTime(now.add(Duration(hours: s.sessionHours)));
    final t = await showTimePicker(
      context: context,
      initialTime: init,
      helpText: '${s.name} 세션 리셋 시각',
    );
    if (t == null) return;
    onChange(
      (now) => s.setSessionReset(nextClockTime(t.hour, t.minute, now), now),
    );
  }

  Future<void> _pickSessionPct(BuildContext context) async {
    final s = service;
    final v = await _askPercent(
      context,
      '${s.name} 세션 사용량',
      s.effectiveSessionPct(now) ?? 0,
    );
    if (v == null) return;
    onChange((now) => s.setSessionPct(v, now));
  }

  Future<void> _pickWeeklyReset(BuildContext context) async {
    final s = service;
    final cur = s.nextWeeklyReset(now);
    final today = DateTime(now.year, now.month, now.day);
    final d = await showDatePicker(
      context: context,
      initialDate: cur != null ? DateTime(cur.year, cur.month, cur.day) : today,
      firstDate: today,
      lastDate: today.add(const Duration(days: 7)),
      helpText: '${s.name} 주간 리셋 날짜',
    );
    if (d == null || !context.mounted) return;
    final t = await showTimePicker(
      context: context,
      initialTime: cur != null
          ? TimeOfDay.fromDateTime(cur)
          : const TimeOfDay(hour: 9, minute: 0),
      helpText: '${s.name} 주간 리셋 시각',
    );
    if (t == null) return;
    final at = DateTime(d.year, d.month, d.day, t.hour, t.minute);
    onChange((now) => s.setWeeklyReset(at, now));
  }

  Future<void> _pickWeeklyPct(BuildContext context) async {
    final s = service;
    final v = await _askPercent(
      context,
      '${s.name} 주간 사용량',
      s.effectiveWeeklyPct(now) ?? 0,
    );
    if (v == null) return;
    onChange((now) => s.setWeeklyPct(v, now));
  }
}

Future<int?> _askPercent(BuildContext context, String title, int initial) {
  var value = initial.toDouble();
  return showDialog<int>(
    context: context,
    builder: (ctx) => StatefulBuilder(
      builder: (ctx, setLocal) => AlertDialog(
        title: Text(title),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              '${value.round()}%',
              style: Theme.of(ctx).textTheme.displaySmall,
            ),
            Slider(
              value: value,
              max: 100,
              divisions: 100,
              onChanged: (v) => setLocal(() => value = v),
            ),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                for (final step in const [-5, -1, 1, 5])
                  TextButton(
                    onPressed: () => setLocal(
                      () => value = (value + step).clamp(0, 100).toDouble(),
                    ),
                    child: Text(step > 0 ? '+$step' : '$step'),
                  ),
              ],
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('취소'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, value.round()),
            child: const Text('저장'),
          ),
        ],
      ),
    ),
  );
}

class _Section extends StatelessWidget {
  const _Section({
    required this.title,
    required this.body,
    required this.actions,
  });

  final String title;
  final Widget body;
  final List<Widget> actions;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          title,
          style: theme.textTheme.labelLarge?.copyWith(
            color: theme.colorScheme.primary,
          ),
        ),
        const SizedBox(height: 6),
        body,
        const SizedBox(height: 10),
        Wrap(
          spacing: 8,
          runSpacing: 4,
          crossAxisAlignment: WrapCrossAlignment.center,
          children: actions,
        ),
      ],
    );
  }
}

class _Bar extends StatelessWidget {
  const _Bar({required this.value, required this.color, required this.label});

  final double value;
  final Color color;
  final String label;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        ClipRRect(
          borderRadius: BorderRadius.circular(6),
          child: LinearProgressIndicator(
            value: value,
            minHeight: 8,
            color: color,
            backgroundColor: color.withValues(alpha: 0.18),
          ),
        ),
        const SizedBox(height: 4),
        Text(label, style: Theme.of(context).textTheme.bodySmall),
      ],
    );
  }
}

class _StatusChip extends StatelessWidget {
  const _StatusChip({required this.avail});

  final Availability avail;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final (String text, Color bg, Color fg) = switch (avail) {
      Availability.available => (
        '사용 가능',
        const Color(0xFF2E7D32),
        Colors.white,
      ),
      Availability.inSession => (
        '세션 중',
        cs.primaryContainer,
        cs.onPrimaryContainer,
      ),
      Availability.sessionLimited => ('세션 한도', cs.error, cs.onError),
      Availability.weeklyLimited => ('주간 한도', cs.error, cs.onError),
    };
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(20),
      ),
      child: Text(
        text,
        style: TextStyle(color: fg, fontWeight: FontWeight.w600, fontSize: 13),
      ),
    );
  }
}

class _HelpSheet extends StatelessWidget {
  const _HelpSheet();

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    Widget h(String t) => Padding(
      padding: const EdgeInsets.only(top: 14, bottom: 4),
      child: Text(
        t,
        style: theme.textTheme.titleSmall?.copyWith(
          fontWeight: FontWeight.bold,
        ),
      ),
    );
    Widget p(String t) => Padding(
      padding: const EdgeInsets.only(bottom: 4),
      child: Text(t, style: theme.textTheme.bodyMedium),
    );
    return SafeArea(
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(20, 0, 20, 20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('사용법', style: theme.textTheme.titleLarge),
            h('한도 확인하는 곳'),
            p('• Claude Code: 터미널에서 /usage — 세션·주간 사용량(%)과 리셋 시각이 나옵니다.'),
            p('• Codex: 터미널에서 /status — 5시간·주간 한도 사용량과 리셋 시각이 나옵니다.'),
            h('5시간 세션'),
            p(
              '첫 요청을 보낸 순간부터 5시간 창이 시작됩니다. "지금 시작"을 누르거나, '
              'CLI 에 표시된 리셋 시각을 "리셋 시각"에 그대로 입력하세요.',
            ),
            p('한도에 걸리면 "한도 도달"을 켜 두세요. 리셋 시각이 되면 자동으로 풀리고 알림이 옵니다.'),
            h('주간 한도'),
            p('주간 리셋 날짜·시각을 한 번만 입력하면 그 뒤로는 매주 자동으로 계산합니다.'),
            h('홈 화면 위젯'),
            p(
              '상단 위젯 버튼을 누르거나, 홈 화면을 길게 눌러 위젯 → "AI 리미터"를 추가하세요. '
              '두 서비스의 상태와 남은 시간이 실시간으로 표시되고, 누르면 앱이 열립니다.',
            ),
          ],
        ),
      ),
    );
  }
}
