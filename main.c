// ============================================================
//  STALKED — 추격 호러 로그라이크 / Week 2
//  raylib 기반. 창 / 절차적 맵 / 플레이어 이동 / FOV(시야·어둠)
//  + 다익스트라 소음맵 추격자(게임의 심장) + 탈출 목표 + 퍼머데스 루프.
//
//  핵심 훅 = 못 죽이는 추격자. 싸우지 않고 도망치고·속이고·숨는다.
//    - 소음맵: 플레이어 행동(달리기·걷기)이 '소음' → 다익스트라(BFS)로 레벨 전체 전파.
//              추격자는 소음원 방향 경사를 따라 내려온다.
//    - 시야(LOS): 추격자가 플레이어를 보면 락온·가속. 시야를 끊으면 마지막 위치로만 향함.
//    - 에스컬레이션: 한 층에 오래 머물수록 추격자가 빨라짐 → 하강/탈출 압박.
//
//  조작: WASD/방향키 = 이동 / Shift = 달리기(빠름·시끄러움) / Ctrl = 살금(느림·조용)
//        R = 재시작 / TAB = 디버그 오버레이 / ESC = 종료
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
#define FOV_RADIUS  8
#define INF         (1<<28)

// 타일: 0 = 벽, 1 = 바닥
static int map[MAP_H][MAP_W];
static int visible[MAP_H][MAP_W];   // 지금 보이는가
static int explored[MAP_H][MAP_W];  // 한 번이라도 봤는가 (기억 = 어둑하게)
static int dist[MAP_H][MAP_W];      // 다익스트라(BFS) 거리장 — 추격자 경로용으로 매 틱 재계산

typedef struct { int x, y; } Vec;

static const int DIRS[4][2] = {{1,0},{-1,0},{0,1},{0,-1}};

static int in_bounds(int x, int y) {
    return x >= 0 && y >= 0 && x < MAP_W && y < MAP_H;
}
static int is_floor(int x, int y) {
    return in_bounds(x, y) && map[y][x] == 1;
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

// 현재 dist[](= 어떤 목표로부터의 거리장)에서 (x,y) 기준 가장 낮은 값의 이웃으로 내려간다.
// 목표 방향으로 한 칸. 더 낮은 이웃이 없으면(지역 최소=목표 도달) 제자리 유지.
static Vec descend(int x, int y) {
    Vec best = {x, y};
    int bestd = dist[y][x];
    for (int d = 0; d < 4; d++) {
        int nx = x + DIRS[d][0], ny = y + DIRS[d][1];
        if (is_floor(nx, ny) && dist[ny][nx] < bestd) {
            bestd = dist[ny][nx];
            best = (Vec){nx, ny};
        }
    }
    return best;
}

// dist[]에서 유한하면서 가장 먼 바닥 타일 (플레이어에서 먼 구석 = 추격자/탈출구 배치용)
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

// 도달 가능한 임의의 바닥 타일 (추격자 배회 목표)
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
        if (x0 == x1 && y0 == y1) break;   // 도착점(대상 타일)은 통과로 간주 → 벽면이 보임
        if (map[y0][x0] == 0) return 0;    // 중간에 벽이면 시야 막힘
    }
    return 1;
}

static void compute_fov(int px, int py) {
    for (int y = 0; y < MAP_H; y++)
        for (int x = 0; x < MAP_W; x++) visible[y][x] = 0;

    for (int y = py - FOV_RADIUS; y <= py + FOV_RADIUS; y++)
        for (int x = px - FOV_RADIUS; x <= px + FOV_RADIUS; x++) {
            if (!in_bounds(x, y)) continue;
            int ddx = x - px, ddy = y - py;
            if (ddx*ddx + ddy*ddy > FOV_RADIUS*FOV_RADIUS) continue;  // 원형 시야
            if (line_of_sight(px, py, x, y)) { visible[y][x] = 1; explored[y][x] = 1; }
        }
}

// 거리에 따른 밝기 감쇠 (중심 밝고 가장자리 어둡게 → 손전등 느낌)
static float light_level(int px, int py, int x, int y) {
    float d = sqrtf((float)((x-px)*(x-px) + (y-py)*(y-py)));
    float t = 1.0f - d / (float)FOV_RADIUS;
    if (t < 0) t = 0;
    return 0.25f + 0.75f * t;   // 최소 밝기는 유지
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
    float noise_timer;           // 소음이 남아있는 시간(초). 0이면 냄새 끊김
    Vec  last_known;             // 추격자가 기억하는 마지막 플레이어 위치
    int  has_last;
    HuntState state;
    Vec  wander;                 // 배회 목표
    int  has_wander;
    float floor_time;            // 이 층에 머문 시간(초) → 에스컬레이션
    float chaser_timer;          // 추격자 이동 쿨다운
    float move_timer;            // 플레이어 반복이동 쿨다운
    int  depth;                  // 내려온 층 수
    int  caught;                 // 잡혔는가
} Game;

