package com.chameleon.client;

import com.chameleon.net.CamoPaintPacket;
import com.chameleon.net.ChameleonNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

/**
 * 3D 색칠 모드 - 1단계: 위장 입은 내 캐릭터를 마우스로 돌려본다(바닐라 엔티티 렌더).
 * 색칠(피킹)은 다음 단계에서 추가. 2D 편집 상태(CamoEditState)를 공유한다.
 */
public class Paint3DScreen extends Screen {

    public Paint3DScreen() {
        super(Component.literal("위장 색칠 3D"));
    }

    public static void open() {
        CamoEditState.ensureInit();
        Minecraft.getInstance().setScreen(new Paint3DScreen());
    }

    @Override
    protected void init() {
        // 현재 편집 중인 위장을 모델에 즉시 반영
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            CamoClient.apply(mc.player.getUUID(), CamoEditState.pixels.clone());
            CamoEditState.camoOn = true;
        }
        addRenderableWidget(Button.builder(Component.literal("2D로"), b -> PaintScreen.open())
                .bounds(10, this.height - 26, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("완료"), b -> this.onClose())
                .bounds(this.width - 100, this.height - 26, 90, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font, "3D 미리보기 — 마우스로 돌려보기 (색칠은 다음 단계)",
                this.width / 2, 10, 0xFFFFFFFF);

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            int cx = this.width / 2, cy = this.height / 2;
            int scale = Math.max(40, Math.min(this.width, this.height) / 3);
            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    g, cx - 70, cy - 90, cx + 70, cy + 100, scale, 0.0625f,
                    (float) mouseX, (float) mouseY, mc.player);
        }
    }

    @Override
    public void onClose() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            int[] copy = CamoEditState.pixels.clone();
            CamoClient.apply(mc.player.getUUID(), copy);
            ChameleonNet.sendPaintToServer(new CamoPaintPacket(copy));
            CamoEditState.camoOn = true;
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
