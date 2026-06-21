package com.freeze;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FreezeCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB_COMMANDS = Arrays.asList(
            "pos1", "pos2", "set", "on", "off", "info", "show");

    private final FreezePlugin plugin;

    public FreezeCommand(FreezePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§e/freeze <pos1|pos2|set|on|off|info|show>");
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "pos1": return setPos(sender, 1);
            case "pos2": return setPos(sender, 2);
            case "set":  return setCoords(sender, args);
            case "on":   return toggle(sender, true);
            case "off":  return toggle(sender, false);
            case "info": return info(sender);
            case "show": return show(sender);
            default:
                sender.sendMessage("§c알 수 없는 명령어: " + sub);
                return true;
        }
    }

    private boolean setPos(CommandSender sender, int index) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c플레이어만 사용할 수 있습니다.");
            return true;
        }
        plugin.setCorner(index, player.getLocation());
        Location loc = player.getLocation();
        sender.sendMessage(String.format("§a%d번 모서리 설정됨: (%d, %d, %d) @ %s",
                index, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                loc.getWorld().getName()));
        return true;
    }

    private boolean setCoords(CommandSender sender, String[] args) {
        if (args.length < 7) {
            sender.sendMessage("§c사용법: /freeze set <x1> <y1> <z1> <x2> <y2> <z2> [world]");
            return true;
        }
        try {
            int x1 = Integer.parseInt(args[1]);
            int y1 = Integer.parseInt(args[2]);
            int z1 = Integer.parseInt(args[3]);
            int x2 = Integer.parseInt(args[4]);
            int y2 = Integer.parseInt(args[5]);
            int z2 = Integer.parseInt(args[6]);

            World world;
            if (args.length >= 8) {
                world = Bukkit.getWorld(args[7]);
                if (world == null) {
                    sender.sendMessage("§c월드를 찾을 수 없습니다: " + args[7]);
                    return true;
                }
            } else if (sender instanceof Player player) {
                world = player.getWorld();
            } else {
                sender.sendMessage("§c콘솔에서는 월드 이름을 지정해야 합니다.");
                return true;
            }

            plugin.setCorners(world, x1, y1, z1, x2, y2, z2);
            sender.sendMessage("§a영역 설정 완료: " + plugin.getArea().describe());
        } catch (NumberFormatException e) {
            sender.sendMessage("§c좌표는 숫자여야 합니다.");
        }
        return true;
    }

    private boolean toggle(CommandSender sender, boolean on) {
        FreezeArea area = plugin.getArea();
        if (on && !area.isConfigured()) {
            sender.sendMessage("§c영역이 설정되지 않았습니다. 먼저 pos1, pos2 또는 set을 사용하세요.");
            return true;
        }
        plugin.setEnabled(on);
        sender.sendMessage(on ? "§aFreeze 활성화됨." : "§eFreeze 비활성화됨.");
        return true;
    }

    private boolean info(CommandSender sender) {
        sender.sendMessage("§b[Freeze] " + plugin.getArea().describe());
        return true;
    }

    private boolean show(CommandSender sender) {
        FreezeArea area = plugin.getArea();
        if (!area.isConfigured()) {
            sender.sendMessage("§c영역이 설정되지 않았습니다.");
            return true;
        }
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks++ >= 100) { cancel(); return; }
                World w = area.getWorld();
                double minX = area.minX(), maxX = area.maxX() + 1;
                double minY = area.minY(), maxY = area.maxY() + 1;
                double minZ = area.minZ(), maxZ = area.maxZ() + 1;
                double step = 1.0;
                for (double x = minX; x <= maxX; x += step) {
                    spawn(w, x, minY, minZ); spawn(w, x, minY, maxZ);
                    spawn(w, x, maxY, minZ); spawn(w, x, maxY, maxZ);
                }
                for (double y = minY; y <= maxY; y += step) {
                    spawn(w, minX, y, minZ); spawn(w, minX, y, maxZ);
                    spawn(w, maxX, y, minZ); spawn(w, maxX, y, maxZ);
                }
                for (double z = minZ; z <= maxZ; z += step) {
                    spawn(w, minX, minY, z); spawn(w, minX, maxY, z);
                    spawn(w, maxX, minY, z); spawn(w, maxX, maxY, z);
                }
            }
            private void spawn(World w, double x, double y, double z) {
                w.spawnParticle(Particle.END_ROD, x, y, z, 1, 0, 0, 0, 0);
            }
        }.runTaskTimer(plugin, 0L, 10L);
        sender.sendMessage("§a영역 경계를 10초간 표시합니다.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            for (String s : SUB_COMMANDS) {
                if (s.startsWith(args[0].toLowerCase())) out.add(s);
            }
            return out;
        }
        return new ArrayList<>();
    }
}
