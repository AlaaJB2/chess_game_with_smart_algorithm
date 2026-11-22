package com.lab.board3d.rules;

/**
 * Self-test using "perft": count every reachable position to a given depth and compare with
 * the numbers published by the chess community. If all match, the rules are almost certainly right.
 * Run:  java -cp out com.lab.board3d.rules.Perft
 */
public final class Perft {

    static long perft(Position p, int depth) {
        if (depth == 0) return 1;
        long n = 0;
        for (Move m : p.legalMoves()) n += perft(p.makeMove(m), depth - 1);
        return n;
    }

    private static boolean ok = true;

    private static void check(String name, String fen, long... expected) {
        Position p = Position.fromFen(fen);
        for (int d = 1; d <= expected.length; d++) {
            long got = perft(p, d);
            boolean good = got == expected[d - 1];
            ok &= good;
            System.out.printf("%-12s depth %d: %8d %s%n", name, d, got, good ? "OK" : "WRONG (expected " + expected[d - 1] + ")");
        }
    }

    public static void main(String[] args) {
        check("start", Position.START_FEN, 20, 400, 8902, 197281);
        check("kiwipete", "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1", 48, 2039, 97862);
        check("endgame", "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1", 14, 191, 2812, 43238);
        check("promo+cast", "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1", 6, 264, 9467);
        check("position5", "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8", 44, 1486, 62379);

        // FEN round trip + game end
        String f = "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 2";
        ok &= Position.fromFen(f).toFen().equals(f);
        Position mate = Position.fromFen("rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3");
        ok &= mate.status() == GameStatus.CHECKMATE;
        System.out.println("FEN round-trip + fool's mate: " + (ok ? "OK" : "WRONG"));
        System.out.println(ok ? "ALL TESTS PASSED" : "SOME TESTS FAILED");
        System.exit(ok ? 0 : 1);
    }
}
