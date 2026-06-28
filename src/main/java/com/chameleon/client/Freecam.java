package com.chameleon.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ViewportEvent;

import java.lang.reflect.Field;

/**
 * 분리형 자유 시점 카메라(클라이언트 전용).
 * - 4번: 카메라가 몸에서 분리되어 자유 비행. 마우스로 카메라 시점을 돌려도 캐릭터는 고정(회전 X).
 *   WASD=카메라가 보는 방향 이동, 스페이스/시프트=상하. 3인칭 유지(F5로 안 사라짐).
 * - 5번: 카메라가 캐릭터로 복귀.
 *
 * 위치는 Camera 의 Vec3 position 필드를 매 프레임 덮어쓰고, 틱→프레임 보간(lerp)으로 부드럽게.
 * 카메라 각도(camYaw/camPitch)는 마우스 이동량을 받아 누적하고, 플레이어 회전은 매 프레임 고정.
 */
public final class Freecam {
    private static boolean active = false;
    private static Vec3 pos = Vec3.ZERO;
    private static Vec3 posOld = Vec3.ZERO;
    private static float camYaw, camPitch;          // 카메라 자체 각도
    private static float frozenYaw, frozenPitch;     // 캐릭터를 고정할 각도
    private static float prevPlayerYaw, prevPlayerPitch;
    private static CameraType prevType = CameraType.FIRST_PERSON;

    private static final double MOVE_SPEED = 0.45;   // 칸/틱
    private static final double SPRINT_MULT = 2.5;

    private static Field posField;
    private static boolean fieldResolved = false;

    private Freecam() {}

    public static boolean isActive() { return active; }

    // 직접 칠하기(브러시 화면)에서 쓰는 카메라 위치/각도 접근자
    public static Vec3 camPos() { return pos; }
    public static float camYaw() { return camYaw; }
    public static float camPitch() { return camPitch; }

    /** 브러시 화면에서 우클릭 드래그로 카메라를 돌릴 때 사용. */
    public static void addCamRotation(float dYaw, float dPitch) {
        camYaw += dYaw;
        camPitch = (float) Math.max(-90.0, Math.min(90.0, camPitch + dPitch));
    }

