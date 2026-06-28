package com.chameleon.net;

import com.chameleon.game.CamoGame;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** 클라 → 서버: 숨는 사람이 준비시간에 고른 캐릭터 크기 배율(0.5/0.7/1.0). */
public class ScaleChoicePacket {
    public final float scale;

    public ScaleChoicePacket(float scale) {
        this.scale = scale;
    }

    public static void encode(ScaleChoicePacket m, FriendlyByteBuf buf) {
        buf.writeFloat(m.scale);
    }

    public static ScaleChoicePacket decode(FriendlyByteBuf buf) {
        try {
            return new ScaleChoicePacket(buf.readFloat());
        } catch (Exception e) {
            return new ScaleChoicePacket(0.5f);
        }
    }

    public static void handle(ScaleChoicePacket m, CustomPayloadEvent.Context ctx) {
        ctx.setPacketHandled(true);
        ServerPlayer sp = ctx.getSender();
        if (sp != null) CamoGame.setHiderScale(sp, m.scale);
    }
}
