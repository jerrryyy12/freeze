# 🦎 MECCHA CHAMELEON

> **몸을 환경 색으로 직접 칠해 숨는 위장 숨바꼭질** — Minecraft 1.21.1 Forge 모드

<p>
  <img alt="Minecraft" src="https://img.shields.io/badge/Minecraft-1.21.1-62B47A">
  <img alt="Forge" src="https://img.shields.io/badge/Forge-52.1.0-1E2D42">
  <img alt="Java" src="https://img.shields.io/badge/Java-21-orange">
  <img alt="Build" src="https://img.shields.io/badge/CI-GitHub%20Actions-2088FF">
  <img alt="License" src="https://img.shields.io/badge/License-MIT-green">
</p>

플레이어가 **자기 캐릭터 몸을 팔레트로 직접 색칠**해 주변 블록과 똑같이 위장하고, 술래가 그 위장한 사람들을 찾아내는 숨바꼭질 게임 모드입니다. 일본 게임 *메챠 카멜레온*에서 영감을 받았습니다.

시판 라이브러리에 기대지 않고 **믹스인(mixin) 없이** Forge 이벤트·리플렉션·직접 렌더링만으로 커스텀 플레이어 렌더링, 오프스크린 GPU 피킹, 분리형 카메라, 페이즈 기반 게임 루프를 구현한 것이 핵심입니다.

---

## 🎮 게임 개요

| 역할 | 목표 | 능력 |
|------|------|------|
| **숨는 사람** | 몸을 주변 색으로 칠해 숨는다 | 크기 축소(0.5/0.7/1배 선택), 이동속도 ↑, 위장 색칠 |
| **술래** | 숨은 사람을 찾아 샷건으로 제거 | 3배 크기, 샷건, 전원 발광 표시 |

**진행 페이즈:** `로비 → 준비(크기 선택) → 숨기(3분) → 찾기 → 정답 공개`

---

## ✨ 주요 기능

### 위장 색칠 시스템
- **2D 전개도 편집기 / 3D 모델 편집기** 두 가지 색칠 화면
- **자유시점 직접 칠하기** — 캐릭터를 3인칭으로 보며 몸에 붓으로 바로 색칠
- **스포이드** — 화면 속 블록 색을 그대로 추출 (프레임버퍼 픽셀 읽기)
- **주변 블록색 팔레트 자동 생성** — 지금 서 있는 곳의 블록 색들이 팔레트에 뜸
- **블록픽셀 모드** — 마인크래프트 블록 1픽셀 단위로 정수 정렬 색칠

### 게임 플레이
- **페이즈 기반 라운드 진행** + 실시간 타이머 / 타이틀 연출
- **샷건** — 12발 산탄 콘, 커스텀 명중 판정
- **감염 모드** — 잡히면 술래로 전환, 숨는 사람끼리는 서로 안 보임
- **반칙 방지** — 블록 속에 파묻혀 숨으면 경고 후 발광으로 위치 공개
- **정답 공개** — 살아남은 사람 발광 + 위장 유지 + 닉네임 표시

### 이모트 & 이동
- **이모트 휠(R)** — 10종 포즈를 실제 3D 피규어 아이콘으로 선택 (인사·만세·T포즈·눕기 등)
- **벽/천장 타기** — 벽에 붙어 오르내리고 천장에 매달림
- **자유시점 카메라** — 몸에서 분리된 카메라로 자유 비행 관전

---

## 🛠 기술적 하이라이트

포트폴리오 관점에서 이 프로젝트가 다루는 엔지니어링 문제들:

### 1. 믹스인 없는 커스텀 플레이어 렌더링
`RenderPlayerEvent.Pre`를 취소하고 `PlayerModel`의 각 부위를 직접 포즈 잡아 다시 렌더(`EmoteRenderer`). 눕기 이모트는 사망 애니메이션과 같은 회전 변환(YP/ZP/XP + `scale(-1,-1,1)`)을 재현. **바이트코드 조작 없이 순수 이벤트만으로** 완전한 커스텀 포즈를 구현.

### 2. 오프스크린 UV 피킹 (`BrushUvPicker`)
"화면에서 캐릭터 몸을 클릭하면 스킨의 어느 텍셀인지"를 알아내는 문제를, **모델을 텍셀마다 (u,v)를 색으로 인코딩한 텍스처로 별도 프레임버퍼(FBO)에 한 번 더 렌더**한 뒤 커서 밑 픽셀을 읽어 역산하는 방식으로 해결. 실제 렌더 포즈 그대로 그리므로 이모트·눕기 등 **어떤 포즈에서도 정확**하며, 조명 왜곡은 B채널에 상수(255)를 심어 그 값으로 정규화해 보정.

### 3. 분리형 자유 카메라 (`Freecam`)
`Camera`의 위치 필드를 **리플렉션으로 매 프레임 덮어써** 몸에서 분리된 카메라를 구현. 마우스 델타를 카메라 각도에 누적하되 캐릭터 회전은 고정, 틱→프레임 보간(lerp)으로 부드럽게 처리.

