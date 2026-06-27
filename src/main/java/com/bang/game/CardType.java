package com.bang.game;

/**
 * 카드 종류와 기본 덱 구성(총 80장).
 * category: 카드 성격, count: 덱에 들어가는 장수, weaponRange: 무기면 사정거리(아니면 0).
 */
public enum CardType {
    // 즉시 사용(갈색)
    BANG(Category.BANG, "BANG!", 25, 0),
    MISSED(Category.MISSED, "빗나감", 12, 0),
    BEER(Category.PLAY, "맥주", 6, 0),
    SALOON(Category.PLAY, "술집", 1, 0),
    WELLS_FARGO(Category.PLAY, "웰스 파고", 1, 0),
    STAGECOACH(Category.PLAY, "역마차", 2, 0),
    GENERAL_STORE(Category.PLAY, "잡화점", 2, 0),
    PANIC(Category.PLAY, "공황", 4, 0),
    CAT_BALOU(Category.PLAY, "캣 발루", 4, 0),
    INDIANS(Category.PLAY, "인디언 습격", 2, 0),
    DUEL(Category.PLAY, "결투", 3, 0),
    GATLING(Category.PLAY, "개틀링", 1, 0),

    // 무기(파랑) — 장착 시 사정거리 변경
    VOLCANIC(Category.WEAPON, "볼캐닉", 2, 1),
    SCHOFIELD(Category.WEAPON, "스코필드", 3, 2),
    REMINGTON(Category.WEAPON, "레밍턴", 1, 3),
    CARABINE(Category.WEAPON, "레버액션 카빈", 1, 4),
    WINCHESTER(Category.WEAPON, "윈체스터", 1, 5),

    // 장비(파랑) — 깔아두고 지속
    BARREL(Category.EQUIP, "통", 2, 0),
    SCOPE(Category.EQUIP, "조준경", 1, 0),
    MUSTANG(Category.EQUIP, "머스탱", 2, 0),
    JAIL(Category.EQUIP, "감옥", 3, 0),
    DYNAMITE(Category.EQUIP, "다이너마이트", 1, 0);

    public enum Category {
        BANG,    // 공격 카드
        MISSED,  // 방어 반응 카드
        PLAY,    // 그 외 즉시 효과
        WEAPON,  // 무기(사정거리)
        EQUIP    // 장비(통/조준경/머스탱/감옥/다이너마이트)
    }

    public final Category category;
    public final String kr;
    public final int count;
    public final int weaponRange;

    CardType(Category category, String kr, int count, int weaponRange) {
        this.category = category;
        this.kr = kr;
        this.count = count;
        this.weaponRange = weaponRange;
    }

    public boolean staysInPlay() {
        return category == Category.WEAPON || category == Category.EQUIP;
    }
}
