package com.lab.board3d;

import javafx.geometry.Bounds;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Scale;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * يبحث في مجلد models عن ملف STL لكل نوع حجر (حسب الاسم: KING, QUEEN, ROOK,
 * BISHOP, KNIGHT, PAWN داخل اسم الملف) ويحمّله مرة واحدة فقط.
 * كل الأحجار تُكبَّر بنفس المعامل (حتى تبقى النسب بينها كما صمّمتها).
 */
final class PieceLibrary {

    /** الحد الأقصى لعدد مثلثات كل حجر (الأثقل يُبسَّط تلقائياً). قلّله إذا كان البرنامج بطيئاً. */
    static final int MAX_TRIANGLES_PER_PIECE = 50_000;

    private static final Map<PieceType, String[]> KEYWORDS = new EnumMap<>(PieceType.class);
    static {
        KEYWORDS.put(PieceType.KING,   new String[]{"KING"});
        KEYWORDS.put(PieceType.QUEEN,  new String[]{"QUEEN"});
        KEYWORDS.put(PieceType.ROOK,   new String[]{"ROOK", "CASTLE", "TOWER"});
        KEYWORDS.put(PieceType.BISHOP, new String[]{"BISHOP"});
        KEYWORDS.put(PieceType.KNIGHT, new String[]{"KNIGHT", "HORSE"});
        KEYWORDS.put(PieceType.PAWN,   new String[]{"PAWN"});
    }

    private final Map<PieceType, TriangleMesh> meshes = new EnumMap<>(PieceType.class);
    private double scale = 1.0;

    private PieceLibrary() {}

    /**
     * @param dir  مجلد ملفات STL
     * @param cell عرض مربع الرقعة
     * @param fill النسبة التي يشغلها أكبر حجر من عرض المربع (مثلاً 0.7)
     */
    static PieceLibrary load(Path dir, double cell, double fill) throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".stl"))
             .filter(p -> !p.getFileName().toString().toLowerCase(Locale.ROOT).contains("chiquier"))
             .sorted()
             .forEach(files::add);
        }

        PieceLibrary lib = new PieceLibrary();
        List<Path> used = new ArrayList<>();
        double maxFootprint = 0;

        for (PieceType type : PieceType.values()) {
            Path found = find(files, type);
            if (found == null) {
                System.out.println("تحذير: لا يوجد ملف STL للحجر " + type
                        + " (يجب أن يحتوي اسم الملف على: " + String.join(" / ", KEYWORDS.get(type)) + ")");
                continue;
            }
            MeshView mv = StlLoader.load(found, 1.0, MAX_TRIANGLES_PER_PIECE);
            Bounds b = mv.getBoundsInLocal();
            maxFootprint = Math.max(maxFootprint, Math.max(b.getWidth(), b.getDepth()));
            lib.meshes.put(type, (TriangleMesh) mv.getMesh());
            used.add(found);
            System.out.println("PIECE: " + type + " <- " + found.getFileName());
        }

        if (maxFootprint > 0) {
            lib.scale = cell * fill / maxFootprint;
        }
        for (Path p : files) {
            if (!used.contains(p)) {
                System.out.println("ملف STL لم أطابقه مع أي حجر: " + p.getFileName());
            }
        }
        return lib;
    }

    private static Path find(List<Path> files, PieceType type) {
        for (Path p : files) {
            String name = p.getFileName().toString().toUpperCase(Locale.ROOT);
            for (String kw : KEYWORDS.get(type)) {
                if (name.contains(kw)) return p;
            }
        }
        return null;
    }

    boolean has(PieceType type) {
        return meshes.containsKey(type);
    }

    /** نسخة جديدة (MeshView) تشارك نفس الشبكة، بالحجم الصحيح وقاعدتها عند Y = 0. */
    MeshView create(PieceType type) {
        MeshView view = new MeshView(meshes.get(type));
        view.setCullFace(CullFace.NONE);
        view.getTransforms().add(new Scale(scale, scale, scale, 0, 0, 0));
        return view;
    }
}
