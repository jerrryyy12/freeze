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

    // 벽/천장 타기 설정 (상시 가능)
    private static final double CLIMB_SPEED = 0.15;  // 벽 오르내림 속도
    private static int stickMode = 0;                // 0=없음, 1=벽, 2=천장

    // 인사 이모트 자동 종료 (반복 애니메이션이라 2초 뒤 자동으로 끔)
    private static final int WAVE_TICKS = 40;        // 2초
    private static int waveStart = -1;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();

        handleClimb(mc);

        // 자유 시점 ON/OFF (4/5) — 술래는 게임 중(준비/숨기/찾기) 금지(반칙 방지). 숨는 사람은 허용.
        boolean inPlay = CamoEditState.phase >= 1 && CamoEditState.phase <= 3;
        boolean seekerBlocked = inPlay && CamoEditState.localRole == 2;
        while (ChameleonClient.FREECAM_ON.consumeClick()) {
            if (seekerBlocked) {
                if (mc.player != null)
                    mc.player.displayClientMessage(Component.literal("§7술래는 게임 중 자유 시점을 쓸 수 없어요"), true);
            } else {
                Freecam.enable();
            }
        }
        while (ChameleonClient.FREECAM_OFF.consumeClick()) Freecam.disable();
        if (seekerBlocked && Freecam.isActive()) Freecam.disable(); // 술래는 게임 시작 시 강제 해제
        Freecam.tick(mc);
        if (Freecam.isActive()) {
            // 자유 시점 중에는 Q(버리기)가 게임으로 새지 않게 소비 — 카메라 아래 이동 전용
            while (mc.options.keyDrop.consumeClick()) { /* 버리기 무시 */ }
        }

        while (ChameleonClient.PAINT_KEY.consumeClick()) {
            if (mc.screen == null) {
                if (Freecam.isActive()) FreecamBrushScreen.open(); // 자유시점: 캐릭터에 직접 칠하기
                else PaintScreen.open();                            // 평소: 2D/3D 편집창
            }
        }
        while (ChameleonClient.TOGGLE_KEY.consumeClick()) {
            toggleCamo(mc);
        }
        while (ChameleonClient.EMOTE_KEY.consumeClick()) {
            if (mc.screen == null) EmoteWheelScreen.open();
        }
        // 고정 포즈 이모트는 이동해도 유지된다(끄려면 R 휠 가운데).
        // 단, 인사(0번)는 반복 애니메이션이라 2초 뒤 자동으로 끈다.
        handleWaveAutoStop(mc);
    }

    /** 인사 이모트는 시작 후 2초가 지나면 자동으로 해제한다. */
    private static void handleWaveAutoStop(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) { waveStart = -1; return; }
        if (EmoteState.emoteOf(p.getUUID()) == 0) {
            if (waveStart < 0) {
                waveStart = p.tickCount;
            } else if (p.tickCount - waveStart >= WAVE_TICKS) {
                EmoteState.set(p.getUUID(), -1);
                ChameleonNet.sendEmote(-1);
                waveStart = -1;
            }
        } else {
            waveStart = -1;
        }
    }

    /**
     * 벽타기 (상시 가능, 게임 중이 아니어도 작동).
     * - 벽에 밀착(W로 밀고 있을 때)하면 그 자리에 달라붙는다(미끄러지지 않음, 유지).
     * - 점프키 = 위로, Q(아이템 버리기 키) = 아래로, 아무것도 안 누르면 그 자리 정지.
     * - 땅에 닿거나 벽에서 완전히 멀어지면 해제된다.
     */
    private static void handleClimb(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || Freecam.isActive() || p.isSpectator() || p.isPassenger()
                || p.isFallFlying() || p.getAbilities().flying
                || p.onClimbable() || p.isInWater() || p.isInLava()) {
            stickMode = 0;
            return;
        }
        if (p.onGround()) { stickMode = 0; return; }
        // 화면(색칠 등)이 열려 있어도 붙어있던 상태면 그 자리에 고정
        if (mc.screen != null) {
            if (stickMode != 0) { p.setDeltaMovement(0, 0, 0); p.resetFallDistance(); }
            return;
        }

        boolean ceiling = ceilingAbove(p);
        // 붙기 판정: 이미 붙은 상태에서 천장을 만나면 천장 매달림, 벽에 밀착하면 벽, 벗어나면 해제
        if (ceiling && stickMode != 0) stickMode = 2;
        else if (p.horizontalCollision) stickMode = 1;
        else if (stickMode == 1 && !nearWall(p)) stickMode = 0;
        else if (stickMode == 2 && !ceiling) stickMode = 0;
        if (stickMode == 0) return;

        Vec3 m = p.getDeltaMovement();
        if (stickMode == 2) {
            // 천장 매달림: Q=떨어짐, 그 외엔 수평 이동하며 매달림(위로 안 떨어지게)
            if (mc.options.keyDrop.isDown()) { stickMode = 0; return; }
            p.setDeltaMovement(m.x, 0.0, m.z);
        } else {
            // 벽: 점프=위, Q=아래, 무입력=정지
            double vy = mc.options.keyJump.isDown() ? CLIMB_SPEED
                    : mc.options.keyDrop.isDown() ? -CLIMB_SPEED : 0.0;
            p.setDeltaMovement(m.x, vy, m.z);
        }
        p.resetFallDistance();
    }

    /** 플레이어 옆(수평)에 벽이 있는지 — 벽에서 떨어졌는지 판단용. */
    private static boolean nearWall(LocalPlayer p) {
        AABB box = p.getBoundingBox().inflate(0.2, -0.1, 0.2);
        return !p.level().noCollision(p, box);
    }

    /** 플레이어 머리 위에 천장(블록)이 있는지. */
    private static boolean ceilingAbove(LocalPlayer p) {
        AABB b = p.getBoundingBox();
        AABB above = new AABB(b.minX + 0.05, b.maxY, b.minZ + 0.05, b.maxX - 0.05, b.maxY + 0.2, b.maxZ - 0.05);
        return !p.level().noCollision(p, above);
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
        Freecam.applyCamera(event);
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
    public static int readPixelAt(double guiX, double guiY) {
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
