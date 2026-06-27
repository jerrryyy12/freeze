package com.chameleon.client;

import com.chameleon.net.CamoPaintPacket;
import com.chameleon.net.CamoSyncPacket;
import com.chameleon.net.ChameleonNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 위장 색칠 화면. 몸을 앞/뒤로 펼쳐 보여주고, 붓으로 픽셀을 칠한다.
 * - 팔레트: 주변 블록 색 + 기본색 (스포이드로 추출됨)
 * - 브러시 크기 조절(+/-)
 * - 스포이드 토글(몸에서 색 추출)
 * - 칠한 결과는 즉시 내 몸에 반영되고, 서버를 통해 모두에게 동기화된다.
 */
public class PaintScreen extends Screen {

    private static final int SIZE = 64;
    private static final int CELL = 9; // 텍셀 1개 = 화면 9px

    /** 몸 부위. 앞면/뒷면 UV(스킨 64x64 기준)와 화면 위치. */
    private static final class Part {
        final String name;
        final int fu, fv, bu, bv, w, h; // 앞 UV, 뒤 UV, 크기(텍셀)
        int sx, sy;                     // 화면 좌상단(픽셀)
        Part(String name, int fu, int fv, int bu, int bv, int w, int h) {
            this.name = name; this.fu = fu; this.fv = fv; this.bu = bu; this.bv = bv; this.w = w; this.h = h;
        }
        int u(boolean back) { return back ? bu : fu; }
        int v(boolean back) { return back ? bv : fv; }
    }

    // 베이스 레이어를 채워둘 영역(부위 전체 사각형) — 옆/위/아래 면도 회색 캔버스로.
    private static final int[][] BASE_RECTS = {
            {0, 0, 32, 16},   // 머리
            {0, 16, 16, 16},  // 오른다리
            {16, 16, 24, 16}, // 몸통
            {40, 16, 16, 16}, // 오른팔
            {16, 48, 16, 16}, // 왼다리
            {32, 48, 16, 16}, // 왼팔
    };

    private final Part[] parts = {
            new Part("머리", 8, 8, 24, 8, 8, 8),
            new Part("몸통", 20, 20, 32, 20, 8, 12),
            new Part("오른팔", 44, 20, 52, 20, 4, 12),
            new Part("왼팔", 36, 52, 44, 52, 4, 12),
            new Part("오른다리", 4, 20, 12, 20, 4, 12),
            new Part("왼다리", 20, 52, 28, 52, 4, 12),
    };

    private int[] pixels = new int[SIZE * SIZE];
    private int selectedColor = 0xFFFF0000;
    private int brush = 1;            // 반경(텍셀): 0=1px, 1=3x3, ...
    private boolean back = false;     // 앞/뒤 보기
    private boolean eyedrop = false;  // 스포이드 모드
    private boolean dirty = false;    // 마지막 전송 이후 변경됨

    private final List<Integer> palette = new ArrayList<>();

