package com.chameleon.client;

import com.chameleon.ChameleonMod;
import com.chameleon.net.CamoPaintPacket;
import com.chameleon.net.ChameleonNet;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

/**
 * 클라이언트 입력 처리 (FORGE 버스).
 * - G: 색칠 화면 / H: 위장↔스킨 토글
 * - 스포이드 무장 시: 휠클릭 → 화면 중앙(조준점) 픽셀 색을 그대로 추출(옆면 등 보이는 그대로)
 */
@Mod.EventBusSubscriber(modid = ChameleonMod.MOD_ID, value = Dist.CLIENT)
public class ChameleonInput {

    private static boolean sampleRequested = false; // 휠클릭됨 → 다음 렌더에서 픽셀 추출
    private static boolean pendingReopen = false;    // 추출 후 색칠 화면 다시 열기

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();

        if (pendingReopen && mc.screen == null) {
            pendingReopen = false;
            PaintScreen.open();
            return;
        }

        if (CamoEditState.eyedropperArmed) {
            if (mc.screen == null) {
                if (mc.options.keyPickItem.consumeClick()) {
                    sampleRequested = true; // 실제 추출은 RenderGuiEvent.Pre(월드만 그려진 시점)에서
                } else if (ChameleonClient.PAINT_KEY.consumeClick()) {
                    CamoEditState.eyedropperArmed = false;
                    PaintScreen.open();
                }
            }
            return;
        }

        while (ChameleonClient.PAINT_KEY.consumeClick()) {
            if (mc.screen == null) PaintScreen.open();
        }
        while (ChameleonClient.TOGGLE_KEY.consumeClick()) {
            toggleCamo(mc);
        }
    }

    /** 월드 렌더 후반(파티클까지) = 크로스헤어 그려지기 전. 여기서 조준점 픽셀을 읽는다. */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!sampleRequested) return;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        sampleRequested = false;
        int color = readCenterPixel();
        CamoEditState.addColor(color);
        CamoEditState.eyedropperArmed = false;
        pendingReopen = true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null)
            mc.player.displayClientMessage(Component.literal("§a색 추출: #" + String.format("%06X", color & 0xFFFFFF)), true);
    }

    /** 게임 중에는 머리 위 닉네임을 숨긴다(숨는 사람 위치 노출 방지). */
    @SubscribeEvent
    public static void onNameTag(RenderNameTagEvent event) {
        if (CamoEditState.gameActive && event.getEntity() instanceof Player) {
            event.setResult(Event.Result.DENY);
        }
    }

    private static int readCenterPixel() {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget rt = mc.getMainRenderTarget();
        int cx = rt.width / 2, cy = rt.height / 2;
        ByteBuffer pb = BufferUtils.createByteBuffer(16);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, rt.frameBufferId);
        GL11.glReadPixels(cx, cy, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pb);
        rt.bindWrite(false); // 뷰포트는 건드리지 않고 프레임버퍼 바인딩만 복구
        int r = pb.get(0) & 0xFF, g = pb.get(1) & 0xFF, b = pb.get(2) & 0xFF;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static void toggleCamo(Minecraft mc) {
        if (mc.player == null) return;
        if (CamoEditState.gameActive) {
            mc.player.displayClientMessage(Component.literal("§7게임 중엔 위장을 끌 수 없어요"), true);
            return;
        }
        if (CamoEditState.camoOn) {
            CamoClient.apply(mc.player.getUUID(), null);
            ChameleonNet.sendPaintToServer(new CamoPaintPacket(null));
            CamoEditState.camoOn = false;
            mc.player.displayClientMessage(Component.literal("§7원래 스킨"), true);
        } else {
            CamoEditState.ensureInit();
            int[] copy = CamoEditState.pixels.clone();
            CamoClient.apply(mc.player.getUUID(), copy);
            ChameleonNet.sendPaintToServer(new CamoPaintPacket(copy));
            CamoEditState.camoOn = true;
            mc.player.displayClientMessage(Component.literal("§a위장 ON"), true);
        }
    }
}
