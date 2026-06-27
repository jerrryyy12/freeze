package com.chameleon.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ViewportEvent;

/**
 * 자유 시점 색추출(스포이드) 모드. 마우스가 풀린 투명 오버레이로,
 * 내 캐릭터를 3인칭으로 보면서 화면 어디든 커서로 가리킨 픽셀의 색을 추출한다.
 * - 좌클릭: 커서 밑 픽셀 색 추출
 * - 우클릭 드래그: 시점(카메라) 회전
 * - 휠: 카메라 거리(깊이) 조절 — 벽에 가까워도 캐릭터가 커지지 않음(벽 충돌 무시)
 *
 * <p>카메라 위치는 ChameleonInput.onCameraSetup → {@link #applyCamera}에서
 * Camera 위치 필드를 덮어써(믹스인 없이) 궤도 카메라로 만든다.
 * 커서 밑 픽셀 색은 onRenderLevel(월드만 그려진 시점)에서 읽어 {@link #updateHover}로 갱신.</p>
 */
public class EyedropperScreen extends Screen {

    // 카메라 상태(여러 번 열어도 유지되도록 static)
    private static double dist = 4.0;     // 카메라 거리(휠)
    private static float camYaw = 0f;     // 시점 yaw(우드래그)
    private static float camPitch = 10f;  // 시점 pitch(우드래그)

    private static final double MIN_DIST = 1.5, MAX_DIST = 30.0;

    private final Screen caller;        // 추출/취소 후 돌아갈 색칠 화면
    private CameraType prevType = CameraType.FIRST_PERSON;
    private double curX, curY;          // 현재 커서(GUI 좌표)
    private int hover = 0xFF000000;     // 커서 밑 색(미리보기)

    public EyedropperScreen(Screen caller) {
        super(Component.literal("스포이드"));
        this.caller = caller;
    }

    public static void open(Screen caller) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {        // 시점은 내가 보던 방향에서 시작
            camYaw = mc.player.getYRot();
            camPitch = Math.max(-20f, Math.min(60f, mc.player.getXRot()));
        }
        mc.setScreen(new EyedropperScreen(caller));
    }

    @Override
    protected void init() {
        curX = this.width / 2.0;
        curY = this.height / 2.0;
        Minecraft mc = Minecraft.getInstance();
        prevType = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); // 내 캐릭터가 보이도록
    }

    /** ComputeCameraAngles에서 호출: 스포이드 모드면 궤도 카메라(각도+위치)를 적용. */
    public static void applyCamera(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof EyedropperScreen) || mc.player == null) return;
        event.setYaw(camYaw);
        event.setPitch(camPitch);
        double yr = Math.toRadians(camYaw), pr = Math.toRadians(camPitch), cp = Math.cos(pr);
        Vec3 look = new Vec3(-Math.sin(yr) * cp, -Math.sin(pr), Math.cos(yr) * cp);
        Vec3 focus = mc.player.getEyePosition((float) event.getPartialTick());
        Freecam.setCameraPosition(event.getCamera(), focus.subtract(look.scale(dist)));
    }

    /** 커서 밑 픽셀 색을 외부(렌더 단계)에서 갱신. */
    public void updateHover(int argb) {
        this.hover = argb;
    }

    public double cursorX() { return curX; }
    public double cursorY() { return curY; }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 어둡게/블러 없이 월드를 그대로 보이게 둔다.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        curX = mouseX;
        curY = mouseY;

        g.drawCenteredString(this.font, "스포이드 — [좌클릭] 색 추출 · [우클릭 드래그] 시점 회전 · [휠] 거리  (ESC 취소)",
                this.width / 2, 8, 0xFFFFFFFF);

        drawCursor(g, mouseX, mouseY);

        // 미리보기 스와치 + HEX (커서 우하단, 화면 밖이면 반대쪽)
        int sw = 30;
        int sx = mouseX + 14, sy = mouseY + 14;
        if (sx + sw + 2 > this.width) sx = mouseX - 14 - sw;
        if (sy + sw + 12 > this.height) sy = mouseY - 14 - sw;
        g.fill(sx - 2, sy - 2, sx + sw + 2, sy + sw + 2, 0xFF000000);
        g.fill(sx, sy, sx + sw, sy + sw, 0xFFFFFFFF);
        g.fill(sx + 1, sy + 1, sx + sw - 1, sy + sw - 1, hover);
        g.drawString(this.font, String.format("#%06X", hover & 0xFFFFFF), sx, sy + sw + 2, 0xFFFFFFFF);
    }

    /** 포토샵식 커서: 가운데 비운 십자선 + 작은 원(정확히 가리키는 픽셀이 보이도록). */
    private void drawCursor(GuiGraphics g, int cx, int cy) {
        int core = 0xFFFFFFFF, edge = 0xFF000000;
        for (int d = 3; d <= 10; d++) {
            px(g, cx, cy - d, core, edge);
            px(g, cx, cy + d, core, edge);
            px(g, cx - d, cy, core, edge);
            px(g, cx + d, cy, core, edge);
        }
        int r = 7, seg = 28;
        for (int i = 0; i < seg; i++) {
            double a = i * 2 * Math.PI / seg;
            px(g, cx + (int) Math.round(r * Math.cos(a)), cy + (int) Math.round(r * Math.sin(a)), core, edge);
        }
    }

    private void px(GuiGraphics g, int x, int y, int core, int edge) {
        g.fill(x - 1, y - 1, x + 2, y + 2, edge);
        g.fill(x, y, x + 1, y + 1, core);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {           // 좌클릭 = 추출
            CamoEditState.addColor(hover);
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null)
                mc.player.displayClientMessage(
                        Component.literal("§a색 추출: #" + String.format("%06X", hover & 0xFFFFFF)), true);
            close();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button == 1) {           // 우클릭 드래그 = 시점 회전
            camYaw += (float) dx * 0.4f;
            camPitch = (float) Math.max(-89, Math.min(89, camPitch + dy * 0.4f));
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (sy != 0) {               // 휠 = 카메라 거리
            dist = Math.max(MIN_DIST, Math.min(MAX_DIST, dist - sy));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    private void close() {
        Minecraft.getInstance().setScreen(caller);
    }

    @Override
    public void onClose() {
        close();
    }

    @Override
    public void removed() {
        Minecraft.getInstance().options.setCameraType(prevType); // 시점 복구
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
