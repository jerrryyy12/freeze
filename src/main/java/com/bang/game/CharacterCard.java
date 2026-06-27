package com.bang.game;

/**
 * 캐릭터 카드. 각자 최대 체력(총알)과 고유 능력을 가진다.
 * 능력 텍스트는 직접 작성한 요약이며, 실제 능력 처리는 단계적으로 구현한다.
 */
public enum CharacterCard {
    BART_CASSIDY(4, "바트 캐시디", "피해를 1점 받을 때마다 카드 1장을 뽑는다"),
    BLACK_JACK(4, "블랙 잭", "뽑기 단계에서 두 번째 카드를 공개; 하트/다이아면 1장 더"),
    CALAMITY_JANET(4, "칼라미티 자넷", "BANG!과 빗나감 카드를 서로 바꿔 쓸 수 있다"),
    EL_GRINGO(3, "엘 그링고", "피해를 준 상대의 손에서 카드 1장을 빼앗는다"),
    JESSE_JONES(4, "제시 존스", "뽑기 단계 첫 장을 다른 플레이어 손에서 뽑을 수 있다"),
    JOURDONNAIS(4, "주르도네", "통(배럴)을 항상 가진 것처럼 한 번 방어 시도"),
    KIT_CARLSON(4, "키트 칼슨", "뽑기 단계에 3장을 보고 2장을 고른다"),
    LUCKY_DUKE(4, "럭키 듀크", "판정 시 2장을 뽑아 유리한 쪽을 택한다"),
    PAUL_REGRET(3, "폴 리그렛", "다른 모든 플레이어와의 거리가 +1"),
    PEDRO_RAMIREZ(4, "페드로 라미레즈", "뽑기 단계 첫 장을 버린 더미에서 가져올 수 있다"),
    ROSE_DOOLAN(4, "로즈 둘란", "다른 모든 플레이어를 향한 거리가 -1"),
    SID_KETCHUM(4, "시드 케첨", "손패 2장을 버려 체력 1 회복(언제든)"),
    SLAB_THE_KILLER(4, "슬랩 더 킬러", "BANG!을 막으려면 빗나감 2장이 필요"),
    SUZY_LAFAYETTE(4, "수지 라파예트", "손패가 0장이 되면 즉시 1장을 뽑는다"),
    VULTURE_SAM(4, "벌처 샘", "누군가 죽으면 그 손패와 장비를 모두 가져온다"),
    WILLY_THE_KID(4, "윌리 더 키드", "BANG!을 매 턴 횟수 제한 없이 낼 수 있다");

    public final int hp;
    public final String kr;
    public final String ability;

    CharacterCard(int hp, String kr, String ability) {
        this.hp = hp;
        this.kr = kr;
        this.ability = ability;
    }
}
