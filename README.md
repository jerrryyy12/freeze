# 폰 검수 (Phone Inspector)

중고폰 매입 검수를 자동화하는 PC(Flutter Desktop) 프로그램.
USB로 연결된 안드로이드 폰을 adb로 검사하고 결과를 대시보드로 보여줍니다.

## 검사 항목

**adb 자동** (프로그램이 판정)
- 배터리 (잔량·온도·상태·충전 소스)
- 유심 (삽입 여부·통신사)
- 무선 (WiFi 실제 연결·블루투스·NFC)
- 센서 (필수 센서 존재 확인, 상세 목록 보기)
- 저장소 (전체 용량)
- 시스템 소프트웨어 (모델·안드로이드 버전 등)

**육안** (검수원이 실물 보고 판정 — 예정)
- 액정, 카메라, 지문, 스피커·마이크, S펜

## 개발

```bash
flutter pub get
flutter run -d windows
```

USB 디버깅이 켜진 안드로이드 폰을 연결하면 자동으로 카드가 뜹니다.

## 빌드 & 배포

태그를 푸시하면 GitHub Actions가 자동으로 Windows 설치 마법사를 만듭니다:

```bash
git tag v1.0
git push origin v1.0
```

Actions 탭에서 진행 상황을 보고, 완료되면 Releases 또는 Artifacts에서
`PhoneInspector_Setup.exe`를 받을 수 있습니다. (adb 동봉됨)

수동 빌드는 `빌드_배포_안내.md` 참고.

## 이어서 개발하기

`HANDOFF.md`에 프로젝트 전체 맥락과 다음 할 일이 정리되어 있습니다.
