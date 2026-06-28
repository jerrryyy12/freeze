package com.chameleon.client;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.util.Mth;

/**
 * 이모트별 포즈를 PlayerModel에 적용한다.
 * 대부분 고정 포즈, 인사(0)만 손 흔드는 애니메이션. 머리는 건드리지 않아 시선대로 자연스럽다.
 * 앉기/눕기 같은 전신 변환(바닥에 앉히기·눕히기)은 EmoteRenderer가 dropY/lieAxis로 처리.
 */
public final class EmotePoser {

    public static final String[] NAMES = {
            "인사", "만세", "T 포즈", "앉기", "항복",
            "여유", "환영", "허리춤", "대자 눕기", "옆으로 눕기"
    };
    public static final int COUNT = NAMES.length;

    private EmotePoser() {}

    public static void apply(PlayerModel<?> m, int emote, float age) {
        switch (emote) {
            case 0 -> { // 인사 — 오른팔 들고 좌우로 흔듦(애니메이션)
                m.rightArm.xRot = -2.3f;
                m.rightArm.zRot = -0.4f + Mth.cos(age * 0.4f) * 0.45f;
            }
            case 1 -> { // 만세 — 양팔 위로
                m.rightArm.xRot = -2.95f; m.rightArm.zRot = -0.15f;
                m.leftArm.xRot = -2.95f;  m.leftArm.zRot = 0.15f;
            }
            case 2 -> { // T 포즈 — 양팔 옆으로
                m.rightArm.zRot = 1.55f;
                m.leftArm.zRot = -1.55f;
            }
            case 3 -> { // 앉기 — 다리 앞으로(바닥 내림은 렌더러)
                m.rightLeg.xRot = -1.5f; m.rightLeg.zRot = 0.15f;
                m.leftLeg.xRot = -1.5f;  m.leftLeg.zRot = -0.15f;
                m.rightArm.xRot = -0.5f;  m.leftArm.xRot = -0.5f;
            }
            case 4 -> { // 항복 — 양팔 위로 굽힘
                m.rightArm.xRot = -2.5f; m.rightArm.zRot = -0.45f;
                m.leftArm.xRot = -2.5f;  m.leftArm.zRot = 0.45f;
            }
            case 5 -> { // 여유 — 양팔 굽혀 머리 뒤로(팔꿈치 밖)
                m.rightArm.xRot = -2.2f; m.rightArm.zRot = -1.05f;
                m.leftArm.xRot = -2.2f;  m.leftArm.zRot = 1.05f;
            }
            case 6 -> { // 환영 — 양팔 앞으로
                m.rightArm.xRot = -1.25f;
                m.leftArm.xRot = -1.25f;
            }
            case 7 -> { // 허리춤 — 양손 허리에
                m.rightArm.xRot = -0.2f; m.rightArm.zRot = 1.1f;
                m.leftArm.xRot = -0.2f;  m.leftArm.zRot = -1.1f;
            }
            case 8 -> { // 대자 눕기 — 팔다리 X자로 쫙(눕힘은 렌더러)
                m.rightArm.zRot = 1.15f; m.leftArm.zRot = -1.15f;
                m.rightLeg.zRot = 0.45f; m.leftLeg.zRot = -0.45f;
            }
            case 9 -> { // 옆으로 눕기 — 약간 웅크림(눕힘+굴림은 렌더러)
                m.rightArm.xRot = -0.6f; m.leftArm.xRot = -0.45f;
                m.rightLeg.xRot = -0.6f; m.leftLeg.xRot = -0.85f;
            }
            default -> { }
        }

        // 오버레이(겉옷/소매/바지/모자) 파트를 베이스에 다시 맞춘다.
        m.hat.copyFrom(m.head);
        m.jacket.copyFrom(m.body);
        m.rightSleeve.copyFrom(m.rightArm);
        m.leftSleeve.copyFrom(m.leftArm);
        m.rightPants.copyFrom(m.rightLeg);
        m.leftPants.copyFrom(m.leftLeg);
    }

    /** 앉기: 몸을 바닥으로 내리는 양(엔티티 공간, -가 아래). */
    public static float dropY(int emote) {
        return emote == 3 ? -0.62f : 0f;
    }

    /** 눕기: 0=없음, 1=옆으로(ZP90), 2=대자/뒤로(XP90). 사망 쓰러짐과 같은 위치에서 회전. */
    public static int lieAxis(int emote) {
        return switch (emote) {
            case 8 -> 2;
            case 9 -> 1;
            default -> 0;
        };
    }
}
