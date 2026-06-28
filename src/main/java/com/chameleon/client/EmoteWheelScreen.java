package com.chameleon.client;

import com.chameleon.net.ChameleonNet;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * 이모트 선택 휠(R). 화면 중앙 원형 메뉴에서 각 이모트의 "실제 포즈"를 작은 3D 피규어로
 * 그려 보여준다(이름 대신 그림). 마우스 방향으로 고르고 좌클릭으로 재생,
 * 가운데(데드존)에서 클릭하거나 ESC로 취소(끄기).
 */
public class EmoteWheelScreen extends Screen {

    private static final int RING = 92;       // 피규어 배치 반지름
    private static final int DEADZONE = 30;    // 가운데 끄기 영역
    private static final int FIGURE = 22;      // 기본 피규어 스케일
    private static final int FIGURE_HOVER = 30; // 가리킨 피규어 스케일(강조)
    private int cx, cy;
    private int hover = -1;

    public EmoteWheelScreen() {
        super(Component.literal("이모트"));
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new EmoteWheelScreen());
    }

    @Override
    protected void init() {
        cx = this.width / 2;
        cy = this.height / 2;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0x88000000); // 살짝 어둡게
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        hover = hoveredSlot(mouseX, mouseY);

        Minecraft mc = Minecraft.getInstance();
        float age = (mc.player != null) ? mc.player.tickCount + partialTick : 0f;

        int n = EmotePoser.COUNT;
        for (int i = 0; i < n; i++) {
            double ang = Math.toRadians(i * (360.0 / n) - 90.0); // 0번=위, 시계방향
            int lx = cx + (int) (Math.cos(ang) * RING);
            int ly = cy + (int) (Math.sin(ang) * RING);
            boolean on = (i == hover);
            int r = on ? 27 : 23;
            // 슬롯 배경 원판
            g.fill(lx - r, ly - r, lx + r, ly + r, on ? 0xFF2BB6FF : 0x99151515);
            renderFigure(g, i, lx, ly, on ? FIGURE_HOVER : FIGURE, age);
        }

        // 가운데: 끄기 표시
        boolean center = (hover < 0);
        g.drawCenteredString(this.font, "§c끄기", cx, cy - 4, center ? 0xFFFF5555 : 0xFF888888);
        g.drawCenteredString(this.font, "좌클릭=재생 · 가운데=끄기 · ESC=취소",
                cx, this.height - 22, 0xFF9A9A9A);
    }

    /** 슬롯 i의 이모트를 작은 3D 피규어로 그린다(인벤토리 엔티티 렌더와 같은 방식). */
    private void renderFigure(GuiGraphics g, int emote, int x, int y, int scale, float age) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.player instanceof AbstractClientPlayer player)) return;
        if (!(mc.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer)) return;
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();

        // 모델 기본 셋업 후 이모트 포즈 덮어쓰기 (정면 응시, 다리 정지)
        model.young = false;
        model.crouching = false;
        model.riding = false;
        model.attackTime = 0f;
        model.setupAnim(player, 0f, 0f, age, 0f, 0f);
        EmotePoser.apply(model, emote, age);
        int lie = EmotePoser.lieAxis(emote);

        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(x, y, 50.0);
        ps.mulPose(new Matrix4f().scaling(scale, scale, -scale));
        ps.translate(0f, 0.9f, 0f); // 발끝~머리 세로 중앙 정렬
        Quaternionf q = new Quaternionf().rotateZ((float) Math.PI); // GUI Y축(아래) → 똑바로 세우기
        q.mul(new Quaternionf().rotateX((float) Math.toRadians(8)));   // 살짝 아래로 내려본 각도
        q.mul(new Quaternionf().rotateY((float) Math.toRadians(-26))); // 3/4 측면 각도
        ps.mulPose(q);
        // 눕는 이모트는 눕혀서 보여준다(서 있는 포즈와 구분)
        if (lie == 1) ps.mulPose(Axis.ZP.rotationDegrees(90f));
        else if (lie == 2) ps.mulPose(Axis.XP.rotationDegrees(90f));

        g.flush();
        Lighting.setupForEntityInInventory();
        MultiBufferSource.BufferSource bs = g.bufferSource();
        ResourceLocation skin = renderer.getTextureLocation(player);
        model.renderToBuffer(ps, bs.getBuffer(model.renderType(skin)),
                0xF000F0, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        ResourceLocation camo = CamoClient.texture(player.getUUID());
        if (camo != null) {
            model.renderToBuffer(ps, bs.getBuffer(RenderType.entityCutoutNoCull(camo)),
                    0xF000F0, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        }
        bs.endBatch();
        Lighting.setupFor3DItems();
        ps.popPose();
    }

    private int hoveredSlot(double mx, double my) {
        double dx = mx - cx, dy = my - cy;
        if (dx * dx + dy * dy < DEADZONE * DEADZONE) return -1;
        double a = Math.toDegrees(Math.atan2(dy, dx)) + 90.0; // 위=0
        if (a < 0) a += 360.0;
        int n = EmotePoser.COUNT;
        return ((int) Math.round(a / (360.0 / n))) % n;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            int slot = hoveredSlot(mx, my);
            if (slot >= 0) play(slot);
            else play(-1); // 가운데 = 이모트 끄기
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    /** emote=-1 이면 끄기. */
    private void play(int emote) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            EmoteState.set(mc.player.getUUID(), emote); // 즉시 로컬 반영
            ChameleonNet.sendEmote(emote);               // 서버 → 전체 동기화
        }
        this.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
