package com.chameleon.client;

import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;

/**
 * 분리형 자유 시점 카메라(클라이언트 전용).
 * - 4번: 카메라가 몸에서 분리되어 자유 비행(WASD/스페이스/시프트 이동, 마우스로 시점).
 *        내 캐릭터는 제자리에 고정된 채로 보인다(3인칭으로 전환).
 * - 5번: 카메라가 캐릭터로 복귀.
 *
 * 카메라 위치는 Camera 의 Vec3 position 필드를 매 프레임 덮어써서 옮긴다(믹스인 없이 동작).
 */
public final class Freecam {
    private static boolean active = false;
    private static Vec3 pos = Vec3.ZERO;
    private static CameraType prevType = CameraType.FIRST_PERSON;

    private static final double MOVE_SPEED = 0.45;  // 칸/틱
    private static final double SPRINT_MULT = 2.5;  // 달리기 키 누르면 가속

    private static Field posField;          // Camera 의 Vec3 position 필드(이름 대신 타입으로 탐색)
    private static boolean fieldResolved = false;

    private Freecam() {}

    public static boolean isActive() { return active; }

    public static void toggle() {
        if (active) disable(); else enable();
    }

    public static void enable() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || active) return;
        if (posField() == null) { // 카메라 위치 필드를 못 찾으면 자유 카메라 불가
            p.displayClientMessage(Component.literal("§c자유 시점을 초기화하지 못했어요"), true);
            return;
        }
        pos = p.getEyePosition(1.0f);
        prevType = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); // 내 몸이 보이도록 3인칭
        active = true;
        p.displayClientMessage(Component.literal("§a자유 시점 ON — WASD/스페이스/시프트 이동, 5번으로 복귀"), true);
    }

    public static void disable() {
        Minecraft mc = Minecraft.getInstance();
        if (!active) return;
        active = false;
        mc.options.setCameraType(prevType);
        if (mc.player != null)
            mc.player.displayClientMessage(Component.literal("§7자유 시점 OFF"), true);
    }

    /** 매 클라이언트 틱: 입력으로 카메라 위치를 이동시킨다. */
    public static void tick(Minecraft mc) {
        if (!active) return;
        LocalPlayer p = mc.player;
        if (p == null || p.isRemoved() || p.isSpectator()) { disable(); return; }

        double speed = MOVE_SPEED * (mc.options.keySprint.isDown() ? SPRINT_MULT : 1.0);
        double rad = Math.toRadians(p.getYRot());
        Vec3 forward = new Vec3(-Math.sin(rad), 0, Math.cos(rad)); // 바라보는 수평 방향
        Vec3 left = new Vec3(Math.cos(rad), 0, Math.sin(rad));     // 왼쪽(A) 방향

        Vec3 d = Vec3.ZERO;
        if (mc.options.keyUp.isDown())    d = d.add(forward);
        if (mc.options.keyDown.isDown())  d = d.subtract(forward);
        if (mc.options.keyLeft.isDown())  d = d.add(left);
        if (mc.options.keyRight.isDown()) d = d.subtract(left);
        if (mc.options.keyJump.isDown())  d = d.add(0, 1, 0);
        if (mc.options.keyShift.isDown()) d = d.add(0, -1, 0);
        if (d.lengthSqr() > 1.0e-6) pos = pos.add(d.normalize().scale(speed));
    }

    /** Camera.setup 도중(ComputeCameraAngles 이벤트)에 호출: 카메라 위치를 freePos로 덮어쓴다. */
    public static void applyCameraPosition(Camera cam) {
        if (!active) return;
        Field f = posField();
        if (f == null) return;
        try {
            f.set(cam, pos);
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
