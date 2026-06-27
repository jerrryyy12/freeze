package com.chameleon.game;

import com.chameleon.ChameleonItems;
import com.chameleon.CamoStore;
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
 * 메차 카멜레온 게임(서버). 페이즈: 로비 → 숨기(3분) → 찾기 → 정답공개(30초).
 * - 로비: 파란 양털=숨는 사람, 빨간 양털=술래
 * - 숨기: 숨는 사람 0.5배+속도2배+1하트+무적, 술래 2배+총+발광 + (실명·고정으로 대기)
 * - 찾기: 술래 풀림, 총으로 숨은 사람 탈락
 * - 공개: 살아남은 숨는 사람 발광+고정+위장유지(닉네임 표시) 30초 → 전체 복구
 */
public class CamoGame {
    public enum Role { HIDER, SEEKER }
    public enum Phase { LOBBY, HIDE, SEEK, REVEAL }

    private static final double HIDER_SCALE = 0.5;
    private static final double SEEKER_SCALE = 2.0;
    private static final double HIDER_SPEED = 0.2;
    private static final double NORMAL_SPEED = 0.1;
    private static final String SEEKER_TEAM = "camo_seeker";

    // 커스텀 설정(명령어로 변경) — 숨기/공개/기본 찾기 시간(초)
    private static int hideSeconds = 180;
    private static int revealSeconds = 30;
    private static int defaultSeekSeconds = 300;
    private static final int GLOW_FOREVER = 1_000_000; // 게임 내내 발광

    public static void setHideSeconds(int s) { hideSeconds = Math.max(0, s); }
    public static void setRevealSeconds(int s) { revealSeconds = Math.max(0, s); }
    public static void setDefaultSeekSeconds(int s) { defaultSeekSeconds = Math.max(1, s); }
    public static int getHideSeconds() { return hideSeconds; }
    public static int getRevealSeconds() { return revealSeconds; }
    public static int getDefaultSeekSeconds() { return defaultSeekSeconds; }

    // ---- 샷건 설정 ----
    private static final int SHOTGUN_PELLETS = 12;
    private static final double SHOTGUN_SPREAD = 0.12;
    private static final double SHOTGUN_REACH = 45.0;
    private static final int SHOTGUN_COOLDOWN = 16;

    private static Phase phase = Phase.LOBBY;
    private static int phaseTicks = 0;
    private static int seekDurationTicks = 0;
    private static int hiderCount = 0;
    private static final Map<UUID, Role> roles = new HashMap<>();
    private static final Map<UUID, GameType> origModes = new HashMap<>();

    public static boolean isActive() { return phase != Phase.LOBBY; }
    public static int phaseId() { return phase.ordinal(); }
    public static Role roleOf(UUID id) { return roles.get(id); }
    public static int secondsLeft() { return phaseTicks / 20; }

