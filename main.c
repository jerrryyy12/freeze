// ============================================================
//  STALKED — 추격 호러 로그라이크 / Week 1 스캐폴드
//  raylib 기반. 창 / 절차적 맵 / 플레이어 이동 / FOV(시야·어둠).
//  이 뼈대 위에 Week 2에서 "다익스트라 소음맵 추격자"를 올린다.
//
//  빌드 (raylib 설치 필요, 정적 라이브러리 libraylib.a + -s 스트립 시 exe 최소):
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

// 타일: 0 = 벽, 1 = 바닥
static int map[MAP_H][MAP_W];
static int visible[MAP_H][MAP_W];   // 지금 보이는가
static int explored[MAP_H][MAP_W];  // 한 번이라도 봤는가 (기억 = 어둑하게)

typedef struct { int x, y; } Vec;

static int in_bounds(int x, int y) {
    return x >= 0 && y >= 0 && x < MAP_W && y < MAP_H;
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

int main(void) {
    InitWindow(MAP_W * TILE, MAP_H * TILE, "STALKED - 1.44MB scaffold");
    SetTargetFPS(60);
    SetRandomSeed((unsigned int)time(NULL));   // 매 실행 다른 맵

    Vec start = generate_map();
    int px = start.x, py = start.y;
    compute_fov(px, py);

    float move_timer = 0.0f;
    const float MOVE_DELAY = 0.11f;   // 꾹 눌렀을 때 반복 이동 간격

    while (!WindowShouldClose()) {
        int mvx = 0, mvy = 0, pressed = 0;
        if (IsKeyPressed(KEY_W) || IsKeyPressed(KEY_UP))    { mvy = -1; pressed = 1; }
        if (IsKeyPressed(KEY_S) || IsKeyPressed(KEY_DOWN))  { mvy =  1; pressed = 1; }
        if (IsKeyPressed(KEY_A) || IsKeyPressed(KEY_LEFT))  { mvx = -1; pressed = 1; }
        if (IsKeyPressed(KEY_D) || IsKeyPressed(KEY_RIGHT)) { mvx =  1; pressed = 1; }

        move_timer -= GetFrameTime();
        if (!pressed && move_timer <= 0.0f) {   // 꾹 누르면 반복
            if      (IsKeyDown(KEY_W) || IsKeyDown(KEY_UP))    { mvy = -1; pressed = 1; }
            else if (IsKeyDown(KEY_S) || IsKeyDown(KEY_DOWN))  { mvy =  1; pressed = 1; }
            else if (IsKeyDown(KEY_A) || IsKeyDown(KEY_LEFT))  { mvx = -1; pressed = 1; }
            else if (IsKeyDown(KEY_D) || IsKeyDown(KEY_RIGHT)) { mvx =  1; pressed = 1; }
        }

        if (pressed) {
            int nx = px + mvx, ny = py + mvy;
            if (in_bounds(nx, ny) && map[ny][nx] == 1) { px = nx; py = ny; }
            compute_fov(px, py);
            move_timer = MOVE_DELAY;
        }
        if (IsKeyPressed(KEY_R)) {   // 새 맵 (테스트용)
            Vec s = generate_map(); px = s.x; py = s.y; compute_fov(px, py);
        }

        // ---------- 렌더 ----------
        BeginDrawing();
        ClearBackground(BLACK);
        for (int y = 0; y < MAP_H; y++)
            for (int x = 0; x < MAP_W; x++) {
                Color c;
                if (visible[y][x]) {
                    float l = light_level(px, py, x, y);
                    if (map[y][x] == 1)   // 바닥
                        c = (Color){ (unsigned char)(50*l), (unsigned char)(55*l), (unsigned char)(62*l), 255 };
                    else                  // 벽
                        c = (Color){ (unsigned char)(120*l),(unsigned char)(108*l),(unsigned char)(96*l), 255 };
                } else if (explored[y][x]) {   // 기억 = 아주 어둑하게
                    c = (map[y][x] == 1) ? (Color){10,12,18,255} : (Color){22,24,32,255};
                } else continue;               // 미탐색 = 완전 검정
                DrawRectangle(x*TILE, y*TILE, TILE, TILE, c);
            }

        DrawRectangle(px*TILE+3, py*TILE+3, TILE-6, TILE-6, (Color){220,210,120,255}); // 플레이어
        DrawText("WASD/Arrows: move   R: regen map", 8, 8, 14, (Color){90,90,90,255});
        EndDrawing();
    }

    CloseWindow();
    return 0;
}
