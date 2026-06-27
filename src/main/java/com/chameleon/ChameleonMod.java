package com.chameleon;

import com.chameleon.net.ChameleonNet;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * MECCHA CHAMELEON - 위장 숨바꼭질 모드.
 *
 * <p>1단계: 커스텀 플레이어 렌더 레이어 검증(웅크리면 발밑 블록색). ✔</p>
 * <p>2-a단계: 플레이어별 위장 텍스처 + 네트워킹 + 텍스처 렌더. 검증 명령어 /camo.</p>
 */
@Mod(ChameleonMod.MOD_ID)
public class ChameleonMod {
    public static final String MOD_ID = "chameleon";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ChameleonMod() {
        ChameleonNet.register();
        MinecraftForge.EVENT_BUS.register(this);
        // 클라이언트 렌더링 등록은 client.ChameleonClient(@EventBusSubscriber, Dist.CLIENT)가 담당.
        LOGGER.info("Chameleon 모드 로드 완료");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CamoCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            CamoStore.onLogin(sp);
        }
    }
}
