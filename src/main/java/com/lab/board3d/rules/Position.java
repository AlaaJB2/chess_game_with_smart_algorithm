package com.lab.board3d.rules;

import com.lab.board3d.PieceColor;
import com.lab.board3d.PieceType;
import com.lab.board3d.Square;

import java.util.List;

import static com.lab.board3d.PieceColor.BLACK;
import static com.lab.board3d.PieceColor.WHITE;
import static com.lab.board3d.PieceType.*;

/**
 * A full chess position (everything FEN contains). No JavaFX here, so it is easy to test.
 * Positions are never modified: makeMove() returns a NEW position.
 * Square index = rank * 8 + file  (a1 = 0, h1 = 7, a8 = 56, h8 = 63).
 */
public final class Position {

    public static final String START_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    // castling[] order: 0 = white king-side, 1 = white queen-side, 2 = black king-side, 3 = black queen-side
    final PieceType[] types = new PieceType[64];
    final PieceColor[] colors = new PieceColor[64];
    final boolean[] castling = new boolean[4];
    PieceColor sideToMove = WHITE;
    Square enPassant;          // square "behind" a pawn that just advanced two squares, or null
    int halfmoveClock;
    int fullmoveNumber = 1;

    private Position() {}

    // ------------------------------------------------------------ creation

    public static Position start() { return fromFen(START_FEN); }

    public static Position fromFen(String fen) {
        String[] part = fen.trim().split("\\s+");
        if (part.length < 4) throw new IllegalArgumentException("bad FEN: " + fen);

        Position p = new Position();
        String[] ranks = part[0].split("/");
        if (ranks.length != 8) throw new IllegalArgumentException("FEN needs 8 ranks: " + fen);
        for (int i = 0; i < 8; i++) {
            int rank = 7 - i, file = 0;
            for (char ch : ranks[i].toCharArray()) {
                if (Character.isDigit(ch)) {
                    file += ch - '0';
                } else {
                    PieceColor c = Character.isUpperCase(ch) ? WHITE : BLACK;
                    p.set(idx(file, rank), typeFromChar(ch), c);
                    file++;
                }
            }
            if (file != 8) throw new IllegalArgumentException("bad rank in FEN: " + ranks[i]);
        }
        p.sideToMove = part[1].equals("w") ? WHITE : BLACK;
        p.castling[0] = part[2].contains("K");
        p.castling[1] = part[2].contains("Q");
        p.castling[2] = part[2].contains("k");
        p.castling[3] = part[2].contains("q");
        p.enPassant = part[3].equals("-") ? null : Square.of(part[3]);
        p.halfmoveClock = part.length > 4 ? Integer.parseInt(part[4]) : 0;
        p.fullmoveNumber = part.length > 5 ? Integer.parseInt(part[5]) : 1;
        return p;
    }

    public String toFen() {
        StringBuilder sb = new StringBuilder();
        for (int rank = 7; rank >= 0; rank--) {
            int empty = 0;
            for (int file = 0; file < 8; file++) {
                int i = idx(file, rank);
                if (types[i] == null) {
                    empty++;
                } else {
                    if (empty > 0) { sb.append(empty); empty = 0; }
                    char ch = charFromType(types[i]);
                    sb.append(colors[i] == WHITE ? Character.toUpperCase(ch) : ch);
                }
            }
            if (empty > 0) sb.append(empty);
            if (rank > 0) sb.append('/');
        }
        sb.append(' ').append(sideToMove == WHITE ? 'w' : 'b').append(' ');
        String rights = (castling[0] ? "K" : "") + (castling[1] ? "Q" : "")
                      + (castling[2] ? "k" : "") + (castling[3] ? "q" : "");
        sb.append(rights.isEmpty() ? "-" : rights).append(' ');
        sb.append(enPassant == null ? "-" : enPassant.toString()).append(' ');
        sb.append(halfmoveClock).append(' ').append(fullmoveNumber);
        return sb.toString();
    }

    // ------------------------------------------------------------ queries

    public PieceColor sideToMove() { return sideToMove; }
    public Square enPassantSquare() { return enPassant; }
    public int fullmoveNumber() { return fullmoveNumber; }
    public PieceType typeAt(Square s) { return types[idx(s)]; }
    public PieceColor colorAt(Square s) { return colors[idx(s)]; }

