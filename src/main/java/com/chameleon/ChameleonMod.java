package com.chameleon;

import com.chameleon.game.CamoGame;
import com.chameleon.net.ChameleonNet;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
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
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ChameleonItems.register(modBus);
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
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            CamoGame.tick(event.getServer());
        }
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            CamoStore.onLogin(sp);
            ChameleonNet.sendGameState(sp, CamoGame.phaseId(), CamoGame.secondsLeft());
        }
    }

    /** 술래가 총(샷건)을 우클릭 → 발사. (서버 측에서만 처리) */
    @SubscribeEvent
    public void onGunUse(PlayerInteractEvent.RightClickItem event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (event.getItemStack().getItem() != ChameleonItems.GUN.get()) return;
        if (event.getEntity() instanceof ServerPlayer sp) {
            CamoGame.fireGun(sp);
        }
    }
}
