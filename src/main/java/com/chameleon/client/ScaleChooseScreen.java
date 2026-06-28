package com.chameleon.client;

import com.chameleon.net.ChameleonNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 준비 시간: 숨는 사람이 캐릭터 크기(0.5/0.7/1배)를 고르는 팝업.
 * 선택하면 서버로 전송 후 닫힌다. 준비 시간이 끝나면(숨기 전환) 자동으로 닫힌다.
 */
public class ScaleChooseScreen extends Screen {

    private static final float[] SCALES = {0.5f, 0.7f, 1.0f};

    public ScaleChooseScreen() {
        super(Component.literal("크기 선택"));
    }

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof ScaleChooseScreen)) {
            mc.setScreen(new ScaleChooseScreen());
        }
    }

    @Override
    protected void init() {
        int bw = 96, bh = 30, gap = 12;
        int total = SCALES.length * bw + (SCALES.length - 1) * gap;
        int x0 = this.width / 2 - total / 2;
        int y = this.height / 2;
        for (int i = 0; i < SCALES.length; i++) {
            float s = SCALES[i];
            String label = (s == 1.0f) ? "1배" : (s + "배");
            int x = x0 + i * (bw + gap);
            this.addRenderableWidget(Button.builder(Component.literal(label), b -> choose(s))
                    .bounds(x, y, bw, bh).build());
        }
    }

    private void choose(float s) {
        ChameleonNet.sendScaleChoice(s);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            String t = (s == 1.0f) ? "1" : String.valueOf(s);
            mc.player.displayClientMessage(Component.literal("§a크기 " + t + "배 선택"), true);
        }
        this.onClose();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xC0000000); // 어둡게
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        super.render(g, mouseX, mouseY, pt); // 배경 + 버튼
        g.drawCenteredString(this.font, "§b숨을 준비! 캐릭터 크기를 고르세요",
                this.width / 2, this.height / 2 - 44, 0xFFFFFFFF);
        g.drawCenteredString(this.font, "작을수록 숨기 쉬움 · 클수록 잘 보임",
                this.width / 2, this.height / 2 - 28, 0xFFB0B0B0);
    }

    @Override
    public void tick() {
        // 준비 페이즈(1)가 아니면(숨기 시작 등) 자동으로 닫는다.
        if (CamoEditState.phase != 1) this.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
