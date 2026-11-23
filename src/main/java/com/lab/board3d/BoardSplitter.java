package com.lab.board3d;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * يقرأ STL الثنائي للرقعة ويقسم مثلثاته إلى ثلاث مجموعات:
 *   - مربعات ذهبية
 *   - مربعات فضية
 *   - الإطار والقاعدة (كل ما تبقى)
 *
 * هذا الكلاس لا يعتمد على JavaFX (منطق هندسي فقط).
 *
 * افتراضات عن ملف الرقعة (تم قياسها من الملف نفسه):
 *   - المحور الرأسي (الأعلى) هو Y، والرقعة مفروشة على المستوي X/Z.
 *   - حول المربعات إطار عرضه 8 وحدات.
 *   - 8×8 مربعاً، سطحها العلوي عند Y = 3.4 مع أخاديد على شكل V بين المربعات.
 *
 * التحويل إلى محاور JavaFX (دوران 180° حول X، بدون انعكاس):
 *   x' = (x - مركز)·scale ،  y' = -(y - 3.4)·scale ،  z' = -(z - مركز)·scale
 * فيصبح سطح المربعات عند Y = 0، والرقعة مركزها (0,0).
 * الكاميرا تنظر من Z السالب، لذا الصف 1 (الأبيض) هو الأقرب للكاميرا.
 */
final class BoardSplitter {

    static final int N = 8;
    static final double BORDER = 8.0;         // عرض الإطار (وحدات الملف)
    static final double SQUARE_TOP_Y = 3.4;   // ارتفاع سطح المربعات (وحدات الملف)
    static final double GROOVE_TOL = 0.85;    // عمق الأخدود V + هامش

    static final class Result {
        float[] gold, silver, frame;          // 9 أرقام لكل مثلث (3 رؤوس × xyz)
        int goldTris, silverTris, frameTris;
        int[] trisPerSquare = new int[N * N];
        double[] fileX = new double[N];       // مركز كل عمود (a..h) بإحداثيات JavaFX
        double[] rankZ = new double[N];       // مركز كل صف (1..8) بإحداثيات JavaFX
        double cell;                          // عرض المربع الكامل بعد التكبير
        double width;                         // عرض الرقعة كاملة بعد التكبير
        double thickness;                     // سماكة الرقعة تحت سطح المربعات
    }

    private static final class Buf {
        float[] a = new float[9 * 4096];
        int n = 0; // عدد الأرقام
        void tri(float[] v) {
            if (n + 9 > a.length) a = Arrays.copyOf(a, a.length * 2);
            System.arraycopy(v, 0, a, n, 9);
            n += 9;
        }
        float[] done() { return Arrays.copyOf(a, n); }
    }

    static Result split(byte[] d, double scale, boolean goldOnA1) throws IOException {
        if (d.length < 84) throw new IOException("ملف STL صغير جداً");
        ByteBuffer b = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN);
        long n = b.getInt(80) & 0xFFFFFFFFL;
        if (84L + 50L * n != d.length) {
            throw new IOException("الرقعة يجب أن تكون STL ثنائي (Binary). عدد المثلثات المعلن لا يطابق حجم الملف.");
        }
        int tris = (int) n;

        // ---- المرور الأول: الحدود ----
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE, minY = Double.MAX_VALUE;
        for (int t = 0; t < tris; t++) {
            int o = 84 + 50 * t + 12;
            for (int k = 0; k < 3; k++) {
                double x = b.getFloat(o + 12 * k);
                double y = b.getFloat(o + 12 * k + 4);
                double z = b.getFloat(o + 12 * k + 8);
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
            }
        }
        double cx0 = (minX + maxX) / 2.0;
        double cz0 = (minZ + maxZ) / 2.0;
        double gridX0 = minX + BORDER;
        double gridZ0 = minZ + BORDER;
        double cell = (maxX - minX - 2 * BORDER) / N;
        double gridX1 = gridX0 + N * cell;
        double gridZ1 = gridZ0 + N * cell;

        Result r = new Result();
        r.cell = cell * scale;
        r.width = (maxX - minX) * scale;
        r.thickness = (SQUARE_TOP_Y - minY) * scale;
        for (int i = 0; i < N; i++) {
            double xs = gridX0 + (i + 0.5) * cell;
            r.fileX[i] = (xs - cx0) * scale;
            // الصف 1 عند أكبر z في الملف (الأقرب للكاميرا بعد التحويل)
            double zs = gridZ0 + ((N - 1 - i) + 0.5) * cell;
            r.rankZ[i] = -(zs - cz0) * scale;
        }

        // ---- المرور الثاني: التصنيف ----
        Buf gold = new Buf(), silver = new Buf(), frame = new Buf();
        float[] out = new float[9];
        for (int t = 0; t < tris; t++) {
            int o = 84 + 50 * t + 12;
            double sx = 0, sy = 0, sz = 0;
            for (int k = 0; k < 3; k++) {
                double x = b.getFloat(o + 12 * k);
                double y = b.getFloat(o + 12 * k + 4);
                double z = b.getFloat(o + 12 * k + 8);
                sx += x; sy += y; sz += z;
                out[3 * k]     = (float) ((x - cx0) * scale);
                out[3 * k + 1] = (float) (-(y - SQUARE_TOP_Y) * scale);
                out[3 * k + 2] = (float) (-(z - cz0) * scale);
            }
            double cx = sx / 3, cy = sy / 3, cz = sz / 3;

            boolean inGrid = cx > gridX0 && cx < gridX1
                          && cz > gridZ0 && cz < gridZ1
                          && cy > SQUARE_TOP_Y - GROOVE_TOL;
            if (!inGrid) {
                frame.tri(out);
                continue;
            }
            int col = (int) Math.floor((cx - gridX0) / cell);
            int row = (int) Math.floor((cz - gridZ0) / cell);
            col = Math.max(0, Math.min(N - 1, col));
            row = Math.max(0, Math.min(N - 1, row));
            int rank = N - 1 - row;               // 0 = الصف 1
            r.trisPerSquare[rank * N + col]++;
            boolean isGold = ((col + rank) % 2 == 0) == goldOnA1;
            (isGold ? gold : silver).tri(out);
        }
        r.gold = gold.done();     r.goldTris = gold.n / 9;
        r.silver = silver.done(); r.silverTris = silver.n / 9;
        r.frame = frame.done();   r.frameTris = frame.n / 9;
        return r;
    }
}
