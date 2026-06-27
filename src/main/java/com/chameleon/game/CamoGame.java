package com.chameleon.game;

import com.chameleon.ChameleonItems;
import com.chameleon.net.ChameleonNet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 메차 카멜레온 게임(서버).
 * - 로비: 파란 양털=숨는 사람, 빨간 양털=술래
 * - 시작: 숨는 사람 0.3배+속도2배+1하트+무적(총만 탈락), 술래 정상+총+빨간 발광
 * - 종료: 시간 초과(숨는 사람 승리) 또는 전멸(술래 승리)
 */
public class CamoGame {
    public enum Role { HIDER, SEEKER }

    private static final double HIDER_SCALE = 0.3;
    private static final double HIDER_SPEED = 0.2;
    private static final double NORMAL_SPEED = 0.1;
    private static final String SEEKER_TEAM = "camo_seeker";

    // ---- 샷건 설정 ----
    private static final int SHOTGUN_PELLETS = 12;   // 펠릿 개수
    private static final double SHOTGUN_SPREAD = 0.12; // 콘 반각(라디안, ~7도)
    private static final double SHOTGUN_REACH = 45.0;  // 사거리(근접 샷건)
    private static final int SHOTGUN_COOLDOWN = 16;    // 재장전 쿨다운(틱)

    private static boolean active = false;
    private static int ticksLeft = 0;
    private static int hiderCount = 0;
    private static final Map<UUID, Role> roles = new HashMap<>();
    private static final Map<UUID, GameType> origModes = new HashMap<>();

    public static boolean isActive() { return active; }
    public static Role roleOf(UUID id) { return roles.get(id); }
    public static int secondsLeft() { return ticksLeft / 20; }

