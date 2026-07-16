# BUILD — STALKED

순수 C99 + raylib 정적 링크. 최종 실행파일 하드 제약: **1,474,560 바이트(1.44MB) 이하**.

## 1. raylib 정적 라이브러리 (크기 최적화 빌드)

raylib **5.5** 소스를 받아 `src/config.h`에서 안 쓰는 기능을 끈 뒤 정적 빌드한다.
게임은 오디오/이미지 파일을 로드하지 않고(파형은 코드 생성, 폰트는 임베드), 3D도 쓰지 않는다.

```sh
git clone --depth 1 --branch 5.5 https://github.com/raysan5/raylib.git
cd raylib/src

# config.h — 미사용 디코더/모듈 제거 (용량 회수 ~160KB)
sed -i 's/^#define SUPPORT_FILEFORMAT_WAV .*/\/\/&/;  s/^#define SUPPORT_FILEFORMAT_OGG .*/\/\/&/' config.h
sed -i 's/^#define SUPPORT_FILEFORMAT_MP3 .*/\/\/&/;  s/^#define SUPPORT_FILEFORMAT_QOA .*/\/\/&/' config.h
sed -i 's/^#define SUPPORT_FILEFORMAT_PNG .*/\/\/&/;  s/^#define SUPPORT_FILEFORMAT_GIF .*/\/\/&/' config.h
sed -i 's/^#define SUPPORT_FILEFORMAT_DDS .*/\/\/&/' config.h
sed -i 's/^#define SUPPORT_MODULE_RMODELS .*/#define SUPPORT_MODULE_RMODELS 0/' config.h

make PLATFORM=PLATFORM_DESKTOP RAYLIB_LIBTYPE=STATIC RAYLIB_BUILD_MODE=RELEASE -j4
# → libraylib.a 생성
```

Linux 빌드 시 GL/X11 개발 헤더 필요:
`libgl1-mesa-dev libx11-dev libxrandr-dev libxinerama-dev libxcursor-dev libxi-dev libxext-dev`

## 2. 게임 빌드 (정적 링크 + 스트립 + -Os)

`RL` = 위 `raylib/src` 경로.

```sh
# Windows (심사 타겟)
gcc main.c -o game.exe -Os -s -I"$RL" -L"$RL" -lraylib -lopengl32 -lgdi32 -lwinmm

# Linux
gcc main.c -o game     -Os -s -I"$RL" -L"$RL" -lraylib -lGL -lm -lpthread -ldl -lrt -lX11

# macOS
clang main.c -o game   -Os    -I"$RL" -L"$RL" -lraylib -framework OpenGL -framework Cocoa -framework IOKit
```

## 3. Windows 크로스 빌드 (Linux에서, 심사 타겟)

mingw-w64로 Linux에서 바로 Windows exe를 뽑을 수 있다.

```sh
apt-get install gcc-mingw-w64-x86-64

# raylib를 Windows용으로 재빌드 (동일 슬림 config.h)
cd raylib/src && make clean
make PLATFORM=PLATFORM_DESKTOP RAYLIB_LIBTYPE=STATIC RAYLIB_BUILD_MODE=RELEASE \
     OS=Windows_NT CC=x86_64-w64-mingw32-gcc AR=x86_64-w64-mingw32-ar -j4

# 게임 링크 (-mwindows = 콘솔창 숨김)
x86_64-w64-mingw32-gcc main.c -o game.exe -Os -s -mwindows \
    -I"$RL" -L"$RL" -lraylib -lopengl32 -lgdi32 -lwinmm
```

의존 DLL은 전부 Windows 기본 시스템 DLL(GDI32/KERNEL32/msvcrt/SHELL32/USER32/WINMM)
→ 외부 DLL 없는 **단일 독립 실행파일**. (win32 스레드 변종 사용 → libwinpthread 불필요)

## 4. 용량 실측 (상시 감시)

| 시점 | Linux exe | Windows exe (심사 타겟) |
|---|---|---|
| Week 1 스캐폴드 | 902,600 B (61.2%) | — |
| Week 2 추격자 | 906,696 B (61.5%) | — |
| Week 3 오디오+빛 (raylib 슬림) | 1,194,040 B (81.0%) | — |
| Week 4 아이템+숨기 | 1,198,136 B (81.3%) | **1,125,376 B (76.3%)** |

Windows exe가 Linux보다 작다(오디오 백엔드 WASAPI가 ALSA+Pulse+JACK+OSS보다 간결,
mingw는 libc를 시스템 msvcrt로 링크). 심사 타겟 기준 **여유 ~341KB**.
최후 버퍼로 UPX(자체 압축) 사용 가능.