    public static void enable() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || active) return;
        if (posField() == null) {
            p.displayClientMessage(Component.literal("§c자유 시점을 초기화하지 못했어요"), true);
            return;
        }
        pos = posOld = p.getEyePosition(1.0f);
        camYaw = frozenYaw = prevPlayerYaw = p.getYRot();
        camPitch = frozenPitch = prevPlayerPitch = p.getXRot();
        prevType = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); // 내 몸이 보이도록 3인칭
        active = true;
        p.displayClientMessage(Component.literal("§a자유 시점 ON — WASD/스페이스/시프트 이동, 마우스로 시점, 5번 복귀"), true);
    }

    public static void disable() {
        Minecraft mc = Minecraft.getInstance();
        if (!active) return;
        active = false;
        mc.options.setCameraType(prevType);
        if (mc.player != null)
            mc.player.displayClientMessage(Component.literal("§7자유 시점 OFF"), true);
    }

    /** 매 클라이언트 틱(20Hz): 입력으로 카메라 목표 위치를 한 스텝 이동(렌더에서 보간). */
    public static void tick(Minecraft mc) {
        if (!active) return;
        LocalPlayer p = mc.player;
        if (p == null || p.isRemoved() || p.isSpectator()) { disable(); return; }
        // F5 등으로 시점이 바뀌어 내 몸이 안 보이는 것 방지 — 항상 3인칭 유지
        if (mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK)
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);

        posOld = pos;
        // 브러시 화면이 열려 있어도 WASD로 카메라 이동 가능(화면이 키를 막으니 원시 키 상태를 읽음).
        // 다른 화면(채팅·일시정지 등)일 땐 이동하지 않는다.
        boolean brush = mc.screen instanceof FreecamBrushScreen;
        if (mc.screen == null || brush) {
            double speed = MOVE_SPEED * (held(mc, mc.options.keySprint, brush) ? SPRINT_MULT : 1.0);
            double yr = Math.toRadians(camYaw), pr = Math.toRadians(camPitch), cp = Math.cos(pr);
            Vec3 look = new Vec3(-Math.sin(yr) * cp, -Math.sin(pr), Math.cos(yr) * cp); // 카메라가 보는 방향
            Vec3 left = new Vec3(Math.cos(yr), 0, Math.sin(yr));                          // 왼쪽(A)

            Vec3 d = Vec3.ZERO;
            if (held(mc, mc.options.keyUp, brush))    d = d.add(look);
            if (held(mc, mc.options.keyDown, brush))  d = d.subtract(look);
            if (held(mc, mc.options.keyLeft, brush))  d = d.add(left);
            if (held(mc, mc.options.keyRight, brush)) d = d.subtract(left);
            if (held(mc, mc.options.keyJump, brush))  d = d.add(0, 1, 0);                       // 스페이스 = 위
            if (held(mc, mc.options.keyShift, brush)) d = d.add(0, -1, 0);                      // 시프트 = 아래
            if (!brush && mc.options.keyDrop.isDown()) d = d.add(0, -1, 0);                     // Q = 아래(일반 시점)
            if (d.lengthSqr() > 1.0e-6) pos = pos.add(d.normalize().scale(speed));
        }

        // 몸은 자유 시점 동안 그 자리에 완전히 고정(중력/관성으로 떨어지지 않게)
        p.setDeltaMovement(0, 0, 0);
        p.resetFallDistance();
    }

    /**
     * ComputeCameraAngles(매 프레임): 마우스 이동량을 카메라 각도에 누적하고 캐릭터 회전은 고정.
     * 카메라 위치는 직전 틱→현재 틱 사이를 partialTick으로 보간해 부드럽게.
     */
    public static void applyCamera(ViewportEvent.ComputeCameraAngles event) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;

        // 이번 프레임 마우스가 돌린 양 → 카메라 각도에 누적
        camYaw += p.getYRot() - prevPlayerYaw;
        camPitch = (float) Math.max(-90.0, Math.min(90.0, camPitch + (p.getXRot() - prevPlayerPitch)));
        // 캐릭터는 회전하지 않도록 고정
        freezeRotation(p, frozenYaw, frozenPitch);
        prevPlayerYaw = frozenYaw;
        prevPlayerPitch = frozenPitch;

        event.setYaw(camYaw);
        event.setPitch(camPitch);
        Vec3 render = posOld.lerp(pos, event.getPartialTick());
        setCameraPosition(event.getCamera(), render);
    }

    /** 키가 눌렸는지. raw=true면(화면 열림) 윈도우의 원시 키 상태를 직접 읽는다. */
    private static boolean held(Minecraft mc, KeyMapping k, boolean raw) {
        if (!raw) return k.isDown();
        InputConstants.Key key = k.getKey();
        return key.getType() == InputConstants.Type.KEYSYM
                && InputConstants.isKeyDown(mc.getWindow().getWindow(), key.getValue());
    }

    private static void freezeRotation(LocalPlayer p, float yaw, float pitch) {
        p.setYRot(yaw); p.yRotO = yaw;
        p.setXRot(pitch); p.xRotO = pitch;
        p.setYBodyRot(yaw); p.yBodyRotO = yaw;
        p.setYHeadRot(yaw); p.yHeadRotO = yaw;
    }

    /** 카메라 위치를 임의 좌표로 강제(리플렉션). 스포이드 모드 등에서도 재사용. */
    public static void setCameraPosition(Camera cam, Vec3 p) {
        Field f = posField();
        if (f == null) return;
        try {
            f.set(cam, p);
        } catch (IllegalAccessException ignored) {
        }
    }

    private static Field posField() {
        if (fieldResolved) return posField;
        fieldResolved = true;
        for (Field f : Camera.class.getDeclaredFields()) {
            if (f.getType() == Vec3.class) {
                try {
                    f.setAccessible(true);
                    posField = f;
                } catch (Exception ignored) {
                }
                break;
            }
        }
        return posField;
    }
}
