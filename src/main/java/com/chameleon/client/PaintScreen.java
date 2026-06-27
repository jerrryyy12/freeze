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
 * 휠=확대/축소, 우클릭드래그=이동, 좌클릭/드래그=색칠.
 * 오른쪽 패널: 컬러 피커(채도·명도 + 색조) + 추출 팔레트.
 */
public class PaintScreen extends Screen {

    private static final int SIZE = CamoSyncPacket.SIZE;
    private static final int SCALE = SIZE / 64;

    private static final String[] PART_NAMES = {"머리", "몸통", "오른팔", "왼팔", "오른다리", "왼다리"};
    private static final int[][][] FACES = scaleFaces(new int[][][]{
            {{8, 8, 8, 8}, {24, 8, 8, 8}, {16, 8, 8, 8}, {0, 8, 8, 8}, {8, 0, 8, 8}, {16, 0, 8, 8}},
            {{20, 20, 8, 12}, {32, 20, 8, 12}, {28, 20, 4, 12}, {16, 20, 4, 12}, {20, 16, 8, 4}, {28, 16, 8, 4}},
            {{44, 20, 4, 12}, {52, 20, 4, 12}, {48, 20, 4, 12}, {40, 20, 4, 12}, {44, 16, 4, 4}, {48, 16, 4, 4}},
            {{36, 52, 4, 12}, {44, 52, 4, 12}, {40, 52, 4, 12}, {32, 52, 4, 12}, {36, 48, 4, 4}, {40, 48, 4, 4}},
            {{4, 20, 4, 12}, {12, 20, 4, 12}, {8, 20, 4, 12}, {0, 20, 4, 12}, {4, 16, 4, 4}, {8, 16, 4, 4}},
            {{20, 52, 4, 12}, {28, 52, 4, 12}, {24, 52, 4, 12}, {16, 52, 4, 12}, {20, 48, 4, 4}, {24, 48, 4, 4}},
    });
    private static final String[] VIEW_NAMES = {"앞", "뒤", "좌", "우", "위", "아래"};

    private static int[][][] scaleFaces(int[][][] base) {
        for (int[][] part : base)
            for (int[] f : part)
                for (int k = 0; k < 4; k++) f[k] *= SCALE;
        return base;
    }

    // 컬러 피커 기하
    private static final int SV = 76, HUEW = 10, PICK_TOP = 50;
    private float h = 0, s = 1, v = 1;
    private int svX, svY, hueX;

    private int cell = 6;
    private int panX = 0, panY = 0;
    private final int[] sx = new int[6];
    private final int[] sy = new int[6];
    private int viewX0, viewY0, viewX1, viewY1;
    private int palX, presetY;
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
        selectColor(CamoEditState.selectedColor);

        int paletteW = 8 * 16 + 16;
        viewX0 = 8;
        viewY0 = PICK_TOP;
        viewX1 = this.width - paletteW - 8;
        viewY1 = this.height - 32;
        palX = this.width - paletteW + 8;
        svX = palX; svY = PICK_TOP; hueX = palX + SV + 6;
        presetY = svY + SV + 28;

        int wT = FACES[2][0][2] + FACES[1][0][2] + FACES[3][0][2];
        int hT = FACES[0][0][3] + FACES[1][0][3] + FACES[4][0][3];
        int fitW = (viewX1 - viewX0 - 24) / Math.max(1, wT);
        int fitH = (viewY1 - viewY0 - 24) / Math.max(1, hT);
        cell = Math.max(2, Math.min(12, Math.min(fitW, fitH)));
        panX = 0; panY = 0;
        layout();

        int vw = Math.min(46, (this.width - paletteW - 20) / 6);
        for (int i = 0; i < 6; i++) {
            final int vi = i;
            addRenderableWidget(Button.builder(Component.literal(VIEW_NAMES[i]), b -> CamoEditState.view = vi)
                    .bounds(10 + i * (vw + 2), 24, vw, 20).build());
        }

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

    private void layout() {
        int view = CamoEditState.view;
        int gap = Math.max(2, cell);
        int ox = viewX0 + 12 + panX, oy = viewY0 + 14 + panY;
        int rarmW = FACES[2][view][2] * cell;
        int torsoW = FACES[1][view][2] * cell, torsoH = FACES[1][view][3] * cell;
        int headW = FACES[0][view][2] * cell, headH = FACES[0][view][3] * cell;
        int rlegW = FACES[4][view][2] * cell;
        int torsoX = ox + rarmW + gap, torsoY = oy + headH + gap;
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

        renderPicker(g);
        renderPresets(g);
    }

