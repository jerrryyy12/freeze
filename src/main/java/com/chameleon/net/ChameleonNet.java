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
    }

    /** 모든 클라이언트에게 전송 */
    public static void broadcast(CamoSyncPacket p) {
        CHANNEL.send(p, PacketDistributor.ALL.noArg());
    }

    /** 특정 플레이어에게 전송 */
    public static void sendTo(ServerPlayer player, CamoSyncPacket p) {
        CHANNEL.send(p, PacketDistributor.PLAYER.with(player));
    }
}
