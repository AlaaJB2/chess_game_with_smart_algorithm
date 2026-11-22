package com.lab.board3d.rules;

import com.lab.board3d.PieceColor;
import com.lab.board3d.PieceType;
import com.lab.board3d.Square;

import java.util.ArrayList;
import java.util.List;

import static com.lab.board3d.PieceColor.WHITE;
import static com.lab.board3d.PieceType.*;

/**
 * Two stages:
 *  1) pseudo-legal moves: every move a piece could make by its own movement rules
 *  2) legal filter: throw away moves that leave your own king in check
 */
final class MoveGenerator {

    private static final int[][] KNIGHT_JUMPS = {{1,2},{2,1},{2,-1},{1,-2},{-1,-2},{-2,-1},{-2,1},{-1,2}};
    private static final PieceType[] PROMOTIONS = {QUEEN, ROOK, BISHOP, KNIGHT};

    private MoveGenerator() {}

    static List<Move> legalMoves(Position pos) {
        PieceColor me = pos.sideToMove;
        List<Move> legal = new ArrayList<>();
        for (Move m : pseudoLegal(pos)) {
            if (!pos.makeMove(m).isInCheck(me)) legal.add(m);
        }
        return legal;
    }

    private static List<Move> pseudoLegal(Position pos) {
        List<Move> out = new ArrayList<>();
        PieceColor me = pos.sideToMove;
        for (int i = 0; i < 64; i++) {
            if (pos.colors[i] != me) continue;
            int f = i % 8, r = i / 8;
            switch (pos.types[i]) {
                case PAWN -> pawn(pos, f, r, out);
                case KNIGHT -> { for (int[] d : KNIGHT_JUMPS) step(pos, f, r, d[0], d[1], out); }
                case BISHOP -> slide(pos, f, r, Position.BISHOP_DIRS, out);
                case ROOK -> slide(pos, f, r, Position.ROOK_DIRS, out);
                case QUEEN -> { slide(pos, f, r, Position.BISHOP_DIRS, out); slide(pos, f, r, Position.ROOK_DIRS, out); }
                case KING -> { king(pos, f, r, out); castle(pos, out); }
            }
        }
        return out;
    }

    private static void pawn(Position pos, int f, int r, List<Move> out) {
        PieceColor me = pos.sideToMove;
        int dir = me == WHITE ? 1 : -1;
        int startRank = me == WHITE ? 1 : 6;
        int lastRank = me == WHITE ? 7 : 0;
        Square from = new Square(f, r);

        // forward 1 and 2
        int r1 = r + dir;
        if (Square.isValid(f, r1) && pos.types[Position.idx(f, r1)] == null) {
            add(from, new Square(f, r1), r1 == lastRank, out);
            int r2 = r + 2 * dir;
            if (r == startRank && pos.types[Position.idx(f, r2)] == null) {
                out.add(new Move(from, new Square(f, r2)));
            }
        }
        // diagonal captures + en passant
        for (int df = -1; df <= 1; df += 2) {
            int x = f + df;
            if (!Square.isValid(x, r1)) continue;
            int i = Position.idx(x, r1);
            Square to = new Square(x, r1);
            if (pos.types[i] != null && pos.colors[i] != me) add(from, to, r1 == lastRank, out);
            else if (pos.types[i] == null && to.equals(pos.enPassant)) out.add(new Move(from, to));
        }
    }

    private static void add(Square from, Square to, boolean promote, List<Move> out) {
        if (promote) for (PieceType p : PROMOTIONS) out.add(new Move(from, to, p));
        else out.add(new Move(from, to));
    }

    private static void step(Position pos, int f, int r, int df, int dr, List<Move> out) {
        int x = f + df, y = r + dr;
        if (!Square.isValid(x, y)) return;
        int i = Position.idx(x, y);
        if (pos.types[i] == null || pos.colors[i] != pos.sideToMove) {
            out.add(new Move(new Square(f, r), new Square(x, y)));
        }
    }

    private static void king(Position pos, int f, int r, List<Move> out) {
        for (int df = -1; df <= 1; df++)
            for (int dr = -1; dr <= 1; dr++)
                if (df != 0 || dr != 0) step(pos, f, r, df, dr, out);
    }

    private static void slide(Position pos, int f, int r, int[][] dirs, List<Move> out) {
        Square from = new Square(f, r);
        for (int[] d : dirs) {
            int x = f + d[0], y = r + d[1];
            while (Square.isValid(x, y)) {
                int i = Position.idx(x, y);
                if (pos.types[i] == null) {
                    out.add(new Move(from, new Square(x, y)));
                } else {
                    if (pos.colors[i] != pos.sideToMove) out.add(new Move(from, new Square(x, y)));
                    break;
                }
                x += d[0]; y += d[1];
            }
        }
    }

    /** Castling: rights kept, squares between empty, king not in check, king does not cross an attacked square. */
    private static void castle(Position pos, List<Move> out) {
        PieceColor me = pos.sideToMove;
        PieceColor foe = me.opposite();
        int r = me == WHITE ? 0 : 7;
        int base = me == WHITE ? 0 : 2;                 // index into castling[]
        int kingSq = Position.idx(4, r);
        if (pos.types[kingSq] != PieceType.KING || pos.colors[kingSq] != me) return;
        if (pos.isAttacked(kingSq, foe)) return;

        // king side: f and g empty, rook on h, f and g not attacked
        if (pos.castling[base] && isRook(pos, 7, r, me)
                && empty(pos, 5, r) && empty(pos, 6, r)
                && !pos.isAttacked(Position.idx(5, r), foe) && !pos.isAttacked(Position.idx(6, r), foe)) {
            out.add(new Move(new Square(4, r), new Square(6, r)));
        }
        // queen side: b, c, d empty, rook on a, c and d not attacked (b may be attacked)
        if (pos.castling[base + 1] && isRook(pos, 0, r, me)
                && empty(pos, 1, r) && empty(pos, 2, r) && empty(pos, 3, r)
                && !pos.isAttacked(Position.idx(3, r), foe) && !pos.isAttacked(Position.idx(2, r), foe)) {
            out.add(new Move(new Square(4, r), new Square(2, r)));
        }
    }

    private static boolean empty(Position pos, int f, int r) { return pos.types[Position.idx(f, r)] == null; }

    private static boolean isRook(Position pos, int f, int r, PieceColor c) {
        int i = Position.idx(f, r);
        return pos.types[i] == PieceType.ROOK && pos.colors[i] == c;
    }
}
