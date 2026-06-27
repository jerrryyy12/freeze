package com.bang.game;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 게임 안의 한 플레이어 상태. */
public class BangPlayer {

    public final UUID id;
    public final String name;

    public Role role;
    public CharacterCard character;
    public int maxHp;
    public int hp;
    public boolean alive = true;
    public int seat = -1;
    public boolean isBot = false;

    public final List<Card> hand = new ArrayList<>();
    public final List<Card> equipment = new ArrayList<>();

    // 한 턴 동안 쓴 BANG! 수
    public int bangsThisTurn = 0;

    public BangPlayer(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    /** 장착한 무기의 사정거리(없으면 1) */
    public int weaponRange() {
        int range = 1;
        for (Card c : equipment) {
            if (c.type.category == CardType.Category.WEAPON) {
                range = Math.max(range, c.type.weaponRange);
            }
        }
        return range;
    }

    public boolean hasEquip(CardType type) {
        for (Card c : equipment) if (c.type == type) return true;
        return false;
    }

    public boolean hasCard(CardType type) {
        for (Card c : hand) if (c.type == type) return true;
        return false;
    }

    public Card takeCard(CardType type) {
        for (int i = 0; i < hand.size(); i++) {
            if (hand.get(i).type == type) return hand.remove(i);
        }
        return null;
    }

    public int handLimit() {
        return Math.max(0, hp);
    }
}
