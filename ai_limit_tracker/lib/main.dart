import 'dart:async';

import 'package:flutter/material.dart';

import 'app_model.dart';
import 'status.dart';
import 'storage.dart';
import 'ui/common.dart';
import 'ui/limits_page.dart';
import 'ui/settings_page.dart';
import 'ui/stats_page.dart';
import 'ui/status_page.dart';

void main() {
  runApp(const TrackerApp());
}

class TrackerApp extends StatelessWidget {
  const TrackerApp({super.key, this.statusFetcher = fetchStatus});

  final Future<ServiceStatus> Function(StatusSource) statusFetcher;

  @override
  Widget build(BuildContext context) {
    ThemeData theme(Brightness b) => ThemeData(
      useMaterial3: true,
      brightness: b,
      colorSchemeSeed: const Color(0xFF555555),
    );
    return MaterialApp(
      title: 'AI 리미터',
      debugShowCheckedModeBanner: false,
      theme: theme(Brightness.light),
      darkTheme: theme(Brightness.dark),
      home: HomeShell(statusFetcher: statusFetcher),
    );
  }
}

class HomeShell extends StatefulWidget {
  const HomeShell({super.key, required this.statusFetcher});

  final Future<ServiceStatus> Function(StatusSource) statusFetcher;

  @override
  State<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends State<HomeShell> with WidgetsBindingObserver {
  final model = AppModel();
  int _tab = 0;
  Timer? _ticker;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _init();
    // 남은 시간 표시 갱신
    _ticker = Timer.periodic(const Duration(seconds: 30), (_) => model.tick());
  }

  Future<void> _init() async {
    await model.load();
    // 리셋·사용량 알림을 받으려면 안드로이드 13+ 에서 권한이 필요.
    await Storage.requestNotificationPermission();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) model.tick();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _ticker?.cancel();
    model.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Palette.bg(context),
      body: SafeArea(
        bottom: false,
        child: ListenableBuilder(
          listenable: model,
          builder: (context, _) => model.state == null
              ? const Center(child: CircularProgressIndicator())
              : IndexedStack(
                  index: _tab,
                  children: [
                    LimitsPage(model: model),
                    StatusPage(fetcher: widget.statusFetcher),
                    StatsPage(model: model),
                    SettingsPage(model: model),
                  ],
                ),
        ),
      ),
      extendBody: true,
      bottomNavigationBar: _BottomBar(
        index: _tab,
        onTap: (i) => setState(() => _tab = i),
      ),
    );
  }
}

/// 떠 있는 알약 모양 하단 탭.
class _BottomBar extends StatelessWidget {
  const _BottomBar({required this.index, required this.onTap});

  final int index;
  final ValueChanged<int> onTap;

  static const _items = [
    (Icons.speed_outlined, Icons.speed, '한도'),
    (Icons.verified_outlined, Icons.verified, '상태'),
    (Icons.bar_chart_outlined, Icons.bar_chart, '통계'),
    (Icons.settings_outlined, Icons.settings, '설정'),
  ];

  @override
  Widget build(BuildContext context) {
    final text = Palette.text(context);
    return SafeArea(
      child: Container(
        margin: const EdgeInsets.fromLTRB(24, 0, 24, 10),
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
        decoration: BoxDecoration(
          color: Palette.card(context),
          borderRadius: BorderRadius.circular(36),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.10),
              blurRadius: 24,
              offset: const Offset(0, 6),
            ),
          ],
        ),
        child: Row(
          children: [
            for (var i = 0; i < _items.length; i++)
              Expanded(
                child: GestureDetector(
                  behavior: HitTestBehavior.opaque,
                  onTap: () => onTap(i),
                  child: Container(
                    padding: const EdgeInsets.symmetric(vertical: 8),
                    decoration: BoxDecoration(
                      color: i == index ? Palette.chip(context) : null,
                      borderRadius: BorderRadius.circular(30),
                    ),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(
                          i == index ? _items[i].$2 : _items[i].$1,
                          color: i == index ? text : Palette.sub(context),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          _items[i].$3,
                          style: TextStyle(
                            fontSize: 12,
                            fontWeight: i == index
                                ? FontWeight.w700
                                : FontWeight.w500,
                            color: i == index ? text : Palette.sub(context),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}
