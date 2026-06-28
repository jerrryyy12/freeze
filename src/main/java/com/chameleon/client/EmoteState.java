package com.chameleon.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 클라이언트: 플레이어별 현재 이모트 id 보관. (-1/없음) */
public class EmoteState {
    private static final Map<UUID, Integer> EMOTES = new HashMap<>();

    public static void set(UUID id, int emoteId) {
        if (emoteId < 0) EMOTES.remove(id);
        else EMOTES.put(id, emoteId);
    }

    /** 그 플레이어의 이모트 id (없으면 -1). */
    public static int emoteOf(UUID id) {
        Integer v = EMOTES.get(id);
        return v == null ? -1 : v;
    }
}
