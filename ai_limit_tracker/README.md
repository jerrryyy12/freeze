# AI 리미터 (AI Limit Tracker)

**Claude Code** 와 **Codex** 의 사용 한도(5시간 세션 · 주간)를 추적하는 안드로이드 앱.
홈 화면 위젯으로 두 서비스의 상태와 리셋까지 남은 시간을 실시간으로 보여줍니다.

## 설치 (폰)

1. 폰 브라우저로 이 저장소의 **Releases → "AI 리미터 트래커 (최신 APK)"** 열기
2. `AI-Limit-Tracker.apk` 다운로드 → 열기
3. "출처를 알 수 없는 앱 설치" 허용 → 설치
4. 앱 첫 실행 시 알림 권한 허용 (리셋 알림용)

새 버전도 같은 방법으로 덮어 설치하면 기록이 그대로 유지됩니다.

## 기능

- **5시간 세션 한도**: "지금 시작" 또는 CLI 에 나온 리셋 시각 입력 → 카운트다운
- **주간 한도**: 리셋 요일·시각을 한 번만 입력하면 매주 자동 계산
- **사용량(%)**: Claude Code `/usage`, Codex `/status` 에 나온 값을 입력하면 막대로 표시
- **한도 도달 표시**: 켜 두면 리셋 시각에 자동 해제
- **리셋 알림**: 세션·주간 한도가 리셋되는 순간 알림 (재부팅해도 유지)
- **홈 화면 위젯 (4×2)**: 두 서비스 상태 배지 + 실시간 카운트다운, 누르면 앱 열림

사용량은 각 CLI 에서 확인한 값을 직접 입력하는 방식입니다
(로그인 토큰을 폰에 넣지 않기 위해 자동 조회는 하지 않음).

## 구조

| 파일 | 역할 |
|---|---|
| `lib/models.dart` | 상태·계산 로직 (순수 Dart, `test/models_test.dart` 로 검증) |
| `lib/main.dart` | 앱 화면 |
| `lib/storage.dart` | 네이티브와의 MethodChannel (저장·알림 권한·위젯 추가) |
| `android/.../TrackerCore.kt` | 위젯 그리기, 리셋 알람·알림 (models.dart 와 같은 JSON·규칙) |
| `android/.../LimitWidgetProvider.kt` | 홈 화면 위젯 |
| `android/.../ResetAlarmReceiver.kt` | 리셋 알람, 재부팅·시간 변경 시 알람 재설정 |

## 개발

```bash
cd ai_limit_tracker
flutter pub get
flutter test
flutter run            # USB 연결된 폰에 바로 실행
flutter build apk      # build/app/outputs/flutter-apk/app-release.apk
```

`android/app/sideload.p12` 는 직접 설치용 고정 서명 키입니다(덮어 설치 시 데이터 유지용).
Play 스토어에 올릴 경우엔 별도 키를 만들어 쓰세요.

푸시하면 GitHub Actions(`.github/workflows/ai-limit-tracker.yml`)가 APK 를 빌드해
Releases 의 `tracker-latest` 에 올립니다.
