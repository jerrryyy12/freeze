package com.freeze;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public class FreezeArea {

    private ResourceKey<Level> worldKey;
    private Integer x1, y1, z1;
    private Integer x2, y2, z2;
    private boolean enabled;

    public boolean isConfigured() {
        return worldKey != null
                && x1 != null && y1 != null && z1 != null
                && x2 != null && y2 != null && z2 != null;
    }

    public boolean isEnabled() {
        return enabled && isConfigured();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public ResourceKey<Level> getWorldKey() {
        return worldKey;
    }

    public void setCorner(int index, ServerPlayer player) {
        this.worldKey = ((ServerLevel) player.level()).dimension();
        BlockPos pos = player.blockPosition();
        if (index == 1) {
            x1 = pos.getX(); y1 = pos.getY(); z1 = pos.getZ();
        } else {
            x2 = pos.getX(); y2 = pos.getY(); z2 = pos.getZ();
        }
    }

    public void setCorners(ResourceKey<Level> world, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.worldKey = world;
        this.x1 = x1; this.y1 = y1; this.z1 = z1;
        this.x2 = x2; this.y2 = y2; this.z2 = z2;
    }

    public boolean contains(ServerPlayer player) {
        if (!isConfigured()) return false;
        if (!((ServerLevel) player.level()).dimension().equals(worldKey)) return false;
        // 직사각형(X/Z) 판정, 위아래(Y)는 무제한
        double x = player.getX();
        double z = player.getZ();
        return x >= minX() && x <= maxX() + 1
                && z >= minZ() && z <= maxZ() + 1;
    }

    public int minX() { return Math.min(x1, x2); }
    public int maxX() { return Math.max(x1, x2); }
    public int minY() { return Math.min(y1, y2); }
    public int maxY() { return Math.max(y1, y2); }
    public int minZ() { return Math.min(z1, z2); }
    public int maxZ() { return Math.max(z1, z2); }

    public String describe() {
        if (!isConfigured()) return "설정되지 않음";
        return String.format("[%s] (%d, %d) ~ (%d, %d) / 위아래 무제한 / %s",
                worldKey.identifier(), minX(), minZ(), maxX(), maxZ(),
                enabled ? "활성" : "비활성");
    }
}
