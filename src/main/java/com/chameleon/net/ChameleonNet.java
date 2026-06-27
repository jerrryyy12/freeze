package com.chameleon.net;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

/** Chameleon 네트워크 채널 + 패킷 등록/전송. */
public class ChameleonNet {
    private static final int PROTOCOL = 1;
    public static SimpleChannel CHANNEL;

    public static void register() {
        CHANNEL = ChannelBuilder
                .named(ResourceLocation.fromNamespaceAndPath("chameleon", "main"))
                .networkProtocolVersion(PROTOCOL)
                .acceptedVersions((status, version) -> true)
                .simpleChannel();

        CHANNEL.messageBuilder(CamoSyncPacket.class)
                .encoder(CamoSyncPacket::encode)
                .decoder(CamoSyncPacket::decode)
                .consumerMainThread(CamoSyncPacket::handle)
                .add();

        CHANNEL.messageBuilder(CamoPaintPacket.class)
                .encoder(CamoPaintPacket::encode)
                .decoder(CamoPaintPacket::decode)
                .consumerMainThread(CamoPaintPacket::handle)
                .add();

        CHANNEL.messageBuilder(OpenPaintPacket.class)
                .encoder(OpenPaintPacket::encode)
                .decoder(OpenPaintPacket::decode)
                .consumerMainThread(OpenPaintPacket::handle)
                .add();

        CHANNEL.messageBuilder(GameStatePacket.class)
                .encoder(GameStatePacket::encode)
                .decoder(GameStatePacket::decode)
                .consumerMainThread(GameStatePacket::handle)
                .add();
    }

    /** 서버 → 모든 클라: 게임 진행 여부 + 남은 시간 */
    public static void broadcastGameState(boolean active, int secondsLeft) {
        CHANNEL.send(new GameStatePacket(active, secondsLeft), PacketDistributor.ALL.noArg());
    }

    /** 서버 → 특정 플레이어: 게임 진행 여부(접속 시 동기화) */
    public static void sendGameState(ServerPlayer player, boolean active, int secondsLeft) {
        CHANNEL.send(new GameStatePacket(active, secondsLeft), PacketDistributor.PLAYER.with(player));
    }

    /** 서버 → 모든 클라이언트 */
    public static void broadcast(CamoSyncPacket p) {
        CHANNEL.send(p, PacketDistributor.ALL.noArg());
    }

    /** 서버 → 특정 플레이어 */
    public static void sendTo(ServerPlayer player, CamoSyncPacket p) {
        CHANNEL.send(p, PacketDistributor.PLAYER.with(player));
    }

    /** 서버 → 특정 플레이어: 페인트 화면 열기 */
    public static void sendOpenPaint(ServerPlayer player) {
        CHANNEL.send(new OpenPaintPacket(), PacketDistributor.PLAYER.with(player));
    }

    /** 클라이언트 → 서버: 내가 칠한 텍스처 업로드 */
    public static void sendPaintToServer(CamoPaintPacket p) {
        CHANNEL.send(p, PacketDistributor.SERVER.noArg());
    }
}
