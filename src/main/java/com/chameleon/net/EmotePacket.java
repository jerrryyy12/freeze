package com.chameleon.net;

import com.chameleon.EmoteStore;
import com.chameleon.client.EmoteState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.UUID;

/**
 * 이모트 동기화.
 * - 클라 → 서버: id=null, emoteId(=-1 해제). 서버가 보낸 사람으로 저장 후 전체 브로드캐스트.
 * - 서버 → 클라: id=대상 UUID, emoteId. 클라가 그 플레이어의 이모트를 적용.
 */
public class EmotePacket {
    public final UUID id;   // null이면 클라→서버(보낸 사람)
    public final int emoteId; // -1 = 해제

    public EmotePacket(UUID id, int emoteId) {
        this.id = id;
        this.emoteId = emoteId;
    }

    public static void encode(EmotePacket m, FriendlyByteBuf buf) {
        buf.writeBoolean(m.id != null);
        if (m.id != null) buf.writeUUID(m.id);
        buf.writeVarInt(m.emoteId + 1); // -1 허용 → +1로 양수화
    }

    public static EmotePacket decode(FriendlyByteBuf buf) {
        try {
            UUID id = buf.readBoolean() ? buf.readUUID() : null;
            int emoteId = buf.readVarInt() - 1;
            return new EmotePacket(id, emoteId);
        } catch (Exception e) {
            return new EmotePacket(null, -1);
        }
    }

    public static void handle(EmotePacket m, CustomPayloadEvent.Context ctx) {
        ctx.setPacketHandled(true);
        ServerPlayer sp = ctx.getSender();
        if (sp != null) {
            // 서버: 클라가 이모트 요청 → 저장 + 브로드캐스트
            EmoteStore.set(sp.getUUID(), m.emoteId);
        } else if (FMLEnvironment.dist == Dist.CLIENT && m.id != null) {
            // 클라: 적용
            EmoteState.set(m.id, m.emoteId);
        }
    }
}
