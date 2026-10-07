import 'package:flutter/material.dart';

import '../app_model.dart';
import '../models.dart';
import 'common.dart';
import 'edit_sheet.dart';

const plans = {
  'claude': ['Pro', 'Max 5x', 'Max 20x', 'Team', 'Enterprise'],
  'codex': ['Plus', 'Pro', 'Business', 'Enterprise'],
};

class LimitsPage extends StatelessWidget {
  const LimitsPage({super.key, required this.model});

  final AppModel model;

  void _pickService(BuildContext context) {
    final s = model.state!;
    showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      backgroundColor: Palette.card(context),
      builder: (ctx) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 0, 20, 16),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                '사용량 입력',
                style: TextStyle(
                  fontSize: 22,
                  fontWeight: FontWeight.w800,
                  color: Palette.text(ctx),
                ),
              ),
              const SizedBox(height: 14),
              for (final svc in s.services)
                Container(
                  margin: const EdgeInsets.only(bottom: 10),
                  padding: const EdgeInsets.symmetric(
                    horizontal: 16,
                    vertical: 12,
                  ),
                  decoration: BoxDecoration(
                    border: Border.all(color: Palette.line(ctx)),
                    borderRadius: BorderRadius.circular(18),
                  ),
                  child: Row(
                    children: [
                      ServiceIcon(svc.id, size: 26),
                      const SizedBox(width: 14),
                      Expanded(
                        child: Text(
                          svc.name,
                          style: TextStyle(
                            fontSize: 17,
                            color: Palette.text(ctx),
                          ),
                        ),
                      ),
                      FilledButton(
                        style: FilledButton.styleFrom(
                          backgroundColor: Palette.text(ctx),
                          foregroundColor: Palette.card(ctx),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(12),
                          ),
                        ),
                        onPressed: () {
                          Navigator.pop(ctx);
                          showEditSheet(context, model, svc.id);
                        },
                        child: const Text('입력'),
                      ),
                    ],
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: model,
      builder: (context, _) {
        final s = model.state!;
        final now = DateTime.now();
        final last = s.lastUpdated;
        return ListView(
          padding: const EdgeInsets.fromLTRB(16, 4, 16, 120),
          children: [
            Row(
              children: [
                IconButton(
                  tooltip: '사용량 입력',
                  icon: const Icon(Icons.add, size: 28),
                  onPressed: () => _pickService(context),
                ),
                const Spacer(),
                IconButton(
                  tooltip: '새로고침',
                  icon: const Icon(Icons.refresh, size: 26),
                  onPressed: () => model.update((_, _) {}),
                ),
              ],
            ),
            PageHeader(
              title: '한도',
              trailing: last == null
                  ? '아직 입력 없음'
                  : '업데이트 ${formatAgo(last, now)}',
            ),
            for (final svc in s.ordered(now))
              ServiceCard(model: model, service: svc, now: now),
          ],
        );
      },
    );
  }
}

class ServiceCard extends StatelessWidget {
  const ServiceCard({
    super.key,
    required this.model,
    required this.service,
    required this.now,
  });

  final AppModel model;
  final ServiceState service;
  final DateTime now;

