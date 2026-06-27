package com.chameleon.client;

import com.chameleon.ChameleonMod;
import com.chameleon.net.CamoPaintPacket;
import com.chameleon.net.ChameleonNet;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 클라이언트 입력 처리 (FORGE 버스).
 * - G: 색칠 화면 열기
 * - H: 위장 / 원래 스킨 토글
 * - 스포이드 무장 시: 휠클릭으로 바라보는 블록 색 추출 후 화면 복귀(G로 취소)
 */
@Mod.EventBusSubscriber(modid = ChameleonMod.MOD_ID, value = Dist.CLIENT)
public class ChameleonInput {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return; // 바닐라 입력 처리 전에 가로채기
        Minecraft mc = Minecraft.getInstance();

        // 스포이드 무장 상태: 월드에서 블록 색 추출
        if (CamoEditState.eyedropperArmed) {
            if (mc.screen == null) {
                if (mc.options.keyPickItem.consumeClick()) {
                    sampleBlock(mc);
                    CamoEditState.eyedropperArmed = false;
                    PaintScreen.open();
                } else if (ChameleonClient.PAINT_KEY.consumeClick()) {
                    CamoEditState.eyedropperArmed = false;
                    PaintScreen.open();
                }
            }
            return;
        }

        // G: 색칠 화면
        while (ChameleonClient.PAINT_KEY.consumeClick()) {
            if (mc.screen == null) PaintScreen.open();
        }

        // H: 위장/스킨 토글
        while (ChameleonClient.TOGGLE_KEY.consumeClick()) {
            toggleCamo(mc);
        }
    }

    private static void sampleBlock(Minecraft mc) {
        int color = 0xFFFFFFFF;
        if (mc.hitResult instanceof BlockHitResult bhr && mc.level != null) {
            BlockPos pos = bhr.getBlockPos();
            BlockState st = mc.level.getBlockState(pos);
            MapColor c = st.getMapColor(mc.level, pos);
            if (c != MapColor.NONE) color = 0xFF000000 | (c.col & 0xFFFFFF);
        }
        CamoEditState.addColor(color);
        if (mc.player != null)
            mc.player.displayClientMessage(Component.literal("§a색 추출 완료"), true);
    }

    private static void toggleCamo(Minecraft mc) {
        if (mc.player == null) return;
        if (CamoEditState.camoOn) {
            // 끄기 → 원래 스킨
            CamoClient.apply(mc.player.getUUID(), null);
            ChameleonNet.sendPaintToServer(new CamoPaintPacket(null));
            CamoEditState.camoOn = false;
            mc.player.displayClientMessage(Component.literal("§7원래 스킨"), true);
        } else {
            // 켜기 → 칠한 위장
            CamoEditState.ensureInit();
            int[] copy = CamoEditState.pixels.clone();
            CamoClient.apply(mc.player.getUUID(), copy);
            ChameleonNet.sendPaintToServer(new CamoPaintPacket(copy));
            CamoEditState.camoOn = true;
            mc.player.displayClientMessage(Component.literal("§a위장 ON"), true);
        }
    }
}