    public List<Move> legalMoves() { return MoveGenerator.legalMoves(this); }

    public boolean isLegal(Move m) { return legalMoves().contains(m); }

    /**
     * مفتاح لمقارنة الوضعيات في قاعدة التكرار الثلاثي: القطع + الدور + التبييت + المرور.
     * مربع المرور لا يُحسب إلا إذا كان الأخذ بالمرور ممكناً فعلاً (قاعدة FIDE)،
     * وعدّادا النقلات لا يدخلان في المقارنة.
     */
    public String repetitionKey() {
        String[] f = toFen().split(" ");
        String ep = "-";
        if (enPassant != null) {
            for (Move m : legalMoves()) {
                if (m.to().equals(enPassant) && types[idx(m.from())] == PAWN
                        && m.from().file() != m.to().file()) {
                    ep = enPassant.toString();
                    break;
                }
            }
        }
        return f[0] + " " + f[1] + " " + f[2] + " " + ep;
    }

    /** Finds the legal move matching from/to. If it is a promotion and none is given, queen. */
    public Move findLegal(Square from, Square to, PieceType promotionOrNull) {
        for (Move m : legalMoves()) {
            if (m.from().equals(from) && m.to().equals(to)) {
                if (m.promotion() == null) return m;
                PieceType want = promotionOrNull == null ? QUEEN : promotionOrNull;
                if (m.promotion() == want) return m;
            }
        }
        return null;
    }

    public boolean isInCheck(PieceColor side) {
        for (int i = 0; i < 64; i++) {
            if (types[i] == KING && colors[i] == side) {
                return isAttacked(i, side.opposite());
            }
        }
        return false;   // no king (only in test positions)
    }

    public GameStatus status() {
        if (legalMoves().isEmpty()) {
            return isInCheck(sideToMove) ? GameStatus.CHECKMATE : GameStatus.STALEMATE;
        }
        if (halfmoveClock >= 100) return GameStatus.DRAW_FIFTY_MOVES;
        if (insufficientMaterial()) return GameStatus.DRAW_INSUFFICIENT_MATERIAL;
        return GameStatus.ONGOING;
    }

    private boolean insufficientMaterial() {
        int minors = 0;
        for (int i = 0; i < 64; i++) {
            PieceType t = types[i];
            if (t == null || t == KING) continue;
            if (t == BISHOP || t == KNIGHT) minors++;
            else return false;            // pawn, rook or queen => mate is possible
        }
        return minors <= 1;               // K vs K, or K+minor vs K
    }

    // ------------------------------------------------------------ making a move

    /** Applies a LEGAL move and returns the new position (handles castling, en passant, promotion). */
    public Position makeMove(Move m) {
        Position p = copy();
        int from = idx(m.from()), to = idx(m.to());
        PieceType t = types[from];
        PieceColor c = colors[from];
        boolean capture = types[to] != null;

        // en passant: pawn moves diagonally onto the empty en-passant square
        if (t == PAWN && m.to().equals(enPassant) && m.from().file() != m.to().file()) {
            p.clear(idx(m.to().file(), m.from().rank()));   // the captured pawn sits beside the start square
            capture = true;
        }

        p.set(to, m.promotion() != null ? m.promotion() : t, c);
        p.clear(from);

        // castling: the king moved two files, so also move the rook
        if (t == KING && Math.abs(m.to().file() - m.from().file()) == 2) {
            int r = m.from().rank();
            if (m.to().file() == 6) { p.set(idx(5, r), ROOK, c); p.clear(idx(7, r)); }
            else                    { p.set(idx(3, r), ROOK, c); p.clear(idx(0, r)); }
        }

        // castling rights: lost when the king moves, or a rook moves from / is captured on its corner
        if (t == KING) {
            if (c == WHITE) { p.castling[0] = false; p.castling[1] = false; }
            else            { p.castling[2] = false; p.castling[3] = false; }
        }
        p.loseRightsAt(from);
        p.loseRightsAt(to);

        // en passant target
        p.enPassant = (t == PAWN && Math.abs(m.to().rank() - m.from().rank()) == 2)
                ? new Square(m.from().file(), (m.from().rank() + m.to().rank()) / 2) : null;

        p.halfmoveClock = (t == PAWN || capture) ? 0 : halfmoveClock + 1;
        if (c == BLACK) p.fullmoveNumber = fullmoveNumber + 1;
        p.sideToMove = c.opposite();
        return p;
    }

