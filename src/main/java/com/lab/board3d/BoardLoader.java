package com.lab.board3d;

import javafx.geometry.Point3D;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * يحمّل ملف الرقعة STL ويبنيها من 3 أجزاء بألوان مختلفة:
 * مربعات داكنة + مربعات فاتحة + إطار/قاعدة.
 *
 * بعد التحميل: مركز الرقعة عند (0,0)، وسطح المربعات عند Y = 0.
 * استخدم board.squareCenter(file, rank) لمعرفة مكان أي مربع (لوضع الأحجار).
 */
public final class BoardLoader {

    // ---- الألوان (غيّرها كما تشاء) ----
    public static final Color DARK_SQUARE_COLOR  = Color.web("#8B3A32");   // خشب ماهوغني (الأحجار الداكنة تستخدم نفس اللون)
    public static final Color LIGHT_SQUARE_COLOR = Color.web("#E6DAD4");   // كريمي (الأحجار الفاتحة تستخدم نفس اللون)
    public static final Color FRAME_COLOR        = Color.web("#5A1F1B");   // الإطار: خشب أغمق

    private BoardLoader() {}

    public static final class Board {
        public final Group node = new Group();
        public final double cell;       // عرض المربع (بعد التكبير)
        public final double width;      // عرض الرقعة كاملة
        public final double thickness;  // سماكة الرقعة تحت سطح المربعات
        private final double[] fileX;
        private final double[] rankZ;

        Board(BoardSplitter.Result r) {
            this.cell = r.cell;
            this.width = r.width;
            this.thickness = r.thickness;
            this.fileX = r.fileX;
            this.rankZ = r.rankZ;
        }

        /**
         * مركز المربع على سطح الرقعة.
         * @param file العمود 0..7  (0 = a ، 7 = h)
         * @param rank الصف   0..7  (0 = الصف 1 الأقرب للكاميرا ، 7 = الصف 8)
         */
        public Point3D squareCenter(int file, int rank) {
            return new Point3D(fileX[file], 0, rankZ[rank]);
        }

        /**
         * المربع الذي تقع عليه النقطة (x, z) بإحداثيات الرقعة، أو null إذا كانت
         * النقطة خارج المربعات (على الإطار).
         */
        public Square squareAt(double x, double z) {
            double half = 4 * cell;
            int f = (int) Math.floor((x + half) / cell);
            int r = (int) Math.floor((z + half) / cell);
            return Square.isValid(f, r) ? new Square(f, r) : null;
        }
    }

    public static Board load(Path path, double scale, boolean darkOnA1) throws IOException {
        BoardSplitter.Result r = BoardSplitter.split(Files.readAllBytes(path), scale, darkOnA1);

        int found = 0;
        for (int c : r.trisPerSquare) if (c > 0) found++;
        System.out.printf("BOARD: %s | مربعات: %d/64 | داكن: %d | فاتح: %d | إطار: %d مثلث | عرض المربع: %.1f%n",
                path.getFileName(), found, r.goldTris, r.silverTris, r.frameTris, r.cell);

        Board board = new Board(r);
        board.node.getChildren().addAll(
                toView(r.frame,  r.frameTris,  material(FRAME_COLOR,        Color.web("#3A1A17"), 20)),
                toView(r.gold,   r.goldTris,   material(DARK_SQUARE_COLOR,  Color.web("#6A5550"), 35)),
                toView(r.silver, r.silverTris, material(LIGHT_SQUARE_COLOR, Color.web("#9A9090"), 60)));
        return board;
    }

    private static PhongMaterial material(Color diffuse, Color specular, double power) {
        PhongMaterial m = new PhongMaterial();
        m.setDiffuseColor(diffuse);
        m.setSpecularColor(specular);
        m.setSpecularPower(power);
        return m;
    }

    private static MeshView toView(float[] pts, int tris, PhongMaterial mat) {
        TriangleMesh mesh = new TriangleMesh();
        mesh.getTexCoords().addAll(0f, 0f);
        mesh.getPoints().addAll(pts, 0, pts.length);

        int[] faces = new int[tris * 6];
        for (int i = 0; i < tris; i++) {
            faces[6 * i]     = 3 * i;
            faces[6 * i + 1] = 0;
            faces[6 * i + 2] = 3 * i + 1;
            faces[6 * i + 3] = 0;
            faces[6 * i + 4] = 3 * i + 2;
            faces[6 * i + 5] = 0;
        }
        mesh.getFaces().addAll(faces, 0, faces.length);
        mesh.getFaceSmoothingGroups().addAll(new int[tris], 0, tris); // تظليل مسطّح

        MeshView view = new MeshView(mesh);
        view.setMaterial(mat);
        view.setCullFace(CullFace.NONE);
        return view;
    }
}
