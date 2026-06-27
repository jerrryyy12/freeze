package com.chameleon.client;

import com.chameleon.net.CamoPaintPacket;
import com.chameleon.net.CamoSyncPacket;
import com.chameleon.net.ChameleonNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

/**
 * 3D 색칠 모드(3D-2). 자체 직교투영 렌더러로 몸 6박스를 그리고, 같은 변환으로 커서→텍셀 피킹.
 * 우클릭드래그=회전, 휠=확대, 좌클릭/드래그=색칠. 색은 팔레트/HEX로 선택(정밀색은 2D 피커).
 */
public class Paint3DScreen extends Screen {

    private static final int SIZE = CamoSyncPacket.SIZE;
    private static final int SCALE = SIZE / 64;

    // 부위 박스: cx,cy,cz, sx,sy,sz (모델 단위=텍셀, 부위 순서는 PaintScreen.FACES와 동일)
    private static final int[][] BOXES = {
            {0, 12, 0, 8, 8, 8},   // 머리
            {0, 2, 0, 8, 12, 4},   // 몸통
            {-6, 2, 0, 4, 12, 4},  // 오른팔
            {6, 2, 0, 4, 12, 4},   // 왼팔
            {-2, -10, 0, 4, 12, 4},// 오른다리
            {2, -10, 0, 4, 12, 4}, // 왼다리
    };
    // 면 UV(앞뒤좌우위아래) → PaintScreen.FACES 재사용
    private static final int[][][] FACES = PaintScreen.FACES;

    private float yaw = 0.6f, pitch = -0.2f;
    private float scale = 6f;
    private int cx, cy; // 3D 뷰 중심
    private int panX = 0, panY = 0; // 휠클릭 드래그 이동
    private int viewX0, viewY0, viewX1, viewY1;
    private int palX, presetY;
    private boolean dirty = false;

    // 컬러 피커(HSV) — 2D와 동일
    private static final int SV = 76, HUEW = 10;
    private float h = 0, sat = 1, val = 1;
    private int svX, svY, hueX;

    private EditBox hexField;
    private boolean updatingHex = false;

    public Paint3DScreen() {
        super(Component.literal("위장 색칠 3D"));
    }

    public static void open() {
        CamoEditState.ensureInit();
        CamoEditState.refreshPalette(); // 열 때마다 주변 블록색 갱신
        Minecraft.getInstance().setScreen(new Paint3DScreen());
    }

    @Override
    protected void init() {
        CamoEditState.ensureInit();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            CamoClient.apply(mc.player.getUUID(), CamoEditState.pixels.clone());
            CamoEditState.camoOn = true;
        }

        int paletteW = 8 * 16 + 16;
        viewX0 = 8; viewY0 = 26; viewX1 = this.width - paletteW - 8; viewY1 = this.height - 32;
        cx = (viewX0 + viewX1) / 2; cy = (viewY0 + viewY1) / 2;
        palX = this.width - paletteW + 8;
        scale = Math.max(3f, (viewY1 - viewY0) / 46f);

        // 컬러 피커(HSV) 위치 + 그 아래 선택색/HEX/팔레트
        svX = palX; svY = 26; hueX = palX + SV + 6;
        presetY = svY + SV + 60;
        selectColor(CamoEditState.selectedColor);

        // HEX 입력
        hexField = new EditBox(this.font, palX, svY + SV + 24, 92, 16, Component.literal("HEX"));
        hexField.setMaxLength(7);
        hexField.setResponder(txt -> {
            if (updatingHex) return;
            Integer c = parseHex(txt);
            if (c != null) {
                CamoEditState.selectedColor = 0xFF000000 | c;
                float[] f = PaintScreen.rgbToHsv(c);
                h = f[0]; sat = f[1]; val = f[2];
            }
        });
        addRenderableWidget(hexField);
        updateHexField();

