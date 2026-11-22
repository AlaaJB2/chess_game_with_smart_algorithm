package com.lab.board3d;

import javafx.geometry.Bounds;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Scale;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * يقرأ ملف STL (ثنائي أو نصي) ويحوله إلى MeshView جاهز للعرض.
 *
 * - محاور STL عادةً: Z للأعلى. محاور JavaFX: Y للأسفل.
 *   لذلك نحوّل (x, y, z) -> (x, -z, y) فيصبح "الأعلى" صحيحاً.
 * - المجسم يُوسَّط على المحورين X وZ، وقاعدته تُوضع عند Y = 0.
 * - scale: معامل تكبير/تصغير لتوحيد الوحدات مع الرقعة.
 * - maxTriangles: إذا كان الملف أثقل من هذا العدد يُبسَّط تلقائياً (انظر MeshSimplifier).
 *   0 = بدون تبسيط.
 */
public final class StlLoader {

    private StlLoader() {}

    /** بدون تبسيط. */
    public static MeshView load(Path path, double scale) throws IOException {
        return load(path, scale, 0);
    }

    public static MeshView load(Path path, double scale, int maxTriangles) throws IOException {
        byte[] data = Files.readAllBytes(path);
        float[] soup = isBinary(data) ? readBinary(data) : readAscii(data);
        data = null; // تحرير الذاكرة

        if (soup.length == 0) {
            throw new IOException("ملف STL فارغ أو غير مفهوم: " + path);
        }

        MeshSimplifier.Result r = MeshSimplifier.simplify(soup, maxTriangles);
        soup = null;

        // ---- تحويل المحاور (STL Z-up -> JavaFX) وحساب الحدود ----
        float[] p = r.points;
        int vc = p.length / 3;
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE, minY = Float.MAX_VALUE;
        for (int i = 0; i < vc; i++) {
            float x = p[3 * i];
            float y = -p[3 * i + 2];
            float z = p[3 * i + 1];
            p[3 * i] = x;
            p[3 * i + 1] = y;
            p[3 * i + 2] = z;
            minX = Math.min(minX, x); maxX = Math.max(maxX, x);
            minY = Math.min(minY, y); maxY = Math.max(maxY, y);
            minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
        }
        float cx = (minX + maxX) / 2f;
        float cz = (minZ + maxZ) / 2f;
        for (int i = 0; i < vc; i++) {
            p[3 * i]     = (float) ((p[3 * i] - cx) * scale);
            p[3 * i + 1] = (float) ((p[3 * i + 1] - maxY) * scale);
            p[3 * i + 2] = (float) ((p[3 * i + 2] - cz) * scale);
        }

        TriangleMesh mesh = new TriangleMesh();
        mesh.getTexCoords().addAll(0f, 0f);
        mesh.getPoints().addAll(p, 0, p.length);

        int tris = r.triangles;
        int[] faces = new int[tris * 6];
        for (int t = 0; t < tris; t++) {
            faces[6 * t]     = r.faces[3 * t];
            faces[6 * t + 1] = 0;
            faces[6 * t + 2] = r.faces[3 * t + 1];
            faces[6 * t + 3] = 0;
            faces[6 * t + 4] = r.faces[3 * t + 2];
            faces[6 * t + 5] = 0;
        }
        mesh.getFaces().addAll(faces, 0, faces.length);

        int[] smooth = new int[tris];
        Arrays.fill(smooth, 1);                       // تظليل ناعم (مجموعة تنعيم واحدة)
        mesh.getFaceSmoothingGroups().addAll(smooth, 0, tris);

        System.out.printf("STL: %s | مثلثات: %d -> %d | الحجم (x,y,z) = %.2f x %.2f x %.2f (قبل التكبير)%n",
                path.getFileName(), r.inputTriangles, tris,
                maxX - minX, maxY - minY, maxZ - minZ);

        MeshView view = new MeshView(mesh);
        view.setCullFace(CullFace.NONE); // يتجنب مشاكل اتجاه الوجوه
        return view;
    }

    /**
     * يحمّل الحجر ويكبّره/يصغّره تلقائياً بحيث يكون أكبر بُعد لقاعدته
     * (عرض أو عمق) يساوي targetFootprint. قاعدته عند Y = 0 ومركزه عند (0,0).
     */
    public static MeshView loadFitted(Path path, double targetFootprint) throws IOException {
        MeshView view = load(path, 1.0);
        Bounds b = view.getBoundsInLocal();
        double k = targetFootprint / Math.max(b.getWidth(), b.getDepth());
        view.getTransforms().add(new Scale(k, k, k, 0, 0, 0));
        return view;
    }

    private static boolean isBinary(byte[] d) {
        if (d.length < 84) return false;
        long n = ByteBuffer.wrap(d, 80, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL;
        return 84L + 50L * n == d.length;
    }

    /** يعيد 9 أرقام لكل مثلث. */
    private static float[] readBinary(byte[] d) {
        ByteBuffer buf = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN);
        int n = buf.getInt(80);
        float[] soup = new float[n * 9];
        for (int t = 0; t < n; t++) {
            int o = 84 + 50 * t + 12; // تخطي الناظم
            for (int k = 0; k < 9; k++) {
                soup[9 * t + k] = buf.getFloat(o + 4 * k);
            }
        }
        return soup;
    }

    private static float[] readAscii(byte[] d) {
        String text = new String(d, StandardCharsets.US_ASCII);
        float[] out = new float[3 * 3 * 1024];
        int n = 0;
        for (String line : text.split("\\R")) {
            String s = line.trim();
            if (s.startsWith("vertex")) {
                String[] t = s.split("\\s+");
                if (n + 3 > out.length) out = Arrays.copyOf(out, out.length * 2);
                out[n++] = Float.parseFloat(t[1]);
                out[n++] = Float.parseFloat(t[2]);
                out[n++] = Float.parseFloat(t[3]);
            }
        }
        return Arrays.copyOf(out, n - n % 9);
    }
}
