package com.chameleon.client;

import com.chameleon.net.CamoPaintPacket;
import com.chameleon.net.CamoSyncPacket;
import com.chameleon.net.ChameleonNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 위장 색칠 화면. 몸을 방향별(앞/뒤/좌/우/위/아래) 실루엣으로 보여주고 붓으로 픽셀을 칠한다.
 * 휠 = 확대/축소, 우클릭 드래그 = 이동, 좌클릭/드래그 = 색칠.
 * 텍스처 해상도는 CamoSyncPacket.SIZE(현재 128 = 기본의 2배 정밀도).
 * 편집 상태는 CamoEditState(정적)에 보관 → 스포이드로 나갔다 와도 작업 유지.
 */
public class PaintScreen extends Screen {

    private static final int SIZE = CamoSyncPacket.SIZE;
    private static final int SCALE = SIZE / 64; // 64-단위 좌표 → 실제 텍스처 배율

    private static final String[] PART_NAMES = {"머리", "몸통", "오른팔", "왼팔", "오른다리", "왼다리"};
    // 면: 0앞 1뒤 2좌 3우 4위 5아래 → {u,v,w,h}(64-단위, SCALE로 확대됨)
    private static final int[][][] FACES = scaleFaces(new int[][][]{
            {{8, 8, 8, 8}, {24, 8, 8, 8}, {16, 8, 8, 8}, {0, 8, 8, 8}, {8, 0, 8, 8}, {16, 0, 8, 8}},        // 머리
            {{20, 20, 8, 12}, {32, 20, 8, 12}, {28, 20, 4, 12}, {16, 20, 4, 12}, {20, 16, 8, 4}, {28, 16, 8, 4}}, // 몸통
            {{44, 20, 4, 12}, {52, 20, 4, 12}, {48, 20, 4, 12}, {40, 20, 4, 12}, {44, 16, 4, 4}, {48, 16, 4, 4}}, // 오른팔
            {{36, 52, 4, 12}, {44, 52, 4, 12}, {40, 52, 4, 12}, {32, 52, 4, 12}, {36, 48, 4, 4}, {40, 48, 4, 4}}, // 왼팔
            {{4, 20, 4, 12}, {12, 20, 4, 12}, {8, 20, 4, 12}, {0, 20, 4, 12}, {4, 16, 4, 4}, {8, 16, 4, 4}},       // 오른다리
            {{20, 52, 4, 12}, {28, 52, 4, 12}, {24, 52, 4, 12}, {16, 52, 4, 12}, {20, 48, 4, 4}, {24, 48, 4, 4}},  // 왼다리
    });
    private static final String[] VIEW_NAMES = {"앞", "뒤", "좌", "우", "위", "아래"};

    private static int[][][] scaleFaces(int[][][] base) {
        for (int[][] part : base)
            for (int[] f : part)
                for (int k = 0; k < 4; k++) f[k] *= SCALE;
        return base;
    }

    private int cell = 6;
    private int panX = 0, panY = 0;
    private final int[] sx = new int[6];
    private final int[] sy = new int[6];
    private int viewX0, viewY0, viewX1, viewY1; // 몸 표시 영역(클립)
    private int palX, palY;
    private boolean dirty = false;

    public PaintScreen() {
        super(Component.literal("위장 색칠"));
    }

    public static void open() {
        CamoEditState.ensureInit();
        Minecraft.getInstance().setScreen(new PaintScreen());
    }

