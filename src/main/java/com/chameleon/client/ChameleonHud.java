package com.chameleon.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * 게임 HUD: 상단에 남은 시간 계속 표시 + 마지막 10초 큰 카운트다운.
 */
public class ChameleonHud {

    public static final IGuiOverlay TIMER = (gui, g, partial, w, h) -> {
        if (!CamoEditState.gameActive) return;
        Minecraft mc = Minecraft.getInstance();
        int s = CamoEditState.gameSecondsLeft;
        String t = String.format("§e남은 시간 %d:%02d", s / 60, s % 60);
        g.drawCenteredString(mc.font, t, w / 2, 6, 0xFFFFFFFF);

        if (s <= 10 && s > 0) {
            g.pose().pushPose();
            g.pose().translate(w / 2f, h / 2f - 30, 0);
            g.pose().scale(4f, 4f, 1f);
            int col = s <= 3 ? 0xFFFF4444 : 0xFFFFFFFF;
            g.drawCenteredString(mc.font, String.valueOf(s), 0, -4, col);
            g.pose().popPose();
        }
    };
}
