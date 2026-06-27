package com.chameleon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * 위장 레이어. 칠한 위장 텍스처가 있으면 그 텍스처로 몸 전체를 덮어 그린다(웅크림 불필요).
 * 원래 스킨 위에 한 번 더 그려서 덮는 방식이라 모든 클라이언트에 동일하게 보인다.
 */
public class CamoLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public CamoLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                       AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        ResourceLocation camo = CamoClient.texture(player.getUUID());
        if (camo == null) return; // 칠한 위장이 없으면 평소 스킨 그대로

        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(camo));
        this.getParentModel().renderToBuffer(poseStack, vc, packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
    }
}
