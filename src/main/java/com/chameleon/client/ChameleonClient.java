package com.chameleon.client;

import com.chameleon.ChameleonMod;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * 클라이언트 전용 등록. 플레이어 렌더러(기본/슬림 스킨 모델)에 위장 레이어를 추가하고,
 * 색칠 화면 키바인딩을 등록한다. Dist.CLIENT 로 한정되어 전용 서버에서는 로드되지 않는다.
 */
@Mod.EventBusSubscriber(modid = ChameleonMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ChameleonClient {

    /** 위장 색칠 화면 열기 (기본 G). */
    public static final KeyMapping PAINT_KEY =
            new KeyMapping("key.chameleon.paint", GLFW.GLFW_KEY_G, "key.categories.chameleon");

    /** 위장 / 원래 스킨 토글 (기본 H). */
    public static final KeyMapping TOGGLE_KEY =
            new KeyMapping("key.chameleon.toggle", GLFW.GLFW_KEY_H, "key.categories.chameleon");

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(PAINT_KEY);
        event.register(TOGGLE_KEY);
    }

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("camo_timer", ChameleonHud.TIMER);
    }

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (var skin : event.getSkins()) {
            // Object 캡처로 제네릭 추론/타입 변환 문제를 피한다.
            Object renderer = event.getPlayerSkin(skin);
            if (renderer instanceof PlayerRenderer pr) {
                pr.addLayer(new CamoLayer(pr));
            }
        }
        ChameleonMod.LOGGER.info("Chameleon 위장 레이어 등록 완료");
    }
}
