// ============================================================
//  STALKED — 추격 호러 로그라이크 / Week 3
//  raylib 기반. 창 / 절차적 맵 / FOV / 다익스트라 소음맵 추격자
//  + 절차적 오디오(코드 생성 파형) + 빛/연료 긴장 시스템.
//
//  핵심 훅 = 못 죽이는 추격자. 싸우지 않고 도망치고·속이고·숨는다.
//    - 소음맵: 행동(달리기·걷기)이 '소음' → 다익스트라(BFS)로 전파. 추격자는 경사 하강.
//    - 시야(LOS): 추격자가 플레이어를 보면 락온·가속. 어두우면 근접해야만 들킴.
//    - 빛/연료: 손전등을 켜면 넓게 보이나 연료 소모 + '빛이 추격자를 끌어당김'.
//              끄면 안전하나 시야 3칸. 배터리로 보충. 매 순간 '보이기 vs 안전' 선택.
//    - 오디오가 공포를 만든다: 추격자를 대부분 안 보이게 하고 '소리로만' 존재.
//              심장박동·발소리·드론을 AudioStream 콜백으로 실시간 합성 → 에셋 0바이트.
//
//  조작: WASD/방향키=이동 / Shift=달리기(빠름·시끄러움) / Ctrl=살금(느림·조용)
//        F=손전등 토글 / R=재시작 / TAB=디버그 / ESC=종료
//
//  빌드 (raylib 정적 라이브러리 libraylib.a + -Os -s):
//    Windows: gcc main.c -o game.exe -Os -s -lraylib -lopengl32 -lgdi32 -lwinmm
//    Linux:   gcc main.c -o game     -Os -s -lraylib -lGL -lm -lpthread -ldl -lrt -lX11
//    macOS:   clang main.c -o game   -Os    -lraylib -framework OpenGL -framework Cocoa -framework IOKit
// ============================================================

#include "raylib.h"
#include <math.h>
#include <stdlib.h>
#include <time.h>

#define MAP_W       48
#define MAP_H       32
#define TILE        20
#define FOV_MAX     8          // 시야 최대 반경(배열/원형 판정 상한)
#define INF         (1<<28)
#define MAX_BATT    4
#define MAX_HIDE    3
#define PI2         6.2831853f
#define SR          22050      // 오디오 샘플레이트

// 타일: 0 = 벽, 1 = 바닥
static int map[MAP_H][MAP_W];
static int visible[MAP_H][MAP_W];   // 지금 보이는가
static int explored[MAP_H][MAP_W];  // 한 번이라도 봤는가 (기억 = 어둑하게)
static int dist[MAP_H][MAP_W];      // 다익스트라(BFS) 거리장 — 추격자 경로용

typedef struct { int x, y; } Vec;

static const int DIRS[4][2] = {{1,0},{-1,0},{0,1},{0,-1}};

static int in_bounds(int x, int y) {
    return x >= 0 && y >= 0 && x < MAP_W && y < MAP_H;
}
static int is_floor(int x, int y) {
    return in_bounds(x, y) && map[y][x] == 1;
}

// ============================================================
//  절차적 오디오 — AudioStream 콜백에서 파형을 실시간 합성 (에셋 0바이트)
//  메인 스레드가 아래 공유 파라미터를 매 프레임 갱신, 오디오 스레드가 읽어 씀.
// ============================================================
static volatile float    av_prox    = 0.0f;   // 추격자 근접도 0..1
static volatile int      av_lockon  = 0;      // 추격자가 락온 중인가
static volatile int      av_caught  = 0;      // 잡혔는가(스팅어)
static volatile unsigned av_footstep = 0;     // 추격자 스텝마다 증가 → 발소리 트리거

