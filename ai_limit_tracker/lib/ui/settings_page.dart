import 'package:flutter/material.dart';

import '../app_model.dart';
import '../storage.dart';
import 'common.dart';

class SettingsPage extends StatelessWidget {
  const SettingsPage({super.key, required this.model});

  final AppModel model;

  Future<void> _addWidget(BuildContext context) async {
    final shown = await Storage.requestPinWidget();
    if (!context.mounted || shown) return;
    showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('홈 화면에 위젯 추가'),
        content: const Text('홈 화면 빈 곳을 길게 누름 → 위젯 → "AI 리미터" 를 찾아 끌어다 놓으세요.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('확인'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: model,
      builder: (context, _) {
        final s = model.state!;
        final st = s.settings;
        final text = Palette.text(context);
        final sub = Palette.sub(context);

        Widget tile(
          String title,
          String? subtitle, {
          Widget? trailing,
          VoidCallback? onTap,
        }) => InkWell(
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: 12),
            child: Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        title,
                        style: TextStyle(
                          fontSize: 17,
                          fontWeight: FontWeight.w600,
                          color: text,
                        ),
                      ),
                      if (subtitle != null) ...[
                        const SizedBox(height: 3),
                        Text(
                          subtitle,
                          style: TextStyle(fontSize: 14, color: sub),
                        ),
                      ],
                    ],
                  ),
                ),
                ?trailing,
              ],
            ),
          ),
        );

        Widget check(bool on) =>
            Icon(Icons.check, color: on ? text : Colors.transparent);

        Widget sw(bool v, ValueChanged<bool> f) => Switch(
          value: v,
          onChanged: f,
          activeTrackColor: text,
          activeThumbColor: Palette.card(context),
        );

        final orderIds = [
          ...st.customOrder.where((id) => s.services.any((x) => x.id == id)),
          ...s.services
              .map((x) => x.id)
              .where((id) => !st.customOrder.contains(id)),
        ];

        return ListView(
          padding: const EdgeInsets.fromLTRB(16, 52, 16, 120),
          children: [
            const PageHeader(title: '설정'),
            AppCard(
              padding: const EdgeInsets.fromLTRB(20, 6, 12, 6),
              child: Column(
                children: [
                  tile(
                    '절대 시각으로 표시',
                    '예: 금 23:00 리셋',
                    trailing: sw(
                      st.absoluteTime,
                      (v) =>
                          model.update((s, _) => s.settings.absoluteTime = v),
                    ),
                  ),
                  Divider(height: 1, color: Palette.line(context)),
                  Padding(
                    padding: const EdgeInsets.only(top: 14, bottom: 2),
                    child: Align(
                      alignment: Alignment.centerLeft,
                      child: Text(
                        '서비스 순서',
                        style: TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.w700,
                          color: sub,
                        ),
                      ),
                    ),
                  ),
                  tile(
                    '급한 순',
                    '한도가 먼저 바닥날 서비스가 위로',
                    trailing: check(st.urgentFirst),
                    onTap: () =>
                        model.update((s, _) => s.settings.urgentFirst = true),
                  ),
                  tile(
                    '직접 정하기',
                    '아래 목록을 길게 눌러 순서 변경',
                    trailing: check(!st.urgentFirst),
                    onTap: () =>
                        model.update((s, _) => s.settings.urgentFirst = false),
                  ),
                  if (!st.urgentFirst)
                    ReorderableListView(
                      shrinkWrap: true,
                      physics: const NeverScrollableScrollPhysics(),
                      buildDefaultDragHandles: false,
                      onReorderItem: (from, to) => model.update((s, _) {
                        final ids = [...orderIds];
                        final id = ids.removeAt(from);
                        ids.insert(to, id);
                        s.settings.customOrder = ids;
                      }),
                      children: [
                        for (var i = 0; i < orderIds.length; i++)
                          ReorderableDelayedDragStartListener(
                            key: ValueKey(orderIds[i]),
                            index: i,
                            child: ListTile(
                              contentPadding: EdgeInsets.zero,
                              leading: ServiceIcon(orderIds[i]),
                              title: Text(s.service(orderIds[i]).name),
                              trailing: const Icon(Icons.drag_handle),
                            ),
                          ),
                      ],
                    ),
                ],
              ),
            ),
            const SectionLabel('사용량 알림'),
            AppCard(
              padding: const EdgeInsets.fromLTRB(20, 10, 12, 10),
              child: Column(
                children: [
                  for (final (i, svc) in s.services.indexed) ...[
                    if (i > 0)
                      Divider(height: 20, color: Palette.line(context)),
                    Builder(
                      builder: (context) {
                        final a = st.alertFor(svc.id);
                        return Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              children: [
                                ServiceIcon(svc.id, size: 20),
                                const SizedBox(width: 10),
                                Text(
                                  svc.name,
                                  style: TextStyle(
                                    fontSize: 17,
                                    fontWeight: FontWeight.w600,
                                    color: text,
                                  ),
                                ),
                              ],
                            ),
                            tile(
                              '주간 잔여가 ${a.threshold}% 이하로 떨어지면 알림',
                              null,
                              trailing: sw(
                                a.enabled,
                                (v) => model.update((s, _) {
                                  final x = s.settings.alertFor(svc.id);
                                  x.enabled = v;
                                  x.alertedFor = null;
                                }),
                              ),
                            ),
                            SliderTheme(
                              data: SliderTheme.of(context).copyWith(
                                activeTrackColor: text,
                                thumbColor: text,
                                inactiveTrackColor: Palette.track(context),
                              ),
                              child: Slider(
                                value: a.threshold.toDouble(),
                                min: 1,
                                max: 90,
                                divisions: 89,
                                label: '${a.threshold}%',
                                onChanged: (v) => model.update((s, _) {
                                  final x = s.settings.alertFor(svc.id);
                                  x.threshold = v.round();
                                  x.alertedFor = null;
                                }),
                              ),
                            ),
                          ],
                        );
                      },
                    ),
                  ],
                ],
              ),
            ),
            const SectionLabel('리셋 알림'),
            AppCard(
              padding: const EdgeInsets.fromLTRB(20, 6, 12, 6),
              child: tile(
                '한도가 리셋되면 알림',
                '5시간·주간 한도가 다시 채워지는 순간',
                trailing: sw(
                  st.resetAlerts,
                  (v) => model.update((s, _) => s.settings.resetAlerts = v),
                ),
              ),
            ),
            const SectionLabel('위젯'),
            AppCard(
              padding: const EdgeInsets.fromLTRB(20, 6, 12, 6),
              child: tile(
                '홈 화면에 위젯 추가',
                '모든 서비스의 한도를 한 위젯에',
                trailing: const Icon(Icons.chevron_right),
                onTap: () => _addWidget(context),
              ),
            ),
            const SectionLabel('도움말'),
            AppCard(
              child: Text(
                '• Claude Code: 터미널에서 /usage 입력 → 세션·주간 사용량과 리셋 시각\n'
                '• Codex: 터미널에서 /status 입력 → 5시간·주간 한도와 리셋 시각\n\n'
                '한도 화면의 서비스 카드를 눌러 값을 입력하면 위젯·알림·통계에 반영돼요. '
                '주간 리셋 시각은 한 번만 넣으면 매주 자동으로 계산합니다.',
                style: TextStyle(fontSize: 14, height: 1.5, color: sub),
              ),
            ),
          ],
        );
      },
    );
  }
}
