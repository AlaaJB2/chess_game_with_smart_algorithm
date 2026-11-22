package com.lab.board3d;

import javafx.animation.Interpolator;
import javafx.animation.Transition;
import javafx.geometry.Point3D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.MeshView;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * ===== الواجهة الرئيسية للرقعة والأحجار (هذا الكلاس هو ما يستخدمه زميلك) =====
 *
 * الإحداثيات: Square(file, rank) أو Square.of("e4")
 *    file 0..7 = a..h  ،  rank 0..7 = الصف 1..8   (الأبيض يبدأ من الصف 1)
 *
 * الاستخدام الأساسي:
 *    BoardView view = new BoardView(Path.of("models"), "_chiquier_final.stl", 3.0, true);
 *    root.getChildren().add(view.getNode());   // إضافة الرقعة للمشهد
 *    view.install(scene);                      // تفعيل النقر بالماوس
 *    view.setupInitialPosition();              // أو placePiece(...) لوضع أحجار يدوياً
 *    view.setMoveListener((piece, from, to) -> { ...; return true; });
 *
 * مهم: كل الدوال تُستدعى من خيط JavaFX. إذا كانت الخوارزمية تعمل في خيط
 * آخر فاستدعِ الدوال داخل Platform.runLater(() -> ...).
 */
public final class BoardView {

    // ---- إعدادات قابلة للتعديل ----
    public static final Color WHITE_COLOR = BoardLoader.LIGHT_SQUARE_COLOR;  // الأحجار الفاتحة = لون المربعات الفاتحة
    public static final Color BLACK_COLOR = BoardLoader.DARK_SQUARE_COLOR;   // الأحجار الداكنة = لون المربعات الداكنة
    public static final double PIECE_FILL = 0.7;       // نسبة أكبر حجر من عرض المربع
    public static final double PIECE_YAW_DEG = 180;    // اتجاه نظر الأحجار البيضاء (السوداء تُعاكسها تلقائياً). جرّب 0 / 90 / 180 / 270
    public static final double MOVE_MILLIS = 450;      // مدة حركة الحجر

    // تصحيح اتجاه نظر أحجار معيّنة (يُضاف فوق PIECE_YAW_DEG). السوداء تُعاكس البيضاء تلقائياً.
    // إذا دار الحجر للجهة الغلط غيّر الإشارة (مثلاً -90 إلى 90) أو جرّب 180.
    public static final double KNIGHT_YAW_OFFSET = -90;
    public static final double QUEEN_YAW_OFFSET = 180;

    private static final Color SELECT_COLOR = Color.rgb(40, 200, 90, 0.55);
    private static final Color HINT_COLOR = Color.rgb(40, 140, 255, 0.75);

    private final Group root = new Group();
    private final Group piecesLayer = new Group();
    private final Group highlightLayer = new Group();

    private final BoardLoader.Board board;
    private final PieceLibrary library;
    private final Piece[][] grid = new Piece[8][8];   // [file][rank]

    private final PhongMaterial whiteMat = pieceMaterial(WHITE_COLOR);
    private final PhongMaterial blackMat = pieceMaterial(BLACK_COLOR);

    private Square selected;
    private Box selectionMarker;
    private final List<Node> hintMarkers = new ArrayList<>();

    private MoveListener moveListener;
    private Consumer<Piece> selectionListener;
    private PieceColor selectableColor;   // null = يمكن اختيار الجميع
    private int activeAnimations;

    /**
     * @param modelsDir  مجلد ملفات STL (الرقعة والأحجار)
     * @param boardFile  اسم ملف الرقعة داخل المجلد
     * @param boardScale معامل تكبير الرقعة (3.0 => عرضها 768)
     * @param darkOnA1   true: المربع a1 بلون المربعات الداكنة (الكحلي)
     */
    public BoardView(Path modelsDir, String boardFile, double boardScale, boolean darkOnA1) throws IOException {
        this.board = BoardLoader.load(modelsDir.resolve(boardFile), boardScale, darkOnA1);
        this.library = PieceLibrary.load(modelsDir, board.cell, PIECE_FILL);

        highlightLayer.setMouseTransparent(true);   // لا تعترض النقرات
        root.getChildren().addAll(board.node, piecesLayer, highlightLayer);
    }

    // =====================================================================
    //  المشهد
    // =====================================================================

    /** العقدة التي تضيفها للمشهد. لا تُطبّق عليها تحويلات (تحريك/تدوير) حتى يبقى النقر دقيقاً. */
    public Group getNode() { return root; }

    /** عرض المربع بوحدات المشهد. */
    public double getCellSize() { return board.cell; }

    /** تفعيل النقر بالماوس (اضغط على حجر ثم على مربع). */
    public void install(Scene scene) {
        scene.addEventHandler(MouseEvent.MOUSE_CLICKED, this::handleClick);
    }

    // =====================================================================
    //  الأحجار
    // =====================================================================

