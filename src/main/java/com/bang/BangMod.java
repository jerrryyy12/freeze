package com.bang;

import com.bang.game.BangGame;
import com.bang.game.BangHeads;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(BangMod.MOD_ID)
public class BangMod {
    public static final String MOD_ID = "bang";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** 현재 진행 중인 게임 (서버당 1판). */
    public static BangGame game;

    public BangMod() {
        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        BangItems.register(modBus);
        BangMenus.register(modBus);
        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("Bang 모드 로드 완료");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        BangCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (game != null && game.isPlaying()) {
            BangHeads.tick(event.getServer(), game);
        }
    }
}