    private void renderPicker(GuiGraphics g) {
        // 채도(x)·명도(y) 사각형
        for (int yy = 0; yy < SV; yy += 4)
            for (int xx = 0; xx < SV; xx += 4)
                g.fill(svX + xx, svY + yy, svX + xx + 4, svY + yy + 4, hsv(h, xx / (float) SV, 1f - yy / (float) SV));
        // SV 마커
        int mxp = svX + (int) (s * SV), myp = svY + (int) ((1 - v) * SV);
        g.fill(mxp - 2, myp - 2, mxp + 3, myp + 3, 0xFFFFFFFF);
        g.fill(mxp - 1, myp - 1, mxp + 2, myp + 2, 0xFF000000);

        // 색조 바
        for (int yy = 0; yy < SV; yy += 2)
            g.fill(hueX, svY + yy, hueX + HUEW, svY + yy + 2, hsv(yy / (float) SV, 1f, 1f));
        int hy = svY + (int) (h * SV);
        g.fill(hueX - 1, hy - 1, hueX + HUEW + 1, hy + 1, 0xFFFFFFFF);

        // 미리보기 + HEX
        int prevX = palX, prevY = svY + SV + 6;
        g.fill(prevX, prevY, prevX + 24, prevY + 14, CamoEditState.selectedColor);
        g.drawString(this.font, "#" + String.format("%06X", CamoEditState.selectedColor & 0xFFFFFF),
                prevX + 30, prevY + 3, 0xFFFFFFFF);
    }

    private void renderPresets(GuiGraphics g) {
        g.drawString(this.font, "추출색", palX, presetY - 11, 0xFFFFFFFF);
        for (int i = 0; i < CamoEditState.palette.size(); i++) {
            int x = palX + (i % 8) * 16, y = presetY + (i / 8) * 16;
            if (y > this.height - 56) break;
            int col = CamoEditState.palette.get(i);
            if (col == CamoEditState.selectedColor) g.fill(x - 1, y - 1, x + 15, y + 15, 0xFFFFFF00);
            g.fill(x, y, x + 14, y + 14, col);
        }
        g.drawString(this.font, "브러시: " + (CamoEditState.brush * 2 + 1) + "칸   (H=위장/스킨)",
                palX, this.height - 44, 0xFFAAAAAA);
    }

    private void armEyedropper() {
        CamoEditState.eyedropperArmed = true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null)
            mc.player.displayClientMessage(Component.literal("§e조준점을 대고 [휠클릭]으로 그 색 추출  (G = 취소)"), true);
        this.onClose();
    }

    private boolean inView(double mx, double my) {
        return mx >= viewX0 && mx <= viewX1 && my >= viewY0 && my <= viewY1;
    }

    /** 컬러 피커 클릭/드래그 처리. */
    private boolean pickerAt(double mx, double my) {
        if (mx >= svX && mx < svX + SV && my >= svY && my < svY + SV) {
            s = clamp01((mx - svX) / SV);
            v = clamp01(1 - (my - svY) / SV);
            CamoEditState.selectedColor = hsv(h, s, v);
            return true;
        }
        if (mx >= hueX && mx < hueX + HUEW && my >= svY && my < svY + SV) {
            h = clamp01((my - svY) / SV);
            CamoEditState.selectedColor = hsv(h, s, v);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button == 0 && pickerAt(mx, my)) return true;
        // 추출색 선택
        for (int i = 0; i < CamoEditState.palette.size(); i++) {
            int x = palX + (i % 8) * 16, y = presetY + (i / 8) * 16;
            if (mx >= x && mx < x + 14 && my >= y && my < y + 14) {
                selectColor(CamoEditState.palette.get(i));
                return true;
            }
        }
        if (button == 0 && inView(mx, my)) return paintAt(mx, my);
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button == 1) { panX += (int) dx; panY += (int) dy; return true; }
        if (button == 0 && pickerAt(mx, my)) return true;
        if (button == 0 && inView(mx, my) && paintAt(mx, my)) return true;
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (scrollY != 0 && inView(mx, my)) {
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
            int w = f[2] * cell, h2 = f[3] * cell;
            if (mx < sx[p] || mx >= sx[p] + w || my < sy[p] || my >= sy[p] + h2) continue;
            int tx = f[0] + (int) ((mx - sx[p]) / cell);
            int ty = f[1] + (int) ((my - sy[p]) / cell);
            int b = CamoEditState.brush;
            for (int oy = -b; oy <= b; oy++) {
                for (int ox = -b; ox <= b; ox++) {
                    int xx = tx + ox, yy = ty + oy;
                    if (xx < f[0] || xx >= f[0] + f[2] || yy < f[1] || yy >= f[1] + f[3]) continue;
                    CamoEditState.pixels[yy * SIZE + xx] = CamoEditState.selectedColor;
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

    // ---- 색 변환 ----

    private void selectColor(int argb) {
        CamoEditState.selectedColor = 0xFF000000 | (argb & 0xFFFFFF);
        float[] f = rgbToHsv(argb);
        h = f[0]; s = f[1]; v = f[2];
    }

    private static float clamp01(double x) {
        return (float) Math.max(0, Math.min(1, x));
    }

    static int hsv(float h, float s, float v) {
        int i = (int) (h * 6) % 6;
        float f = h * 6 - (int) (h * 6);
        float p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
        float r, g, b;
        switch (i) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return 0xFF000000 | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    static float[] rgbToHsv(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float v = max, d = max - min;
        float s = max == 0 ? 0 : d / max;
        float h = 0;
        if (d != 0) {
            if (max == r) h = ((g - b) / d) % 6;
            else if (max == g) h = (b - r) / d + 2;
            else h = (r - g) / d + 4;
            h /= 6;
            if (h < 0) h += 1;
        }
        return new float[]{h, s, v};
    }
}
