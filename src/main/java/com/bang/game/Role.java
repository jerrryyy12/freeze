package com.bang.game;

/** 4개의 비밀 역할 */
public enum Role {
    SHERIFF("보안관", "무법자와 배신자를 모두 제거하면 승리. 정체 공개, 체력 +1"),
    DEPUTY("부관", "보안관을 도와 무법자·배신자를 제거하면 승리"),
    OUTLAW("무법자", "보안관을 제거하면 승리"),
    RENEGADE("배신자", "마지막에 혼자 살아남으면 승리");

    public final String kr;
    public final String goal;

    Role(String kr, String goal) {
        this.kr = kr;
        this.goal = goal;
    }
}