        int by = this.height - 26;
        int bw = Math.min(80, (this.width - 20) / 7);
        int x = 10;
        addRenderableWidget(Button.builder(Component.literal("2D로"), b -> PaintScreen.open()).bounds(x, by, bw, 20).build());
        x += bw + 2;
        addRenderableWidget(Button.builder(Component.literal("크기 -"), b -> brushDelta(-1)).bounds(x, by, bw, 20).build());
        x += bw + 2;
        addRenderableWidget(Button.builder(Component.literal("크기 +"), b -> brushDelta(+1)).bounds(x, by, bw, 20).build());
        x += bw + 2;
        addRenderableWidget(Button.builder(Component.literal("블록픽셀"),
                b -> CamoEditState.blockPixelMode = !CamoEditState.blockPixelMode).bounds(x, by, bw, 20).build());
        x += bw + 2;
        addRenderableWidget(Button.builder(Component.literal("되돌리기"), b -> doUndo()).bounds(x, by, bw, 20).build());
        x += bw + 2;
        addRenderableWidget(Button.builder(Component.literal("채우기"), b -> {
            CamoEditState.pushUndo();
            CamoEditState.fillAll(CamoEditState.selectedColor);
            dirty = true;
            sync();
        }).bounds(x, by, bw, 20).build());
        x += bw + 2;
        addRenderableWidget(Button.builder(Component.literal("완료"), b -> this.onClose()).bounds(x, by, bw, 20).build());
    }

    private void selectColor(int argb) {
        CamoEditState.selectedColor = 0xFF000000 | (argb & 0xFFFFFF);
        float[] f = PaintScreen.rgbToHsv(argb);
        h = f[0]; sat = f[1]; val = f[2];
        updateHexField();
    }

    private boolean pickerAt(double mx, double my) {
        if (mx >= svX && mx < svX + SV && my >= svY && my < svY + SV) {
            sat = clamp01((mx - svX) / SV);
            val = clamp01(1 - (my - svY) / SV);
            CamoEditState.selectedColor = PaintScreen.hsv(h, sat, val);
            updateHexField();
            return true;
        }
        if (mx >= hueX && mx < hueX + HUEW && my >= svY && my < svY + SV) {
            h = clamp01((my - svY) / SV);
            CamoEditState.selectedColor = PaintScreen.hsv(h, sat, val);
            updateHexField();
            return true;
        }
        return false;
    }

    private void renderPicker(GuiGraphics g) {
        for (int yy = 0; yy < SV; yy += 4)
            for (int xx = 0; xx < SV; xx += 4)
                g.fill(svX + xx, svY + yy, svX + xx + 4, svY + yy + 4, PaintScreen.hsv(h, xx / (float) SV, 1f - yy / (float) SV));
        int mxp = svX + (int) (sat * SV), myp = svY + (int) ((1 - val) * SV);
        g.fill(mxp - 2, myp - 2, mxp + 3, myp + 3, 0xFFFFFFFF);
        g.fill(mxp - 1, myp - 1, mxp + 2, myp + 2, 0xFF000000);
        for (int yy = 0; yy < SV; yy += 2)
            g.fill(hueX, svY + yy, hueX + HUEW, svY + yy + 2, PaintScreen.hsv(yy / (float) SV, 1f, 1f));
        int hy = svY + (int) (h * SV);
        g.fill(hueX - 1, hy - 1, hueX + HUEW + 1, hy + 1, 0xFFFFFFFF);
    }

    private static float clamp01(double x) {
        return (float) Math.max(0, Math.min(1, x));
    }

    /** 모드에 따라 브러시 크기 조절. 블록픽셀 1칸에서 '-' 누르면 자동으로 자유 브러시로 풀림(세밀). */
    private void brushDelta(int d) {
        if (CamoEditState.blockPixelMode) {
            if (d < 0 && CamoEditState.blockBrush <= 1) {
                CamoEditState.blockPixelMode = false;
                CamoEditState.brush = Math.max(1, (int) CamoEditState.TEXELS_PER_BLOCKPIXEL);
            } else {
                CamoEditState.blockBrush = Math.max(1, Math.min(8, CamoEditState.blockBrush + d));
            }
        } else {
            CamoEditState.brush = Math.max(1, Math.min(16, CamoEditState.brush + d));
        }
    }

    // ---- 좌표 변환 ----

    private Matrix4f viewMatrix() {
        Matrix4f m = new Matrix4f();
        m.translate(cx + panX, cy + panY, 0);
        m.scale(scale, -scale, scale);
        m.rotateX(pitch);
        m.rotateY(yaw);
        return m;
    }

    /** 면의 3D 정보: o=좌상단 원점, ue/ve=면 전체 가로/세로 모서리 벡터(텍셀 좌표축 방향). */
    private static void faceGeo(int part, int face, Vector3f o, Vector3f ue, Vector3f ve) {
        int[] b = BOXES[part];
        float hx = b[3] / 2f, hy = b[4] / 2f, hz = b[5] / 2f;
        float bx = b[0], by = b[1], bz = b[2];
        switch (face) {
            case 0 -> { o.set(bx - hx, by + hy, bz + hz); ue.set(2 * hx, 0, 0); ve.set(0, -2 * hy, 0); }   // 앞 +z
            case 1 -> { o.set(bx + hx, by + hy, bz - hz); ue.set(-2 * hx, 0, 0); ve.set(0, -2 * hy, 0); }  // 뒤 -z
            case 2 -> { o.set(bx + hx, by + hy, bz + hz); ue.set(0, 0, -2 * hz); ve.set(0, -2 * hy, 0); }  // 좌 +x
            case 3 -> { o.set(bx - hx, by + hy, bz - hz); ue.set(0, 0, 2 * hz); ve.set(0, -2 * hy, 0); }   // 우 -x
            case 4 -> { o.set(bx - hx, by + hy, bz - hz); ue.set(2 * hx, 0, 0); ve.set(0, 0, 2 * hz); }    // 위 +y
            default -> { o.set(bx - hx, by - hy, bz + hz); ue.set(2 * hx, 0, 0); ve.set(0, 0, -2 * hz); }  // 아래 -y
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 어둡게/블러 없이 뒤의 실제 월드가 보이게 → 색 비교 가능. 도구 영역에만 반투명 배경.
        int panel = 0xCC101010;
        int paletteW = 8 * 16 + 16;
        g.fill(0, 0, this.width, 24, panel);                                    // 상단(제목)
        g.fill(this.width - paletteW, 24, this.width, this.height - 30, panel); // 우측 도구열
        g.fill(0, this.height - 30, this.width, this.height, panel);            // 하단 버튼바
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font, "위장 색칠(3D) — 우클릭=회전, 휠클릭=이동, 휠=확대, 좌클릭=색칠",
                this.width / 2, 8, 0xFFFFFFFF);

        Matrix4f M = viewMatrix();
        // 면 깊이 정렬(뒤→앞)
        Integer[] order = new Integer[36];
        float[] depth = new float[36];
        for (int i = 0; i < 36; i++) {
            order[i] = i;
            int part = i / 6, face = i % 6;
            Vector3f o = new Vector3f(), ue = new Vector3f(), ve = new Vector3f();
            faceGeo(part, face, o, ue, ve);
            Vector3f center = new Vector3f(o).add(ue.x() / 2, ue.y() / 2, ue.z() / 2).add(ve.x() / 2, ve.y() / 2, ve.z() / 2);
            depth[i] = M.transformPosition(center).z();
        }
        java.util.Arrays.sort(order, (a, b) -> Float.compare(depth[a], depth[b]));

        g.enableScissor(viewX0, viewY0, viewX1, viewY1);
        g.pose().pushPose();
        g.pose().translate(cx + panX, cy + panY, 0);
        g.pose().scale(scale, -scale, scale);
        g.pose().mulPose(new Quaternionf().rotationX(pitch));
        g.pose().mulPose(new Quaternionf().rotationY(yaw));
        for (int idx : order) {
            int part = idx / 6, face = idx % 6;
            renderFace(g, part, face);
        }
        g.pose().popPose();
        g.disableScissor();

        renderPicker(g);
        renderColorColumn(g);

        // 브러시 커서 (자유=원, 블록픽셀=사각형)
        if (inView(mouseX, mouseY)) {
            if (CamoEditState.blockPixelMode) {
                int side = Math.max(2, (int) Math.round(
                        CamoEditState.blockBrush * CamoEditState.TEXELS_PER_BLOCKPIXEL * scale / SCALE));
                drawSquare(g, mouseX, mouseY, side, 0xFF000000);
                drawSquare(g, mouseX, mouseY, side - 2, 0xFFFFFFFF);
            } else {
                int r = Math.max(1, (int) (CamoEditState.brush * scale / (2f * SCALE)));
                drawCircle(g, mouseX, mouseY, r, 0xFF000000);
                drawCircle(g, mouseX, mouseY, r - 1, 0xFFFFFFFF);
            }
        }
    }

    private void drawSquare(GuiGraphics g, int cxp, int cyp, int side, int color) {
        if (side < 2) return;
        int hh = side / 2;
        int x0 = cxp - hh, y0 = cyp - hh, x1 = cxp + hh, y1 = cyp + hh;
        g.fill(x0, y0, x1, y0 + 1, color);
        g.fill(x0, y1 - 1, x1, y1, color);
        g.fill(x0, y0, x0 + 1, y1, color);
        g.fill(x1 - 1, y0, x1, y1, color);
    }

    private void renderFace(GuiGraphics g, int part, int face) {
        int[] f = FACES[part][face];
        int fw = f[2], fh = f[3], u0 = f[0], v0 = f[1];
        Vector3f o = new Vector3f(), ue = new Vector3f(), ve = new Vector3f();
        faceGeo(part, face, o, ue, ve);
        Vector3f ua = new Vector3f(ue).div(fw), va = new Vector3f(ve).div(fh);
        Vector3f n = new Vector3f(ua).cross(va);

        Matrix4f fb = new Matrix4f();
        fb.m00(ua.x()); fb.m01(ua.y()); fb.m02(ua.z());
        fb.m10(va.x()); fb.m11(va.y()); fb.m12(va.z());
        fb.m20(n.x()); fb.m21(n.y()); fb.m22(n.z());
        fb.m30(o.x()); fb.m31(o.y()); fb.m32(o.z());

        g.pose().pushPose();
        g.pose().last().pose().mul(fb);
        for (int tv = 0; tv < fh; tv++) {
            for (int tu = 0; tu < fw; tu++) {
                int argb = CamoEditState.pixels[(v0 + tv) * SIZE + (u0 + tu)];
                if ((argb >>> 24) == 0) continue;
                g.fill(tu, tv, tu + 1, tv + 1, argb);
            }
        }
        g.pose().popPose();
    }

    private void drawCircle(GuiGraphics g, int cxp, int cyp, int r, int color) {
        if (r < 1) return;
        int seg = Math.max(20, r * 3);
        for (int i = 0; i < seg; i++) {
            double a = i * 2 * Math.PI / seg;
            int x = cxp + (int) Math.round(r * Math.cos(a));
            int y = cyp + (int) Math.round(r * Math.sin(a));
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    // ---- 색 컬럼 ----

    private void renderColorColumn(GuiGraphics g) {
        int prevY = svY + SV + 6;
        g.fill(palX, prevY, palX + 22, prevY + 14, CamoEditState.selectedColor);
        g.drawString(this.font, "선택색", palX + 28, prevY + 3, 0xFFFFFFFF);

        g.drawString(this.font, "팔레트 (주변 블록색)", palX, presetY - 11, 0xFFFFFFFF);
        for (int i = 0; i < CamoEditState.palette.size(); i++) {
            int x = palX + (i % 8) * 16, y = presetY + (i / 8) * 16;
            if (y > this.height - 50) break;
            int col = CamoEditState.palette.get(i);
            if (col == CamoEditState.selectedColor) g.fill(x - 1, y - 1, x + 15, y + 15, 0xFFFFFF00);
            g.fill(x, y, x + 14, y + 14, col);
        }
        String binfo = CamoEditState.blockPixelMode
                ? "§a블록픽셀 " + CamoEditState.blockBrush + "칸 (" + (int) CamoEditState.TEXELS_PER_BLOCKPIXEL + "px)"
                : "브러시 " + CamoEditState.brush + "칸";
        g.drawString(this.font, binfo, palX, this.height - 42, 0xFFAAAAAA);
    }

    private boolean inView(double mx, double my) {
        return mx >= viewX0 && mx <= viewX1 && my >= viewY0 && my <= viewY1;
    }

    // ---- 입력 ----

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button == 0 && pickerAt(mx, my)) return true;
        for (int i = 0; i < CamoEditState.palette.size(); i++) {
            int x = palX + (i % 8) * 16, y = presetY + (i / 8) * 16;
            if (mx >= x && mx < x + 14 && my >= y && my < y + 14) {
                selectColor(CamoEditState.palette.get(i));
                return true;
            }
        }
        if (button == 0 && inView(mx, my)) {
            CamoEditState.pushUndo();
            return paintAt(mx, my);
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button == 1) { yaw += (float) dx * 0.01f; pitch += (float) dy * 0.01f; return true; }
        if (button == 2) { panX += (int) dx; panY += (int) dy; return true; } // 휠클릭 = 이동
        if (button == 0 && pickerAt(mx, my)) return true; // 컬러 피커 드래그
        if (button == 0 && inView(mx, my) && paintAt(mx, my)) return true;
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (scrollY != 0 && inView(mx, my)) {
            scale = Math.max(2f, Math.min(40f, scale + (float) scrollY));
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (hexField != null && hexField.isFocused()) return super.keyPressed(keyCode, scanCode, modifiers);
        if (keyCode == GLFW.GLFW_KEY_Z && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            doUndo();
            return true;
        }
        if (ChameleonClient.PAINT_KEY.matches(keyCode, scanCode)) { // G로 닫기
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dirty) sync();
        return super.mouseReleased(mx, my, button);
    }

    /** 커서 → 면/텍셀 피킹 후 색칠. 직교투영이라 각 면은 평행사변형 → 선형 역산. */
    private boolean paintAt(double mx, double my) {
        Matrix4f M = viewMatrix();
        int bestPart = -1, bestFace = -1, bestTu = 0, bestTv = 0;
        float bestZ = -Float.MAX_VALUE;
        for (int part = 0; part < 6; part++) {
            for (int face = 0; face < 6; face++) {
                int[] f = FACES[part][face];
                int fw = f[2], fh = f[3];
                Vector3f o = new Vector3f(), ue = new Vector3f(), ve = new Vector3f();
                faceGeo(part, face, o, ue, ve);
                Vector3f p00 = M.transformPosition(new Vector3f(o));
                Vector3f pu = M.transformPosition(new Vector3f(o).add(ue));
                Vector3f pv = M.transformPosition(new Vector3f(o).add(ve));
                float e1x = pu.x() - p00.x(), e1y = pu.y() - p00.y();
                float e2x = pv.x() - p00.x(), e2y = pv.y() - p00.y();
                float det = e1x * e2y - e1y * e2x;
                if (Math.abs(det) < 1e-4) continue;
                float rx = (float) mx - p00.x(), ry = (float) my - p00.y();
                float u = (rx * e2y - ry * e2x) / det;
                float v = (e1x * ry - e1y * rx) / det;
                if (u < 0 || u >= 1 || v < 0 || v >= 1) continue;
                float z = p00.z();
                if (z > bestZ) { // 더 앞면(뷰어쪽) 우선
                    bestZ = z; bestPart = part; bestFace = face;
                    bestTu = (int) (u * fw); bestTv = (int) (v * fh);
                }
            }
        }
        if (bestPart < 0) return false;
        int[] f = FACES[bestPart][bestFace];
        int tx = f[0] + bestTu, ty = f[1] + bestTv;
        CamoEditState.applyBrushOnFace(f, tx, ty);
        dirty = true;
        return true;
    }

    private void doUndo() {
        if (CamoEditState.undo()) sync();
    }

    private void sync() {
        int[] copy = CamoEditState.pixels.clone();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) CamoClient.apply(mc.player.getUUID(), copy);
        ChameleonNet.sendPaintToServer(new CamoPaintPacket(copy));
        CamoEditState.camoOn = true;
        dirty = false;
    }

    private void updateHexField() {
        if (hexField == null) return;
        updatingHex = true;
        hexField.setValue(String.format("%06X", CamoEditState.selectedColor & 0xFFFFFF));
        updatingHex = false;
    }

    private static Integer parseHex(String s) {
        s = s.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() != 6) return null;
        try {
            return Integer.parseInt(s, 16);
        } catch (NumberFormatException e) {
            return null;
        }
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
