package com.chameleon.client;

import com.chameleon.net.ChameleonNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 이모트 선택 휠(R). 화면 중앙 원형 메뉴에서 마우스 방향으로 이모트를 고르고
 * 좌클릭으로 재생. 가운데(데드존)에서 클릭하거나 ESC로 취소.
 */
public class EmoteWheelScreen extends Screen {

    private static final int RING = 95;     // 라벨 배치 반지름
    private static final int DEADZONE = 28;  // 가운데 취소 영역
    private int cx, cy;
    private int hover = -1;

    public EmoteWheelScreen() {
        super(Component.literal("이모트"));
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new EmoteWheelScreen());
    }

    @Override
    protected void init() {
        cx = this.width / 2;
        cy = this.height / 2;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0x88000000); // 살짝 어둡게
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        hover = hoveredSlot(mouseX, mouseY);

        // 가운데 안내
        g.drawCenteredString(this.font, hover >= 0 ? EmotePoser.NAMES[hover] : "§c이모트 끄기",
                cx, cy - 4, 0xFFFFFFFF);

        int n = EmotePoser.COUNT;
        for (int i = 0; i < n; i++) {
            double ang = Math.toRadians(i * (360.0 / n) - 90.0); // 0번=위, 시계방향
            int lx = cx + (int) (Math.cos(ang) * RING);
            int ly = cy + (int) (Math.sin(ang) * RING);
            String name = EmotePoser.NAMES[i];
            int w = this.font.width(name);
            boolean on = (i == hover);
            // 라벨 배경 박스
            g.fill(lx - w / 2 - 4, ly - 8, lx + w / 2 + 4, ly + 9,
                    on ? 0xFF2BB6FF : 0xC0202020);
            g.drawString(this.font, name, lx - w / 2, ly - 4, on ? 0xFF000000 : 0xFFE0E0E0, false);
        }
        g.drawCenteredString(this.font, "좌클릭=재생 · 가운데=끄기 · ESC=취소", cx, cy + 8, 0xFF9A9A9A);
    }

    private int hoveredSlot(double mx, double my) {
        double dx = mx - cx, dy = my - cy;
        if (dx * dx + dy * dy < DEADZONE * DEADZONE) return -1;
        double a = Math.toDegrees(Math.atan2(dy, dx)) + 90.0; // 위=0
        if (a < 0) a += 360.0;
        int n = EmotePoser.COUNT;
        return ((int) Math.round(a / (360.0 / n))) % n;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            int slot = hoveredSlot(mx, my);
            if (slot >= 0) play(slot);
            else play(-1); // 가운데 = 이모트 끄기
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    /** emote=-1 이면 끄기. */
    private void play(int emote) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            EmoteState.set(mc.player.getUUID(), emote); // 즉시 로컬 반영
            ChameleonNet.sendEmote(emote);               // 서버 → 전체 동기화
        }
        this.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