    /** يضع حجراً على مربع (إذا كان فيه حجر يُستبدل). */
    public Piece placePiece(PieceType type, PieceColor color, Square sq) {
        if (!library.has(type)) {
            throw new IllegalStateException("لا يوجد ملف STL للحجر " + type + " في مجلد models");
        }
        if (grid[sq.file()][sq.rank()] != null) {
            removePiece(sq);
        }

        MeshView mesh = library.create(type);
        mesh.setMaterial(color == PieceColor.WHITE ? whiteMat : blackMat);

        Group node = new Group(mesh);
        double yaw = PIECE_YAW_DEG + yawOffset(type) + (color == PieceColor.BLACK ? 180 : 0);
        node.getTransforms().add(new Rotate(yaw, Rotate.Y_AXIS));

        Point3D c = board.squareCenter(sq.file(), sq.rank());
        node.setTranslateX(c.getX());
        node.setTranslateZ(c.getZ());

        Piece piece = new Piece(type, color, sq, node);
        node.setUserData(piece);
        node.setId(piece.getName());

        piecesLayer.getChildren().add(node);
        grid[sq.file()][sq.rank()] = piece;
        return piece;
    }

    /** يزيل الحجر الموجود على المربع (إن وُجد). */
    public void removePiece(Square sq) {
        Piece p = grid[sq.file()][sq.rank()];
        if (p == null) return;
        if (sq.equals(selected)) clearSelection();
        piecesLayer.getChildren().remove(p.node);
        grid[sq.file()][sq.rank()] = null;
    }

    /** يزيل كل الأحجار. */
    public void clearPieces() {
        clearSelection();
        piecesLayer.getChildren().clear();
        for (Piece[] column : grid) java.util.Arrays.fill(column, null);
    }

    /** الحجر على المربع أو null. */
    public Piece getPiece(Square sq) {
        return grid[sq.file()][sq.rank()];
    }

    /** كل الأحجار الموجودة حالياً. */
    public List<Piece> getPieces() {
        List<Piece> out = new ArrayList<>();
        for (Piece[] column : grid) {
            for (Piece p : column) if (p != null) out.add(p);
        }
        return out;
    }

    /** الوضع الابتدائي للشطرنج (32 حجراً). */
    public void setupInitialPosition() {
        clearPieces();
        PieceType[] back = {PieceType.ROOK, PieceType.KNIGHT, PieceType.BISHOP, PieceType.QUEEN,
                            PieceType.KING, PieceType.BISHOP, PieceType.KNIGHT, PieceType.ROOK};
        for (int f = 0; f < 8; f++) {
            tryPlace(back[f], PieceColor.WHITE, new Square(f, 0));
            tryPlace(PieceType.PAWN, PieceColor.WHITE, new Square(f, 1));
            tryPlace(PieceType.PAWN, PieceColor.BLACK, new Square(f, 6));
            tryPlace(back[f], PieceColor.BLACK, new Square(f, 7));
        }
    }

    private static double yawOffset(PieceType type) {
        return switch (type) {
            case KNIGHT -> KNIGHT_YAW_OFFSET;
            case QUEEN -> QUEEN_YAW_OFFSET;
            default -> 0;
        };
    }

    private void tryPlace(PieceType t, PieceColor c, Square s) {
        if (library.has(t)) placePiece(t, c, s);
    }

    /**
     * يحرك حجراً من مربع إلى آخر بأنيميشن (قفزة قوسية).
     * إذا كان على المربع الهدف حجر فإنه يُؤكل (يُزال) عند وصول الحجر.
     */
    public void movePiece(Square from, Square to) {
        movePiece(from, to, null);
    }

    /** نفس السابق، مع Runnable يُنفَّذ بعد انتهاء الحركة (مفيد لتسلسل حركات الخوارزمية). */
    public void movePiece(Square from, Square to, Runnable onFinished) {
        Piece piece = getPiece(from);
        if (piece == null) {
            throw new IllegalArgumentException("لا يوجد حجر على " + from);
        }
        Piece captured = getPiece(to);
        if (captured == piece) return;
        if (selected != null) clearSelection();

        grid[from.file()][from.rank()] = null;
        grid[to.file()][to.rank()] = piece;
        piece.setSquare(to);

        Point3D a = board.squareCenter(from.file(), from.rank());
        Point3D b = board.squareCenter(to.file(), to.rank());
        double lift = board.cell * 0.9;

        Transition anim = new Transition() {
            {
                setCycleDuration(Duration.millis(MOVE_MILLIS));
                setInterpolator(Interpolator.EASE_BOTH);
            }

            @Override
            protected void interpolate(double f) {
                piece.node.setTranslateX(a.getX() + (b.getX() - a.getX()) * f);
                piece.node.setTranslateZ(a.getZ() + (b.getZ() - a.getZ()) * f);
                piece.node.setTranslateY(-Math.sin(Math.PI * f) * lift);
            }
        };
        anim.setOnFinished(ev -> {
            piece.node.setTranslateX(b.getX());
            piece.node.setTranslateZ(b.getZ());
            piece.node.setTranslateY(0);
            if (captured != null) piecesLayer.getChildren().remove(captured.node);
            activeAnimations--;
            if (onFinished != null) onFinished.run();
        });
        activeAnimations++;
        anim.play();
    }