    public static int[] start(MinecraftServer server, int seconds) {
        int hiders = 0, seekers = 0;
        ticksLeft = seconds * 20;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Role r = roles.getOrDefault(p.getUUID(), Role.HIDER);
            roles.put(p.getUUID(), r);
            origModes.put(p.getUUID(), p.gameMode.getGameModeForPlayer());
            if (r == Role.SEEKER) {
                seekers++;
                applySeeker(server, p, seconds);
                p.displayClientMessage(Component.literal("§c당신은 술래! 총으로 숨은 사람을 찾아 쏘세요."), false);
            } else {
                hiders++;
                applyHider(p);
                p.displayClientMessage(Component.literal("§b숨는 사람! G로 위장을 칠하고 숨으세요. (총에 맞으면 탈락)"), false);
            }
        }
        hiderCount = hiders;
        active = true;
        ChameleonNet.broadcastGameState(true, seconds);
        return new int[]{hiders, seekers};
    }

    public static void stop(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            try {
                restore(server, p);
            } catch (Exception e) {
                // 한 명 복구 실패가 서버 틱 전체를 죽이지 않도록 방어
                com.chameleon.ChameleonMod.LOGGER.error("플레이어 복구 실패: {}", p.getScoreboardName(), e);
            }
        }
        roles.clear();
        origModes.clear();
        active = false;
        ticksLeft = 0;
        hiderCount = 0;
        ChameleonNet.broadcastGameState(false, 0);
    }

    public static void tick(MinecraftServer server) {
        if (!active) {
            lobbyTick(server);
            return;
        }
        if (ticksLeft > 0) ticksLeft--;
        if (ticksLeft <= 0) {
            announce(server, Component.literal("§b숨는 사람 승리!"), Component.literal("시간 종료 — 살아남았다"));
            stop(server);
            return;
        }
        if (hiderCount > 0 && aliveHiders(server) == 0) {
            announce(server, Component.literal("§c술래 승리!"), Component.literal("숨는 사람 전멸"));
            stop(server);
            return;
        }
        if (ticksLeft % 20 == 0) showTimer(server);
    }

    /**
     * 술래가 샷건을 쏨 → 시선 주변 콘으로 여러 펠릿을 발사,
     * 콘 안에 들어온 숨는 사람을 모두 탈락(관전)시킨다.
     */
    public static void fireGun(ServerPlayer shooter) {
        if (!active || roles.get(shooter.getUUID()) != Role.SEEKER) return;
        MinecraftServer server = shooter.getServer();
        if (server == null) return;
        shooter.getCooldowns().addCooldown(ChameleonItems.GUN.get(), SHOTGUN_COOLDOWN);

        Vec3 eye = shooter.getEyePosition();
        Vec3 look = shooter.getViewVector(1.0f).normalize();
        // 시선에 수직인 기저(우/상) 생성
        Vec3 up0 = Math.abs(look.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = look.cross(up0).normalize();
        Vec3 up = right.cross(look).normalize();

        Set<ServerPlayer> hitTargets = new HashSet<>();
        for (int i = 0; i < SHOTGUN_PELLETS; i++) {
            double ox = (Math.random() * 2 - 1) * SHOTGUN_SPREAD;
            double oy = (Math.random() * 2 - 1) * SHOTGUN_SPREAD;
            Vec3 dir = look.add(right.scale(ox)).add(up.scale(oy)).normalize();
            Vec3 end = eye.add(dir.scale(SHOTGUN_REACH));
            AABB box = shooter.getBoundingBox().expandTowards(dir.scale(SHOTGUN_REACH)).inflate(1.0);
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(shooter, eye, end, box,
                    e -> e instanceof ServerPlayer tp
                            && roles.get(tp.getUUID()) == Role.HIDER && !tp.isSpectator(),
                    SHOTGUN_REACH * SHOTGUN_REACH);
            if (hit != null && hit.getEntity() instanceof ServerPlayer target) {
                hitTargets.add(target);
            }
        }
        // 발사음(샷건 느낌)
        shooter.level().playSound(null, shooter.blockPosition(),
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6f, 1.6f);
        for (ServerPlayer target : hitTargets) {
            eliminate(server, target);
        }
    }

    private static void eliminate(MinecraftServer server, ServerPlayer hider) {
        hider.setGameMode(GameType.SPECTATOR);
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("§c" + hider.getName().getString() + " 발견됨! (탈락)"), false);
    }

    private static void lobbyTick(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Role pad = padRole(p);
            if (pad != null && roles.get(p.getUUID()) != pad) {
                roles.put(p.getUUID(), pad);
                p.displayClientMessage(Component.literal(pad == Role.SEEKER
                        ? "§c[술래]로 선택됨 — /camo game start 시 시작" : "§b[숨는 사람]으로 선택됨"), true);
            }
        }
    }

    private static Role padRole(ServerPlayer p) {
        BlockState below = p.level().getBlockState(p.blockPosition().below());
        if (below.is(Blocks.RED_WOOL)) return Role.SEEKER;
        if (below.is(Blocks.BLUE_WOOL)) return Role.HIDER;
        return null;
    }

    private static int aliveHiders(MinecraftServer server) {
        int n = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (roles.get(p.getUUID()) == Role.HIDER && !p.isSpectator()) n++;
        }
        return n;
    }

    // ---- 역할별 능력치 ----

    /** 숨는 사람: 축소 + 속도2배 + 1하트 + (총만 탈락시키도록) 무적. */
    private static void applyHider(ServerPlayer p) {
        setAttr(p, Attributes.SCALE, HIDER_SCALE);
        setAttr(p, Attributes.MOVEMENT_SPEED, HIDER_SPEED);
        AttributeInstance maxH = p.getAttribute(Attributes.MAX_HEALTH);
        if (maxH != null) maxH.setBaseValue(2.0);
        p.setHealth(2.0f);
        p.setInvulnerable(true);
    }

    /** 술래: 정상 능력치 + 총 + 빨간 발광. */
    private static void applySeeker(MinecraftServer server, ServerPlayer p, int seconds) {
        setAttr(p, Attributes.SCALE, 1.0);
        setAttr(p, Attributes.MOVEMENT_SPEED, NORMAL_SPEED);
        AttributeInstance maxH = p.getAttribute(Attributes.MAX_HEALTH);
        if (maxH != null) maxH.setBaseValue(20.0);
        p.setHealth(20.0f);
        p.setInvulnerable(false);
        p.getInventory().add(new ItemStack(ChameleonItems.GUN.get()));
        p.addEffect(new MobEffectInstance(MobEffects.GLOWING, seconds * 20, 0, false, false));
        ServerScoreboard sb = server.getScoreboard();
        sb.addPlayerToTeam(p.getScoreboardName(), seekerTeam(sb));
    }

    /** 종료 시 복구. */
    private static void restore(MinecraftServer server, ServerPlayer p) {
        setAttr(p, Attributes.SCALE, 1.0);
        setAttr(p, Attributes.MOVEMENT_SPEED, NORMAL_SPEED);
        AttributeInstance maxH = p.getAttribute(Attributes.MAX_HEALTH);
        if (maxH != null) maxH.setBaseValue(20.0);
        p.setHealth(20.0f);
        p.setInvulnerable(false);
        p.removeEffect(MobEffects.GLOWING);
        // 총 제거
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).getItem() == ChameleonItems.GUN.get()) inv.setItem(i, ItemStack.EMPTY);
        }
        // 팀 제거 (그 팀에 실제 속한 경우에만 — 아니면 IllegalStateException으로 서버가 죽음)
        ServerScoreboard sb = server.getScoreboard();
        PlayerTeam t = sb.getPlayerTeam(SEEKER_TEAM);
        if (t != null && t.getPlayers().contains(p.getScoreboardName())) {
            sb.removePlayerFromTeam(p.getScoreboardName(), t);
        }
        // 게임모드 복구(탈락해서 관전이 된 경우 등)
        GameType orig = origModes.get(p.getUUID());
        if (orig != null) p.setGameMode(orig);
    }

    private static PlayerTeam seekerTeam(ServerScoreboard sb) {
        PlayerTeam t = sb.getPlayerTeam(SEEKER_TEAM);
        if (t == null) {
            t = sb.addPlayerTeam(SEEKER_TEAM);
            t.setColor(ChatFormatting.RED);
        }
        return t;
    }

    private static void setAttr(ServerPlayer p, Holder<Attribute> attr, double v) {
        AttributeInstance a = p.getAttribute(attr);
        if (a != null) a.setBaseValue(v);
    }

    // ---- 화면 표시 ----

    private static void showTimer(MinecraftServer server) {
        int s = secondsLeft();
        Component bar = Component.literal(String.format("§e남은 시간 %d:%02d", s / 60, s % 60));
        boolean count = s <= 10 && s >= 1;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.displayClientMessage(bar, true);
            if (count) {
                p.connection.send(new ClientboundSetTitlesAnimationPacket(0, 24, 4));
                p.connection.send(new ClientboundSetTitleTextPacket(
                        Component.literal((s <= 3 ? "§c§l" : "§f§l") + s)));
            }
        }
    }

    private static void announce(MinecraftServer server, Component title, Component sub) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
            p.connection.send(new ClientboundSetSubtitleTextPacket(sub));
            p.connection.send(new ClientboundSetTitleTextPacket(title));
        }
    }
}