static void audio_cb(void *buffer, unsigned int frames) {
    short *out = (short *)buffer;
    static unsigned long long sc = 0;   // 샘플 카운터
    static double heart_phase = 0.0;    // 심박 위상(박자 단위)
    static double foot_env = 0.0;       // 발소리 엔벨로프
    static unsigned last_step = 0;
    static float lp = 0.0f;             // 발소리용 원폴 저역필터 상태
    static unsigned int rng = 0x1234567u;

    float prox = av_prox; if (prox < 0) prox = 0; if (prox > 1) prox = 1;
    float bpm = 52.0f + prox * 96.0f;   // 근접할수록 빨라짐
    if (av_caught) bpm = 155.0f;
    if (av_footstep != last_step) { foot_env = 1.0; last_step = av_footstep; }

    for (unsigned int i = 0; i < frames; i++) {
        double t = (double)sc / SR; sc++;

        // 심장박동: 박자당 'lub-dub' 두 번의 감쇠 톤(≈58Hz)
        heart_phase += (bpm / 60.0) / SR;
        double bp = heart_phase - (double)(long long)heart_phase;   // 0..1
        double henv = 0.0;
        if      (bp < 0.12)               henv = 1.0 - bp / 0.12;
        else if (bp >= 0.18 && bp < 0.30) henv = 0.7 * (1.0 - (bp - 0.18) / 0.12);
        double heart = henv * henv * sin(PI2 * 58.0 * t) * (0.18 + 0.82 * prox);

        // 발소리: 짧은 저역 노이즈 '쿵'. 추격자 스텝마다 재트리거, 거리로 음량 조절
        rng = rng * 1664525u + 1013904223u;
        float wn = ((int)((rng >> 9) & 0x7FFFFF) / 4194304.0f) - 1.0f;   // -1..1
        lp += 0.14f * (wn - lp);
        double foot = lp * foot_env * (0.15 + 0.85 * prox) * (av_lockon ? 1.3 : 1.0);
        foot_env *= 0.9993;   // ≈65ms 감쇠

        // 저역 앰비언트 드론: 항상 은은하게, 근접 시 조금 부풀림
        double drone = sin(PI2 * 40.0 * t) * 0.05 * (0.4 + 0.6 * prox);

        double s = heart * 0.7 + foot * 0.85 + drone * 0.5;
        if (s >  1.0) s =  1.0;
        if (s < -1.0) s = -1.0;
        out[i] = (short)(s * 31000.0);
    }
}

// ---------- 절차적 맵: 방 몇 개를 파고 L자 복도로 잇는다 ----------
static void carve_room(int rx, int ry, int rw, int rh) {
    for (int y = ry; y < ry + rh; y++)
        for (int x = rx; x < rx + rw; x++)
            if (in_bounds(x, y)) map[y][x] = 1;
}
static void carve_h(int x0, int x1, int y) {
    if (x0 > x1) { int t = x0; x0 = x1; x1 = t; }
    for (int x = x0; x <= x1; x++) if (in_bounds(x, y)) map[y][x] = 1;
}
static void carve_v(int y0, int y1, int x) {
    if (y0 > y1) { int t = y0; y0 = y1; y1 = t; }
    for (int y = y0; y <= y1; y++) if (in_bounds(x, y)) map[y][x] = 1;
}

static Vec generate_map(void) {
    for (int y = 0; y < MAP_H; y++)
        for (int x = 0; x < MAP_W; x++) { map[y][x] = 0; explored[y][x] = 0; }

    Vec first = {0};
    int prev_cx = 0, prev_cy = 0, n = 0;
    for (int i = 0; i < 12; i++) {
        int rw = GetRandomValue(4, 8);
        int rh = GetRandomValue(3, 6);
        int rx = GetRandomValue(1, MAP_W - rw - 1);
        int ry = GetRandomValue(1, MAP_H - rh - 1);
        carve_room(rx, ry, rw, rh);
        int cx = rx + rw / 2, cy = ry + rh / 2;
        if (n > 0) { carve_h(prev_cx, cx, prev_cy); carve_v(prev_cy, cy, cx); }
        if (n == 0) first = (Vec){cx, cy};   // 플레이어 시작 = 첫 방 중심
        prev_cx = cx; prev_cy = cy; n++;
    }
    return first;
}

// ---------- 다익스트라 소음/경로 거리장: src에서 바닥을 따라 BFS(균일비용) ----------
static void dijkstra_from(int sx, int sy) {
    for (int y = 0; y < MAP_H; y++)
        for (int x = 0; x < MAP_W; x++) dist[y][x] = INF;
    if (!is_floor(sx, sy)) return;

    static Vec q[MAP_W * MAP_H];
    int head = 0, tail = 0;
    dist[sy][sx] = 0;
    q[tail++] = (Vec){sx, sy};
    while (head < tail) {
        Vec c = q[head++];
        for (int d = 0; d < 4; d++) {
            int nx = c.x + DIRS[d][0], ny = c.y + DIRS[d][1];
            if (is_floor(nx, ny) && dist[ny][nx] == INF) {
                dist[ny][nx] = dist[c.y][c.x] + 1;
                q[tail++] = (Vec){nx, ny};
            }
        }
    }
}

