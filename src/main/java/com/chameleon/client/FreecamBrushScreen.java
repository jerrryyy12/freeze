package com.chameleon.client;

import com.chameleon.net.CamoPaintPacket;
import com.chameleon.net.ChameleonNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 자유시점에서 캐릭터에 "직접" 칠하는 브러시 화면.
 * 뒤로 실제 월드(자유시점)가 보이고, 마우스 커서로 내 캐릭터 몸을 직접 칠한다.
 * 피킹은 BrushUvPicker가 캐릭터를 UV값으로 오프스크린 렌더한 결과를 읽어 텍셀을 구한다
 * (실제 렌더 포즈 그대로라 이모트·눕기 등 어떤 포즈에서도 정확).
 * - 좌클릭/드래그: 색칠 · 우클릭드래그: 시점 회전 · 우클릭(제자리): 스포이드
 * - 팔레트클릭: 색 선택 · 휠: 브러시 크기 · WASD: 카메라 이동 · G/ESC: 닫기
 */
public class FreecamBrushScreen extends Screen {

    private static final int SIZE = CamoEditState.SIZE;
    private static final int SCALE = SIZE / 64;
    private static final int[][][] FACES = PaintScreen.FACES;

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

        // 팔레트
        java.util.List<Integer> pal = CamoEditState.palette;
        for (int i = 0; i < pal.size() && i < COLS * PAL_ROWS; i++) {
            int x = PAL_X + (i % COLS) * (SW + 2), y = PAL_Y + (i / COLS) * (SW + 2);
            int col = pal.get(i);
            if (col == CamoEditState.selectedColor) g.fill(x - 1, y - 1, x + SW + 1, y + SW + 1, 0xFFFFFF00);
            g.fill(x, y, x + SW, y + SW, col);
        }

        g.drawCenteredString(this.font,
                "좌클릭=칠하기 · 우클릭드래그=시점 · 우클릭=스포이드 · 팔레트=색 · 휠=크기 · WASD=이동 · G=닫기",
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

    /** 커서 밑 픽셀의 스킨 텍셀을 읽어 칠한다(BrushUvPicker가 렌더해둔 UV 버퍼 사용). */
    private void paintAt(double mx, double my) {
        if (CamoEditState.pixels == null) return;
        int[] uv = BrushUvPicker.readUV(mx, my);
        if (uv == null) return; // 캐릭터 밖
        int su = uv[0] * SCALE, sv = uv[1] * SCALE;
        int[] f = faceAt(su, sv);
        if (f != null) {
            CamoEditState.applyBrushOnFace(f, su, sv);
        } else {
            int b = Math.max(1, CamoEditState.brush);
            CamoEditState.fill(su - b / 2, sv - b / 2, b, b, CamoEditState.selectedColor);
        }
        dirty = true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) CamoClient.apply(mc.player.getUUID(), CamoEditState.pixels.clone());
    }

    /** 텍셀 (su,sv)가 속한 면 UV 박스를 찾는다(없으면 null). */
    private static int[] faceAt(int su, int sv) {
        for (int part = 0; part < 6; part++) {
            for (int face = 0; face < 6; face++) {
                int[] f = FACES[part][face];
                if (su >= f[0] && su < f[0] + f[2] && sv >= f[1] && sv < f[1] + f[3]) return f;
            }
        }
        return null;
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
