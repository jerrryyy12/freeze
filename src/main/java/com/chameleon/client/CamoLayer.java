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
 * 위장 레이어. 플레이어가 웅크리면(Shift) 발밑 블록의 지도 색으로 몸 전체를 단색으로 칠한다.
 *
 * <p>플레이어 모델 전체를 흰색 텍스처에 단색 틴트로 다시 그려서 원래 스킨을 덮는다.
 * 웅크림/위치는 마인크래프트가 모든 클라이언트로 동기화하므로, 별도 네트워킹 없이도
 * 같은 색이 모두에게 동일하게 보인다. (1단계 렌더링 검증용)</p>
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

        BlockPos below = player.blockPosition().below();
        BlockState state = player.level().getBlockState(below);
        MapColor mapColor = state.getMapColor(player.level(), below);
        int color = 0xFF000000 | (mapColor.col & 0xFFFFFF);

        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        this.getParentModel().renderToBuffer(poseStack, vc, packedLight, OverlayTexture.NO_OVERLAY, color);
    }
}
