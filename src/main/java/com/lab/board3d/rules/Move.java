package com.lab.board3d.rules;

import com.lab.board3d.PieceType;
import com.lab.board3d.Square;

/** A move: from, to, and (only for pawn promotion) the piece it becomes. */
public record Move(Square from, Square to, PieceType promotion) {

    public Move(Square from, Square to) {
        this(from, to, null);
    }

    /** UCI text: "e2e4" or "e7e8q". This is the format Python sends back. */
    public String toUci() {
        String s = from.toString() + to.toString();
        if (promotion == null) return s;
        return s + switch (promotion) {
            case QUEEN -> "q";
            case ROOK -> "r";
            case BISHOP -> "b";
            case KNIGHT -> "n";
            default -> throw new IllegalStateException("bad promotion " + promotion);
        };
    }

    public static Move fromUci(String uci) {
        uci = uci.trim().toLowerCase();
        if (uci.length() != 4 && uci.length() != 5) {
            throw new IllegalArgumentException("bad UCI move: " + uci);
        }
        Square from = Square.of(uci.substring(0, 2));
        Square to = Square.of(uci.substring(2, 4));
        PieceType promo = null;
        if (uci.length() == 5) {
            promo = switch (uci.charAt(4)) {
                case 'q' -> PieceType.QUEEN;
                case 'r' -> PieceType.ROOK;
                case 'b' -> PieceType.BISHOP;
                case 'n' -> PieceType.KNIGHT;
                default -> throw new IllegalArgumentException("bad promotion in " + uci);
            };
        }
        return new Move(from, to, promo);
    }

    @Override
    public String toString() { return toUci(); }
}
