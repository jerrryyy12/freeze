import 'package:flutter/material.dart';

import '../app_model.dart';
import '../models.dart';
import 'common.dart';

/// 서비스 하나의 5시간·주간 사용량과 리셋 시각을 입력하는 시트.
Future<void> showEditSheet(BuildContext context, AppModel model, String id) {
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    showDragHandle: true,
    backgroundColor: Palette.card(context),
    builder: (_) => _EditSheet(model: model, id: id),
  );
}

class _Draft {
  _Draft(LimitWindow w, DateTime now)
    : remaining = (w.remainingNow(now) ?? 100).toDouble(),
      reset = w.currentReset(now);

  double remaining;
  DateTime? reset;
  bool dirty = false;
}

class _EditSheet extends StatefulWidget {
  const _EditSheet({required this.model, required this.id});

  final AppModel model;
  final String id;

  @override
  State<_EditSheet> createState() => _EditSheetState();
}

class _EditSheetState extends State<_EditSheet> {
  late final ServiceState svc = widget.model.state!.service(widget.id);
  late final DateTime opened = DateTime.now();
  late final session = _Draft(svc.session, opened);
  late final weekly = _Draft(svc.weekly, opened);

  void _save() {
    widget.model.update((s, now) {
      for (final (kind, d) in [
        (WindowKind.session, session),
        (WindowKind.weekly, weekly),
      ]) {
        if (!d.dirty) continue;
        s.record(
          svc.id,
          kind,
          usedPct: 100 - d.remaining.round(),
          resetAt: d.reset,
          now: now,
        );
      }
    });
    Navigator.pop(context);
  }

  Future<void> _pickSessionReset() async {
    final now = DateTime.now();
    final init = session.reset ?? now.add(kSessionLength);
    final t = await showTimePicker(
      context: context,
      initialTime: TimeOfDay.fromDateTime(init),
      helpText: '5시간 한도 리셋 시각',
    );
    if (t == null) return;
    setState(() {
      session.reset = nextClockTime(t.hour, t.minute, DateTime.now());
      session.dirty = true;
    });
  }

  Future<void> _pickWeeklyReset() async {
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);
    final cur = weekly.reset;
    final d = await showDatePicker(
      context: context,
      initialDate: cur != null ? DateTime(cur.year, cur.month, cur.day) : today,
      firstDate: today,
      lastDate: today.add(const Duration(days: 7)),
      helpText: '주간 한도 리셋 날짜',
    );
    if (d == null || !mounted) return;
    final t = await showTimePicker(
      context: context,
      initialTime: cur != null
          ? TimeOfDay.fromDateTime(cur)
          : const TimeOfDay(hour: 9, minute: 0),
      helpText: '주간 한도 리셋 시각',
    );
    if (t == null) return;
    setState(() {
      weekly.reset = DateTime(d.year, d.month, d.day, t.hour, t.minute);
      weekly.dirty = true;
    });
  }

  @override
  Widget build(BuildContext context) {
    final now = DateTime.now();
    final hint = svc.id == 'claude'
        ? 'Claude Code 에서 /usage 를 입력하면 나오는 값을 옮겨 적으세요.'
        : 'Codex 에서 /status 를 입력하면 나오는 값을 옮겨 적으세요.';
    return SafeArea(
      child: Padding(
        padding: EdgeInsets.fromLTRB(
          20,
          0,
          20,
          16 + MediaQuery.of(context).viewInsets.bottom,
        ),
        child: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  ServiceIcon(svc.id, size: 26),
                  const SizedBox(width: 12),
                  Text(
                    svc.name,
                    style: TextStyle(
                      fontSize: 22,
                      fontWeight: FontWeight.w800,
                      color: Palette.text(context),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 6),
              Text(
                hint,
                style: TextStyle(fontSize: 14, color: Palette.sub(context)),
              ),
              const SizedBox(height: 18),
              _section(
                title: '5시간',
                draft: session,
                resetText: session.reset == null
                    ? '리셋 시각 입력'
                    : '${formatClock(session.reset!, now)} 리셋',
                onPickReset: _pickSessionReset,
                extra: ActionChip(
                  avatar: const Icon(Icons.play_arrow, size: 18),
                  label: const Text('지금 시작'),
                  onPressed: () => setState(() {
                    session.reset = DateTime.now().add(kSessionLength);
                    session.remaining = 100;
                    session.dirty = true;
                  }),
                ),
              ),
              Divider(height: 32, color: Palette.line(context)),
              _section(
                title: '주간',
                draft: weekly,
                resetText: weekly.reset == null
                    ? '리셋 날짜·시각 입력'
                    : '${formatClock(weekly.reset!, now)} 리셋',
                onPickReset: _pickWeeklyReset,
              ),
              const SizedBox(height: 22),
              SizedBox(
                width: double.infinity,
                child: FilledButton(
                  style: FilledButton.styleFrom(
                    backgroundColor: Palette.text(context),
                    foregroundColor: Palette.card(context),
                    padding: const EdgeInsets.symmetric(vertical: 16),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(16),
                    ),
                  ),
                  onPressed: (session.dirty || weekly.dirty) ? _save : null,
                  child: const Text('저장', style: TextStyle(fontSize: 16)),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _section({
    required String title,
    required _Draft draft,
    required String resetText,
    required VoidCallback onPickReset,
    Widget? extra,
  }) {
    final rem = draft.remaining.round();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            Text(
              title,
              style: TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.w700,
                color: Palette.text(context),
              ),
            ),
            const Spacer(),
            Text.rich(
              TextSpan(
                children: [
                  TextSpan(
                    text: '$rem%',
                    style: TextStyle(
                      fontSize: 26,
                      fontWeight: FontWeight.w700,
                      color: rem < 20 ? Palette.red : Palette.text(context),
                    ),
                  ),
                  TextSpan(
                    text: ' 남음',
                    style: TextStyle(fontSize: 15, color: Palette.sub(context)),
                  ),
                ],
              ),
            ),
          ],
        ),
        SliderTheme(
          data: SliderTheme.of(context).copyWith(
            activeTrackColor: Palette.forRemaining(rem),
            thumbColor: Palette.text(context),
            inactiveTrackColor: Palette.track(context),
          ),
          child: Slider(
            value: draft.remaining,
            max: 100,
            divisions: 100,
            onChanged: (v) => setState(() {
              draft.remaining = v;
              draft.dirty = true;
            }),
          ),
        ),
        Wrap(
          spacing: 8,
          runSpacing: 6,
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            for (final step in const [-10, -1, 1, 10])
              ActionChip(
                label: Text(step > 0 ? '+$step' : '$step'),
                onPressed: () => setState(() {
                  draft.remaining = (draft.remaining + step)
                      .clamp(0, 100)
                      .toDouble();
                  draft.dirty = true;
                }),
              ),
          ],
        ),
        const SizedBox(height: 8),
        Wrap(
          spacing: 8,
          runSpacing: 6,
          children: [
            ActionChip(
              avatar: const Icon(Icons.schedule, size: 18),
              label: Text(resetText),
              onPressed: onPickReset,
            ),
            ?extra,
          ],
        ),
      ],
    );
  }
}
