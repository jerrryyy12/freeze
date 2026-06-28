package com.chameleon.client;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.util.Mth;

/**
 * 이모트별 팔다리 포즈를 PlayerModel에 적용한다(기본 애니메이션 위에 덮어씀).
 * 각 부위의 xRot/yRot/zRot(라디안)을 직접 설정. age로 시간 애니메이션.
 */
public final class EmotePoser {

    /** 휠에 표시할 이모트 이름(인덱스 = 이모트 id). */
    public static final String[] NAMES = {
            "손 흔들기", "만세", "앉기", "가리키기", "T 포즈", "춤", "절", "얼굴 가리기"
    };
    public static final int COUNT = NAMES.length;

    private EmotePoser() {}

    public static void apply(PlayerModel<?> m, int emote, float age) {
        float t = age * 0.3f; // 애니메이션 속도

        switch (emote) {
            case 0 -> { // 손 흔들기 (오른팔 위로 들고 좌우)
                m.rightArm.xRot = -2.6f;
                m.rightArm.zRot = -0.3f + Mth.cos(t * 1.6f) * 0.35f;
                m.head.zRot = Mth.cos(t * 1.6f) * 0.08f;
            }
            case 1 -> { // 만세 (양팔 위로)
                m.rightArm.xRot = -2.95f; m.rightArm.zRot = -0.15f;
                m.leftArm.xRot = -2.95f;  m.leftArm.zRot = 0.15f;
            }
            case 2 -> { // 앉기 (다리 앞으로 굽힘)
                m.rightLeg.xRot = -1.55f; m.rightLeg.zRot = 0.1f;
                m.leftLeg.xRot = -1.55f;  m.leftLeg.zRot = -0.1f;
                m.rightArm.xRot = -0.2f;  m.leftArm.xRot = -0.2f;
            }
            case 3 -> { // 가리키기 (오른팔 앞으로)
                m.rightArm.xRot = -1.6f;
                m.head.yRot = 0.2f;
            }
            case 4 -> { // T 포즈 (양팔 옆으로 쭉)
                m.rightArm.zRot = -1.55f;
                m.leftArm.zRot = 1.55f;
            }
            case 5 -> { // 춤 (팔다리 흔들)
                float s = Mth.cos(t * 2f);
                m.rightArm.xRot = s * 1.2f - 1.1f; m.rightArm.zRot = -0.2f;
                m.leftArm.xRot = -s * 1.2f - 1.1f; m.leftArm.zRot = 0.2f;
                m.rightLeg.xRot = s * 0.4f;
                m.leftLeg.xRot = -s * 0.4f;
                m.head.zRot = s * 0.15f;
            }
            case 6 -> { // 절 (상체 숙임)
                m.body.xRot = 0.6f;
                m.head.xRot = 0.5f;
                m.rightArm.xRot = 0.6f - m.body.xRot;
                m.leftArm.xRot = 0.6f - m.body.xRot;
            }
            case 7 -> { // 얼굴 가리기 (양팔 얼굴로)
                m.rightArm.xRot = -2.1f; m.rightArm.zRot = -0.7f;
                m.leftArm.xRot = -2.1f;  m.leftArm.zRot = 0.7f;
                m.head.xRot = 0.25f;
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
