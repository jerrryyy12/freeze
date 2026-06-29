package com.chameleon.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 클라이언트: 플레이어별 현재 이모트 id 보관. (-1/없음) */
public class EmoteState {
    private static final Map<UUID, Integer> EMOTES = new HashMap<>();

    public static void set(UUID id, int emoteId) {
        if (emoteId < 0) EMOTES.remove(id);
        else EMOTES.put(id, emoteId);
        // 눕기 등 히트박스 변경을 즉시 반영(크기 재계산)
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            Player p = mc.level.getPlayerByUUID(id);
            if (p != null) p.refreshDimensions();
        }
    }

    /** 그 플레이어의 이모트 id (없으면 -1). */
    public static int emoteOf(UUID id) {
        Integer v = EMOTES.get(id);
        return v == null ? -1 : v;
    }
}
