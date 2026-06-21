package com.freeze;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class FreezeListener implements Listener {

    private final FreezePlugin plugin;

    public FreezeListener(FreezePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        check(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        check(event.getPlayer());
    }

    private void check(Player player) {
        FreezeArea area = plugin.getArea();
        if (!area.isEnabled()) return;
        if (player.hasPermission("freeze.bypass")) return;

        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) return;

        if (!area.contains(player.getLocation())) {
            player.setLastDamageCause(new EntityDamageEvent(
                    player, EntityDamageEvent.DamageCause.CUSTOM, player.getHealth()));
            player.setHealth(0.0);
        }
    }
}