// 현재 dist[]에서 (x,y) 기준 가장 낮은 값의 이웃으로 한 칸 내려간다(목표 방향).
static Vec descend(int x, int y) {
    Vec best = {x, y};
    int bestd = dist[y][x];
    for (int d = 0; d < 4; d++) {
        int nx = x + DIRS[d][0], ny = y + DIRS[d][1];
        if (is_floor(nx, ny) && dist[ny][nx] < bestd) {
            bestd = dist[ny][nx]; best = (Vec){nx, ny};
        }
    }
    return best;
}

// dist[]에서 유한하면서 가장 먼 바닥 타일 (먼 구석 = 추격자/탈출구 배치용)
static Vec farthest(void) {
    Vec best = {1, 1};
    int bestd = -1;
    for (int y = 0; y < MAP_H; y++)
        for (int x = 0; x < MAP_W; x++)
            if (map[y][x] == 1 && dist[y][x] < INF && dist[y][x] > bestd) {
                bestd = dist[y][x]; best = (Vec){x, y};
            }
    return best;
}

static Vec random_reachable(void) {
    for (int tries = 0; tries < 200; tries++) {
        int x = GetRandomValue(1, MAP_W - 2), y = GetRandomValue(1, MAP_H - 2);
        if (map[y][x] == 1 && dist[y][x] < INF) return (Vec){x, y};
    }
    return (Vec){1, 1};
}

// ---------- 시야: 플레이어→각 타일로 직선을 쏴 벽에 막히는지 (Bresenham) ----------
static int line_of_sight(int x0, int y0, int x1, int y1) {
    int dx = abs(x1 - x0), dy = abs(y1 - y0);
    int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
    int err = dx - dy;
    while (x0 != x1 || y0 != y1) {
        int e2 = 2 * err;
        if (e2 > -dy) { err -= dy; x0 += sx; }
        if (e2 <  dx) { err += dx; y0 += sy; }
        if (x0 == x1 && y0 == y1) break;   // 도착점은 통과 → 벽면이 보임
        if (map[y0][x0] == 0) return 0;    // 중간에 벽이면 막힘
    }
    return 1;
}

// 반경 R의 원형 시야 계산 (손전등 밝기/연료에 따라 R이 바뀜)
static void compute_fov(int px, int py, int R) {
    for (int y = 0; y < MAP_H; y++)
        for (int x = 0; x < MAP_W; x++) visible[y][x] = 0;
    for (int y = py - R; y <= py + R; y++)
        for (int x = px - R; x <= px + R; x++) {
            if (!in_bounds(x, y)) continue;
            int ddx = x - px, ddy = y - py;
            if (ddx*ddx + ddy*ddy > R*R) continue;
            if (line_of_sight(px, py, x, y)) { visible[y][x] = 1; explored[y][x] = 1; }
        }
}

// 거리에 따른 밝기 감쇠 (중심 밝고 가장자리 어둡게 → 손전등 느낌)
static float light_level(int px, int py, int x, int y, int R) {
    float d = sqrtf((float)((x-px)*(x-px) + (y-py)*(y-py)));
    float t = 1.0f - d / (float)R;
    if (t < 0) t = 0;
    return 0.25f + 0.75f * t;
}

// ============================================================
//  게임 상태 (한 판 = 한 층. 퍼머데스 루프)
// ============================================================
typedef enum { HUNT_WANDER, HUNT_NOISE, HUNT_LOCKON } HuntState;