  @override
  Widget build(BuildContext context) {
    final svc = service;
    final absolute = model.state!.settings.absoluteTime;
    final last = svc.lastUpdated;
    final empty = !svc.session.configured && !svc.weekly.configured;

    return AppCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              ServiceIcon(svc.id, size: 24),
              const SizedBox(width: 12),
              Expanded(
                child: Text(
                  svc.name,
                  style: TextStyle(
                    fontSize: 21,
                    fontWeight: FontWeight.w700,
                    color: Palette.text(context),
                  ),
                ),
              ),
              if (svc.plan != null)
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 12,
                    vertical: 5,
                  ),
                  decoration: BoxDecoration(
                    color: Palette.chip(context),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Text(
                    svc.plan!,
                    style: TextStyle(
                      fontSize: 14,
                      color: Palette.text(context),
                    ),
                  ),
                ),
              _menu(context),
            ],
          ),
          Padding(
            padding: const EdgeInsets.only(left: 36, top: 2, bottom: 6),
            child: Text(
              last == null
                  ? '아직 입력한 사용량이 없어요'
                  : '마지막 확인 ${formatAgo(last, now)}',
              style: TextStyle(fontSize: 14, color: Palette.sub(context)),
            ),
          ),
          if (empty)
            Padding(
              padding: const EdgeInsets.only(top: 10),
              child: SizedBox(
                width: double.infinity,
                child: OutlinedButton(
                  onPressed: () => showEditSheet(context, model, svc.id),
                  style: OutlinedButton.styleFrom(
                    padding: const EdgeInsets.symmetric(vertical: 14),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(14),
                    ),
                  ),
                  child: const Text('사용량 입력하기'),
                ),
              ),
            )
          else ...[
            _WindowRow(
              label: '5시간',
              window: svc.session,
              now: now,
              absolute: absolute,
              onTap: () => showEditSheet(context, model, svc.id),
            ),
            Divider(height: 1, color: Palette.line(context)),
            _WindowRow(
              label: '주간',
              window: svc.weekly,
              now: now,
              absolute: absolute,
              onTap: () => showEditSheet(context, model, svc.id),
            ),
          ],
        ],
      ),
    );
  }

  Widget _menu(BuildContext context) {
    final svc = service;
    return PopupMenuButton<String>(
      icon: Icon(Icons.more_horiz, color: Palette.text(context)),
      onSelected: (v) async {
        switch (v) {
          case 'edit':
            showEditSheet(context, model, svc.id);
          case 'plan':
            final picked = await showDialog<String>(
              context: context,
              builder: (ctx) => SimpleDialog(
                title: const Text('요금제'),
                children: [
                  for (final p in plans[svc.id] ?? const <String>[])
                    SimpleDialogOption(
                      onPressed: () => Navigator.pop(ctx, p),
                      child: Text(p),
                    ),
                  SimpleDialogOption(
                    onPressed: () => Navigator.pop(ctx, ''),
                    child: const Text('표시 안 함'),
                  ),
                ],
              ),
            );
            if (picked != null) {
              model.update(
                (s, _) =>
                    s.service(svc.id).plan = picked.isEmpty ? null : picked,
              );
            }
          case 'clear':
            model.update((s, _) {
              s.service(svc.id).session.clear();
              s.service(svc.id).weekly.clear();
            });
        }
      },
      itemBuilder: (_) => const [
        PopupMenuItem(value: 'edit', child: Text('사용량 입력')),
        PopupMenuItem(value: 'plan', child: Text('요금제 표시')),
        PopupMenuItem(value: 'clear', child: Text('초기화')),
      ],
    );
  }
}

class _WindowRow extends StatelessWidget {
  const _WindowRow({
    required this.label,
    required this.window,
    required this.now,
    required this.absolute,
    required this.onTap,
  });

  final String label;
  final LimitWindow window;
  final DateTime now;
  final bool absolute;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final rem = window.remainingNow(now);
    final reset = window.currentReset(now);
    final pace = window.pace(now);
    final text = Palette.text(context);
    final sub = Palette.sub(context);

    final String left;
    Color leftColor = text;
    if (rem == null) {
      left = '미입력';
      leftColor = sub;
    } else if (pace.kind == PaceKind.unknown) {
      left = '';
    } else {
      left = paceText(pace, now);
      if (pace.warning) leftColor = Palette.red;
    }

    final String right;
    if (reset != null) {
      right = formatReset(reset, now, absolute: absolute);
    } else if (window.kind == WindowKind.session && window.resetAt != null) {
      right = '새 세션 대기';
    } else {
      right = '리셋 시각 미입력';
    }

    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                Text(label, style: TextStyle(fontSize: 17, color: text)),
                const Spacer(),
                if (rem == null)
                  Text('—', style: TextStyle(fontSize: 22, color: sub))
                else
                  Text.rich(
                    TextSpan(
                      children: [
                        TextSpan(
                          text: '$rem%',
                          style: TextStyle(
                            fontSize: 25,
                            fontWeight: FontWeight.w700,
                            letterSpacing: -0.5,
                            color: rem < 20 ? Palette.red : text,
                          ),
                        ),
                        TextSpan(
                          text: ' 남음',
                          style: TextStyle(fontSize: 15, color: sub),
                        ),
                      ],
                    ),
                  ),
              ],
            ),
            const SizedBox(height: 12),
            LimitBar(
              value: (rem ?? 0) / 100,
              color: Palette.forRemaining(rem ?? 0),
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: Text(
                    left,
                    style: TextStyle(
                      fontSize: 15,
                      fontWeight: FontWeight.w600,
                      color: leftColor,
                    ),
                  ),
                ),
                Text(right, style: TextStyle(fontSize: 15, color: sub)),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
