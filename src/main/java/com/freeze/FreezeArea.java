package com.freeze;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

public class FreezeArea {

    private World world;
    private Integer x1, y1, z1;
    private Integer x2, y2, z2;
    private boolean enabled;

    public boolean isConfigured() {
        return world != null
                && x1 != null && y1 != null && z1 != null
                && x2 != null && y2 != null && z2 != null;
    }

    public boolean isEnabled() {
        return enabled && isConfigured();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public World getWorld() {
        return world;
    }

    public void setCorner(int index, Location loc) {
        this.world = loc.getWorld();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        if (index == 1) {
            x1 = x; y1 = y; z1 = z;
        } else {
            x2 = x; y2 = y; z2 = z;
        }
    }

    public void setCorners(World world, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.world = world;
        this.x1 = x1; this.y1 = y1; this.z1 = z1;
        this.x2 = x2; this.y2 = y2; this.z2 = z2;
    }

    public boolean contains(Location loc) {
        if (!isConfigured() || !loc.getWorld().equals(world)) {
            return false;
        }
        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();
        return x >= minX() && x <= maxX() + 1
                && y >= minY() && y <= maxY() + 1
                && z >= minZ() && z <= maxZ() + 1;
    }

    public int minX() { return Math.min(x1, x2); }
    public int maxX() { return Math.max(x1, x2); }
    public int minY() { return Math.min(y1, y2); }
    public int maxY() { return Math.max(y1, y2); }
    public int minZ() { return Math.min(z1, z2); }
    public int maxZ() { return Math.max(z1, z2); }

    public String describe() {
        if (!isConfigured()) {
            return "설정되지 않음";
        }
        return String.format("[%s] (%d,%d,%d) ~ (%d,%d,%d) / %s",
                world.getName(), minX(), minY(), minZ(), maxX(), maxY(), maxZ(),
                enabled ? "활성" : "비활성");
    }

    public void writeToConfig(FileConfiguration config) {
        config.set("area.world", world == null ? null : world.getName());
        config.set("area.x1", x1);
        config.set("area.y1", y1);
        config.set("area.z1", z1);
        config.set("area.x2", x2);
        config.set("area.y2", y2);
        config.set("area.z2", z2);
        config.set("area.enabled", enabled);
    }

    public static FreezeArea fromConfig(FileConfiguration config, Server server) {
        FreezeArea area = new FreezeArea();
        String worldName = config.getString("area.world");
        if (worldName != null) {
            area.world = server.getWorld(worldName);
        }
        area.x1 = readInt(config, "area.x1");
        area.y1 = readInt(config, "area.y1");
        area.z1 = readInt(config, "area.z1");
        area.x2 = readInt(config, "area.x2");
        area.y2 = readInt(config, "area.y2");
        area.z2 = readInt(config, "area.z2");
        area.enabled = config.getBoolean("area.enabled", false);
        return area;
    }

    private static Integer readInt(FileConfiguration config, String path) {
        return config.contains(path) ? config.getInt(path) : null;
    }
}
