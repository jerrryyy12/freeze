package com.chameleon.client;

import com.chameleon.ChameleonMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 이모트 중인 플레이어를 직접 포즈를 잡아 렌더한다.
 * 기본 렌더(RenderPlayerEvent.Pre)를 취소하고, setupAnim 이후 포즈를 덮어쓴 모델을
 * 직접 그린다(스킨 + 위장). 믹스인 없이 동작.
 */
@Mod.EventBusSubscriber(modid = ChameleonMod.MOD_ID, value = Dist.CLIENT)
public class EmoteRenderer {

    // 눕기 보정값(조정용)
    private static final float LIE_LIFT = 0.30f;    // 바닥 위로 들어올림(블록 파묻힘 완화)
    private static final float LIE_CENTER = 0.95f;  // 발 피벗 → 몸 중심을 플레이어 위치로 가운데 정렬

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer player)) return;
        int emote = EmoteState.emoteOf(player.getUUID());
        if (emote < 0) return;
        event.setCanceled(true);

        PlayerRenderer renderer = event.getRenderer();
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource buffer = event.getMultiBufferSource();
        int light = event.getPackedLight();
        float partial = event.getPartialTick();
        float age = player.tickCount + partial;

        // 모델 기본 상태 셋업 후 이모트 포즈 덮어쓰기 (머리는 실제 시선대로 자연스럽게)
        model.young = false;
        model.crouching = false;
        model.riding = false;
        model.attackTime = 0f;
        float bodyYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
        float headYaw = Mth.rotLerp(partial, player.yHeadRotO, player.yHeadRot);
        float headPitch = Mth.lerp(partial, player.xRotO, player.getXRot());
        // 이동 시 다리는 자연스럽게 걷도록 실제 보행 애니메이션 값 사용
        float limbSwing = player.walkAnimation.position(partial);
        float limbAmt = Math.min(1.0f, player.walkAnimation.speed(partial));
        model.setupAnim(player, limbSwing, limbAmt, age, headYaw - bodyYaw, headPitch);
        EmotePoser.apply(model, emote, age);

        // LivingEntityRenderer.render의 변환을 흉내. 앉기/눕기는 추가 변환.
        ps.pushPose();
        float drop = EmotePoser.dropY(emote);
        if (drop != 0f) ps.translate(0f, drop, 0f);           // 앉기: 바닥으로 내림
        ps.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        int lie = EmotePoser.lieAxis(emote);                   // 눕기(사망 쓰러짐과 같은 위치)
        if (lie != 0) ps.translate(0f, LIE_LIFT, 0f);          // 바닥 위로 살짝 들어올림(파묻힘 완화)
        if (lie == 1) ps.mulPose(Axis.ZP.rotationDegrees(90.0F));
        else if (lie == 2) ps.mulPose(Axis.XP.rotationDegrees(90.0F));
        ps.scale(-1f, -1f, 1f);
        float sc = player.getScale();
        if (sc != 1f) ps.scale(sc, sc, sc);
        // 눕기는 발이 피벗이라 몸이 한쪽으로 치우침 → 몸 길이축으로 당겨 가운데 정렬
        ps.translate(0f, lie != 0 ? -1.501f + LIE_CENTER : -1.501f, 0f);

        ResourceLocation skin = renderer.getTextureLocation(player);
        model.renderToBuffer(ps, buffer.getBuffer(model.renderType(skin)),
                light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        ResourceLocation camo = CamoClient.texture(player.getUUID());
        if (camo != null) {
            model.renderToBuffer(ps, buffer.getBuffer(RenderType.entityCutoutNoCull(camo)),
                    light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        }
        ps.popPose();
    }
}
