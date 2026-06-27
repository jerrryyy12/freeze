package com.chameleon.client;

import com.chameleon.ChameleonMod;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 클라이언트 전용 등록. 플레이어 렌더러(기본/슬림 스킨 모델)에 위장 레이어를 추가한다.
 * Dist.CLIENT 로 한정되어 전용 서버에서는 로드되지 않는다.
 */
@Mod.EventBusSubscriber(modid = ChameleonMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ChameleonClient {

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
