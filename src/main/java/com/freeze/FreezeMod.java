package com.freeze;

import com.mojang.logging.LogUtils;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
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

    // 빨간 파티클 (RGB 0xFF1A1A, 크기 1.5)
    private static final DustParticleOptions RED_DUST =
            new DustParticleOptions(0xFF1A1A, 1.5f);
    private static final double PARTICLE_STEP = 1.5;   // 수평 외곽선 간격
    private static final double PILLAR_STEP = 2.0;     // 모서리 기둥 세로 간격
    private static final double FRAME_STEP = 8.0;      // 수평 외곽선을 그리는 높이 간격
    private static final int PARTICLE_INTERVAL_TICKS = 10;

    private int tickCounter = 0;

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

        if (tickCounter++ % PARTICLE_INTERVAL_TICKS == 0) {
            renderBoundary(server);
        }
    }

    private void renderBoundary(MinecraftServer server) {
        ServerLevel level = server.getLevel(AREA.getWorldKey());
        if (level == null) return;

        double minX = AREA.minX();
        double maxX = AREA.maxX() + 1;
        double minZ = AREA.minZ();
        double maxZ = AREA.maxZ() + 1;
        // 위아래는 월드 높이 전체
        double minY = level.getMinY();
        double maxY = level.getMaxY();

        // 4개 모서리 기둥 (월드 바닥~천장)
        for (double y = minY; y <= maxY; y += PILLAR_STEP) {
            spawn(level, minX, y, minZ);
            spawn(level, minX, y, maxZ);
            spawn(level, maxX, y, minZ);
            spawn(level, maxX, y, maxZ);
        }

        // 수평 사각형 외곽선 (일정 높이마다)
        for (double y = minY; y <= maxY; y += FRAME_STEP) {
            for (double x = minX; x <= maxX; x += PARTICLE_STEP) {
                spawn(level, x, y, minZ);
                spawn(level, x, y, maxZ);
            }
            for (double z = minZ; z <= maxZ; z += PARTICLE_STEP) {
                spawn(level, minX, y, z);
                spawn(level, maxX, y, z);
            }
        }
    }

    private void spawn(ServerLevel level, double x, double y, double z) {
        level.sendParticles(RED_DUST, x, y, z, 1, 0, 0, 0, 0);
    }
}
