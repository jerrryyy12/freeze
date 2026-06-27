package com.bang.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** 뽑는 더미 + 버린 더미. */
public class Deck {

    private final List<Card> drawPile = new ArrayList<>();
    private final List<Card> discardPile = new ArrayList<>();
    private final Random random;

    public Deck(long seed) {
        this.random = new Random(seed);
        build();
        Collections.shuffle(drawPile, random);
    }

    private void build() {
        Card.Suit[] suits = Card.Suit.values();
        int i = 0;
        for (CardType type : CardType.values()) {
            for (int n = 0; n < type.count; n++) {
                // 무늬/숫자는 판정용으로 고르게 분배(정식 카드의 정확한 무늬와는 다를 수 있음)
                Card.Suit suit = suits[i % suits.length];
                int rank = (i % 13) + 1;
                drawPile.add(new Card(type, suit, rank));
                i++;
            }
        }
    }

    public Card draw() {
        if (drawPile.isEmpty()) {
            reshuffle();
            if (drawPile.isEmpty()) return null;
        }
        return drawPile.remove(drawPile.size() - 1);
    }

    public void discard(Card card) {
        if (card != null) discardPile.add(card);
    }

    public Card peekTopDiscard() {
        return discardPile.isEmpty() ? null : discardPile.get(discardPile.size() - 1);
    }

    private void reshuffle() {
        if (discardPile.isEmpty()) return;
        drawPile.addAll(discardPile);
        discardPile.clear();
        Collections.shuffle(drawPile, random);
    }

    public int drawCount() { return drawPile.size(); }
    public int discardCount() { return discardPile.size(); }
}
