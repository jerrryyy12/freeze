package com.chameleon.client;

import com.chameleon.net.CamoSyncPacket;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 클라이언트측: 플레이어별 위장 텍스처를 DynamicTexture로 관리한다.
 * 서버에서 받은 ARGB 픽셀 배열을 NativeImage(ABGR)로 변환해 업로드한다.
 */
public class CamoClient {
    private static final int SIZE = CamoSyncPacket.SIZE;

    private static final Map<UUID, int[]> PIXELS = new HashMap<>();
    private static final Map<UUID, DynamicTexture> DYN = new HashMap<>();
    private static final Map<UUID, ResourceLocation> TEX = new HashMap<>();

    /** 위장 텍스처 적용(px == null 이면 해제). 클라이언트 메인 스레드에서 호출됨. */
    public static void apply(UUID id, int[] px) {
        if (px == null) {
            remove(id);
            return;
        }
        if (px.length != SIZE * SIZE) return; // 크기 안 맞으면 무시(버전 차이 방어)
        PIXELS.put(id, px);

        DynamicTexture dt = DYN.get(id);
        NativeImage img;
        if (dt == null) {
            img = new NativeImage(SIZE, SIZE, false);
            dt = new DynamicTexture(img);
            ResourceLocation rl = ResourceLocation.fromNamespaceAndPath("chameleon", "camo/" + id);
            Minecraft.getInstance().getTextureManager().register(rl, dt);
            DYN.put(id, dt);
            TEX.put(id, rl);
        } else {
            img = dt.getPixels();
            if (img == null) return;
        }

        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                img.setPixelRGBA(x, y, argbToAbgr(px[y * SIZE + x]));
            }
        }
        dt.upload();
    }

    /** 렌더에 쓸 텍스처 위치(없으면 null). */
    public static ResourceLocation texture(UUID id) {
        return TEX.get(id);
    }

    /** 현재 위장 픽셀(없으면 null). 페인트 화면 초기값으로 사용. */
    public static int[] getPixels(UUID id) {
        return PIXELS.get(id);
    }

    private static void remove(UUID id) {
        PIXELS.remove(id);
        DYN.remove(id);
        ResourceLocation rl = TEX.remove(id);
        if (rl != null) Minecraft.getInstance().getTextureManager().release(rl);
    }

    /** 0xAARRGGBB → 0xAABBGGRR (NativeImage는 ABGR 순서). */
    private static int argbToAbgr(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }
}
