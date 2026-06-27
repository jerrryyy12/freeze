package com.chameleon.net;

import com.chameleon.client.CamoClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.io.ByteArrayOutputStream;
import java.util.UUID;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * 서버 → 모든 클라이언트: 한 플레이어의 위장 텍스처(64×64 ARGB)를 동기화한다.
 * pixels == null 이면 위장 해제(텍스처 제거).
 */
public class CamoSyncPacket {
    /** 위장 텍스처 한 변 크기. 64=기본 스킨 해상도, 128=2배(더 정밀한 색칠). */
    public static final int SIZE = 128;
    public static final int LEN = SIZE * SIZE;

    public final UUID id;
    public final int[] pixels; // ARGB, 길이 LEN, 또는 null

    public CamoSyncPacket(UUID id, int[] pixels) {
        this.id = id;
        this.pixels = pixels;
    }

    public static void encode(CamoSyncPacket m, FriendlyByteBuf buf) {
        buf.writeUUID(m.id);
        writePixels(buf, m.pixels);
    }

    public static CamoSyncPacket decode(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        return new CamoSyncPacket(id, readPixels(buf));
    }

    /**
     * 픽셀 배열을 zlib 압축해서 전송. (serverbound는 32KB 한도가 있어 압축 필수)
     * 형식: hasData(boolean) [intCount(varint) + 압축바이트(byteArray)]
     */
    public static void writePixels(FriendlyByteBuf buf, int[] px) {
        if (px == null) {
            buf.writeBoolean(false);
            return;
        }
        buf.writeBoolean(true);
        buf.writeVarInt(px.length);
        buf.writeByteArray(deflate(toBytes(px)));
    }

    public static int[] readPixels(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) return null;
        int n = buf.readVarInt();
        if (n <= 0 || n > 256 * 256) return null;
        byte[] comp = buf.readByteArray(1 << 20);
        byte[] raw = inflate(comp, n * 4);
        return raw == null ? null : toInts(raw, n);
    }

    private static byte[] toBytes(int[] px) {
        byte[] b = new byte[px.length * 4];
        for (int i = 0; i < px.length; i++) {
            int v = px[i];
            b[i * 4] = (byte) (v >>> 24);
            b[i * 4 + 1] = (byte) (v >>> 16);
            b[i * 4 + 2] = (byte) (v >>> 8);
            b[i * 4 + 3] = (byte) v;
        }
        return b;
    }

    private static int[] toInts(byte[] b, int n) {
        int[] px = new int[n];
        for (int i = 0; i < n; i++) {
            px[i] = ((b[i * 4] & 0xFF) << 24) | ((b[i * 4 + 1] & 0xFF) << 16)
                    | ((b[i * 4 + 2] & 0xFF) << 8) | (b[i * 4 + 3] & 0xFF);
        }
        return px;
    }

    private static byte[] deflate(byte[] data) {
        Deflater d = new Deflater(Deflater.BEST_SPEED);
        d.setInput(data);
        d.finish();
        ByteArrayOutputStream bos = new ByteArrayOutputStream(Math.max(64, data.length / 4));
        byte[] tmp = new byte[8192];
        while (!d.finished()) {
            int n = d.deflate(tmp);
            bos.write(tmp, 0, n);
        }
        d.end();
        return bos.toByteArray();
    }

    private static byte[] inflate(byte[] data, int expectedLen) {
        Inflater inf = new Inflater();
        inf.setInput(data);
        byte[] out = new byte[expectedLen];
        try {
            int off = 0;
            while (!inf.finished() && off < expectedLen) {
                int n = inf.inflate(out, off, expectedLen - off);
                if (n == 0) break;
                off += n;
            }
            return out;
        } catch (DataFormatException e) {
            return null;
        } finally {
            inf.end();
        }
    }

    /** 클라이언트에서만 실행: 텍스처 적용. (전용 서버에서는 CamoClient를 건드리지 않음) */
    public static void handle(CamoSyncPacket m, CustomPayloadEvent.Context ctx) {
        ctx.setPacketHandled(true);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            CamoClient.apply(m.id, m.pixels);
        }
    }
}
