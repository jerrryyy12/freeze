package com.chameleon.net;

import com.chameleon.client.CamoEditState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * 서버 → 클라(플레이어별): 게임 페이즈 + 남은 시간(초) + 내 역할.
 * phase: 0=로비, 1=준비, 2=숨기, 3=찾기, 4=정답공개.  role: 0=없음/관전, 1=숨는사람, 2=술래.
 * (H 잠금·위장상시, 닉네임 숨김, HUD 타이머, 종료 리셋, 술래 자유시점 차단, 크기선택 팝업에 사용)
 */
public class GameStatePacket {
    public final int phase;
    public final int secondsLeft;
    public final int role;

    public GameStatePacket(int phase, int secondsLeft, int role) {
        this.phase = phase;
        this.secondsLeft = secondsLeft;
        this.role = role;
    }

    public static void encode(GameStatePacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.phase);
        buf.writeVarInt(m.secondsLeft);
        buf.writeVarInt(m.role);
    }

    public static GameStatePacket decode(FriendlyByteBuf buf) {
        try {
            return new GameStatePacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        } catch (Exception e) {
            return new GameStatePacket(0, 0, 0); // 버전 불일치 등 → 안전 기본값(연결 유지)
        }
    }

    public static void handle(GameStatePacket m, CustomPayloadEvent.Context ctx) {
        ctx.setPacketHandled(true);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            int prevPhase = CamoEditState.phase;
            boolean wasActive = CamoEditState.gameActive;
            CamoEditState.phase = m.phase;
            CamoEditState.gameActive = m.phase != 0;
            CamoEditState.hideNames = (m.phase >= 1 && m.phase <= 3); // 준비/숨기/찾기엔 닉네임 숨김
            CamoEditState.gameSecondsLeft = m.secondsLeft;
            CamoEditState.localRole = m.role;
            if (wasActive && m.phase == 0) {
                CamoEditState.resetForGameEnd(); // 게임 종료 → 그린 것 초기화 + 원래 스킨
            }
            // 준비 페이즈 진입 시 숨는 사람에게 크기 선택 팝업
            if (m.phase == 1 && prevPhase != 1 && m.role == 1) {
                com.chameleon.client.ScaleChooseScreen.open();
            }
        }
    }
}
