package com.chameleon.net;

import com.chameleon.client.CamoEditState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** 서버 → 클라: 게임 진행 여부(H 잠금 등에 사용). */
public class GameStatePacket {
    public final boolean active;

    public GameStatePacket(boolean active) {
        this.active = active;
    }

    public static void encode(GameStatePacket m, FriendlyByteBuf buf) {
        buf.writeBoolean(m.active);
    }

    public static GameStatePacket decode(FriendlyByteBuf buf) {
        return new GameStatePacket(buf.readBoolean());
    }

    public static void handle(GameStatePacket m, CustomPayloadEvent.Context ctx) {
        ctx.setPacketHandled(true);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            CamoEditState.gameActive = m.active;
        }
    }
}
