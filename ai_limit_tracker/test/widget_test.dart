import 'package:ai_limit_tracker/main.dart';
import 'package:ai_limit_tracker/status.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('한도 화면에 두 서비스가 뜨고, 탭 이동이 된다', (tester) async {
    String? saved;
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      const MethodChannel('ai_limit_tracker/native'),
      (call) async {
        switch (call.method) {
          case 'load':
            return saved;
          case 'save':
            saved = call.arguments as String;
            return null;
          default:
            return true;
        }
      },
    );
    Future<ServiceStatus> fakeStatus(StatusSource s) async => ServiceStatus(
      source: s,
      health: Health.operational,
      days: List.filled(statusDays, 0),
    );

    await tester.pumpWidget(TrackerApp(statusFetcher: fakeStatus));
    await tester.pump(const Duration(milliseconds: 100));

    expect(find.text('한도'), findsWidgets);
    expect(find.text('Codex'), findsOneWidget);
    expect(find.text('Claude Code'), findsOneWidget);
    expect(find.text('사용량 입력하기'), findsNWidgets(2));

    // 입력 시트 열고 저장
    await tester.tap(find.text('사용량 입력하기').first);
    await tester.pumpAndSettle();
    await tester.tap(find.text('지금 시작'));
    await tester.pump();
    await tester.tap(find.text('저장'));
    await tester.pumpAndSettle();
    expect(find.text('100% 남음', findRichText: true), findsOneWidget);
    expect(find.text('여유 있음'), findsOneWidget);
    expect(saved, contains('"v":2'));

    // 상태 탭
    await tester.tap(find.text('상태'));
    await tester.pump(const Duration(milliseconds: 100));
    expect(find.text('모든 서비스 정상'), findsOneWidget);

    // 설정 탭
    await tester.tap(find.text('설정'));
    await tester.pump(const Duration(milliseconds: 100));
    expect(find.text('절대 시각으로 표시'), findsOneWidget);
  });
}