    private void loseRightsAt(int i) {
        switch (i) {
            case 7 -> castling[0] = false;    // h1
            case 0 -> castling[1] = false;    // a1
            case 63 -> castling[2] = false;   // h8
            case 56 -> castling[3] = false;   // a8
            default -> { }
        }
    }

    // ------------------------------------------------------------ attacks

    private static final int[][] KNIGHT_JUMPS = {{1,2},{2,1},{2,-1},{1,-2},{-1,-2},{-2,-1},{-2,1},{-1,2}};
    static final int[][] ROOK_DIRS = {{1,0},{-1,0},{0,1},{0,-1}};
    static final int[][] BISHOP_DIRS = {{1,1},{1,-1},{-1,1},{-1,-1}};

    /** Is square `sq` attacked by any piece of color `by`? */
    boolean isAttacked(int sq, PieceColor by) {
        int f = sq % 8, r = sq / 8;

        int pr = r + (by == WHITE ? -1 : 1);          // a pawn of `by` attacks diagonally forward
        for (int df = -1; df <= 1; df += 2) {
            if (is(f + df, pr, by, PAWN)) return true;
        }
        for (int[] d : KNIGHT_JUMPS) if (is(f + d[0], r + d[1], by, KNIGHT)) return true;
        for (int df = -1; df <= 1; df++)
            for (int dr = -1; dr <= 1; dr++)
                if ((df != 0 || dr != 0) && is(f + df, r + dr, by, KING)) return true;

        return slider(f, r, by, ROOK_DIRS, ROOK) || slider(f, r, by, BISHOP_DIRS, BISHOP);
    }

    private boolean slider(int f, int r, PieceColor by, int[][] dirs, PieceType kind) {
        for (int[] d : dirs) {
            int x = f + d[0], y = r + d[1];
            while (Square.isValid(x, y)) {
                int i = idx(x, y);
                if (types[i] != null) {
                    if (colors[i] == by && (types[i] == kind || types[i] == QUEEN)) return true;
                    break;
                }
                x += d[0]; y += d[1];
            }
        }
        return false;
    }

    private boolean is(int f, int r, PieceColor c, PieceType t) {
        return Square.isValid(f, r) && types[idx(f, r)] == t && colors[idx(f, r)] == c;
    }

    // ------------------------------------------------------------ helpers

    static int idx(int file, int rank) { return rank * 8 + file; }
    static int idx(Square s) { return s.rank() * 8 + s.file(); }

    void set(int i, PieceType t, PieceColor c) { types[i] = t; colors[i] = c; }
    void clear(int i) { types[i] = null; colors[i] = null; }

    private Position copy() {
        Position p = new Position();
        System.arraycopy(types, 0, p.types, 0, 64);
        System.arraycopy(colors, 0, p.colors, 0, 64);
        System.arraycopy(castling, 0, p.castling, 0, 4);
        p.sideToMove = sideToMove;
        p.enPassant = enPassant;
        p.halfmoveClock = halfmoveClock;
        p.fullmoveNumber = fullmoveNumber;
        return p;
    }

    private static PieceType typeFromChar(char ch) {
        return switch (Character.toLowerCase(ch)) {
            case 'k' -> KING; case 'q' -> QUEEN; case 'r' -> ROOK;
            case 'b' -> BISHOP; case 'n' -> KNIGHT; case 'p' -> PAWN;
            default -> throw new IllegalArgumentException("bad piece char: " + ch);
        };
    }

    private static char charFromType(PieceType t) {
        return switch (t) {
            case KING -> 'k'; case QUEEN -> 'q'; case ROOK -> 'r';
            case BISHOP -> 'b'; case KNIGHT -> 'n'; case PAWN -> 'p';
        };
    }
}
