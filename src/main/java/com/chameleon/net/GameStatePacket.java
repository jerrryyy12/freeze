package com.chameleon.net;

import com.chameleon.client.CamoEditState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * 서버 → 클라: 게임 페이즈 + 남은 시간(초).
 * phase: 0=로비, 1=숨기, 2=찾기, 3=정답공개.
 * (H 잠금·위장상시, 닉네임 숨김, HUD 타이머, 종료 리셋에 사용)
 */
public class GameStatePacket {
    public final int phase;
    public final int secondsLeft;

    public GameStatePacket(int phase, int secondsLeft) {
        this.phase = phase;
        this.secondsLeft = secondsLeft;
    }

    public static void encode(GameStatePacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.phase);
        buf.writeVarInt(m.secondsLeft);
    }

    public static GameStatePacket decode(FriendlyByteBuf buf) {
        try {
            return new GameStatePacket(buf.readVarInt(), buf.readVarInt());
        } catch (Exception e) {
            return new GameStatePacket(0, 0); // 버전 불일치 등 → 안전 기본값(연결 유지)
        }
    }

    public static void handle(GameStatePacket m, CustomPayloadEvent.Context ctx) {
        ctx.setPacketHandled(true);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            boolean wasActive = CamoEditState.gameActive;
            CamoEditState.phase = m.phase;
            CamoEditState.gameActive = m.phase != 0;              // 진행 중(H 잠금)
            CamoEditState.hideNames = (m.phase == 1 || m.phase == 2); // 숨기/찾기엔 닉네임 숨김
            CamoEditState.gameSecondsLeft = m.secondsLeft;
            if (wasActive && m.phase == 0) {
                CamoEditState.resetForGameEnd(); // 게임 종료 → 그린 것 초기화 + 원래 스킨
            }
        }
    }
}
