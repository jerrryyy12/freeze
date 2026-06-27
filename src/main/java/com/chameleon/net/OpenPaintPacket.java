package com.chameleon.net;

import com.chameleon.client.PaintScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** 서버 → 클라이언트: 페인트 화면을 연다. (/camo paint 로 트리거) */
public class OpenPaintPacket {

    public OpenPaintPacket() {}

    public static void encode(OpenPaintPacket m, FriendlyByteBuf buf) {}

    public static OpenPaintPacket decode(FriendlyByteBuf buf) {
        return new OpenPaintPacket();
    }

    public static void handle(OpenPaintPacket m, CustomPayloadEvent.Context ctx) {
        ctx.setPacketHandled(true);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            PaintScreen.open();
        }
    }
}
