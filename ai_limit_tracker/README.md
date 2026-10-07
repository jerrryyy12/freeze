# AI 리미터 (AI Limit Tracker)

**Claude Code** 와 **Codex** 의 사용 한도(5시간 세션 · 주간)를 추적하는 안드로이드 앱.
홈 화면 위젯으로 두 서비스의 상태와 리셋까지 남은 시간을 실시간으로 보여줍니다.

## 설치 (폰)

1. 폰 브라우저로 이 저장소의 **Releases → "AI 리미터 트래커 (최신 APK)"** 열기
2. `AI-Limit-Tracker.apk` 다운로드 → 열기
3. "출처를 알 수 없는 앱 설치" 허용 → 설치
4. 앱 첫 실행 시 알림 권한 허용 (리셋 알림용)

새 버전도 같은 방법으로 덮어 설치하면 기록이 그대로 유지됩니다.

## 화면

- **한도**: 서비스별 5시간·주간 남은 %, 막대, 소진 예상 시각("수 10:27 소진 예상"), 리셋까지 남은 시간
- **상태**: 공식 상태 페이지(status.openai.com, status.claude.com)를 2분마다 확인 — 현재 상태, 최근 14일 장애 칸, 진행 중 사건
- **통계**: 이번 주 사용량, 하루 평균, 최근 7일 5시간 한도 최고 사용량 막대
- **설정**: 절대 시각 표시, 서비스 순서(급한 순/직접), 주간 잔여 % 알림, 리셋 알림, 위젯 추가
- **홈 화면 위젯 (목록형)**: 서비스마다 더 빠듯한 한도의 남은 %·막대·소진 예상/리셋 시각, 새로고침 버튼

사용량은 각 CLI 에서 확인한 값을 직접 입력하는 방식입니다
(로그인 토큰을 폰에 넣지 않기 위해 자동 조회는 하지 않음).

## 구조

| 파일 | 역할 |
|---|---|
| `lib/models.dart` | 상태·계산 로직 — 남은 %, 페이스(소진 예상), 정렬, 알림 기준 (`test/models_test.dart`) |
| `lib/status.dart` | 공식 상태 페이지 API 파싱 (`test/status_test.dart`) |
| `lib/app_model.dart` | 앱 상태 보관·저장·알림 |
| `lib/main.dart`, `lib/ui/` | 화면 (한도 / 상태 / 통계 / 설정, 입력 시트) |
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