    public PaintScreen() {
        super(Component.literal("위장 색칠"));
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new PaintScreen());
    }

    @Override
    protected void init() {
        // 초기 픽셀: 기존 위장 있으면 그대로, 없으면 베이스 회색 캔버스.
        int[] cur = Minecraft.getInstance().player != null
                ? CamoClient.getPixels(Minecraft.getInstance().player.getUUID()) : null;
        if (cur != null && cur.length == pixels.length) {
            pixels = cur.clone();
        } else {
            java.util.Arrays.fill(pixels, 0x00000000);
            for (int[] r : BASE_RECTS) fillRect(r[0], r[1], r[2], r[3], 0xFFB0B0B0);
        }

        buildPalette();
        layoutParts();

        // 컨트롤 버튼 (오른쪽 하단)
        int bx = this.width - 130;
        int by = this.height - 150;
        addRenderableWidget(Button.builder(Component.literal("브러시 -"), b -> brush = Math.max(0, brush - 1))
                .bounds(bx, by, 60, 20).build());
        addRenderableWidget(Button.builder(Component.literal("브러시 +"), b -> brush = Math.min(8, brush + 1))
                .bounds(bx + 64, by, 60, 20).build());
        addRenderableWidget(Button.builder(Component.literal("스포이드"), b -> eyedrop = !eyedrop)
                .bounds(bx, by + 24, 124, 20).build());
        addRenderableWidget(Button.builder(Component.literal("앞/뒤 전환"), b -> back = !back)
                .bounds(bx, by + 48, 124, 20).build());
        addRenderableWidget(Button.builder(Component.literal("전체 지우기"), b -> {
            java.util.Arrays.fill(pixels, 0x00000000);
            for (int[] r : BASE_RECTS) fillRect(r[0], r[1], r[2], r[3], 0xFFB0B0B0);
            dirty = true;
        }).bounds(bx, by + 72, 124, 20).build());
        addRenderableWidget(Button.builder(Component.literal("완료"), b -> this.onClose())
                .bounds(bx, by + 96, 124, 20).build());
    }

    private void layoutParts() {
        int armW = 4 * CELL, torsoW = 8 * CELL, headH = 8 * CELL, torsoH = 12 * CELL;
        int ox = 50, oy = 40;
        int gap = CELL;
        Part head = parts[0], torso = parts[1], rarm = parts[2], larm = parts[3], rleg = parts[4], lleg = parts[5];
        torso.sx = ox + armW + gap; torso.sy = oy + headH;
        head.sx = torso.sx; head.sy = oy;
        rarm.sx = ox; rarm.sy = torso.sy;
        larm.sx = torso.sx + torsoW + gap; larm.sy = torso.sy;
        rleg.sx = torso.sx; rleg.sy = torso.sy + torsoH;
        lleg.sx = torso.sx + 4 * CELL; lleg.sy = torso.sy + torsoH;
    }

    private void buildPalette() {
        Set<Integer> set = new LinkedHashSet<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            Level level = mc.player.level();
            BlockPos center = mc.player.blockPosition();
            int R = 6;
            for (int dx = -R; dx <= R; dx++)
                for (int dy = -R; dy <= R; dy++)
                    for (int dz = -R; dz <= R; dz++) {
                        BlockPos p = center.offset(dx, dy, dz);
                        BlockState st = level.getBlockState(p);
                        if (st.isAir()) continue;
                        MapColor c = st.getMapColor(level, p);
                        if (c != MapColor.NONE) set.add(0xFF000000 | (c.col & 0xFFFFFF));
                        if (set.size() >= 28) break;
                    }
        }
        // 기본색 보강
        int[] basics = {0xFF000000, 0xFFFFFFFF, 0xFF808080, 0xFF8B5A2B, 0xFF3A5F0B, 0xFF1E5AA8, 0xFFB02E26, 0xFFFFD83D};
        for (int c : basics) set.add(c);
        palette.clear();
        palette.addAll(set);
        if (!palette.isEmpty()) selectedColor = palette.get(0);
    }

    private void fillRect(int u, int v, int w, int h, int argb) {
        for (int y = v; y < v + h; y++)
            for (int x = u; x < u + w; x++)
                if (x >= 0 && x < SIZE && y >= 0 && y < SIZE) pixels[y * SIZE + x] = argb;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        // 몸 부위 그리기 (텍셀 = fill 사각형)
        for (Part part : parts) {
            int u0 = part.u(back), v0 = part.v(back);
            for (int ty = 0; ty < part.h; ty++) {
                for (int tx = 0; tx < part.w; tx++) {
                    int argb = pixels[(v0 + ty) * SIZE + (u0 + tx)];
                    int sx = part.sx + tx * CELL, sy = part.sy + ty * CELL;
                    // 투명 텍셀은 체커보드로 표시
                    if ((argb >>> 24) == 0) {
                        int chk = (((tx + ty) & 1) == 0) ? 0xFF3A3A3A : 0xFF2E2E2E;
                        g.fill(sx, sy, sx + CELL, sy + CELL, chk);
                    } else {
                        g.fill(sx, sy, sx + CELL, sy + CELL, argb);
                    }
                }
            }
            // 부위 테두리 + 이름
            g.fill(part.sx - 1, part.sy - 1, part.sx + part.w * CELL + 1, part.sy, 0xFF000000);
            g.drawString(this.font, part.name, part.sx, part.sy - 11, 0xFFFFFFFF);
        }

        // 팔레트
        int px = this.width - 130, py = 40;
        g.drawString(this.font, "팔레트", px, py - 12, 0xFFFFFFFF);
        for (int i = 0; i < palette.size(); i++) {
            int sx = px + (i % 7) * 17, sy = py + (i / 7) * 17;
            g.fill(sx, sy, sx + 16, sy + 16, palette.get(i));
            if (palette.get(i) == selectedColor) g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFFFFFF00);
            g.fill(sx, sy, sx + 16, sy + 16, palette.get(i));
        }

        // 상태 표시
        int iy = this.height - 150 - 40;
        g.fill(this.width - 130, iy, this.width - 130 + 20, iy + 20, selectedColor);
        g.drawString(this.font, "선택색", this.width - 105, iy + 6, 0xFFFFFFFF);
        g.drawString(this.font, "브러시: " + (brush * 2 + 1) + "px" + (eyedrop ? "  [스포이드]" : ""),
                this.width - 130, iy + 24, 0xFFFFFFFF);

        g.drawCenteredString(this.font, "몸을 클릭/드래그해서 색칠 — " + (back ? "뒷면" : "앞면"),
                this.width / 2, 12, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        // 팔레트 선택
        int px = this.width - 130, py = 40;
        for (int i = 0; i < palette.size(); i++) {
            int sx = px + (i % 7) * 17, sy = py + (i / 7) * 17;
            if (mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16) {
                selectedColor = palette.get(i);
                return true;
            }
        }
        return paintAt(mx, my);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button == 0) return paintAt(mx, my);
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dirty) sync();
        return super.mouseReleased(mx, my, button);
    }

    /** 화면 좌표에서 어떤 부위/텍셀인지 찾아 칠하거나(스포이드면) 색을 추출한다. */
    private boolean paintAt(double mx, double my) {
        for (Part part : parts) {
            int w = part.w * CELL, h = part.h * CELL;
            if (mx < part.sx || mx >= part.sx + w || my < part.sy || my >= part.sy + h) continue;
            int u0 = part.u(back), v0 = part.v(back);
            int tx = u0 + (int) ((mx - part.sx) / CELL);
            int ty = v0 + (int) ((my - part.sy) / CELL);
            if (eyedrop) {
                int c = pixels[ty * SIZE + tx];
                if ((c >>> 24) != 0) selectedColor = c;
                return true;
            }
            // 브러시: 부위 UV 범위 안에서만
            for (int oy = -brush; oy <= brush; oy++) {
                for (int ox = -brush; ox <= brush; ox++) {
                    int x = tx + ox, y = ty + oy;
                    if (x < u0 || x >= u0 + part.w || y < v0 || y >= v0 + part.h) continue;
                    pixels[y * SIZE + x] = selectedColor;
                }
            }
            dirty = true;
            return true;
        }
        return false;
    }

    /** 현재 텍스처를 내 몸에 즉시 반영하고 서버로 전송. */
    private void sync() {
        int[] copy = pixels.clone();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) CamoClient.apply(mc.player.getUUID(), copy);
        ChameleonNet.sendPaintToServer(new CamoPaintPacket(copy));
        dirty = false;
    }

    @Override
    public void onClose() {
        if (dirty) sync(); else {
            // 변경 없어도 최소 한 번은 반영(처음 칠한 경우 등)
            int[] copy = pixels.clone();
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) CamoClient.apply(mc.player.getUUID(), copy);
            ChameleonNet.sendPaintToServer(new CamoPaintPacket(copy));
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
