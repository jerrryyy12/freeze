package com.chameleon.client;

import com.chameleon.ChameleonMod;
import com.chameleon.net.CamoPaintPacket;
import com.chameleon.net.ChameleonNet;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

/**
 * 클라이언트 입력 처리 (FORGE 버스).
 * - G: 색칠 화면 / H: 위장↔스킨 토글
 * - 스포이드 무장 시: 휠클릭 → 화면 중앙(조준점) 픽셀 색을 그대로 추출(옆면 등 보이는 그대로)
 */
@Mod.EventBusSubscriber(modid = ChameleonMod.MOD_ID, value = Dist.CLIENT)
public class ChameleonInput {

    // 벽타기 설정 (상시 가능)
    private static final double CLIMB_SPEED = 0.15;  // 벽 오르내림 속도
    private static boolean wallStuck = false;        // 벽에 붙어있는 상태(Q로 내려오거나 땅/이탈 전까지 유지)

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();

        handleWallClimb(mc);

        // 자유 시점 ON/OFF (4/5) + 카메라 이동
        while (ChameleonClient.FREECAM_ON.consumeClick()) Freecam.enable();
        while (ChameleonClient.FREECAM_OFF.consumeClick()) Freecam.disable();
        Freecam.tick(mc);

        while (ChameleonClient.PAINT_KEY.consumeClick()) {
            if (mc.screen == null) PaintScreen.open();
        }
        while (ChameleonClient.TOGGLE_KEY.consumeClick()) {
            toggleCamo(mc);
        }
    }

    /**
     * 벽타기 (상시 가능, 게임 중이 아니어도 작동).
     * - 벽에 밀착(W로 밀고 있을 때)하면 그 자리에 달라붙는다(미끄러지지 않음, 유지).
     * - 점프키 = 위로, Q(아이템 버리기 키) = 아래로, 아무것도 안 누르면 그 자리 정지.
     * - 땅에 닿거나 벽에서 완전히 멀어지면 해제된다.
     */
    private static void handleWallClimb(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.screen != null) { wallStuck = false; return; }
        if (p.isSpectator() || p.isPassenger() || p.isFallFlying() || p.getAbilities().flying
                || p.onClimbable() || p.isInWater() || p.isInLava() || p.onGround()) {
            wallStuck = false;
            return;
        }

        if (p.horizontalCollision) wallStuck = true;       // 벽에 밀착하면 붙음
        if (wallStuck && !nearWall(p)) wallStuck = false;  // 벽에서 완전히 떨어지면 해제(낙하)
        if (!wallStuck) return;

        Vec3 m = p.getDeltaMovement();
        double vy;
        if (mc.options.keyJump.isDown())      vy = CLIMB_SPEED;   // 위로
        else if (mc.options.keyDrop.isDown()) vy = -CLIMB_SPEED;  // Q = 아래로
        else                                  vy = 0.0;           // 그 자리 정지
        p.setDeltaMovement(m.x, vy, m.z);
        p.resetFallDistance();
    }

    /** 플레이어 옆(수평)에 벽이 있는지 — 벽에서 떨어졌는지 판단용. */
    private static boolean nearWall(LocalPlayer p) {
        AABB box = p.getBoundingBox().inflate(0.2, -0.1, 0.2);
        return !p.level().noCollision(p, box);
    }

    /** 월드 렌더 후반(파티클까지) = GUI 그려지기 전. 스포이드 모드면 커서 밑 픽셀 색을 읽어 갱신. */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (Minecraft.getInstance().screen instanceof EyedropperScreen es) {
            es.updateHover(readPixelAt(es.cursorX(), es.cursorY()));
        }
    }

    /** 숨기/찾기 페이즈엔 닉네임을 숨긴다(위치 노출 방지). 정답 공개 페이즈엔 보여준다. */
    @SubscribeEvent
    public static void onNameTag(RenderNameTagEvent event) {
        if (CamoEditState.hideNames && event.getEntity() instanceof Player) {
            event.setResult(Event.Result.DENY);
        }
    }

    /** 카메라 셋업 중: 자유 시점/스포이드 모드면 카메라 위치·각도를 덮어쓴다. */
    @SubscribeEvent
    public static void onCameraSetup(ViewportEvent.ComputeCameraAngles event) {
        Freecam.applyCameraPosition(event.getCamera());
        EyedropperScreen.applyCamera(event);
    }

    /** 자유 시점 중에는 캐릭터가 움직이지 않도록 이동 입력을 막는다(이동키는 카메라용). */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!Freecam.isActive()) return;
        var in = event.getInput();
        in.forwardImpulse = 0f;
        in.leftImpulse = 0f;
        in.up = in.down = in.left = in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
    }

    /** GUI 좌표 (guiX,guiY)에 해당하는 프레임버퍼 픽셀(월드) 색을 읽는다. */
    private static int readPixelAt(double guiX, double guiY) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget rt = mc.getMainRenderTarget();
        double gw = mc.getWindow().getGuiScaledWidth();
        double gh = mc.getWindow().getGuiScaledHeight();
        int px = (int) Math.round(guiX / gw * rt.width);
        int py = (int) Math.round(guiY / gh * rt.height);
        px = Math.max(0, Math.min(rt.width - 1, px));
        py = Math.max(0, Math.min(rt.height - 1, py));
        int fy = rt.height - 1 - py; // glReadPixels 원점은 좌하단
        ByteBuffer pb = BufferUtils.createByteBuffer(16);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, rt.frameBufferId);
        GL11.glReadPixels(px, fy, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pb);
        rt.bindWrite(false); // 뷰포트는 건드리지 않고 프레임버퍼 바인딩만 복구
        int r = pb.get(0) & 0xFF, g = pb.get(1) & 0xFF, b = pb.get(2) & 0xFF;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static void toggleCamo(Minecraft mc) {
        if (mc.player == null) return;
        if (CamoEditState.gameActive) {
            mc.player.displayClientMessage(Component.literal("§7게임 중엔 위장을 끌 수 없어요"), true);
            return;
        }
        if (CamoEditState.camoOn) {
            CamoClient.apply(mc.player.getUUID(), null);
            ChameleonNet.sendPaintToServer(new CamoPaintPacket(null));
            CamoEditState.camoOn = false;
            mc.player.displayClientMessage(Component.literal("§7원래 스킨"), true);
        } else {
            CamoEditState.ensureInit();
            int[] copy = CamoEditState.pixels.clone();
            CamoClient.apply(mc.player.getUUID(), copy);
            ChameleonNet.sendPaintToServer(new CamoPaintPacket(copy));
            CamoEditState.camoOn = true;
            mc.player.displayClientMessage(Component.literal("§a위장 ON"), true);
        }
    }
}