    @Override
    protected void init() {
        CamoEditState.ensureInit();

        int paletteW = 8 * 16 + 16;
        viewX0 = 8;
        viewY0 = 50;
        viewX1 = this.width - paletteW - 8;
        viewY1 = this.height - 32;
        palX = this.width - paletteW + 8;
        palY = 50;

        // 화면에 몸 전체가 들어오는 셀 크기로 시작(앞면 기준)
        int wT = FACES[2][0][2] + FACES[1][0][2] + FACES[3][0][2];
        int hT = FACES[0][0][3] + FACES[1][0][3] + FACES[4][0][3];
        int fitW = (viewX1 - viewX0 - 24) / Math.max(1, wT);
        int fitH = (viewY1 - viewY0 - 24) / Math.max(1, hT);
        cell = Math.max(2, Math.min(12, Math.min(fitW, fitH)));
        panX = 0; panY = 0;
        layout();

        // 뷰 전환 버튼 (상단)
        int vw = Math.min(46, (this.width - paletteW - 20) / 6);
        for (int i = 0; i < 6; i++) {
            final int vi = i;
            addRenderableWidget(Button.builder(Component.literal(VIEW_NAMES[i]), b -> {
                CamoEditState.view = vi;
            }).bounds(10 + i * (vw + 2), 24, vw, 20).build());
        }

        // 컨트롤 버튼 (하단)
        int by = this.height - 26;
        int bw = Math.min(92, (this.width - 20) / 5);
        int x = 10;
        addRenderableWidget(Button.builder(Component.literal("브러시 -"),
                b -> CamoEditState.brush = Math.max(0, CamoEditState.brush - 1)).bounds(x, by, bw, 20).build());
        x += bw + 2;
        addRenderableWidget(Button.builder(Component.literal("브러시 +"),
                b -> CamoEditState.brush = Math.min(12, CamoEditState.brush + 1)).bounds(x, by, bw, 20).build());
        x += bw + 2;
        addRenderableWidget(Button.builder(Component.literal("스포이드(주변색)"), b -> armEyedropper())
                .bounds(x, by, bw, 20).build());
        x += bw + 2;
        addRenderableWidget(Button.builder(Component.literal("전체 지우기"), b -> {
            CamoEditState.resetCanvas();
            dirty = true;
        }).bounds(x, by, bw, 20).build());
        x += bw + 2;
        addRenderableWidget(Button.builder(Component.literal("완료"), b -> this.onClose())
                .bounds(x, by, bw, 20).build());
    }

    /** 현재 셀/이동값으로 부위 위치 계산. */
    private void layout() {
        int view = CamoEditState.view;
        int gap = Math.max(2, cell);
        int ox = viewX0 + 12 + panX, oy = viewY0 + 14 + panY;

        int rarmW = FACES[2][view][2] * cell;
        int torsoW = FACES[1][view][2] * cell, torsoH = FACES[1][view][3] * cell;
        int headW = FACES[0][view][2] * cell, headH = FACES[0][view][3] * cell;
        int rlegW = FACES[4][view][2] * cell;

        int torsoX = ox + rarmW + gap;
        int torsoY = oy + headH + gap;
        sx[1] = torsoX; sy[1] = torsoY;
        sx[0] = torsoX + (torsoW - headW) / 2; sy[0] = oy;
        sx[2] = ox; sy[2] = torsoY;
        sx[3] = torsoX + torsoW + gap; sy[3] = torsoY;
        int legY = torsoY + torsoH + gap;
        sx[4] = torsoX + torsoW / 2 - rlegW; sy[4] = legY;
        sx[5] = torsoX + torsoW / 2; sy[5] = legY;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        layout();
        int view = CamoEditState.view;

        g.drawCenteredString(this.font, "위장 색칠 — [" + VIEW_NAMES[view] + "면]  (휠=확대, 우클릭드래그=이동)",
                this.width / 2, 8, 0xFFFFFFFF);

        // 몸 영역만 클립
        g.enableScissor(viewX0, viewY0, viewX1, viewY1);
        for (int p = 0; p < 6; p++) {
            int[] f = FACES[p][view];
            g.drawString(this.font, PART_NAMES[p], sx[p], sy[p] - 10, 0xFFFFFFFF);
            for (int ty = 0; ty < f[3]; ty++) {
                for (int tx = 0; tx < f[2]; tx++) {
                    int argb = CamoEditState.pixels[(f[1] + ty) * SIZE + (f[0] + tx)];
                    int x = sx[p] + tx * cell, y = sy[p] + ty * cell;
                    if ((argb >>> 24) == 0) {
                        int chk = (((tx + ty) & 1) == 0) ? 0xFF3A3A3A : 0xFF2A2A2A;
                        g.fill(x, y, x + cell, y + cell, chk);
                    } else {
                        g.fill(x, y, x + cell, y + cell, argb);
                    }
                }
            }
            g.fill(sx[p] - 1, sy[p] - 1, sx[p] + f[2] * cell + 1, sy[p], 0xFF000000);
        }
        g.disableScissor();

        // 팔레트
        g.drawString(this.font, "팔레트", palX, palY - 11, 0xFFFFFFFF);
        for (int i = 0; i < CamoEditState.palette.size(); i++) {
            int x = palX + (i % 8) * 16, y = palY + (i / 8) * 16;
            if (y > this.height - 80) break;
            int col = CamoEditState.palette.get(i);
            if (col == CamoEditState.selectedColor) g.fill(x - 1, y - 1, x + 15, y + 15, 0xFFFFFF00);
            g.fill(x, y, x + 14, y + 14, col);
        }

        int iy = this.height - 70;
        g.fill(palX, iy, palX + 16, iy + 16, CamoEditState.selectedColor);
        g.drawString(this.font, "선택색", palX + 20, iy + 4, 0xFFFFFFFF);
        g.drawString(this.font, "브러시: " + (CamoEditState.brush * 2 + 1) + "칸", palX, iy + 20, 0xFFFFFFFF);
        g.drawString(this.font, "H = 위장/스킨 전환", palX, iy + 32, 0xFFAAAAAA);
    }

