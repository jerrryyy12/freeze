package com.chameleon.client;

import net.minecraft.client.model.PlayerModel;

/**
 * 이모트별 "고정 포즈"를 PlayerModel에 적용한다(애니메이션 없이 정적).
 * 머리는 건드리지 않아 시선대로 자연스럽게 둔다(EmoteRenderer가 실제 head yaw/pitch 전달).
 * 각 부위 xRot/yRot/zRot(라디안)만 설정.
 */
public final class EmotePoser {

    /** 휠에 표시할 이모트 이름(인덱스 = 이모트 id). */
    public static final String[] NAMES = {
            "손 흔들기", "만세", "T 포즈", "가리키기", "앉기", "절",
            "생각", "항복", "응원", "허리춤"
    };
    public static final int COUNT = NAMES.length;

    private EmotePoser() {}

    public static void apply(PlayerModel<?> m, int emote) {
        switch (emote) {
            case 0 -> { // 손 흔들기 — 오른팔을 위로 들어 옆으로(고정)
                m.rightArm.xRot = -2.4f;
                m.rightArm.zRot = -0.5f;
            }
            case 1 -> { // 만세 — 양팔 위로
                m.rightArm.xRot = -2.95f; m.rightArm.zRot = -0.15f;
                m.leftArm.xRot = -2.95f;  m.leftArm.zRot = 0.15f;
            }
            case 2 -> { // T 포즈 — 양팔을 옆으로 쭉(바깥쪽)
                m.rightArm.zRot = 1.55f;
                m.leftArm.zRot = -1.55f;
            }
            case 3 -> { // 가리키기 — 오른팔 앞으로
                m.rightArm.xRot = -1.5f;
            }
            case 4 -> { // 앉기 — 다리 앞으로 굽힘, 팔 살짝 앞
                m.rightLeg.xRot = -1.45f; m.rightLeg.zRot = 0.1f;
                m.leftLeg.xRot = -1.45f;  m.leftLeg.zRot = -0.1f;
                m.rightArm.xRot = -0.35f; m.leftArm.xRot = -0.35f;
            }
            case 5 -> { // 절 — 상체 살짝 숙이고 팔 앞으로(다리 안 잘리게 약하게)
                m.body.xRot = 0.4f;
                m.rightArm.xRot = -0.2f; m.leftArm.xRot = -0.2f;
            }
            case 6 -> { // 생각 — 오른손을 얼굴 쪽으로
                m.rightArm.xRot = -1.95f; m.rightArm.zRot = -0.55f;
            }
            case 7 -> { // 항복 — 양팔 위로 굽혀 듦
                m.rightArm.xRot = -2.5f; m.rightArm.zRot = -0.45f;
                m.leftArm.xRot = -2.5f;  m.leftArm.zRot = 0.45f;
            }
            case 8 -> { // 응원 — 양팔 앞으로
                m.rightArm.xRot = -1.25f;
                m.leftArm.xRot = -1.25f;
            }
            case 9 -> { // 허리춤 — 양팔을 허리에(옆으로 벌려 굽힘)
                m.rightArm.xRot = -0.2f; m.rightArm.zRot = 1.1f;
                m.leftArm.xRot = -0.2f;  m.leftArm.zRot = -1.1f;
            }
            default -> { /* 알 수 없는 이모트 → 포즈 없음 */ }
        }

        // 오버레이(겉옷/소매/바지/모자) 파트를 베이스에 다시 맞춘다(setupAnim 이후 덮어썼으므로).
        m.hat.copyFrom(m.head);
        m.jacket.copyFrom(m.body);
        m.rightSleeve.copyFrom(m.rightArm);
        m.leftSleeve.copyFrom(m.leftArm);
        m.rightPants.copyFrom(m.rightLeg);
        m.leftPants.copyFrom(m.leftLeg);
    }
}
