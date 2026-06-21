package com.freeze;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(FreezeMod.MOD_ID)
public class FreezeMod {
    public static final String MOD_ID = "freeze";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final FreezeArea AREA = new FreezeArea();

    // 바닐라 월드보더 기본값 (off 시 복원용)
    private static final double VANILLA_BORDER_SIZE = 59999968.0;

    public FreezeMod() {
        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("Freeze 모드 로드 완료");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        FreezeCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent.Post event) {
        if (!AREA.isEnabled()) return;

        MinecraftServer server = event.server();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            GameType mode = player.gameMode.getGameModeForPlayer();
            if (mode == GameType.CREATIVE || mode == GameType.SPECTATOR) continue;
            if (!AREA.contains(player)) {
                player.kill((ServerLevel) player.level());
            }
        }
    }

    /** 영역에 맞춰 월드보더(빨간/청록 벽)를 설정 */
    public static void applyBorder(MinecraftServer server) {
        if (!AREA.isConfigured()) return;
        ServerLevel level = server.getLevel(AREA.getWorldKey());
        if (level == null) return;
        WorldBorder border = level.getWorldBorder();
        border.setCenter(AREA.centerX(), AREA.centerZ());
        border.setSize(AREA.size());
        border.setWarningBlocks(0);
    }

    /** 월드보더를 바닐라 기본값으로 복원 */
    public static void clearBorder(MinecraftServer server) {
        if (!AREA.isConfigured()) return;
        ServerLevel level = server.getLevel(AREA.getWorldKey());
        if (level == null) return;
        WorldBorder border = level.getWorldBorder();
        border.setCenter(0.0, 0.0);
        border.setSize(VANILLA_BORDER_SIZE);
    }
}