// 층 배치: 맵 생성 후 플레이어에서 가장 먼 곳에 추격자, 추격자에서 가장 먼 곳에 탈출구.
static void reset_floor(Game *g) {
    Vec start = generate_map();
    g->px = start.x; g->py = start.y;

    dijkstra_from(g->px, g->py);
    Vec chaser = farthest();            // 추격자는 먼 구석에서 시작
    g->cx = chaser.x; g->cy = chaser.y;

    dijkstra_from(g->cx, g->cy);
    g->exit = farthest();               // 탈출구는 추격자 반대편

    g->noise_timer = 0.0f;
    g->has_last = 0;
    g->has_wander = 0;
    g->state = HUNT_WANDER;
    g->floor_time = 0.0f;
    g->chaser_timer = 0.0f;
    g->move_timer = 0.0f;
    g->caught = 0;

    compute_fov(g->px, g->py);
}

static void new_run(Game *g) {
    g->depth = 1;
    reset_floor(g);
}

// ---------- 추격자 한 스텝: 목표 결정 → 그 목표의 거리장으로 한 칸 내려감 ----------
static void chaser_step(Game *g) {
    Vec target;
    int see = line_of_sight(g->cx, g->cy, g->px, g->py);

    if (see) {                              // 보인다 → 락온
        target = (Vec){g->px, g->py};
        g->last_known = target; g->has_last = 1;
        g->state = HUNT_LOCKON;
    } else if (g->noise_timer > 0.0f) {     // 소리를 들었다 → 소음원으로
        target = g->noise_src;
        g->last_known = target; g->has_last = 1;
        g->state = HUNT_NOISE;
    } else if (g->has_last) {               // 마지막 목격/소음 지점으로
        target = g->last_known;
        g->state = HUNT_NOISE;
        if (g->cx == target.x && g->cy == target.y) g->has_last = 0;  // 도착 → 단서 소진
    } else {                                // 단서 없음 → 배회
        g->state = HUNT_WANDER;
        if (!g->has_wander ||
            (g->cx == g->wander.x && g->cy == g->wander.y)) {
            dijkstra_from(g->px, g->py);    // 도달 가능 판정용
            g->wander = random_reachable();
            g->has_wander = 1;
        }
        target = g->wander;
    }

    dijkstra_from(target.x, target.y);
    if (dist[g->cy][g->cx] >= INF) return;  // 목표 도달 불가면 대기
    Vec nxt = descend(g->cx, g->cy);
    g->cx = nxt.x; g->cy = nxt.y;
}

// 추격자 이동 간격: 상태별 기본값 × 에스컬레이션(오래 머물수록 빨라짐)
static float chaser_delay(Game *g) {
    float base = (g->state == HUNT_LOCKON) ? 0.11f :
                 (g->state == HUNT_NOISE)  ? 0.17f : 0.26f;
    float escal = 1.0f - g->floor_time * 0.006f;   // ~70초 후 ~0.58배
    if (escal < 0.55f) escal = 0.55f;
    return base * escal;
}

