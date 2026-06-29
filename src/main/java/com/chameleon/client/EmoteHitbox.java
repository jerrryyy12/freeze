package com.chameleon.client;

import com.chameleon.ChameleonMod;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 클라이언트: 눕는 이모트 동안 플레이어 히트박스를 낮은 상자로 가로챈다(서버와 동일).
 * 로컬 플레이어의 이동 예측과 충돌이 서버와 맞도록.
 */
@Mod.EventBusSubscriber(modid = ChameleonMod.MOD_ID, value = Dist.CLIENT)
public final class EmoteHitbox {

    @SubscribeEvent
    public static void onEntitySize(EntityEvent.Size event) {
        if (event.getEntity() instanceof AbstractClientPlayer p
                && ChameleonMod.isLyingEmote(EmoteState.emoteOf(p.getUUID()))) {
            event.setNewSize(ChameleonMod.LIE_BOX);
            event.setNewEyeHeight(0.4f);
        }
    }
}
