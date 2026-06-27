package com.bang.game;

/** 덱 안의 한 장. 종류 + 무늬 + 숫자(판정용). */
public class Card {

    public enum Suit {
        SPADES("♠"), HEARTS("♥"), DIAMONDS("♦"), CLUBS("♣");
        public final String sym;
        Suit(String sym) { this.sym = sym; }
    }

    public final CardType type;
    public final Suit suit;
    public final int rank; // 1=A, 11=J, 12=Q, 13=K

    public Card(CardType type, Suit suit, int rank) {
        this.type = type;
        this.suit = suit;
        this.rank = rank;
    }

    public boolean isRed() {
        return suit == Suit.HEARTS || suit == Suit.DIAMONDS;
    }

    public String rankLabel() {
        return switch (rank) {
            case 1 -> "A";
            case 11 -> "J";
            case 12 -> "Q";
            case 13 -> "K";
            default -> Integer.toString(rank);
        };
    }

    /** "BANG! (A♠)" 형태 */
    public String label() {
        return type.kr + " (" + rankLabel() + suit.sym + ")";
    }
}
