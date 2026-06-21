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

    private static final DustParticleOptions RED_DUST =
            new DustParticleOptions(new Vector3f(1.0f, 0.1f, 0.1f), 1.5f);
    private static final double PARTICLE_STEP = 1.5;
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
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!AREA.isEnabled()) return;

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            GameType mode = player.gameMode.getGameModeForPlayer();
            if (mode == GameType.CREATIVE || mode == GameType.SPECTATOR) continue;
            if (!AREA.contains(player)) {
                player.kill(player.serverLevel());
            }
        }

        if (tickCounter++ % PARTICLE_INTERVAL_TICKS == 0) {
            renderBoundary(event.getServer());
        }
    }

    private void renderBoundary(MinecraftServer server) {
        ServerLevel level = server.getLevel(AREA.getWorldKey());
        if (level == null) return;

        double minX = AREA.minX();
        double maxX = AREA.maxX() + 1;
        double minY = AREA.minY();
        double maxY = AREA.maxY() + 1;
        double minZ = AREA.minZ();
        double maxZ = AREA.maxZ() + 1;

        for (double x = minX; x <= maxX; x += PARTICLE_STEP) {
            spawn(level, x, minY, minZ);
            spawn(level, x, minY, maxZ);
            spawn(level, x, maxY, minZ);
            spawn(level, x, maxY, maxZ);
        }
        for (double y = minY; y <= maxY; y += PARTICLE_STEP) {
            spawn(level, minX, y, minZ);
            spawn(level, minX, y, maxZ);
            spawn(level, maxX, y, minZ);
            spawn(level, maxX, y, maxZ);
        }
        for (double z = minZ; z <= maxZ; z += PARTICLE_STEP) {
            spawn(level, minX, minY, z);
            spawn(level, minX, maxY, z);
            spawn(level, maxX, minY, z);
            spawn(level, maxX, maxY, z);
        }
    }

    private void spawn(ServerLevel level, double x, double y, double z) {
        level.sendParticles(RED_DUST, x, y, z, 1, 0, 0, 0, 0);
    }
}
