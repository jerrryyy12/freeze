package com.chameleon.net;

import com.chameleon.CamoStore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

/**
 * 클라이언트 → 서버: 내가 칠한 위장 텍스처를 서버에 보낸다.
 * 서버는 보낸 사람 UUID로 저장하고 모든 클라이언트에 브로드캐스트한다.
 */
public class CamoPaintPacket {
    public final int[] pixels; // ARGB, 길이 CamoSyncPacket.LEN, 또는 null(해제)

    public CamoPaintPacket(int[] pixels) {
        this.pixels = pixels;
    }

    public static void encode(CamoPaintPacket m, FriendlyByteBuf buf) {
        CamoSyncPacket.writePixels(buf, m.pixels);
    }

    public static CamoPaintPacket decode(FriendlyByteBuf buf) {
        return new CamoPaintPacket(CamoSyncPacket.readPixels(buf));
    }

    public static void handle(CamoPaintPacket m, CustomPayloadEvent.Context ctx) {
        ctx.setPacketHandled(true);
        ServerPlayer sp = ctx.getSender();
        if (sp != null) {
            CamoStore.set(sp.getUUID(), m.pixels);
        }
    }
}
