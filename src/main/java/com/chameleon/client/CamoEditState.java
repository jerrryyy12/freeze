package com.chameleon.client;

import com.chameleon.net.CamoSyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 색칠 편집 상태(클라 전역). 화면을 닫았다 다시 열어도(스포이드 등) 작업이 유지되도록
 * 픽셀/선택색/브러시/팔레트를 정적으로 보관한다.
 */
public class CamoEditState {
    public static final int SIZE = CamoSyncPacket.SIZE;
    public static final int LEN = CamoSyncPacket.LEN;
    public static final int SCALE = SIZE / 64; // 64-단위 좌표 → 실제 텍스처 배율

    public static int[] pixels = null;
    public static int selectedColor = 0xFFFF0000;
    public static int brush = 1;
    public static int view = 0; // 0앞 1뒤 2좌 3우 4위 5아래
    public static boolean eyedropperArmed = false;
    public static boolean camoOn = false; // 현재 위장 표시 중인지(원래 스킨 토글용)
    public static boolean gameActive = false; // 게임 진행 중(H 토글 잠금)
    public static int gameSecondsLeft = 0;    // HUD 타이머용 남은 시간
    public static final List<Integer> palette = new ArrayList<>();

    /** 부위별 베이스(전개도) 영역 — 회색 캔버스로 채울 곳. */
    static final int[][] BASE_RECTS = {
            {0, 0, 32, 16},   // 머리
            {0, 16, 16, 16},  // 오른다리
            {16, 16, 24, 16}, // 몸통
            {40, 16, 16, 16}, // 오른팔
            {16, 48, 16, 16}, // 왼다리
            {32, 48, 16, 16}, // 왼팔
    };

    /** 되돌리기 스냅샷 스택. */
    public static final Deque<int[]> undoStack = new ArrayDeque<>();

    public static void pushUndo() {
        if (pixels == null) return;
        undoStack.push(pixels.clone());
        while (undoStack.size() > 20) undoStack.removeLast();
    }

    public static boolean undo() {
        if (undoStack.isEmpty()) return false;
        pixels = undoStack.pop();
        return true;
    }

    public static void ensureInit() {
        if (pixels == null) {
            int[] cur = Minecraft.getInstance().player != null
                    ? CamoClient.getPixels(Minecraft.getInstance().player.getUUID()) : null;
            if (cur != null && cur.length == LEN) {
                pixels = cur.clone();
            } else {
                resetCanvas();
            }
        }
        if (palette.isEmpty()) buildPalette();
    }

    public static void resetCanvas() {
        pixels = new int[LEN];
        // BASE_RECTS는 64-단위 → SCALE 배율로 채움
        for (int[] r : BASE_RECTS)
            fill(r[0] * SCALE, r[1] * SCALE, r[2] * SCALE, r[3] * SCALE, 0xFFB0B0B0);
    }

    static void fill(int u, int v, int w, int h, int argb) {
        for (int y = v; y < v + h; y++)
            for (int x = u; x < u + w; x++)
                if (x >= 0 && x < SIZE && y >= 0 && y < SIZE) pixels[y * SIZE + x] = argb;
    }

    public static void buildPalette() {
        Set<Integer> set = new LinkedHashSet<>();

        // 주변 블록 색 (가까운 것 우선)
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null) {
            Level level = mc.level;
            BlockPos c = mc.player.blockPosition();
            int R = 8;
            outer:
            for (int rad = 0; rad <= R; rad++) {
                for (int dx = -rad; dx <= rad; dx++)
                    for (int dy = -rad; dy <= rad; dy++)
                        for (int dz = -rad; dz <= rad; dz++) {
                            if (Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) != rad) continue;
                            BlockPos p = c.offset(dx, dy, dz);
                            BlockState st = level.getBlockState(p);
                            if (st.isAir()) continue;
                            MapColor mcCol = st.getMapColor(level, p);
                            if (mcCol != MapColor.NONE) set.add(0xFF000000 | (mcCol.col & 0xFFFFFF));
                            if (set.size() >= 40) break outer;
                        }
            }
        }

        // 기본 팔레트 (회색 + 무지개)
        int[] basics = {
                0xFF000000, 0xFF404040, 0xFF808080, 0xFFB0B0B0, 0xFFD8D8D8, 0xFFFFFFFF,
                0xFFFF0000, 0xFFFF6A00, 0xFFFFD800, 0xFFB6FF00, 0xFF00FF21, 0xFF00FFA8,
                0xFF00FFFF, 0xFF0094FF, 0xFF0026FF, 0xFF7F00FF, 0xFFFF00DC, 0xFFFF006E,
                0xFF8B5A2B, 0xFF5A3A1A, 0xFFC8A05A, 0xFFF0C8A0, 0xFF3A5F0B, 0xFF1E5AA8,
        };
        for (int b : basics) set.add(b);

        palette.clear();
        palette.addAll(set);
        if (selectedColorMissing()) selectedColor = palette.isEmpty() ? 0xFFFF0000 : palette.get(0);
    }

    private static boolean selectedColorMissing() {
        return !palette.contains(selectedColor);
    }

    /** 스포이드로 추출한 색을 팔레트 맨 앞에 추가하고 선택. */
    public static void addColor(int argb) {
        palette.remove((Integer) argb);
        palette.add(0, argb);
        selectedColor = argb;
    }
}