    /** هل هناك حركة قيد التنفيذ؟ (النقر يُتجاهل أثناءها) */
    public boolean isAnimating() { return activeAnimations > 0; }

    // =====================================================================
    //  التفاعل مع اللاعب
    // =====================================================================

    /** يُستدعى عند طلب اللاعب حركة. بدون listener تُسمح كل الحركات. */
    public void setMoveListener(MoveListener listener) { this.moveListener = listener; }

    /**
     * يُستدعى عند اختيار حجر (piece) أو إلغاء الاختيار (null).
     * مفيد لعرض الحركات المتاحة عبر highlightSquares(...).
     */
    public void setSelectionListener(Consumer<Piece> listener) { this.selectionListener = listener; }

    /** حصر اللاعب باختيار أحجار لون واحد (null = الجميع). */
    public void setSelectableColor(PieceColor color) { this.selectableColor = color; }

    /** الحجر المختار حالياً أو null. */
    public Piece getSelectedPiece() { return selected == null ? null : getPiece(selected); }

    /** يعلّم مربعات (نقاط زرقاء) مثلاً لإظهار الحركات الممكنة. */
    public void highlightSquares(Collection<Square> squares) {
        clearHighlights();
        PhongMaterial mat = new PhongMaterial(HINT_COLOR);
        for (Square s : squares) {
            Point3D c = board.squareCenter(s.file(), s.rank());
            Cylinder dot = new Cylinder(board.cell * 0.17, 1.2);
            dot.setMaterial(mat);
            dot.setTranslateX(c.getX());
            dot.setTranslateY(-0.6);
            dot.setTranslateZ(c.getZ());
            highlightLayer.getChildren().add(dot);
            hintMarkers.add(dot);
        }
    }

    public void clearHighlights() {
        highlightLayer.getChildren().removeAll(hintMarkers);
        hintMarkers.clear();
    }

    public void clearSelection() {
        boolean had = selected != null;
        selected = null;
        if (selectionMarker != null) {
            highlightLayer.getChildren().remove(selectionMarker);
            selectionMarker = null;
        }
        clearHighlights();
        if (had && selectionListener != null) selectionListener.accept(null);
    }

    /** اختيار حجر برمجياً (نفس أثر النقر عليه). */
    public void select(Square sq) {
        Piece p = getPiece(sq);
        if (p == null) return;
        clearHighlights();
        if (selectionMarker != null) highlightLayer.getChildren().remove(selectionMarker);

        Point3D c = board.squareCenter(sq.file(), sq.rank());
        selectionMarker = new Box(board.cell * 0.96, 1.2, board.cell * 0.96);
        selectionMarker.setMaterial(new PhongMaterial(SELECT_COLOR));
        selectionMarker.setTranslateX(c.getX());
        selectionMarker.setTranslateY(-0.6);
        selectionMarker.setTranslateZ(c.getZ());
        highlightLayer.getChildren().add(selectionMarker);

        selected = sq;
        if (selectionListener != null) selectionListener.accept(p);
    }

    // =====================================================================
    //  معالجة النقر
    // =====================================================================

    private void handleClick(MouseEvent e) {
        if (e.getButton() != MouseButton.PRIMARY || !e.isStillSincePress() || isAnimating()) return;
        onSquareClicked(resolveSquare(e.getPickResult()));
    }

    /** يحوّل ما نُقر عليه (حجر أو مربع) إلى مربع، أو null إن كان خارج الرقعة. */
    private Square resolveSquare(PickResult pick) {
        for (Node n = pick.getIntersectedNode(); n != null; n = n.getParent()) {
            if (n.getUserData() instanceof Piece piece) {
                return piece.getSquare();
            }
            if (n == board.node) {
                Point3D p = pick.getIntersectedPoint();
                return p == null ? null : board.squareAt(p.getX(), p.getZ());
            }
        }
        return null;
    }

    private void onSquareClicked(Square sq) {
        if (sq == null) {
            clearSelection();
            return;
        }
        Piece clicked = getPiece(sq);

        if (selected == null) {
            if (clicked != null && canSelect(clicked)) select(sq);
            return;
        }
        if (sq.equals(selected)) {
            clearSelection();
            return;
        }
        Piece mover = getPiece(selected);
        if (clicked != null && clicked.getColor() == mover.getColor()) {
            select(sq);   // تبديل الاختيار إلى حجر آخر من نفس اللون
            return;
        }

        Square from = selected;
        boolean ok = moveListener == null || moveListener.onMoveRequested(mover, from, sq);
        clearSelection();
        if (ok) movePiece(from, sq);
    }

    private boolean canSelect(Piece p) {
        return selectableColor == null || p.getColor() == selectableColor;
    }

    private static PhongMaterial pieceMaterial(Color c) {
        PhongMaterial m = new PhongMaterial();
        m.setDiffuseColor(c);
        m.setSpecularColor(Color.web("#707070"));
        m.setSpecularPower(40);
        return m;
    }
}
