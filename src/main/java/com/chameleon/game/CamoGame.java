package com.chameleon.game;

import com.chameleon.net.ChameleonNet;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 메차 카멜레온 게임 상태(서버).
 * - 로비: 파란 양털 위 = 숨는 사람(HIDER), 빨간 양털 위 = 술래(SEEKER)
 * - 시작: 숨는 사람은 0.4배 축소 + 이동속도 2배 + 1하트(한 방 사망), 술래는 정상
 * - 종료: 시간 초과 또는 숨는 사람 전멸
 */
public class CamoGame {
    public enum Role { HIDER, SEEKER }

    private static final double HIDER_SCALE = 0.4;
    private static final double HIDER_SPEED = 0.2;  // 기본 0.1의 2배
    private static final double NORMAL_SPEED = 0.1;

    private static boolean active = false;
    private static int ticksLeft = 0;
    private static int hiderCount = 0;
    private static final Map<UUID, Role> roles = new HashMap<>();

    public static boolean isActive() {
        return active;
    }

    public static Role roleOf(UUID id) {
        return roles.get(id);
    }

    public static int secondsLeft() {
        return ticksLeft / 20;
    }

    /** 게임 시작. 반환: {숨는사람 수, 술래 수} (로비에서 정해진 역할 사용) */
    public static int[] start(MinecraftServer server, int seconds) {
        int hiders = 0, seekers = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Role r = roles.getOrDefault(p.getUUID(), Role.HIDER);
            roles.put(p.getUUID(), r);
            if (r == Role.SEEKER) {
                seekers++;
                applyNormal(p);
                p.displayClientMessage(Component.literal("§c당신은 술래! 숨은 사람을 찾아 처치하세요."), false);
            } else {
                hiders++;
                applyHider(p);
                p.displayClientMessage(Component.literal("§b숨는 사람! G로 위장을 칠하고 숨으세요. (한 방에 죽음 주의)"), false);
            }
        }
        hiderCount = hiders;
        ticksLeft = seconds * 20;
        active = true;
        ChameleonNet.broadcastGameState(true);
        return new int[]{hiders, seekers};
    }

    /** 게임 종료: 모든 능력치 복구. */
    public static void stop(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            applyNormal(p);
        }
        roles.clear();
        active = false;
        ticksLeft = 0;
        hiderCount = 0;
        ChameleonNet.broadcastGameState(false);
    }

    /** 매 서버 틱: (대기 중) 로비 역할배정 / (진행 중) 타이머 + 종료조건. */
    public static void tick(MinecraftServer server) {
        if (!active) {
            lobbyTick(server);
            return;
        }
        if (ticksLeft > 0) ticksLeft--;
        if (ticksLeft <= 0) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("§e시간 종료! 숨는 사람들이 살아남았습니다."), false);
            stop(server);
            return;
        }
        if (hiderCount > 0 && aliveHiders(server) == 0) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("§c숨는 사람 전멸! 술래 승리."), false);
            stop(server);
        }
    }

    /** 살아있는(관전 아닌) 숨는 사람 수. */
    private static int aliveHiders(MinecraftServer server) {
        int n = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (roles.get(p.getUUID()) == Role.HIDER && !p.isSpectator()) n++;
        }
        return n;
    }

    /** 대기 중 로비: 양털을 밟는 즉시 역할 배정(변경 시 알림). */
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

    /** 발밑 양털 → 역할(아니면 null = 선택 유지). */
    private static Role padRole(ServerPlayer p) {
        BlockState below = p.level().getBlockState(p.blockPosition().below());
        if (below.is(Blocks.RED_WOOL)) return Role.SEEKER;
        if (below.is(Blocks.BLUE_WOOL)) return Role.HIDER;
        return null;
    }

    /** 숨는 사람: 축소 + 속도2배 + 1하트. */
    private static void applyHider(ServerPlayer p) {
        setAttr(p, Attributes.SCALE, HIDER_SCALE);
        setAttr(p, Attributes.MOVEMENT_SPEED, HIDER_SPEED);
        AttributeInstance maxH = p.getAttribute(Attributes.MAX_HEALTH);
        if (maxH != null) maxH.setBaseValue(2.0); // 1하트
        p.setHealth(2.0f);
    }

    /** 정상 복구(또는 술래). */
    private static void applyNormal(ServerPlayer p) {
        setAttr(p, Attributes.SCALE, 1.0);
        setAttr(p, Attributes.MOVEMENT_SPEED, NORMAL_SPEED);
        AttributeInstance maxH = p.getAttribute(Attributes.MAX_HEALTH);
        if (maxH != null) maxH.setBaseValue(20.0);
        p.setHealth(20.0f);
    }

    private static void setAttr(ServerPlayer p, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, double v) {
        AttributeInstance a = p.getAttribute(attr);
        if (a != null) a.setBaseValue(v);
    }
}