int main(void) {
    InitWindow(MAP_W * TILE, MAP_H * TILE, "STALKED");
    SetTargetFPS(60);
    SetRandomSeed((unsigned int)time(NULL));

    Game g;
    new_run(&g);

    int debug = 0;

    while (!WindowShouldClose()) {
        float dt = GetFrameTime();

        if (IsKeyPressed(KEY_TAB)) debug = !debug;
        if (IsKeyPressed(KEY_R)) { new_run(&g); }

        if (!g.caught) {
            g.floor_time += dt;

            // ---------- 입력 & 이동 모드 ----------
            int running  = IsKeyDown(KEY_LEFT_SHIFT) || IsKeyDown(KEY_RIGHT_SHIFT);
            int sneaking = IsKeyDown(KEY_LEFT_CONTROL) || IsKeyDown(KEY_RIGHT_CONTROL);
            float pdelay = running ? 0.075f : sneaking ? 0.19f : 0.12f;

            int mvx = 0, mvy = 0, pressed = 0;
            if (IsKeyPressed(KEY_W) || IsKeyPressed(KEY_UP))    { mvy = -1; pressed = 1; }
            if (IsKeyPressed(KEY_S) || IsKeyPressed(KEY_DOWN))  { mvy =  1; pressed = 1; }
            if (IsKeyPressed(KEY_A) || IsKeyPressed(KEY_LEFT))  { mvx = -1; pressed = 1; }
            if (IsKeyPressed(KEY_D) || IsKeyPressed(KEY_RIGHT)) { mvx =  1; pressed = 1; }

            g.move_timer -= dt;
            if (!pressed && g.move_timer <= 0.0f) {   // 꾹 누르면 반복
                if      (IsKeyDown(KEY_W) || IsKeyDown(KEY_UP))    { mvy = -1; pressed = 1; }
                else if (IsKeyDown(KEY_S) || IsKeyDown(KEY_DOWN))  { mvy =  1; pressed = 1; }
                else if (IsKeyDown(KEY_A) || IsKeyDown(KEY_LEFT))  { mvx = -1; pressed = 1; }
                else if (IsKeyDown(KEY_D) || IsKeyDown(KEY_RIGHT)) { mvx =  1; pressed = 1; }
            }

            if (pressed && g.move_timer <= 0.0f) {
                int nx = g.px + mvx, ny = g.py + mvy;
                if (is_floor(nx, ny)) {
                    g.px = nx; g.py = ny;
                    compute_fov(g.px, g.py);
                    // ---------- 소음 발생: 걸으면 살짝, 뛰면 크게, 살금이면 없음 ----------
                    if (!sneaking) {
                        g.noise_src = (Vec){g.px, g.py};
                        g.noise_timer = running ? 3.0f : 0.9f;
                    }
                    // 탈출구 도달 → 다음 층
                    if (g.px == g.exit.x && g.py == g.exit.y) {
                        g.depth++;
                        reset_floor(&g);
                    }
                }
                g.move_timer = pdelay;
            }

            // ---------- 소음 감쇠 ----------
            if (g.noise_timer > 0.0f) g.noise_timer -= dt;

            // ---------- 추격자 갱신 ----------
            g.chaser_timer -= dt;
            if (g.chaser_timer <= 0.0f) {
                chaser_step(&g);
                g.chaser_timer = chaser_delay(&g);
                if (g.cx == g.px && g.cy == g.py) g.caught = 1;   // 잡힘
            }
            if (g.cx == g.px && g.cy == g.py) g.caught = 1;
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
                    float l = (visible[y][x]) ? light_level(g.px, g.py, x, y) : 0.5f;
                    if (map[y][x] == 1)
                        c = (Color){ (unsigned char)(50*l), (unsigned char)(55*l), (unsigned char)(62*l), 255 };
                    else
                        c = (Color){ (unsigned char)(120*l),(unsigned char)(108*l),(unsigned char)(96*l), 255 };
                } else if (explored[y][x]) {
                    c = (map[y][x] == 1) ? (Color){10,12,18,255} : (Color){22,24,32,255};
                } else continue;
                DrawRectangle(x*TILE, y*TILE, TILE, TILE, c);
            }

        // 탈출구 (보이거나 탐색됨) — 은은한 초록
        if (visible[g.exit.y][g.exit.x] || explored[g.exit.y][g.exit.x] || debug) {
            int on = (visible[g.exit.y][g.exit.x] || debug);
            DrawRectangle(g.exit.x*TILE+2, g.exit.y*TILE+2, TILE-4, TILE-4,
                          on ? (Color){60,200,110,255} : (Color){20,60,40,255});
        }

        // 추격자 — 시야에 들어올 때만 보임(공포는 대부분 '안 보임'에서). 디버그면 항상.
        if (visible[g.cy][g.cx] || debug) {
            DrawRectangle(g.cx*TILE+2, g.cy*TILE+2, TILE-4, TILE-4, (Color){200,40,40,255});
            DrawRectangle(g.cx*TILE+6, g.cy*TILE+6, TILE-12, TILE-12, (Color){255,120,120,255});
        }

        // 플레이어
        DrawRectangle(g.px*TILE+3, g.py*TILE+3, TILE-6, TILE-6, (Color){220,210,120,255});

        // ---------- 근접 비네트: 추격자가 가까울수록 화면 가장자리가 붉어짐(코드 생성, 0바이트) ----------
        {
            float d = sqrtf((float)((g.cx-g.px)*(g.cx-g.px) + (g.cy-g.py)*(g.cy-g.py)));
            float prox = 1.0f - d / 12.0f;        // 12칸 밖이면 0
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
        DrawText("Shift:run  Ctrl:sneak  R:restart  Tab:debug",
                 8, MAP_H*TILE - 22, 14, (Color){80,80,88,255});

        if (debug) {
            const char *st = (g.state==HUNT_LOCKON) ? "LOCKON" :
                             (g.state==HUNT_NOISE)  ? "NOISE"  : "WANDER";
            DrawText(TextFormat("chaser:%s  noise:%.1f  floor:%.0fs",
                     st, g.noise_timer, g.floor_time), 8, 28, 14, (Color){200,120,120,255});
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

    CloseWindow();
    return 0;
}
