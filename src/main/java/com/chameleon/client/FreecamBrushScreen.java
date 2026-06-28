package com.chameleon.client;

import com.chameleon.net.CamoPaintPacket;
import com.chameleon.net.ChameleonNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * 자유시점에서 캐릭터에 "직접" 칠하는 브러시 화면.
 * 화면을 띄우면 뒤로 실제 월드(자유시점)가 보이고, 마우스 커서로 내 캐릭터 몸을 직접 칠한다.
 * - 좌클릭/드래그: 조준 지점의 몸 텍셀에 색칠 (조준선 광선 → 6박스 교차)
 * - 우클릭 드래그: 카메라 회전 / 우클릭(제자리): 스포이드(주변 색 추출)
 * - 휠: 브러시 크기, G/ESC: 닫기
 * 박스/면 UV 기하는 3D 편집창과 동일(Paint3DScreen.BOXES/PaintScreen.FACES) 규칙을 재사용한다.
 */
public class FreecamBrushScreen extends Screen {

    private static final int SIZE = CamoEditState.SIZE;

    // 부위 박스(머리/몸통/오른팔/왼팔/오른다리/왼다리): cx,cy,cz, sx,sy,sz (텍셀, 모델 중앙 기준)
    private static final int[][] BOXES = {
            {0, 12, 0, 8, 8, 8},
            {0, 2, 0, 8, 12, 4},
            {-6, 2, 0, 4, 12, 4},
            {6, 2, 0, 4, 12, 4},
            {-2, -10, 0, 4, 12, 4},
            {2, -10, 0, 4, 12, 4},
    };
    private static final int[][][] FACES = PaintScreen.FACES;

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

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 배경 없음 — 뒤의 실제 월드(자유시점)가 그대로 보이게.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        // 브러시 커서(가운데 비움 → 스포이드가 월드 색을 읽게)
        int r = Math.max(3, CamoEditState.brush + 2);
        drawRing(g, mouseX, mouseY, r, 0xFF000000);
        drawRing(g, mouseX, mouseY, r - 1, 0xFFFFFFFF);

        // HUD
        g.fill(8, 8, 30, 22, 0xFF000000);
        g.fill(9, 9, 29, 21, CamoEditState.selectedColor);
        String info = (CamoEditState.blockPixelMode
                ? "블록픽셀 " + CamoEditState.blockBrush + "칸"
                : "브러시 " + CamoEditState.brush + "칸");
        g.drawString(this.font, info, 34, 11, 0xFFFFFFFF, false);
        g.drawCenteredString(this.font,
                "좌클릭=칠하기 · 우클릭드래그=시점 · 우클릭=스포이드 · 휠=크기 · G=닫기",
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

    // ---- 입력 ----

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
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
            Freecam.addCamRotation((float) dx * 0.15f, (float) dy * 0.15f); // 시점 회전
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 1 && !rDragged) {
            // 제자리 우클릭 = 스포이드(커서 밑 월드 색 추출)
            int c = ChameleonInput.readPixelAt(mx, my);
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
        if (ChameleonClient.PAINT_KEY.matches(keyCode, scanCode)) { // G로 닫기
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** 커서 픽셀로 광선을 쏴 6박스 면과 교차 → 가장 앞면 텍셀에 색칠. */
    private void paintAt(double mx, double my) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || CamoEditState.pixels == null) return;

        Vec3 origin = Freecam.camPos();
        Vec3 dir = cursorRay(mx, my);
        double bodyYaw = p.yBodyRot;
        double scale = p.getScale();
        double fx = p.getX(), fy = p.getY(), fz = p.getZ();

        int bestPart = -1, bestFace = 0, bestTu = 0, bestTv = 0;
        double bestT = Double.MAX_VALUE;
        for (int part = 0; part < 6; part++) {
            for (int face = 0; face < 6; face++) {
                Vec3[] geo = faceGeo(part, face);
                Vec3 o = geo[0], ue = geo[1], ve = geo[2];
                Vec3 p00 = world(o, bodyYaw, scale, fx, fy, fz);
                Vec3 pu = world(o.add(ue), bodyYaw, scale, fx, fy, fz);
                Vec3 pv = world(o.add(ve), bodyYaw, scale, fx, fy, fz);
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
        CamoClient.apply(p.getUUID(), CamoEditState.pixels.clone()); // 즉시 반영(라이브)
    }

    /** 커서(GUI 좌표) → 월드 광선 방향. 자유 카메라 각도 + FOV로 계산. */
    private Vec3 cursorRay(double mx, double my) {
        Minecraft mc = Minecraft.getInstance();
        int fovDeg = mc.options.fov().get();
        double fov = Math.toRadians(fovDeg);
        double tanHalf = Math.tan(fov / 2);
        double aspect = (double) this.width / this.height;
        double ndcX = (2.0 * mx / this.width - 1.0) * aspect * tanHalf;
        double ndcY = (1.0 - 2.0 * my / this.height) * tanHalf;
        double yr = Math.toRadians(Freecam.camYaw()), pr = Math.toRadians(Freecam.camPitch());
        double cp = Math.cos(pr);
        Vec3 fwd = new Vec3(-Math.sin(yr) * cp, -Math.sin(pr), Math.cos(yr) * cp);
        Vec3 right = new Vec3(-Math.cos(yr), 0, -Math.sin(yr));
        Vec3 up = right.cross(fwd);
        return fwd.add(right.scale(ndcX)).add(up.scale(ndcY)).normalize();
    }

    /** 박스 텍셀 좌표(모델 중앙 기준) → 월드 좌표. 발=y-16, 머리위=y+16. */
    private static Vec3 world(Vec3 pt, double bodyYaw, double scale, double fx, double fy, double fz) {
        double th = Math.toRadians(bodyYaw), cos = Math.cos(th), sin = Math.sin(th);
        double lx = pt.x / 16.0 * scale;          // 좌(+x) 축
        double uy = (pt.y + 16) / 16.0 * scale;   // 위 축(발이 0)
        double fzz = pt.z / 16.0 * scale;         // 앞(+z) 축
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
