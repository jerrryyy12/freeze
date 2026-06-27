package com.chameleon.client;

import com.chameleon.ChameleonMod;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 클라이언트 입력 처리 (FORGE 버스). 매 클라 틱마다 색칠 키를 확인해 화면을 연다.
 */
@Mod.EventBusSubscriber(modid = ChameleonMod.MOD_ID, value = Dist.CLIENT)
public class ChameleonInput {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        while (ChameleonClient.PAINT_KEY.consumeClick()) {
            if (Minecraft.getInstance().screen == null) {
                PaintScreen.open();
            }
        }
    }
}
