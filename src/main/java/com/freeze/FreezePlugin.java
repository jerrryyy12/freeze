package com.freeze;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

public final class FreezePlugin extends JavaPlugin {

    private FreezeArea area;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.area = FreezeArea.fromConfig(getConfig(), getServer());

        FreezeCommand command = new FreezeCommand(this);
        getCommand("freeze").setExecutor(command);
        getCommand("freeze").setTabCompleter(command);

        getServer().getPluginManager().registerEvents(new FreezeListener(this), this);

        getLogger().info("Freeze 플러그인 활성화됨. 영역: " + area.describe());
    }

    @Override
    public void onDisable() {
        if (area != null) {
            area.writeToConfig(getConfig());
            saveConfig();
        }
    }

    public FreezeArea getArea() {
        return area;
    }

    public void setCorner(int index, Location loc) {
        area.setCorner(index, loc);
        persist();
    }

    public void setCorners(World world, int x1, int y1, int z1, int x2, int y2, int z2) {
        area.setCorners(world, x1, y1, z1, x2, y2, z2);
        persist();
    }

    public void setEnabled(boolean enabled) {
        area.setEnabled(enabled);
        persist();
    }

    private void persist() {
        area.writeToConfig(getConfig());
        saveConfig();
    }
}