    public static int[] start(MinecraftServer server, int seekSeconds) {
        int hiders = 0, seekers = 0;
        seekDurationTicks = seekSeconds * 20;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Role r = roles.getOrDefault(p.getUUID(), Role.HIDER);
            roles.put(p.getUUID(), r);
            origModes.put(p.getUUID(), p.gameMode.getGameModeForPlayer());
            if (r == Role.SEEKER) {
                seekers++;
                applySeeker(server, p);
                freezeSeekerForHide(p);
                p.displayClientMessage(Component.literal("§c당신은 술래! 숨는 시간 동안 대기하세요 (실명)"), false);
            } else {
                hiders++;
                applyHider(p);
                p.displayClientMessage(Component.literal("§b숨는 사람! 3분 안에 위장(G)하고 숨으세요. (총에 맞으면 탈락)"), false);
            }
        }
        hiderCount = hiders;
        phase = Phase.HIDE;
        phaseTicks = hideSeconds * 20;
        ChameleonNet.broadcastGameState(phaseId(), hideSeconds);
        announce(server, Component.literal("§b숨는 시간!"),
                Component.literal(hideSeconds + "초 안에 숨으세요"));
        return new int[]{hiders, seekers};
    }

    public static void stop(MinecraftServer server) {
        fullStop(server);
    }

    private static void fullStop(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            try {
                restore(server, p);
            } catch (Exception e) {
                com.chameleon.ChameleonMod.LOGGER.error("플레이어 복구 실패: {}", p.getScoreboardName(), e);
            }
        }
        roles.clear();
        origModes.clear();
        phase = Phase.LOBBY;
        phaseTicks = 0;
        hiderCount = 0;
        ChameleonNet.broadcastGameState(phaseId(), 0);
    }

    public static void tick(MinecraftServer server) {
        if (phase == Phase.LOBBY) {
            lobbyTick(server);
            return;
        }
        if (phaseTicks > 0) phaseTicks--;
        switch (phase) {
            case HIDE -> {
                if (phaseTicks % 20 == 0) showTimer(server, "§b숨는 시간", true);
                if (phaseTicks <= 0) startSeek(server);
            }
            case SEEK -> {
                if (phaseTicks <= 0) { startReveal(server, true); return; }
                if (hiderCount > 0 && aliveHiders(server) == 0) { startReveal(server, false); return; }
                if (phaseTicks % 20 == 0) showTimer(server, "§e남은 시간", true);
            }
            case REVEAL -> {
                if (phaseTicks % 20 == 0) showTimer(server, "§a정답 공개", false);
                if (phaseTicks <= 0) fullStop(server);
            }
            default -> {}
        }
    }

    /** 숨는 시간 종료 → 술래 풀고 찾기 시작. */
    private static void startSeek(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (roles.get(p.getUUID()) == Role.SEEKER) unfreezeSeeker(p);
        }
        phase = Phase.SEEK;
        phaseTicks = seekDurationTicks;
        ChameleonNet.broadcastGameState(phaseId(), seekDurationTicks / 20);
        announce(server, Component.literal("§c술래 출발!"), Component.literal("숨은 사람을 찾아라"));
    }

    /** 게임 종료 → 정답 공개(살아남은 숨는 사람 발광+고정+위장유지). */
    private static void startReveal(MinecraftServer server, boolean hidersWon) {
        Component title = hidersWon ? Component.literal("§b숨는 사람 승리!") : Component.literal("§c술래 승리!");
        announce(server, title, Component.literal("정답 공개 — 4번=자유 시점"));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (roles.get(p.getUUID()) == Role.HIDER && !p.isSpectator()) {
                setAttr(p, Attributes.MOVEMENT_SPEED, 0.0); // 그 자리에 고정(정답)
                p.setDeltaMovement(0, 0, 0);
                p.addEffect(new MobEffectInstance(MobEffects.GLOWING, revealSeconds * 20, 0, false, false));
                p.setInvulnerable(true);
            }
        }
        phase = Phase.REVEAL;
        phaseTicks = revealSeconds * 20;
        ChameleonNet.broadcastGameState(phaseId(), revealSeconds);
    }

    /**
     * 술래가 샷건을 쏨(찾기 페이즈에서만) → 시선 콘으로 펠릿 발사,
     * 콘 안에 들어온 숨는 사람을 모두 탈락(관전)시킨다.
     */
    public static void fireGun(ServerPlayer shooter) {
        if (phase != Phase.SEEK || roles.get(shooter.getUUID()) != Role.SEEKER) return;
        MinecraftServer server = shooter.getServer();
        if (server == null) return;
        shooter.getCooldowns().addCooldown(ChameleonItems.GUN.get(), SHOTGUN_COOLDOWN);

        Vec3 eye = shooter.getEyePosition();
        Vec3 look = shooter.getViewVector(1.0f).normalize();
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

    /** 숨는 사람: 0.5배 + 속도2배 + 1하트 + (총만 탈락) 무적. */
    private static void applyHider(ServerPlayer p) {
        setAttr(p, Attributes.SCALE, HIDER_SCALE);
        setAttr(p, Attributes.MOVEMENT_SPEED, HIDER_SPEED);
        AttributeInstance maxH = p.getAttribute(Attributes.MAX_HEALTH);
        if (maxH != null) maxH.setBaseValue(2.0);
        p.setHealth(2.0f);
        p.setInvulnerable(true);
    }

    /** 술래: 2배 크기 + 총 + 빨간 발광. */
    private static void applySeeker(MinecraftServer server, ServerPlayer p) {
        setAttr(p, Attributes.SCALE, SEEKER_SCALE);
        setAttr(p, Attributes.MOVEMENT_SPEED, NORMAL_SPEED);
        AttributeInstance maxH = p.getAttribute(Attributes.MAX_HEALTH);
        if (maxH != null) maxH.setBaseValue(20.0);
        p.setHealth(20.0f);
        p.setInvulnerable(false);
        p.getInventory().add(new ItemStack(ChameleonItems.GUN.get()));
        p.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_FOREVER, 0, false, false));
        ServerScoreboard sb = server.getScoreboard();
        sb.addPlayerToTeam(p.getScoreboardName(), seekerTeam(sb));
    }

    /** 숨는 시간 동안 술래를 묶고 실명시킨다. */
    private static void freezeSeekerForHide(ServerPlayer p) {
        setAttr(p, Attributes.MOVEMENT_SPEED, 0.0);
        p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, hideSeconds * 20 + 20, 0, false, false, true));
    }

    /** 숨는 시간 종료 → 술래 풀어줌. */
    private static void unfreezeSeeker(ServerPlayer p) {
        setAttr(p, Attributes.MOVEMENT_SPEED, NORMAL_SPEED);
        p.removeEffect(MobEffects.BLINDNESS);
    }

    /** 종료 시 복구. */
    private static void restore(MinecraftServer server, ServerPlayer p) {
        CamoStore.set(p.getUUID(), null); // 위장 텍스처 해제(원래 스킨)
        setAttr(p, Attributes.SCALE, 1.0);
        setAttr(p, Attributes.MOVEMENT_SPEED, NORMAL_SPEED);
        AttributeInstance maxH = p.getAttribute(Attributes.MAX_HEALTH);
        if (maxH != null) maxH.setBaseValue(20.0);
        p.setHealth(20.0f);
        p.setInvulnerable(false);
        p.removeEffect(MobEffects.GLOWING);
        p.removeEffect(MobEffects.BLINDNESS);
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).getItem() == ChameleonItems.GUN.get()) inv.setItem(i, ItemStack.EMPTY);
        }
        ServerScoreboard sb = server.getScoreboard();
        PlayerTeam t = sb.getPlayerTeam(SEEKER_TEAM);
        if (t != null && t.getPlayers().contains(p.getScoreboardName())) {
            sb.removePlayerFromTeam(p.getScoreboardName(), t);
        }
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

    private static void showTimer(MinecraftServer server, String label, boolean bigCountdown) {
        int s = secondsLeft();
        Component bar = Component.literal(String.format("%s %d:%02d", label, s / 60, s % 60));
        boolean count = bigCountdown && s <= 10 && s >= 1;
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