### 4. 커서 → 3D 모델 정밀 피킹
편집기(3D)는 직교투영 평행사변형 역산, 월드 직접 색칠은 **마인크래프트 실제 투영 행렬을 역투영**해 커서 픽셀을 월드 광선으로 변환 후 부위 박스와 교차.

### 5. 클라이언트 예측 이동
벽/천장 타기는 서버 물리에 기대지 않고 **클라이언트에서 `deltaMovement`를 직접 제어** + AABB 충돌 검사로 벽 밀착/천장 매달림을 구현.

### 6. 서버 권위 네트워킹 & 상태머신
`SimpleChannel` 기반 커스텀 패킷(위장 텍스처·이모트·게임상태·크기선택 동기화)과, 플레이어별 역할을 담은 상태 패킷을 뿌리는 **페이즈 상태머신**(`CamoGame`).

### 7. CI/CD
로컬 Maven 접근이 막힌 환경에서 **GitHub Actions로 push마다 자동 빌드** → jar 아티팩트 업로드. 빌드 번호를 버전에 주입(`0.1.${GITHUB_RUN_NUMBER}`).

---

## 🧱 아키텍처

```
com.chameleon
├─ ChameleonMod          모드 진입점 · 서버 틱 · 이벤트 등록
├─ CamoCommands          /camo 명령어 (게임 시작/정지/모드/시간설정)
├─ CamoStore             서버측 플레이어별 위장 텍스처 저장·동기화
├─ EmoteStore            서버측 이모트 상태
├─ ChameleonItems        샷건 등 아이템 등록
│
├─ game/
│  └─ CamoGame           페이즈 상태머신 · 역할 · 샷건 · 감염 · 반칙방지
│
├─ net/                  커스텀 패킷 (SimpleChannel)
│  ├─ ChameleonNet       채널·패킷 등록/전송
│  ├─ CamoSyncPacket / CamoPaintPacket   위장 텍스처 동기화
│  ├─ EmotePacket / GameStatePacket / ScaleChoicePacket / OpenPaintPacket
│
└─ client/              (Dist.CLIENT)
   ├─ CamoLayer          스킨 위에 위장 텍스처 렌더 레이어
   ├─ CamoClient / CamoEditState   클라 위장 상태·픽셀 버퍼·팔레트
   ├─ PaintScreen / Paint3DScreen  2D·3D 색칠 편집기
   ├─ FreecamBrushScreen / BrushUvPicker   자유시점 직접 색칠 + UV 피킹
   ├─ EyedropperScreen   스포이드
   ├─ Freecam            분리형 자유 카메라
   ├─ EmoteWheelScreen / EmotePoser / EmoteRenderer   이모트 휠·포즈·렌더
   ├─ ChameleonInput     입력 처리 (색칠·토글·벽타기·자유시점)
   ├─ HiderVisibility    감염 모드 가시성(숨는 사람끼리 숨김)
   └─ ScaleChooseScreen  준비시간 크기 선택 팝업
```

약 **4,000 줄 / 30여 개 클래스** (Chameleon 기준).

---

## 📦 기술 스택

- **언어:** Java 21
- **플랫폼:** Minecraft 1.21.1 · MinecraftForge 52.1.0
- **빌드:** ForgeGradle 6 · Gradle 8.8 · Official(Mojang) 매핑
- **렌더:** Blaze3D (OpenGL) · PoseStack · 오프스크린 RenderTarget(FBO)
- **CI:** GitHub Actions

---

## 🚀 빌드 & 실행

```bash
# 빌드 (Java 21 필요)
./gradlew build
# 결과물: build/libs/chameleon-<version>.jar
```

또는 **GitHub Actions** 탭 → 최신 빌드 → **Artifacts**에서 jar 다운로드.

설치: 빌드된 jar를 Forge 1.21.1이 설치된 클라이언트/서버의 `mods/` 폴더에 넣기.

---

## ⌨️ 조작 & 명령어

**키 (변경 가능)**

| 키 | 기능 |
|----|------|
| `G` | 색칠 화면 (자유시점 중엔 몸에 직접 색칠) |
| `H` | 위장 ↔ 원래 스킨 토글 |
| `R` | 이모트 휠 |
| `4` / `5` | 자유시점 켜기 / 끄기 |
| `점프` / `Q` | 벽·천장 타기 오르내림 |

**명령어** (OP)

| 명령어 | 설명 |
|--------|------|
| `/camo game start [초]` | 게임 시작 (파란 양털=숨기, 빨간 양털=술래) |
| `/camo game stop` | 게임 종료 |
| `/camo mode infection \| normal` | 감염 모드 전환 |
| `/camo set hide\|seek\|reveal <초>` | 페이즈 시간 설정 |

---

## 📸 스크린샷

<!-- 여기에 인게임 스크린샷/GIF 추가 (색칠·이모트 휠·자유시점·감염 모드 등) -->
> 인게임 스크린샷은 추후 추가 예정

---

## 📝 라이선스

MIT

> 이 저장소에는 초기 습작으로 만든 소형 유틸리티 모드 `Freeze`(영역 이탈 시 사망)도 포함되어 있습니다. 메인 프로젝트는 **Chameleon**입니다.
