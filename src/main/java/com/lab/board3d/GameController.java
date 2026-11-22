package com.lab.board3d;

import com.lab.board3d.rules.GameStatus;
import com.lab.board3d.rules.Move;
import com.lab.board3d.rules.Position;
import javafx.animation.PauseTransition;
import javafx.util.Duration;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.lab.board3d.PieceType.KING;
import static com.lab.board3d.PieceType.PAWN;

/**
 * يربط محرك القواعد (Position) بالرقعة ثلاثية الأبعاد (BoardView).
 *
 *  - اللاعب يختار لونه (أبيض أو أسود)، والحاسوب يلعب اللون الآخر.
 *  - النقلة القانونية تُنفَّذ على الرقعة مع التبييت / الأخذ بالمرور / الترقية.
 *  - بعدها يلعب الحاسوب عبر Engine (PythonEngine في اللعبة الحقيقية).
 *  - المؤثرات الصوتية والاتجاه (من أي جهة تُرى الرقعة) تُرسل للخارج عبر مستمعين.
 *
 * ملاحظة: Position هي "الحقيقة"، والرقعة مجرد رسم لها.
 */
public final class GameController {

    /**
     * من يختار نقلة الحاسوب. غير متزامن حتى لا تتجمّد الواجهة أثناء التفكير:
     * عند الانتهاء يستدعي callback من خيط الواجهة. PythonEngine هو التنفيذ الحقيقي.
     */
    @FunctionalInterface
    public interface Engine {
        void chooseMove(Position position, Consumer<Move> callback);
    }

    private final BoardView view;
    private final Random random = new Random();

    private PieceColor human = PieceColor.WHITE;
    private Position position = Position.start();
    private boolean gameOver;
    private int gameId;                // يزيد مع كل لعبة جديدة كي نتجاهل ردود المحرك القديمة
    private boolean lastMoveCapture;   // هل كانت آخر نقلة أكلاً؟ (لاختيار الصوت)

    /** كم مرة ظهرت كل وضعية (للتعادل بالتكرار الثلاثي). */
    private final Map<String, Integer> seen = new HashMap<>();

    private Consumer<String> statusListener = System.out::println;
    private Consumer<GameSound> soundListener = s -> { };
    private Consumer<PieceColor> orientationListener = c -> { };

    /** من يختار حجر الترقية للاعب. الافتراضي وزير؛ Main يضع نافذة اختيار حقيقية. */
    private Supplier<PieceType> promotionChooser = () -> PieceType.QUEEN;

    /** المحرك الافتراضي: نقلة قانونية عشوائية (للتجربة فقط). */
    private Engine engine = (pos, callback) -> {
        List<Move> all = pos.legalMoves();
        callback.accept(all.get(random.nextInt(all.size())));
    };

    public GameController(BoardView view) {
        this.view = view;
    }

    public void setEngine(Engine engine) { this.engine = engine; }
    public void setStatusListener(Consumer<String> listener) { this.statusListener = listener; }
    public void setSoundListener(Consumer<GameSound> listener) { this.soundListener = listener; }
    /** يُستدعى عند بداية كل لعبة بلون اللاعب، لتدوير الكاميرا إلى جهته. */
    public void setOrientationListener(Consumer<PieceColor> listener) { this.orientationListener = listener; }
    public void setPromotionChooser(Supplier<PieceType> chooser) { this.promotionChooser = chooser; }

    public Position getPosition() { return position; }
    public boolean isGameOver() { return gameOver; }
    public PieceColor getHumanColor() { return human; }

    // =====================================================================
    //  بدء اللعب
    // =====================================================================

    /** لعبة جديدة بنفس اللون السابق. */
    public void start() { start(human); }

    /** لعبة جديدة: اللاعب بلون humanColor. إذا اختار الأسود يبدأ الحاسوب. */
    public void start(PieceColor humanColor) {
        human = humanColor;
        position = Position.start();
        resetState();
        view.setupInitialPosition();
        finishSetup();
    }

    /** لعبة جديدة بنفس اللون السابق. */
    public void newGame() { start(human); }

    /** نفس start(humanColor) (اسم أوضح عند الضغط على زر "لعبة جديدة"). */
    public void newGame(PieceColor humanColor) { start(humanColor); }

    /** يبدأ من وضعية FEN معيّنة بلون اللاعب الحالي (لتجربة حالات خاصة). */
    public void startFromFen(String fen) {
        position = Position.fromFen(fen);
        resetState();
        view.clearPieces();
        for (int rank = 0; rank < 8; rank++) {
            for (int file = 0; file < 8; file++) {
                Square sq = new Square(file, rank);
                if (position.typeAt(sq) != null) view.placePiece(position.typeAt(sq), position.colorAt(sq), sq);
            }
        }
        finishSetup();
    }

    private void resetState() {
        gameOver = false;
        gameId++;          // أي رد قديم من المحرك أو أنيميشن قديم يُتجاهل
        seen.clear();
        lastMoveCapture = false;
    }

    private void finishSetup() {
        view.setSelectableColor(human);
        view.setSelectionListener(this::onSelection);
        view.setMoveListener(this::onMoveRequested);
        orientationListener.accept(human);
        record();
        soundListener.accept(GameSound.GAME_START);
        announce();
        if (!gameOver && position.sideToMove() != human) computerTurn();   // اللاعب أسود: الحاسوب يبدأ
    }

    /** يسجّل الوضعية الحالية ويُرجع عدد مرات ظهورها. */
    private int record() {
        return seen.merge(position.repetitionKey(), 1, Integer::sum);
    }

    // =====================================================================
    //  اللاعب البشري
    // =====================================================================

