# Freeze (마인크래프트 1.26.2 Forge 모드)

지정한 사각형 영역 밖으로 나가면 즉시 사망. 활성화 중엔 경계가 빨간 파티클로 상시 표시됨.

## 실제 모드 코드 (이게 핵심)

```
src/main/java/com/freeze/FreezeMod.java      메인 (틱마다 영역 체크 + 사망 + 경계 파티클)
src/main/java/com/freeze/FreezeArea.java     영역 데이터 / 판정
src/main/java/com/freeze/FreezeCommand.java  /freeze 명령어
src/main/resources/META-INF/mods.toml        모드 메타데이터
src/main/resources/pack.mcmeta               리소스팩 메타
```

## 빌드 방법 (직접)

1.26.2는 Forge가 **Java 25 + 새 빌드 시스템**을 쓰는 최신 버전이라, 이 repo의 `build.gradle`(구형 ForgeGradle)로는 빌드가 안 됩니다. 가장 확실한 방법:

1. [Forge 다운로드 페이지](https://files.minecraftforge.net/)에서 **1.26.2용 MDK(Mod Developer Kit)** 받기
2. MDK 압축 풀기
3. 위 `src/` 폴더의 5개 파일을 MDK의 같은 경로에 복사
4. MDK 안에서 `./gradlew build` (Java 25 필요)
5. `build/libs/`에 생성된 jar를 `mods/` 폴더에 넣기

> `mods.toml`의 `${...}` 자리표시자는 MDK의 `gradle.properties` 값으로 자동 치환됩니다.
> Forge 1.26.2 매핑 기준으로 일부 API 메서드명(`TickEvent`, `player.kill(...)` 등)이 다르면 IDE가 알려주는 대로 살짝 맞춰주세요.

## 명령어 (OP 권한 필요)

| 명령어 | 설명 |
|--------|------|
| `/freeze pos1` | 현재 위치를 1번 모서리로 설정 |
| `/freeze pos2` | 현재 위치를 2번 모서리로 설정 |
| `/freeze on` | 영역 활성화 (빨간 경계 표시 시작) |
| `/freeze off` | 비활성화 |
| `/freeze info` | 현재 설정 보기 |

## 동작

- 두 좌표 사이의 직육면체가 영역.
- 활성화 중 영역 밖으로 나가면 매 틱 즉시 사망.
- 크리에이티브/스펙테이터는 제외.
- 활성화 중 영역 12개 모서리가 빨간 파티클로 0.5초마다 표시됨.
