import 'package:ai_limit_tracker/main.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('두 서비스 카드가 뜨고 세션을 시작하면 저장된다', (tester) async {
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

    await tester.pumpWidget(const TrackerApp());
    // 저장된 상태 불러오기(비동기) 대기. 1초 타이머가 계속 돌아 pumpAndSettle 은 못 씀.
    await tester.pump(const Duration(milliseconds: 100));
    expect(find.text('Claude Code'), findsOneWidget);
    expect(find.text('Codex'), findsOneWidget);
    expect(find.text('사용 가능'), findsNWidgets(2));

    await tester.tap(find.text('지금 시작').first);
    await tester.pump();
    expect(find.text('세션 중'), findsOneWidget);
    expect(find.textContaining('남음'), findsWidgets);
    expect(saved, contains('"sessionResetAt":1'));
  });
}
