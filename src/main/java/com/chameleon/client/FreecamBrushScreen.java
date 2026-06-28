package com.chameleon.client;

import com.chameleon.net.CamoPaintPacket;
import com.chameleon.net.ChameleonNet;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * 자유시점에서 캐릭터에 "직접" 칠하는 브러시 화면.
 * 뒤로 실제 월드(자유시점)가 보이고, 마우스 커서로 내 캐릭터 몸을 직접 칠한다.
 * 커서 픽셀 → (마인크래프트 실제 투영행렬 역투영) 월드 광선 → 6박스 면 교차 → 텍셀.
 * - 좌클릭/드래그: 색칠 · 우클릭드래그: 시점 회전 · 우클릭(제자리): 스포이드 · 휠: 브러시 크기 · G/ESC: 닫기
 */
public class FreecamBrushScreen extends Screen {

    private static final int SIZE = CamoEditState.SIZE;

    // 부위 박스: cx,cy,cz, sx,sy,sz (텍셀, 모델 중앙 기준) — Paint3DScreen와 동일
    private static final int[][] BOXES = {
            {0, 12, 0, 8, 8, 8},
            {0, 2, 0, 8, 12, 4},
            {-6, 2, 0, 4, 12, 4},
            {6, 2, 0, 4, 12, 4},
            {-2, -10, 0, 4, 12, 4},
            {2, -10, 0, 4, 12, 4},
    };
    private static final int[][][] FACES = PaintScreen.FACES;

    // 월드 렌더에서 캡처한 역행렬(클립→카메라상대월드) + 카메라 위치
    private static Matrix4f invMatrix = null;
    private static Vec3 camPos = Vec3.ZERO;

    // 팔레트 배치
    private static final int SW = 14, COLS = 8, PAL_X = 8, PAL_Y = 28, PAL_ROWS = 4;

    private boolean dirty = false;
    private double rPressX, rPressY;
    private boolean rDragged = false;

    public FreecamBrushScreen() {
        super(Component.literal("직접 칠하기"));
    }