typedef struct {
    int  px, py;                 // 플레이어
    int  cx, cy;                 // 추격자(Chaser)
    Vec  exit;                   // 탈출구
    Vec  noise_src;              // 마지막 소음 위치
    float noise_timer;           // 소음 잔존 시간(초)
    Vec  last_known;             // 추격자가 기억하는 마지막 플레이어 위치
    int  has_last;
    HuntState state;
    Vec  wander;                 // 배회 목표
    int  has_wander;
    float floor_time;            // 이 층 체류 시간(초) → 에스컬레이션
    float chaser_timer;          // 추격자 이동 쿨다운
    float move_timer;            // 플레이어 반복이동 쿨다운
    int  depth;                  // 내려온 층 수
    int  caught;

    // 빛/연료
    int   light_on;              // 손전등 on/off
    float fuel;                  // 0..100
    float stamina;               // 0..100, 달리기 소모 → 질주 거리 제한
    int   fov_r;                 // 이번 프레임 실제 시야 반경(렌더용)
    Vec   batt[MAX_BATT];        // 배터리 픽업
    int   batt_alive[MAX_BATT];
    int   nbatt;

    // 아이템/숨기
    Vec   facing;                // 마지막 이동 방향(미끼 던질 방향)
    int   decoys;               // 남은 미끼 수
    float decoy_flash;          // 착탄 표시 잔광
    Vec   decoy_pos;
    Vec   hide[MAX_HIDE];        // 은신처(로커)
    int   nhide;
    int   hidden;               // 숨어 있는가
} Game;

static int cheby(int x0, int y0, int x1, int y1) {
    int dx = abs(x0-x1), dy = abs(y0-y1);
    return dx > dy ? dx : dy;
}

// 층 배치: 플레이어에서 가장 먼 곳에 추격자, 추격자에서 가장 먼 곳에 탈출구, 배터리 몇 개.
static void reset_floor(Game *g) {
    Vec start = generate_map();
    g->px = start.x; g->py = start.y;

    // 탈출구: 플레이어에서 가장 먼 곳 → 층을 가로지르는 긴 여정
    dijkstra_from(g->px, g->py);
    static int distP[MAP_H][MAP_W];
    for (int y = 0; y < MAP_H; y++)
        for (int x = 0; x < MAP_W; x++) distP[y][x] = dist[y][x];
    g->exit = farthest();

    // 추격자: 플레이어↔탈출구 경로의 병목(양쪽에서 가장 먼 지점)에 매복 → 관문
    dijkstra_from(g->exit.x, g->exit.y);
    Vec chaser = {g->px, g->py};
    int best = -1;
    for (int y = 0; y < MAP_H; y++)
        for (int x = 0; x < MAP_W; x++) {
            if (map[y][x] != 1 || distP[y][x] >= INF || dist[y][x] >= INF) continue;
            int score = distP[y][x] < dist[y][x] ? distP[y][x] : dist[y][x];
            if (score > best) { best = score; chaser = (Vec){x, y}; }
        }
    g->cx = chaser.x; g->cy = chaser.y;

    // 배터리 배치 (플레이어 도달 가능 타일에서 무작위)
    dijkstra_from(g->px, g->py);
    g->nbatt = 3;
    for (int i = 0; i < g->nbatt; i++) { g->batt[i] = random_reachable(); g->batt_alive[i] = 1; }
    g->nhide = MAX_HIDE;
    for (int i = 0; i < g->nhide; i++) g->hide[i] = random_reachable();

    g->facing = (Vec){0, -1};
    g->decoys = 2;
    g->decoy_flash = 0.0f;
    g->hidden = 0;

    g->noise_timer = 0.0f;
    g->has_last = 0;
    g->has_wander = 0;
    g->state = HUNT_WANDER;
    g->floor_time = 0.0f;
    g->chaser_timer = 0.0f;
    g->move_timer = 0.0f;
    g->caught = 0;

    g->light_on = 1;
    g->fuel = 60.0f;
    g->stamina = 100.0f;
    g->fov_r = FOV_MAX;

    compute_fov(g->px, g->py, FOV_MAX);
}

static void new_run(Game *g) {
    g->depth = 1;
    reset_floor(g);
}

