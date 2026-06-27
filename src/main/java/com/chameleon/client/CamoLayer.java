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
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * 위장 레이어. 플레이어가 웅크리면(Shift) 몸 전체를 위장으로 덮어 그린다.
 *
 * <p>칠한 위장 텍스처가 동기화돼 있으면 그 텍스처로 몸을 그리고(2단계),
 * 없으면 발밑 블록 지도색 단색으로 칠한다(1단계 폴백). 원래 스킨 위에 한 번 더
 * 그려서 덮는 방식이라 모든 클라이언트에 동일하게 보인다.</p>
 */
public class CamoLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private static final ResourceLocation WHITE =
            ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    public CamoLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                       AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!player.isCrouching()) return;

        // 2단계: 칠한 위장 텍스처가 있으면 그걸로 몸 전체를 그린다(틴트 없음).
        ResourceLocation camo = CamoClient.texture(player.getUUID());
        if (camo != null) {
            VertexConsumer vcTex = buffer.getBuffer(RenderType.entityCutoutNoCull(camo));
            this.getParentModel().renderToBuffer(poseStack, vcTex, packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
            return;
        }

        // 1단계 폴백: 발밑 블록 지도색 단색.
        BlockPos below = player.blockPosition().below();
        BlockState state = player.level().getBlockState(below);
        MapColor mapColor = state.getMapColor(player.level(), below);
        int color = 0xFF000000 | (mapColor.col & 0xFFFFFF);

        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        this.getParentModel().renderToBuffer(poseStack, vc, packedLight, OverlayTexture.NO_OVERLAY, color);
    }
}
