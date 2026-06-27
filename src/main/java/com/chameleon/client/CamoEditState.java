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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 색칠 편집 상태(클라 전역). 화면을 닫았다 다시 열어도(스포이드 등) 작업이 유지되도록
 * 픽셀/선택색/브러시/팔레트를 정적으로 보관한다.
 */
public class CamoEditState {
    public static final int SIZE = CamoSyncPacket.SIZE;
    public static final int LEN = CamoSyncPacket.LEN;
    public static final int SCALE = SIZE / 64; // 64-단위 좌표 → 실제 텍스처 배율

    /** 숨는 사람 축소 배율(서버 CamoGame.HIDER_SCALE와 동일). 블록픽셀 대응 계산용. */
    public static final double HIDER_SCALE = 0.5;
    /** 줄어든 상태에서 "마크 블록 픽셀 1개"에 해당하는 텍셀 수. (원래크기 SCALE텍셀=블록1픽셀, 0.5배면 ÷0.5=4) */
    public static final double TEXELS_PER_BLOCKPIXEL = SCALE / HIDER_SCALE;

    public static int[] pixels = null;
    public static int selectedColor = 0xFFFF0000;
    public static int brush = 1;            // 자유 브러시 크기(텍셀 단위)
    public static boolean blockPixelMode = false; // true면 블록픽셀 단위로 칠함
    public static int blockBrush = 1;       // 블록픽셀 모드 브러시 크기(블록픽셀 단위)
    public static int view = 0; // 0앞 1뒤 2좌 3우 4위 5아래
    public static boolean camoOn = false; // 현재 위장 표시 중인지(원래 스킨 토글용)
    public static boolean gameActive = false; // 게임 진행 중(H 토글 잠금)
    public static int phase = 0;              // 0로비 1숨기 2찾기 3공개
    public static boolean hideNames = false;  // 닉네임 숨김(숨기/찾기 페이즈)
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
        if (palette.isEmpty()) refreshPalette();
    }

    /** 게임 종료 시: 그린 캔버스 초기화 + 위장 해제(원래 스킨). */
    public static void resetForGameEnd() {
        pixels = null;          // 다음에 색칠 화면을 열면 빈 캔버스
        undoStack.clear();
        camoOn = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) CamoClient.apply(mc.player.getUUID(), null); // 내 위장 텍스처 제거
    }

    public static void resetCanvas() {
        pixels = new int[LEN];
        // BASE_RECTS는 64-단위 → SCALE 배율로 채움
        for (int[] r : BASE_RECTS)
            fill(r[0] * SCALE, r[1] * SCALE, r[2] * SCALE, r[3] * SCALE, 0xFFB0B0B0);
    }

    /** 몸 전체를 한 색으로 칠한다(보고 있는/선택한 블록색으로 한번에 위장). */
    public static void fillAll(int argb) {
        if (pixels == null) ensureInit();
        for (int[] r : BASE_RECTS)
            fill(r[0] * SCALE, r[1] * SCALE, r[2] * SCALE, r[3] * SCALE, argb);
    }

    static void fill(int u, int v, int w, int h, int argb) {
        for (int y = v; y < v + h; y++)
            for (int x = u; x < u + w; x++)
                if (x >= 0 && x < SIZE && y >= 0 && y < SIZE) pixels[y * SIZE + x] = argb;
    }

    /**
     * 면 f(텍셀 UV: x,y,w,h) 위의 텍셀 (tx,ty)에 현재 브러시로 칠한다. 2D/3D 화면 공용.
     * - blockPixelMode: 블록픽셀 셀 단위로 칠함(면 원점 기준 격자에 스냅).
     * - 아니면: 텍셀 단위 자유 브러시.
     */
    public static void applyBrushOnFace(int[] f, int tx, int ty) {
        if (pixels == null) return;
        if (blockPixelMode) {
            double bp = TEXELS_PER_BLOCKPIXEL;
            int bu = (int) Math.floor((tx - f[0]) / bp);
            int bv = (int) Math.floor((ty - f[1]) / bp);
            int n = Math.max(1, blockBrush);
            int start = -(n - 1) / 2;
            for (int cv = 0; cv < n; cv++)
                for (int cu = 0; cu < n; cu++)
                    fillBlockCell(f, bu + start + cu, bv + start + cv);
        } else {
            int b = Math.max(1, brush);
            int lo = -(b - 1) / 2, hi = b / 2;
            for (int oy = lo; oy <= hi; oy++)
                for (int ox = lo; ox <= hi; ox++) {
                    int xx = tx + ox, yy = ty + oy;
                    if (xx < f[0] || xx >= f[0] + f[2] || yy < f[1] || yy >= f[1] + f[3]) continue;
                    pixels[yy * SIZE + xx] = selectedColor;
                }
        }
    }

    /** 면 f의 (cellU,cellV) 블록픽셀 셀을 채운다(면 경계로 클램프). */
    private static void fillBlockCell(int[] f, int cellU, int cellV) {
        double bp = TEXELS_PER_BLOCKPIXEL;
        int u0 = f[0] + (int) Math.round(cellU * bp);
        int u1 = f[0] + (int) Math.round((cellU + 1) * bp);
        int v0 = f[1] + (int) Math.round(cellV * bp);
        int v1 = f[1] + (int) Math.round((cellV + 1) * bp);
        for (int yy = Math.max(v0, f[1]); yy < Math.min(v1, f[1] + f[3]); yy++)
            for (int xx = Math.max(u0, f[0]); xx < Math.min(u1, f[0] + f[2]); xx++)
                pixels[yy * SIZE + xx] = selectedColor;
    }

    /** 기본 팔레트 (회색 + 무지개). */
    private static final int[] BASICS = {
            0xFF000000, 0xFF404040, 0xFF808080, 0xFFB0B0B0, 0xFFD8D8D8, 0xFFFFFFFF,
            0xFFFF0000, 0xFFFF6A00, 0xFFFFD800, 0xFFB6FF00, 0xFF00FF21, 0xFF00FFA8,
            0xFF00FFFF, 0xFF0094FF, 0xFF0026FF, 0xFF7F00FF, 0xFFFF00DC, 0xFFFF006E,
            0xFF8B5A2B, 0xFF5A3A1A, 0xFFC8A05A, 0xFFF0C8A0, 0xFF3A5F0B, 0xFF1E5AA8,
    };

    /** 스포이드로 추출한 색(여러 번 열어도 유지). */
    private static final List<Integer> extracted = new ArrayList<>();

    /**
     * 팔레트 갱신: [스포이드 추출색] + [주변 블록색(많은 순)] + [기본색].
     * 색칠 화면을 열 때마다 호출해 현재 위치의 주변 블록색이 뜨도록 한다.
     */
    public static void refreshPalette() {
        Set<Integer> set = new LinkedHashSet<>();
        set.addAll(extracted);             // 스포이드 추출색 먼저
        set.addAll(nearbyBlockColors());   // 주변 블록색(많이 보이는 색 우선)
        for (int b : BASICS) set.add(b);   // 기본색
        palette.clear();
        palette.addAll(set);
        if (!palette.contains(selectedColor))
            selectedColor = palette.isEmpty() ? 0xFFFF0000 : palette.get(0);
    }

    /** 플레이어 주변 블록의 대표색(MapColor)을 많이 보이는 순으로 모은다. */
    private static List<Integer> nearbyBlockColors() {
        List<Integer> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return out;
        Level level = mc.level;
        BlockPos c = mc.player.blockPosition();
        int R = 8;
        Map<Integer, Integer> freq = new HashMap<>();
        for (int dx = -R; dx <= R; dx++)
            for (int dy = -R; dy <= R; dy++)
                for (int dz = -R; dz <= R; dz++) {
                    BlockPos p = c.offset(dx, dy, dz);
                    BlockState st = level.getBlockState(p);
                    if (st.isAir()) continue;
                    MapColor col = st.getMapColor(level, p);
                    if (col == MapColor.NONE) continue;
                    freq.merge(0xFF000000 | (col.col & 0xFFFFFF), 1, Integer::sum);
                }
        List<Map.Entry<Integer, Integer>> entries = new ArrayList<>(freq.entrySet());
        entries.sort((a, b) -> b.getValue() - a.getValue());
        for (int i = 0; i < entries.size() && i < 32; i++) out.add(entries.get(i).getKey());
        return out;
    }

    /** 스포이드로 추출한 색을 맨 앞에 추가하고 선택. 팔레트 갱신. */
    public static void addColor(int argb) {
        extracted.remove((Integer) argb);
        extracted.add(0, argb);
        while (extracted.size() > 24) extracted.remove(extracted.size() - 1);
        selectedColor = argb;
        refreshPalette();
    }
}
