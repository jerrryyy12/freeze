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

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        AbstractClientPlayer player = event.getEntity();
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

        // 모델 기본 상태 셋업 후 이모트 포즈 덮어쓰기
        model.young = false;
        model.crouching = false;
        model.riding = false;
        model.attackTime = 0f;
        model.setupAnim(player, 0f, 0f, age, 0f, 0f);
        EmotePoser.apply(model, emote, age);

        // LivingEntityRenderer.render의 변환을 흉내(서있는 기준)
        ps.pushPose();
        float bodyYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
        ps.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        ps.scale(-1f, -1f, 1f);
        float sc = player.getScale();
        if (sc != 1f) ps.scale(sc, sc, sc);
        ps.translate(0f, -1.501f, 0f);

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
