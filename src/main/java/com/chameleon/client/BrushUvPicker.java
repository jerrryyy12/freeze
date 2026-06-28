package com.chameleon.client;

import com.chameleon.ChameleonMod;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

/**
 * 직접 칠하기(B): 내 캐릭터를 "텍셀마다 (u,v)를 색으로 인코딩한 텍스처"로 오프스크린에 한 번 더
 * 렌더해, 커서 밑 픽셀 색에서 스킨 텍셀 좌표를 읽는다. 실제 렌더 포즈(이모트·눕기 등) 그대로라
 * 어떤 포즈에서도 정확히 칠해진다. 조명 왜곡은 B=255 기준으로 정규화해 보정한다.
 */
public final class BrushUvPicker {

    // EmoteRenderer와 동일한 눕기 보정값
    private static final float LIE_LIFT = 0.30f, LIE_CENTER = 0.95f;

    private static ResourceLocation uvTex;
    private static TextureTarget target;

    private BrushUvPicker() {}

    private static void ensureTexture() {
        if (uvTex != null) return;
        NativeImage img = new NativeImage(64, 64, false);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                int r = Math.min(255, x * 4), g = Math.min(255, y * 4), b = 255, a = 255;
                img.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r); // NativeImage는 ABGR
            }
        }
        DynamicTexture dyn = new DynamicTexture(img);
        uvTex = ResourceLocation.fromNamespaceAndPath(ChameleonMod.MOD_ID, "uv_pick");
        Minecraft.getInstance().getTextureManager().register(uvTex, dyn);
    }

    /** 월드 렌더 단계(AFTER_PARTICLES)에서 호출: 내 캐릭터를 UV값으로 오프스크린 렌더. */
    public static void renderPass(float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.player instanceof AbstractClientPlayer player)) return;
        if (!(mc.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer)) return;
        ensureTexture();

        RenderTarget main = mc.getMainRenderTarget();
        int w = main.width, h = main.height;
        if (target == null || target.width != w || target.height != h) {
            if (target != null) target.destroyBuffers();
            target = new TextureTarget(w, h, true, Minecraft.ON_OSX);
            target.setClearColor(0f, 0f, 0f, 0f);
        }
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);

        PlayerModel<AbstractClientPlayer> model = renderer.getModel();
        model.young = false;
        model.crouching = false;
        model.riding = false;
        model.attackTime = 0f;
        float bodyYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
        float headYaw = Mth.rotLerp(partial, player.yHeadRotO, player.yHeadRot);
        float headPitch = Mth.lerp(partial, player.xRotO, player.getXRot());
        float limbSwing = player.walkAnimation.position(partial);
        float limbAmt = Math.min(1.0f, player.walkAnimation.speed(partial));
        float age = player.tickCount + partial;
        model.setupAnim(player, limbSwing, limbAmt, age, headYaw - bodyYaw, headPitch);
        int emote = EmoteState.emoteOf(player.getUUID());
        if (emote >= 0) EmotePoser.apply(model, emote, age);

        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double ex = Mth.lerp(partial, player.xOld, player.getX());
        double ey = Mth.lerp(partial, player.yOld, player.getY());
        double ez = Mth.lerp(partial, player.zOld, player.getZ());

        PoseStack ps = new PoseStack();
        ps.translate(ex - cam.x, ey - cam.y, ez - cam.z);     // 카메라 상대 위치(셰이더 모델뷰=카메라 회전)
        float drop = EmotePoser.dropY(emote);
        if (drop != 0f) ps.translate(0f, drop, 0f);
        ps.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        int lie = EmotePoser.lieAxis(emote);
        if (lie != 0) ps.translate(0f, LIE_LIFT, 0f);
        if (lie == 1) ps.mulPose(Axis.ZP.rotationDegrees(90.0F));
        else if (lie == 2) ps.mulPose(Axis.XP.rotationDegrees(90.0F));
        ps.scale(-1f, -1f, 1f);
        float sc = player.getScale();
        if (sc != 1f) ps.scale(sc, sc, sc);
        ps.translate(0f, lie != 0 ? -1.501f + LIE_CENTER : -1.501f, 0f);

        ByteBufferBuilder bb = new ByteBufferBuilder(2048);
        MultiBufferSource.BufferSource bs = MultiBufferSource.immediate(bb);
        model.renderToBuffer(ps, bs.getBuffer(RenderType.entityCutoutNoCull(uvTex)),
                0xF000F0, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        bs.endBatch();
        bb.close();

        main.bindWrite(true);
    }

    /** 커서(GUI 좌표) 밑 픽셀의 스킨 텍셀 (u,v) [0..63]. 모델 밖이면 null. */
    public static int[] readUV(double guiX, double guiY) {
        if (target == null) return null;
        Minecraft mc = Minecraft.getInstance();
        double gw = mc.getWindow().getGuiScaledWidth(), gh = mc.getWindow().getGuiScaledHeight();
        int px = (int) Math.round(guiX / gw * target.width);
        int py = (int) Math.round(guiY / gh * target.height);
        px = Math.max(0, Math.min(target.width - 1, px));
        py = Math.max(0, Math.min(target.height - 1, py));
        int fy = target.height - 1 - py; // glReadPixels 원점은 좌하단
        ByteBuffer pb = BufferUtils.createByteBuffer(4);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.frameBufferId);
        GL11.glReadPixels(px, fy, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pb);
        mc.getMainRenderTarget().bindWrite(false);
        int r = pb.get(0) & 0xFF, g = pb.get(1) & 0xFF, b = pb.get(2) & 0xFF;
        if (b < 40) return null; // 모델이 없는 곳(빈 픽셀)
        double m = b / 255.0;    // 조명 보정 계수(원래 B=255)
        int u = (int) Math.round((r / m) / 4.0);
        int v = (int) Math.round((g / m) / 4.0);
        return new int[]{Math.max(0, Math.min(63, u)), Math.max(0, Math.min(63, v))};
    }
}
