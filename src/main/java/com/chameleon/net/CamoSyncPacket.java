package com.chameleon.net;

import com.chameleon.client.CamoClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.UUID;

/**
 * 서버 → 모든 클라이언트: 한 플레이어의 위장 텍스처(64×64 ARGB)를 동기화한다.
 * pixels == null 이면 위장 해제(텍스처 제거).
 */
public class CamoSyncPacket {
    public static final int SIZE = 64;
    public static final int LEN = SIZE * SIZE;

    public final UUID id;
    public final int[] pixels; // ARGB, 길이 LEN, 또는 null

    public CamoSyncPacket(UUID id, int[] pixels) {
        this.id = id;
        this.pixels = pixels;
    }

    public static void encode(CamoSyncPacket m, FriendlyByteBuf buf) {
        buf.writeUUID(m.id);
        if (m.pixels == null) {
            buf.writeBoolean(false);
        } else {
            buf.writeBoolean(true);
            for (int i = 0; i < LEN; i++) buf.writeInt(m.pixels[i]);
        }
    }

    public static CamoSyncPacket decode(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        if (!buf.readBoolean()) return new CamoSyncPacket(id, null);
        int[] px = new int[LEN];
        for (int i = 0; i < LEN; i++) px[i] = buf.readInt();
        return new CamoSyncPacket(id, px);
    }

    /** 클라이언트에서만 실행: 텍스처 적용. (전용 서버에서는 CamoClient를 건드리지 않음) */
    public static void handle(CamoSyncPacket m, CustomPayloadEvent.Context ctx) {
        ctx.setPacketHandled(true);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            CamoClient.apply(m.id, m.pixels);
        }
    }
}