    /** عند اختيار حجر: نُظهر الحركات القانونية له كنقاط زرقاء. */
    private void onSelection(Piece piece) {
        if (piece == null || gameOver || position.sideToMove() != human) return;
        Set<Square> targets = new LinkedHashSet<>();   // Set لأن الترقية تعطي 4 نقلات لنفس المربع
        for (Move m : position.legalMoves()) {
            if (m.from().equals(piece.getSquare())) targets.add(m.to());
        }
        view.highlightSquares(targets);
    }

    /**
     * يطلبها BoardView عند نقر اللاعب. نُنفّذ النقلة بأنفسنا (لأن BoardView لا يعرف
     * التبييت والترقية)، ولذلك نُرجع دائماً false حتى لا يحرّك BoardView الحجر مرة ثانية.
     */
    private boolean onMoveRequested(Piece piece, Square from, Square to) {
        if (gameOver || position.sideToMove() != human) return false;

        Move move = position.findLegal(from, to, null);
        if (move == null) {
            statusListener.accept("نقلة غير قانونية");
            return false;
        }
        if (move.promotion() != null) {   // ترقية: نسأل اللاعب عن الحجر
            Move chosen = position.findLegal(from, to, promotionChooser.get());
            if (chosen != null) move = chosen;
        }
        applyMove(move, this::afterMove);
        return false;
    }

    // =====================================================================
    //  تنفيذ نقلة على الرقعة
    // =====================================================================

    private void applyMove(Move m, Runnable after) {
        Square from = m.from(), to = m.to();
        Piece piece = view.getPiece(from);
        PieceColor color = piece.getColor();

        boolean enPassant = piece.getType() == PAWN && to.equals(position.enPassantSquare()) && from.file() != to.file();
        lastMoveCapture = enPassant || position.typeAt(to) != null;

        // الأخذ بالمرور: البيدق المأكول بجانب مربع البداية وليس على مربع الهدف
        if (enPassant) {
            view.removePiece(new Square(to.file(), from.rank()));
        }

        // التبييت: الملك يتحرك مربعين، فنحرّك القلعة أيضاً
        if (piece.getType() == KING && Math.abs(to.file() - from.file()) == 2) {
            boolean kingSide = to.file() == 6;
            view.movePiece(new Square(kingSide ? 7 : 0, from.rank()),
                           new Square(kingSide ? 5 : 3, from.rank()));
        }

        position = position.makeMove(m);   // نحدّث الحقيقة أولاً

        final int id = gameId;
        view.movePiece(from, to, () -> {
            if (id != gameId) return;   // بدأت لعبة جديدة أثناء الأنيميشن
            if (m.promotion() != null) {
                view.placePiece(m.promotion(), color, to);   // يستبدل البيدق بالحجر الجديد
            }
            after.run();
        });
    }

    /** بعد انتهاء حركة الحجر على الرقعة: نفحص النتيجة، نشغّل الصوت، ونمرّر الدور. */
    private void afterMove() {
        boolean capture = lastMoveCapture;

        if (record() >= 3) {   // نفس الوضعية ثلاث مرات
            gameOver = true;
            statusListener.accept("تعادل: تكرار الوضعية ثلاث مرات");
            soundListener.accept(GameSound.DRAW);
            return;
        }

        GameStatus status = announce();
        soundListener.accept(switch (status) {
            case CHECKMATE -> GameSound.CHECKMATE;
            case STALEMATE, DRAW_FIFTY_MOVES, DRAW_INSUFFICIENT_MATERIAL -> GameSound.DRAW;
            case ONGOING -> position.isInCheck(position.sideToMove()) ? GameSound.CHECK
                          : capture ? GameSound.CAPTURE : GameSound.MOVE;
        });

        if (!gameOver && position.sideToMove() != human) computerTurn();
    }

    // =====================================================================
    //  الحاسوب
    // =====================================================================

    private void computerTurn() {
        PauseTransition pause = new PauseTransition(Duration.millis(400));
        final int id = gameId;
        final Position asked = position;
        pause.setOnFinished(e -> engine.chooseMove(asked, m -> {
            if (id != gameId || gameOver || position != asked) return;   // رد قديم: تجاهله
            applyMove(m, this::afterMove);
        }));
        pause.play();
    }

    // =====================================================================
    //  الرسائل ونهاية اللعبة
    // =====================================================================

    private static String name(PieceColor c) { return c == PieceColor.WHITE ? "الأبيض" : "الأسود"; }

    private GameStatus announce() {
        GameStatus st = position.status();
        PieceColor toMove = position.sideToMove();
        switch (st) {
            case CHECKMATE -> {
                gameOver = true;
                PieceColor winner = toMove.opposite();
                statusListener.accept("كش مات! فاز " + name(winner) + (winner == human ? " (أنت)" : " (الحاسوب)"));
            }
            case STALEMATE -> {
                gameOver = true;
                statusListener.accept("تعادل: لا توجد نقلات قانونية (Stalemate)");
            }
            case DRAW_FIFTY_MOVES -> {
                gameOver = true;
                statusListener.accept("تعادل: قاعدة الخمسين نقلة");
            }
            case DRAW_INSUFFICIENT_MATERIAL -> {
                gameOver = true;
                statusListener.accept("تعادل: مادة غير كافية للكش مات");
            }
            case ONGOING -> {
                String msg = toMove == human ? "دورك (" + name(human) + ")" : "الحاسوب يفكر...";
                if (position.isInCheck(toMove)) msg += "  - كش!";
                statusListener.accept(msg);
            }
        }
        return st;
    }
}
