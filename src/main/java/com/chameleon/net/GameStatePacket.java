package com.chameleon.net;

import com.chameleon.client.CamoEditState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** 서버 → 클라: 게임 진행 여부 + 남은 시간(초). (H 잠금, HUD 타이머에 사용) */
public class GameStatePacket {
    public final boolean active;
    public final int secondsLeft;

    public GameStatePacket(boolean active, int secondsLeft) {
        this.active = active;
        this.secondsLeft = secondsLeft;
    }

    public static void encode(GameStatePacket m, FriendlyByteBuf buf) {
        buf.writeBoolean(m.active);
        buf.writeVarInt(m.secondsLeft);
    }

    public static GameStatePacket decode(FriendlyByteBuf buf) {
        try {
            return new GameStatePacket(buf.readBoolean(), buf.readVarInt());
        } catch (Exception e) {
            return new GameStatePacket(false, 0); // 버전 불일치 등 → 안전 기본값(연결 유지)
        }
    }

    public static void handle(GameStatePacket m, CustomPayloadEvent.Context ctx) {
        ctx.setPacketHandled(true);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            CamoEditState.gameActive = m.active;
            CamoEditState.gameSecondsLeft = m.secondsLeft;
        }
    }
}
