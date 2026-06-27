package com.chameleon.client;

import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
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
        double speed = MOVE_SPEED * (mc.options.keySprint.isDown() ? SPRINT_MULT : 1.0);
        double yr = Math.toRadians(camYaw), pr = Math.toRadians(camPitch), cp = Math.cos(pr);
        Vec3 look = new Vec3(-Math.sin(yr) * cp, -Math.sin(pr), Math.cos(yr) * cp); // 카메라가 보는 방향
        Vec3 left = new Vec3(Math.cos(yr), 0, Math.sin(yr));                          // 왼쪽(A)

        Vec3 d = Vec3.ZERO;
        if (mc.options.keyUp.isDown())    d = d.add(look);
        if (mc.options.keyDown.isDown())  d = d.subtract(look);
        if (mc.options.keyLeft.isDown())  d = d.add(left);
        if (mc.options.keyRight.isDown()) d = d.subtract(left);
        if (mc.options.keyJump.isDown())  d = d.add(0, 1, 0);                       // 스페이스 = 위
        if (mc.options.keyShift.isDown() || mc.options.keyDrop.isDown()) d = d.add(0, -1, 0); // 시프트/Q = 아래
        if (d.lengthSqr() > 1.0e-6) pos = pos.add(d.normalize().scale(speed));
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
