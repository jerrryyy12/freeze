# Freeze

마인크래프트 Paper 플러그인. 지정한 사각형 영역 밖으로 나가면 즉시 사망합니다.

## 빌드

```
mvn clean package
```

빌드된 `target/Freeze-1.0.0.jar` 파일을 서버의 `plugins/` 폴더에 넣고 재시작하세요.

> Paper API는 `pom.xml`에서 `1.26.2-R0.1-SNAPSHOT`으로 설정되어 있습니다.

## 명령어

| 명령어 | 설명 |
|--------|------|
| `/freeze pos1` | 현재 위치를 1번 모서리로 설정 |
| `/freeze pos2` | 현재 위치를 2번 모서리로 설정 |
| `/freeze set <x1> <y1> <z1> <x2> <y2> <z2> [world]` | 좌표 직접 입력 |
| `/freeze on` | 영역 활성화 |
| `/freeze off` | 영역 비활성화 |
| `/freeze info` | 현재 설정 보기 |
| `/freeze show` | 영역 경계를 파티클로 10초간 표시 |

별칭: `/fz`

## 권한

- `freeze.admin` — 명령어 사용 권한 (기본: op)
- `freeze.bypass` — 영역 밖으로 나가도 죽지 않음 (기본: false)

## 동작

- 영역은 두 좌표 사이의 사각형(직육면체).
- 활성화된 상태에서 플레이어가 영역 밖으로 나가면 즉시 체력이 0이 되어 사망합니다.
- 크리에이티브/스펙테이터 모드는 제외.
