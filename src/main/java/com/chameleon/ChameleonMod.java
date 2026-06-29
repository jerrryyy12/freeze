package com.chameleon;

import com.chameleon.game.CamoGame;
import com.chameleon.net.ChameleonNet;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

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

    /** 눕는 이모트의 낮은 히트박스(가로=세로 0.6, 높이 0.6). 정사각 바닥이라 길이는 못 늘림. */
    public static final EntityDimensions LIE_BOX = EntityDimensions.fixed(0.6f, 0.6f);
    private static final Set<UUID> serverLying = new HashSet<>(); // 서버: 눕기 상태 추적

    /** 눕는 이모트인지(8=대자, 9=옆으로). */
    public static boolean isLyingEmote(int emote) {
        return emote == 8 || emote == 9;
    }

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
            EmoteStore.onLogin(sp);
            ChameleonNet.sendGameState(sp, CamoGame.phaseId(), CamoGame.secondsLeft(), CamoGame.roleIdOf(sp), CamoGame.isInfectionMode());
        }
    }

    /** 눕는 이모트 동안 히트박스를 낮은 상자로 가로챈다(서버 측). */
    @SubscribeEvent
    public void onEntitySize(EntityEvent.Size event) {
        if (event.getEntity() instanceof ServerPlayer sp && isLyingEmote(EmoteStore.emoteOf(sp.getUUID()))) {
            event.setNewSize(LIE_BOX);
            event.setNewEyeHeight(0.4f);
        }
    }

    /** 눕기 상태가 바뀌면 히트박스 재계산을 강제한다(서버). */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer sp)) return;
        UUID id = sp.getUUID();
        boolean lying = isLyingEmote(EmoteStore.emoteOf(id));
        if (lying != serverLying.contains(id)) {
            if (lying) serverLying.add(id); else serverLying.remove(id);
            sp.refreshDimensions();
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
