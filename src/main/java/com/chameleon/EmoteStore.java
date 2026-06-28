package com.chameleon;

import com.chameleon.net.ChameleonNet;
import com.chameleon.net.EmotePacket;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 서버측: 플레이어별 현재 재생 중인 이모트 id 보관 + 동기화. (-1 = 없음) */
public class EmoteStore {
    private static final Map<UUID, Integer> EMOTES = new HashMap<>();

    /** 이모트 설정(또는 -1로 해제) 후 전체 브로드캐스트. */
    public static void set(UUID id, int emoteId) {
        if (emoteId < 0) EMOTES.remove(id);
        else EMOTES.put(id, emoteId);
        ChameleonNet.broadcast(new EmotePacket(id, emoteId));
    }

    /** 접속자에게 현재 모든 이모트 상태 전송. */
    public static void onLogin(ServerPlayer joiner) {
        for (Map.Entry<UUID, Integer> e : EMOTES.entrySet()) {
            ChameleonNet.sendTo(joiner, new EmotePacket(e.getKey(), e.getValue()));
        }
    }

    /** 퇴장/게임종료 등으로 정리. */
    public static void clear(UUID id) {
        if (EMOTES.remove(id) != null) ChameleonNet.broadcast(new EmotePacket(id, -1));
    }
}
