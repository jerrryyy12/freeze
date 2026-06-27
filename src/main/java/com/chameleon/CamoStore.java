package com.chameleon;

import com.chameleon.net.CamoSyncPacket;
import com.chameleon.net.ChameleonNet;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 서버측: 플레이어별 위장 텍스처 보관 + 동기화. */
public class CamoStore {
    private static final Map<UUID, int[]> PIXELS = new HashMap<>();

    /** 위장 텍스처 설정(또는 null로 해제) 후 전체 브로드캐스트. */
    public static void set(UUID id, int[] px) {
        if (px == null) PIXELS.remove(id);
        else PIXELS.put(id, px);
        ChameleonNet.broadcast(new CamoSyncPacket(id, px));
    }

    /** 새로 접속한 플레이어에게 현재 모든 위장 텍스처를 보내준다. */
    public static void onLogin(ServerPlayer joiner) {
        for (Map.Entry<UUID, int[]> e : PIXELS.entrySet()) {
            ChameleonNet.sendTo(joiner, new CamoSyncPacket(e.getKey(), e.getValue()));
        }
    }
}