// ---------- 추격자 한 스텝: 목표 결정 → 그 목표의 거리장으로 한 칸 내려감 ----------
static void chaser_step(Game *g) {
    Vec target;
    // 어두우면(손전등 off/연료 0) 근접해야만 시야로 들킴. 켜져 있으면 원거리도 들킴.
    int lit = g->light_on && g->fuel > 0.0f;
    int see = !g->hidden &&                              // 숨으면 시야로 안 들킴
              line_of_sight(g->cx, g->cy, g->px, g->py) &&
              (lit || cheby(g->cx, g->cy, g->px, g->py) <= 4);

    if (see) {
        target = (Vec){g->px, g->py};
        g->last_known = target; g->has_last = 1;
        g->state = HUNT_LOCKON;
    } else if (g->noise_timer > 0.0f) {
        target = g->noise_src;
        g->last_known = target; g->has_last = 1;
        g->state = HUNT_NOISE;
    } else if (g->has_last) {
        target = g->last_known;
        g->state = HUNT_NOISE;
        if (g->cx == target.x && g->cy == target.y) g->has_last = 0;
    } else {
        g->state = HUNT_WANDER;
        if (!g->has_wander || (g->cx == g->wander.x && g->cy == g->wander.y)) {
            dijkstra_from(g->px, g->py);
            g->wander = random_reachable();
            g->has_wander = 1;
        }
        target = g->wander;
    }

    dijkstra_from(target.x, target.y);
    if (dist[g->cy][g->cx] >= INF) return;
    Vec nxt = descend(g->cx, g->cy);
    g->cx = nxt.x; g->cy = nxt.y;
}

static float chaser_delay(Game *g) {
    float base = (g->state == HUNT_LOCKON) ? 0.080f :   // 봄: 달리기와 동급 이상 → 시야 끊어야 산다
                 (g->state == HUNT_NOISE)  ? 0.090f :    // 들림: 소음=서서히 좁혀옴(시끄러움의 비용)
                 0.26f;                                  // 놓침: 느림 → 무음이면 따돌린다
    float escal = 1.0f - g->floor_time * 0.006f;
    if (escal < 0.55f) escal = 0.55f;
    return base * escal;
}

