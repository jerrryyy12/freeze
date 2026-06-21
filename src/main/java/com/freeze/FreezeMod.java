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

    // 빨간 위험 벽 파티클 (통과 가능, 막지 않음)
    private static final DustParticleOptions RED_DUST =
            new DustParticleOptions(0xFF0000, 1.4f);
    private static final double STEP = 0.5;          // 벽 격자 간격(촘촘할수록 벽처럼 보임)
    private static final double REACH = 10.0;        // 경계에서 이 거리 안이면 벽 표시
    private static final double V_BAND = 5.0;        // 플레이어 위아래로 표시할 높이
    private static final int RENDER_INTERVAL = 5;    // 몇 틱마다 벽 갱신

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
        boolean render = (tickCounter++ % RENDER_INTERVAL == 0);
        ServerLevel level = server.getLevel(AREA.getWorldKey());

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            GameType mode = player.gameMode.getGameModeForPlayer();
            if (mode == GameType.CREATIVE || mode == GameType.SPECTATOR) continue;

            // 영역 밖이면 즉시 사망
            if (!AREA.contains(player)) {
                player.kill((ServerLevel) player.level());
                continue;
            }
            // 경계 근처면 눈앞에 빨간 벽 표시
            if (render && level != null
                    && ((ServerLevel) player.level()).dimension().equals(AREA.getWorldKey())) {
                renderWallsNear(level, player);
            }
        }
    }

    /** 플레이어가 경계에 가까운 변만 촘촘한 빨간 벽으로 표시 */
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

        // 동/서 벽 (x 고정, z 따라)
        for (double wx : new double[]{west, east}) {
            if (Math.abs(px - wx) > REACH) continue;
            double z0 = Math.max(north, pz - REACH);
            double z1 = Math.min(south, pz + REACH);
            for (double z = z0; z <= z1; z += STEP) {
                for (double y = y0; y <= y1; y += STEP) {
                    spawn(level, wx, y, z);
                }
            }
        }
        // 남/북 벽 (z 고정, x 따라)
        for (double wz : new double[]{north, south}) {
            if (Math.abs(pz - wz) > REACH) continue;
            double x0 = Math.max(west, px - REACH);
            double x1 = Math.min(east, px + REACH);
            for (double x = x0; x <= x1; x += STEP) {
                for (double y = y0; y <= y1; y += STEP) {
                    spawn(level, x, y, wz);
                }
            }
        }
    }

    private void spawn(ServerLevel level, double x, double y, double z) {
        level.sendParticles(RED_DUST, x, y, z, 1, 0, 0, 0, 0);
    }
}
