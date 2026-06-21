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
        // 월드보더와 동일한 정사각형 판정 (위아래는 무제한)
        double x = player.getX();
        double z = player.getZ();
        return Math.abs(x - centerX()) <= half() && Math.abs(z - centerZ()) <= half();
    }

    public int minX() { return Math.min(x1, x2); }
    public int maxX() { return Math.max(x1, x2); }
    public int minY() { return Math.min(y1, y2); }
    public int maxY() { return Math.max(y1, y2); }
    public int minZ() { return Math.min(z1, z2); }
    public int maxZ() { return Math.max(z1, z2); }

    // 월드보더용: 두 모서리에서 정사각형(중심 + 한 변=긴 쪽) 산출
    public double centerX() { return (minX() + maxX() + 1) / 2.0; }
    public double centerZ() { return (minZ() + maxZ() + 1) / 2.0; }
    public double size() {
        double xLen = (maxX() - minX()) + 1;
        double zLen = (maxZ() - minZ()) + 1;
        return Math.max(xLen, zLen);
    }
    public double half() { return size() / 2.0; }

    public String describe() {
        if (!isConfigured()) return "설정되지 않음";
        return String.format("[%s] 중심(%.1f, %.1f) 한 변 %.0f칸 / 위아래 무제한 / %s",
                worldKey.identifier(), centerX(), centerZ(), size(),
                enabled ? "활성" : "비활성");
    }
}