int main(void) {
    InitWindow(MAP_W * TILE, MAP_H * TILE, "STALKED");
    SetTargetFPS(60);
    SetRandomSeed((unsigned int)time(NULL));

    // 오디오 스트림: 콜백이 파형을 실시간 합성 (오디오 장치 없으면 조용히 스킵)
    InitAudioDevice();
    AudioStream stream = {0};
    int audio_ok = IsAudioDeviceReady();
    if (audio_ok) {
        stream = LoadAudioStream(SR, 16, 1);
        SetAudioStreamCallback(stream, audio_cb);
        PlayAudioStream(stream);
    }

    Game g;
    new_run(&g);
    int debug = 0;

    while (!WindowShouldClose()) {
        float dt = GetFrameTime();

        if (IsKeyPressed(KEY_TAB)) debug = !debug;
        if (IsKeyPressed(KEY_R))   new_run(&g);

        if (!g.caught) {
            g.floor_time += dt;

            // ---------- 숨기(로커): 은신처 위에서 E → 시야·탐지 차단하고 버티기 ----------
            int on_hide = 0;
            for (int i = 0; i < g.nhide; i++)
                if (g.px == g.hide[i].x && g.py == g.hide[i].y) on_hide = 1;
            if (IsKeyPressed(KEY_E) && on_hide) g.hidden = !g.hidden;

            // ---------- 미끼 던지기: 바라보는 방향으로 소음원을 날려 추격자를 유인 ----------
            if (IsKeyPressed(KEY_Q) && g.decoys > 0 && !g.hidden) {
                int lx = g.px, ly = g.py;
                for (int s = 0; s < 6; s++) {        // 벽에 막힐 때까지 최대 6칸 날아감
                    int nx = lx + g.facing.x, ny = ly + g.facing.y;
                    if (!is_floor(nx, ny)) break;
                    lx = nx; ly = ny;
                }
                g.noise_src = (Vec){lx, ly};
                g.noise_timer = 4.0f;                // 달리기(3s)보다 강한 유인
                g.decoy_pos = (Vec){lx, ly};
                g.decoy_flash = 0.7f;
                g.decoys--;
            }
            if (g.decoy_flash > 0.0f) g.decoy_flash -= dt;

            // ---------- 손전등 토글 ----------
            if (IsKeyPressed(KEY_F) && g.fuel > 0.0f) g.light_on = !g.light_on;
            int lit = g.light_on && g.fuel > 0.0f;
            if (lit) {
                g.fuel -= 3.2f * dt;                 // 켜면 연료 소모
                if (g.fuel < 0.0f) g.fuel = 0.0f;
                // 빛이 추격자를 끌어당김 = 원거리 시야선에 노출(chaser_step의 lit 락온).
                // 시야만 끊으면 따돌릴 수 있으므로 상시 소음 비콘은 두지 않는다.
            }

            // ---------- 이번 프레임 시야 반경 (연료 낮으면 깜빡·축소) ----------
            int R;
            if (g.hidden) {
                R = 3;                               // 로커 틈새 시야
            } else if (lit) {
                R = FOV_MAX;
                if (g.fuel < 20.0f) {                // 저연료 플리커
                    R = 6;
                    if (sinf((float)GetTime() * 22.0f) > 0.6f) R = 3;
                }
            } else {
                R = 3;                               // 어둠: 겨우 발밑만
            }
            g.fov_r = R;

            // ---------- 입력 & 이동 모드 ----------
            int want_run = IsKeyDown(KEY_LEFT_SHIFT)   || IsKeyDown(KEY_RIGHT_SHIFT);
            int sneaking = IsKeyDown(KEY_LEFT_CONTROL) || IsKeyDown(KEY_RIGHT_CONTROL);
            int running  = want_run && g.stamina > 0.0f;   // 스태미나 없으면 못 뜀
            if (running) { g.stamina -= 34.0f * dt; if (g.stamina < 0.0f) g.stamina = 0.0f; }
            else         { g.stamina += 14.0f * dt; if (g.stamina > 100.0f) g.stamina = 100.0f; }
            float pdelay = running ? 0.075f : sneaking ? 0.19f : 0.12f;

            int mvx = 0, mvy = 0, pressed = 0;
            if (IsKeyPressed(KEY_W) || IsKeyPressed(KEY_UP))    { mvy = -1; pressed = 1; }
            if (IsKeyPressed(KEY_S) || IsKeyPressed(KEY_DOWN))  { mvy =  1; pressed = 1; }
            if (IsKeyPressed(KEY_A) || IsKeyPressed(KEY_LEFT))  { mvx = -1; pressed = 1; }
            if (IsKeyPressed(KEY_D) || IsKeyPressed(KEY_RIGHT)) { mvx =  1; pressed = 1; }

            g.move_timer -= dt;
            if (!pressed && g.move_timer <= 0.0f) {
                if      (IsKeyDown(KEY_W) || IsKeyDown(KEY_UP))    { mvy = -1; pressed = 1; }
                else if (IsKeyDown(KEY_S) || IsKeyDown(KEY_DOWN))  { mvy =  1; pressed = 1; }
                else if (IsKeyDown(KEY_A) || IsKeyDown(KEY_LEFT))  { mvx = -1; pressed = 1; }
                else if (IsKeyDown(KEY_D) || IsKeyDown(KEY_RIGHT)) { mvx =  1; pressed = 1; }
            }

            if (pressed && !g.hidden) g.facing = (Vec){mvx, mvy};   // 미끼 던질 방향 갱신
            if (pressed && !g.hidden && g.move_timer <= 0.0f) {
                int nx = g.px + mvx, ny = g.py + mvy;
                if (is_floor(nx, ny)) {
                    g.px = nx; g.py = ny;
                    if (!sneaking) {                 // 소음: 걸으면 살짝, 뛰면 크게, 살금이면 없음
                        g.noise_src = (Vec){g.px, g.py};
                        g.noise_timer = running ? 3.0f : 0.9f;
                    }
                    for (int i = 0; i < g.nbatt; i++)   // 배터리 획득
                        if (g.batt_alive[i] && g.px == g.batt[i].x && g.py == g.batt[i].y) {
                            g.fuel += 35.0f; if (g.fuel > 100.0f) g.fuel = 100.0f;
                            g.batt_alive[i] = 0;
                        }
                    if (g.px == g.exit.x && g.py == g.exit.y) {   // 탈출 → 다음 층
                        g.depth++; reset_floor(&g);
                    }
                }
                g.move_timer = pdelay;
            }

            if (g.noise_timer > 0.0f) g.noise_timer -= dt;

            compute_fov(g.px, g.py, g.fov_r);

            // ---------- 추격자 갱신 ----------
            g.chaser_timer -= dt;
            if (g.chaser_timer <= 0.0f) {
                int ocx = g.cx, ocy = g.cy;
                chaser_step(&g);
                g.chaser_timer = chaser_delay(&g);
                if (g.cx != ocx || g.cy != ocy) av_footstep++;   // 발소리 트리거
                if (!g.hidden && g.cx == g.px && g.cy == g.py) g.caught = 1;
            }
            if (g.cx == g.px && g.cy == g.py) g.caught = 1;
        }

        // ---------- 오디오 파라미터 갱신(오디오 스레드가 읽음) ----------
        {
            float d = sqrtf((float)((g.cx-g.px)*(g.cx-g.px) + (g.cy-g.py)*(g.cy-g.py)));
            float prox = 1.0f - d / 12.0f; if (prox < 0) prox = 0;
            av_prox   = prox;
            av_lockon = (g.state == HUNT_LOCKON);
            av_caught = g.caught;
        }

        // ============================================================
        //  렌더
        // ============================================================
        BeginDrawing();
        ClearBackground(BLACK);

        for (int y = 0; y < MAP_H; y++)
            for (int x = 0; x < MAP_W; x++) {
                Color c;
                if (visible[y][x] || debug) {
                    float l = (visible[y][x]) ? light_level(g.px, g.py, x, y, g.fov_r) : 0.5f;
                    if (map[y][x] == 1)
                        c = (Color){ (unsigned char)(50*l), (unsigned char)(55*l), (unsigned char)(62*l), 255 };
                    else
                        c = (Color){ (unsigned char)(120*l),(unsigned char)(108*l),(unsigned char)(96*l), 255 };
                } else if (explored[y][x]) {
                    c = (map[y][x] == 1) ? (Color){10,12,18,255} : (Color){22,24,32,255};
                } else continue;
                DrawRectangle(x*TILE, y*TILE, TILE, TILE, c);
            }

        // 배터리 (보이거나 디버그) — 청록
        for (int i = 0; i < g.nbatt; i++)
            if (g.batt_alive[i] && (visible[g.batt[i].y][g.batt[i].x] || debug))
                DrawRectangle(g.batt[i].x*TILE+6, g.batt[i].y*TILE+5, TILE-12, TILE-10,
                              (Color){70,200,220,255});

        // 은신처(로커) — 강청색 틀
        for (int i = 0; i < g.nhide; i++) {
            int hx = g.hide[i].x, hy = g.hide[i].y;
            if (visible[hy][hx] || explored[hy][hx] || debug) {
                int on = visible[hy][hx] || debug;
                DrawRectangleLines(hx*TILE+3, hy*TILE+2, TILE-6, TILE-3,
                    on ? (Color){120,140,210,255} : (Color){40,48,80,255});
                DrawRectangle(hx*TILE+9, hy*TILE+5, 3, TILE-9,
                    on ? (Color){120,140,210,220} : (Color){40,48,80,180});
            }
        }

        // 미끼 착탄 잔광 (플레이어 행동 피드백)
        if (g.decoy_flash > 0.0f) {
            float a = g.decoy_flash / 0.7f;
            DrawCircle(g.decoy_pos.x*TILE+TILE/2, g.decoy_pos.y*TILE+TILE/2,
                       6.0f + 10.0f*(1.0f-a), (Color){120,180,255,(unsigned char)(180*a)});
        }

        // 탈출구
        if (visible[g.exit.y][g.exit.x] || explored[g.exit.y][g.exit.x] || debug) {
            int on = (visible[g.exit.y][g.exit.x] || debug);
            DrawRectangle(g.exit.x*TILE+2, g.exit.y*TILE+2, TILE-4, TILE-4,
                          on ? (Color){60,200,110,255} : (Color){20,60,40,255});
        }

        // 추격자 — 시야에 들어올 때만 보임(공포는 대부분 '안 보임'). 디버그면 항상.
        if (visible[g.cy][g.cx] || debug) {
            DrawRectangle(g.cx*TILE+2, g.cy*TILE+2, TILE-4, TILE-4, (Color){200,40,40,255});
            DrawRectangle(g.cx*TILE+6, g.cy*TILE+6, TILE-12, TILE-12, (Color){255,120,120,255});
        }

        // 플레이어 (숨으면 어둑하게)
        {
            Color pc = g.hidden ? (Color){110,120,90,255} : (Color){220,210,120,255};
            DrawRectangle(g.px*TILE+3, g.py*TILE+3, TILE-6, TILE-6, pc);
        }

        // ---------- 근접 비네트: 추격자가 가까울수록 화면 가장자리가 붉게 맥동 ----------
        {
            float d = sqrtf((float)((g.cx-g.px)*(g.cx-g.px) + (g.cy-g.py)*(g.cy-g.py)));
            float prox = 1.0f - d / 12.0f;
            if (prox > 0.0f && !g.caught) {
                float pulse = 0.6f + 0.4f * sinf((float)GetTime() * (3.0f + prox*6.0f));
                unsigned char a = (unsigned char)(prox * pulse * 120.0f);
                int W = MAP_W*TILE, H = MAP_H*TILE, b = 46;
                Color edge = (Color){140, 0, 0, a};
                DrawRectangleGradientV(0, 0, W, b, edge, (Color){140,0,0,0});
                DrawRectangleGradientV(0, H-b, W, b, (Color){140,0,0,0}, edge);
                DrawRectangleGradientH(0, 0, b, H, edge, (Color){140,0,0,0});
                DrawRectangleGradientH(W-b, 0, b, H, (Color){140,0,0,0}, edge);
            }
        }

        // ---------- HUD ----------
        DrawText(TextFormat("DEPTH %d", g.depth), 8, 8, 16, (Color){150,150,160,255});

        // 연료 바
        int bw = 140, bx = 8, by = 30;
        DrawRectangle(bx, by, bw, 8, (Color){35,35,40,255});
        int fw = (int)(bw * g.fuel / 100.0f); if (fw < 0) fw = 0;
        Color fc = g.fuel < 20.0f ? (Color){210,70,50,255} : (Color){210,190,90,255};
        DrawRectangle(bx, by, fw, 8, fc);
        DrawText(g.light_on ? "FLASHLIGHT" : "dark", bx + bw + 8, by - 3, 12,
                 g.light_on ? (Color){210,190,90,255} : (Color){90,90,100,255});
        // 스태미나 바
        int sy = by + 12;
        DrawRectangle(bx, sy, bw, 6, (Color){35,35,40,255});
        int sw = (int)(bw * g.stamina / 100.0f); if (sw < 0) sw = 0;
        DrawRectangle(bx, sy, sw, 6, (Color){90,180,150,255});
        DrawText(TextFormat("DECOY x%d", g.decoys), bx, sy + 10, 12, (Color){120,180,255,255});

        // 숨기 상태/프롬프트
        {
            int oh = 0;
            for (int i = 0; i < g.nhide; i++)
                if (g.px == g.hide[i].x && g.py == g.hide[i].y) oh = 1;
            if (g.hidden) {
                const char *h = "HIDDEN  -  E to leave";
                int w = MeasureText(h, 18);
                DrawText(h, (MAP_W*TILE - w)/2, MAP_H*TILE - 50, 18, (Color){150,170,220,255});
            } else if (oh) {
                const char *h = "E: hide";
                int w = MeasureText(h, 16);
                DrawText(h, (MAP_W*TILE - w)/2, MAP_H*TILE - 48, 16, (Color){120,140,200,220});
            }
        }

        DrawText("WASD move  Shift:run  Ctrl:sneak  F:light  Q:decoy  E:hide  R:restart",
                 8, MAP_H*TILE - 22, 14, (Color){80,80,88,255});

        if (debug) {
            const char *st = (g.state==HUNT_LOCKON) ? "LOCKON" :
                             (g.state==HUNT_NOISE)  ? "NOISE"  : "WANDER";
            DrawText(TextFormat("chaser:%s  noise:%.1f  floor:%.0fs  fuel:%.0f",
                     st, g.noise_timer, g.floor_time, g.fuel), 8, 48, 14,
                     (Color){200,120,120,255});
            if (g.noise_timer > 0.0f)
                DrawCircle(g.noise_src.x*TILE+TILE/2, g.noise_src.y*TILE+TILE/2, 5,
                           (Color){80,160,255,180});
        }

        if (g.caught) {
            int W = MAP_W*TILE, H = MAP_H*TILE;
            DrawRectangle(0, 0, W, H, (Color){40,0,0,150});
            const char *t1 = "CAUGHT";
            const char *t2 = "Press R to run again";
            int w1 = MeasureText(t1, 48), w2 = MeasureText(t2, 20);
            DrawText(t1, (W-w1)/2, H/2 - 40, 48, (Color){230,60,60,255});
            DrawText(t2, (W-w2)/2, H/2 + 16, 20, (Color){200,200,200,255});
        }

        EndDrawing();
    }

    if (audio_ok) { StopAudioStream(stream); UnloadAudioStream(stream); }
    CloseAudioDevice();
    CloseWindow();
    return 0;
}