    public static void open() {
        if (!Freecam.isActive()) return;
        CamoEditState.ensureInit();
        CamoEditState.refreshPalette();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            CamoClient.apply(mc.player.getUUID(), CamoEditState.pixels.clone());
            CamoEditState.camoOn = true;
        }
        mc.setScreen(new FreecamBrushScreen());
    }

    /**
     * 월드 렌더 단계에서 호출: 실제 투영행렬 + 자유 카메라 각도로 뷰행렬을 만들어
     * 역투영(클립→카메라상대월드) 행렬과 카메라 위치를 저장한다.
     * 뷰행렬은 마인크래프트 카메라와 동일하게 RotX(pitch)·RotY(yaw+180).
     */
    public static void captureView() {
        Matrix4f proj = RenderSystem.getProjectionMatrix();
        Matrix4f view = new Matrix4f()
                .rotateX((float) Math.toRadians(Freecam.camPitch()))
                .rotateY((float) Math.toRadians(Freecam.camYaw() + 180.0));
        invMatrix = new Matrix4f(proj).mul(view).invert(new Matrix4f());
        camPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 배경 없음 — 뒤의 실제 월드(자유시점)가 그대로 보이게.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        // 브러시 커서(가운데 비움)
        int r = Math.max(3, CamoEditState.brush + 2);
        drawRing(g, mouseX, mouseY, r, 0xFF000000);
        drawRing(g, mouseX, mouseY, r - 1, 0xFFFFFFFF);

        // HUD: 선택색 + 브러시
        g.fill(PAL_X, 8, PAL_X + 22, 22, 0xFF000000);
        g.fill(PAL_X + 1, 9, PAL_X + 21, 21, CamoEditState.selectedColor);
        String info = CamoEditState.blockPixelMode
                ? "블록픽셀 " + CamoEditState.blockBrush + "칸" : "브러시 " + CamoEditState.brush + "칸";
        g.drawString(this.font, info, PAL_X + 26, 11, 0xFFFFFFFF, false);

        // 팔레트(주변 블록색 + 추출색 + 기본색)
        java.util.List<Integer> pal = CamoEditState.palette;
        for (int i = 0; i < pal.size() && i < COLS * PAL_ROWS; i++) {
            int x = PAL_X + (i % COLS) * (SW + 2), y = PAL_Y + (i / COLS) * (SW + 2);
            int col = pal.get(i);
            if (col == CamoEditState.selectedColor) g.fill(x - 1, y - 1, x + SW + 1, y + SW + 1, 0xFFFFFF00);
            g.fill(x, y, x + SW, y + SW, col);
        }

        g.drawCenteredString(this.font,
                "좌클릭=칠하기 · 우클릭드래그=시점 · 우클릭=스포이드 · 팔레트클릭=색 · 휠=크기 · G=닫기",
                this.width / 2, this.height - 14, 0xFFE0E0E0);
    }

    private void drawRing(GuiGraphics g, int cx, int cy, int r, int color) {
        if (r < 1) return;
        int seg = Math.max(20, r * 3);
        for (int i = 0; i < seg; i++) {
            double a = i * 2 * Math.PI / seg;
            int x = cx + (int) Math.round(r * Math.cos(a));
            int y = cy + (int) Math.round(r * Math.sin(a));
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    /** 팔레트 칸 클릭이면 색 선택하고 true. */
    private boolean clickPalette(double mx, double my) {
        java.util.List<Integer> pal = CamoEditState.palette;
        for (int i = 0; i < pal.size() && i < COLS * PAL_ROWS; i++) {
            int x = PAL_X + (i % COLS) * (SW + 2), y = PAL_Y + (i / COLS) * (SW + 2);
            if (mx >= x && mx < x + SW && my >= y && my < y + SW) {
                CamoEditState.selectedColor = pal.get(i);
                return true;
            }
        }
        return false;
    }

    // ---- 입력 ----

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            if (clickPalette(mx, my)) return true;
            CamoEditState.pushUndo();
            paintAt(mx, my);
            return true;
        }
        if (button == 1) {
            rPressX = mx; rPressY = my; rDragged = false;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button == 0) {
            paintAt(mx, my);
            return true;
        }
        if (button == 1) {
            if (Math.abs(mx - rPressX) > 3 || Math.abs(my - rPressY) > 3) rDragged = true;
            Freecam.addCamRotation((float) dx * 0.15f, (float) dy * 0.15f);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 1 && !rDragged) {
            int c = ChameleonInput.readPixelAt(mx, my); // 제자리 우클릭 = 스포이드
            CamoEditState.addColor(0xFF000000 | (c & 0xFFFFFF));
            return true;
        }
        if (button == 0 && dirty) sync();
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (sy != 0) {
            int d = sy > 0 ? 1 : -1;
            if (CamoEditState.blockPixelMode)
                CamoEditState.blockBrush = Math.max(1, Math.min(8, CamoEditState.blockBrush + d));
            else
                CamoEditState.brush = Math.max(1, Math.min(16, CamoEditState.brush + d));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ChameleonClient.PAINT_KEY.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** 커서 픽셀 → 월드 광선 → 6박스 면 교차 → 가장 앞면 텍셀에 색칠. */
    private void paintAt(double mx, double my) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || CamoEditState.pixels == null || invMatrix == null) return;

        double ndcX = 2.0 * mx / this.width - 1.0;
        double ndcY = 1.0 - 2.0 * my / this.height;
        Vec3 near = unproject(ndcX, ndcY, 0.0);
        Vec3 far = unproject(ndcX, ndcY, 1.0);
        Vec3 origin = near;
        Vec3 dir = far.subtract(near).normalize();

        double bodyYaw = p.yBodyRot;
        double scale = p.getScale();
        double fx = p.getX(), fy = p.getY(), fz = p.getZ();

        int bestPart = -1, bestFace = 0, bestTu = 0, bestTv = 0;
        double bestT = Double.MAX_VALUE;
        for (int part = 0; part < 6; part++) {
            for (int face = 0; face < 6; face++) {
                Vec3[] geo = faceGeo(part, face);
                Vec3 p00 = world(geo[0], bodyYaw, scale, fx, fy, fz);
                Vec3 pu = world(geo[0].add(geo[1]), bodyYaw, scale, fx, fy, fz);
                Vec3 pv = world(geo[0].add(geo[2]), bodyYaw, scale, fx, fy, fz);
                Vec3 e1 = pu.subtract(p00), e2 = pv.subtract(p00);
                Vec3 n = e1.cross(e2);
                double denom = dir.dot(n);
                if (Math.abs(denom) < 1e-9) continue;
                double t = p00.subtract(origin).dot(n) / denom;
                if (t <= 0 || t >= bestT) continue;
                Vec3 w = origin.add(dir.scale(t)).subtract(p00);
                double e11 = e1.dot(e1), e12 = e1.dot(e2), e22 = e2.dot(e2);
                double we1 = w.dot(e1), we2 = w.dot(e2);
                double det = e11 * e22 - e12 * e12;
                if (Math.abs(det) < 1e-9) continue;
                double u = (we1 * e22 - we2 * e12) / det;
                double v = (we2 * e11 - we1 * e12) / det;
                if (u < 0 || u > 1 || v < 0 || v > 1) continue;
                int[] f = FACES[part][face];
                bestT = t; bestPart = part; bestFace = face;
                bestTu = (int) (u * f[2]); bestTv = (int) (v * f[3]);
            }
        }
        if (bestPart < 0) return;
        int[] f = FACES[bestPart][bestFace];
        CamoEditState.applyBrushOnFace(f, f[0] + bestTu, f[1] + bestTv);
        dirty = true;
        CamoClient.apply(p.getUUID(), CamoEditState.pixels.clone()); // 라이브 반영
    }

    /** 클립좌표(ndc) → 월드 좌표(역투영 + 카메라 위치). */
    private static Vec3 unproject(double ndcX, double ndcY, double ndcZ) {
        Vector4f v = new Vector4f((float) ndcX, (float) ndcY, (float) ndcZ, 1f);
        invMatrix.transform(v);
        if (v.w != 0) { v.x /= v.w; v.y /= v.w; v.z /= v.w; }
        return new Vec3(camPos.x + v.x, camPos.y + v.y, camPos.z + v.z);
    }

    /** 박스 텍셀 좌표(모델 중앙 기준) → 월드 좌표. 발=y-16, 머리위=y+16. */
    private static Vec3 world(Vec3 pt, double bodyYaw, double scale, double fx, double fy, double fz) {
        double th = Math.toRadians(bodyYaw), cos = Math.cos(th), sin = Math.sin(th);
        double lx = pt.x / 16.0 * scale;
        double uy = (pt.y + 16) / 16.0 * scale;
        double fzz = pt.z / 16.0 * scale;
        return new Vec3(fx + lx * cos + fzz * (-sin), fy + uy, fz + lx * sin + fzz * cos);
    }

    /** 면 기하: o=좌상단, ue/ve=가로/세로 모서리 벡터(텍셀). Paint3DScreen와 동일. */
    private static Vec3[] faceGeo(int part, int face) {
        int[] b = BOXES[part];
        double hx = b[3] / 2.0, hy = b[4] / 2.0, hz = b[5] / 2.0;
        double bx = b[0], by = b[1], bz = b[2];
        return switch (face) {
            case 0 -> new Vec3[]{new Vec3(bx - hx, by + hy, bz + hz), new Vec3(2 * hx, 0, 0), new Vec3(0, -2 * hy, 0)};
            case 1 -> new Vec3[]{new Vec3(bx + hx, by + hy, bz - hz), new Vec3(-2 * hx, 0, 0), new Vec3(0, -2 * hy, 0)};
            case 2 -> new Vec3[]{new Vec3(bx + hx, by + hy, bz + hz), new Vec3(0, 0, -2 * hz), new Vec3(0, -2 * hy, 0)};
            case 3 -> new Vec3[]{new Vec3(bx - hx, by + hy, bz - hz), new Vec3(0, 0, 2 * hz), new Vec3(0, -2 * hy, 0)};
            case 4 -> new Vec3[]{new Vec3(bx - hx, by + hy, bz - hz), new Vec3(2 * hx, 0, 0), new Vec3(0, 0, 2 * hz)};
            default -> new Vec3[]{new Vec3(bx - hx, by - hy, bz + hz), new Vec3(2 * hx, 0, 0), new Vec3(0, 0, -2 * hz)};
        };
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
        if (dirty) sync();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
