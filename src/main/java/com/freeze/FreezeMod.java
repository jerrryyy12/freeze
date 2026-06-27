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
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;
import org.slf4j.Logger;

@Mod(FreezeMod.MOD_ID)
public class FreezeMod {
    public static final String MOD_ID = "freeze";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final FreezeArea AREA = new FreezeArea();

    // 빨간 위험 벽 파티클 (통과 가능, 막지 않음)
    private static final DustParticleOptions RED_DUST =
            new DustParticleOptions(new Vector3f(1.0f, 0.0f, 0.0f), 1.4f);
    private static final double STEP = 0.5;
    private static final double REACH = 10.0;
    private static final double V_BAND = 5.0;
    private static final int RENDER_INTERVAL = 5;

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
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!AREA.isEnabled()) return;

        MinecraftServer server = event.getServer();
        boolean render = (tickCounter++ % RENDER_INTERVAL == 0);
        ServerLevel level = server.getLevel(AREA.getWorldKey());

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            GameType mode = player.gameMode.getGameModeForPlayer();
            if (mode == GameType.CREATIVE || mode == GameType.SPECTATOR) continue;

            if (!AREA.contains(player)) {
                player.kill();
                continue;
            }
            if (render && level != null
                    && ((ServerLevel) player.level()).dimension().equals(AREA.getWorldKey())) {
                renderWallsNear(level, player);
            }
        }
    }

    private void renderWallsNear(ServerLevel level, ServerPlayer player) {
        double west = AREA.minX();
        double east = AREA.maxX() + 1;
        double north = AREA.minZ();
        double south = AREA.maxZ() + 1;

        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();

        double y0 = py - V_BAND;
        double y1 = py + V_BAND;

        for (double wx : new double[]{west, east}) {
            if (Math.abs(px - wx) > REACH) continue;
            double z0 = Math.max(north, pz - REACH);
            double z1 = Math.min(south, pz + REACH);
            for (double z = z0; z <= z1; z += STEP) {
                for (double y = y0; y <= y1; y += STEP) spawn(level, wx, y, z);
            }
        }
        for (double wz : new double[]{north, south}) {
            if (Math.abs(pz - wz) > REACH) continue;
            double x0 = Math.max(west, px - REACH);
            double x1 = Math.min(east, px + REACH);
            for (double x = x0; x <= x1; x += STEP) {
                for (double y = y0; y <= y1; y += STEP) spawn(level, x, y, wz);
            }
        }
    }

    private void spawn(ServerLevel level, double x, double y, double z) {
        level.sendParticles(RED_DUST, x, y, z, 1, 0, 0, 0, 0);
    }
}