    private void armEyedropper() {
        CamoEditState.eyedropperArmed = true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null)
            mc.player.displayClientMessage(Component.literal("§e블록을 보고 [휠클릭]으로 색 추출  (G = 취소)"), true);
        this.onClose();
    }

    private boolean inView(double mx, double my) {
        return mx >= viewX0 && mx <= viewX1 && my >= viewY0 && my <= viewY1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        // 팔레트 선택
        for (int i = 0; i < CamoEditState.palette.size(); i++) {
            int x = palX + (i % 8) * 16, y = palY + (i / 8) * 16;
            if (mx >= x && mx < x + 14 && my >= y && my < y + 14) {
                CamoEditState.selectedColor = CamoEditState.palette.get(i);
                return true;
            }
        }
        if (button == 0 && inView(mx, my)) return paintAt(mx, my);
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button == 1) { // 우클릭 드래그 = 이동
            panX += (int) dx;
            panY += (int) dy;
            return true;
        }
        if (button == 0 && inView(mx, my) && paintAt(mx, my)) return true;
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (scrollY != 0) {
            cell = Math.max(2, Math.min(28, cell + (scrollY > 0 ? 2 : -2)));
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dirty) sync();
        return super.mouseReleased(mx, my, button);
    }

    private boolean paintAt(double mx, double my) {
        int view = CamoEditState.view;
        for (int p = 0; p < 6; p++) {
            int[] f = FACES[p][view];
            int w = f[2] * cell, h = f[3] * cell;
            if (mx < sx[p] || mx >= sx[p] + w || my < sy[p] || my >= sy[p] + h) continue;
            int tx = f[0] + (int) ((mx - sx[p]) / cell);
            int ty = f[1] + (int) ((my - sy[p]) / cell);
            int b = CamoEditState.brush;
            for (int oy = -b; oy <= b; oy++) {
                for (int ox = -b; ox <= b; ox++) {
                    int x = tx + ox, y = ty + oy;
                    if (x < f[0] || x >= f[0] + f[2] || y < f[1] || y >= f[1] + f[3]) continue;
                    CamoEditState.pixels[y * SIZE + x] = CamoEditState.selectedColor;
                }
            }
            dirty = true;
            return true;
        }
        return false;
    }

    private void sync() {
        int[] copy = CamoEditState.pixels.clone();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) CamoClient.apply(mc.player.getUUID(), copy);
        ChameleonNet.sendPaintToServer(new CamoPaintPacket(copy));
        CamoEditState.camoOn = true;
        dirty = false;
    }

    @Override
    public void onClose() {
        sync();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
