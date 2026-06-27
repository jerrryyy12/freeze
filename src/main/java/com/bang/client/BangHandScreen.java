package com.bang.client;

import com.bang.BangHandMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** 손패 GUI 화면 (임시 외형: 어두운 배경 + 슬롯 칸). */
public class BangHandScreen extends AbstractContainerScreen<BangHandMenu> {

    public BangHandScreen(BangHandMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 18 + 3 * 18 + 8;
        this.inventoryLabelY = -100; // 인벤토리 라벨 숨김
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xCC202428);
        g.fill(x, y, x + imageWidth, y + 16, 0xFF8A5A2B); // 상단 띠
        // 슬롯 칸
        for (int i = 0; i < BangHandMenu.CARD_SLOTS; i++) {
            int sx = x + 8 + (i % 9) * 18;
            int sy = y + 18 + (i / 9) * 18;
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF000000);
            g.fill(sx, sy, sx + 16, sy + 16, 0xFF3A3F45);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFFFFFF, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        this.renderTooltip(g, mouseX, mouseY);
    }
}
