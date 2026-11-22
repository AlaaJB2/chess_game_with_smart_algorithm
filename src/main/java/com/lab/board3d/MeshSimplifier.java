package com.lab.board3d;

import java.util.Arrays;

/**
 * يبسّط شبكة مثلثات ضخمة (حساء مثلثات من STL) بطريقة "التجميع على شبكة منتظمة":
 *   1) تُقسَّم المساحة إلى خلايا مكعبة بحجم h.
 *   2) كل الرؤوس داخل خلية واحدة تُدمج في رأس واحد (متوسطها).
 *   3) المثلثات التي انهارت (رأسان أو أكثر في نفس الخلية) تُحذف.
 * النتيجة شبكة مفهرسة (رؤوس مشتركة) أصغر بكثير، وشكلها العام محفوظ.
 *
 * لا يعتمد على JavaFX. الإحداثيات تبقى بنفس محاور ملف STL.
 */
final class MeshSimplifier {

    static final class Result {
        float[] points;       // xyz لكل رأس
        int[] faces;          // 3 فهارس لكل مثلث
        int triangles;
        int inputTriangles;
        double cellSize;
    }

    private MeshSimplifier() {}

    /**
     * @param soup         9 أرقام لكل مثلث (3 رؤوس × xyz)
     * @param maxTriangles العدد المستهدف تقريباً (0 أو أقل = لا تبسيط، فقط دمج الرؤوس المتطابقة)
     */
    static Result simplify(float[] soup, int maxTriangles) {
        int tris = soup.length / 9;

        // ---- الحدود والمساحة السطحية ----
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        double area = 0;
        for (int t = 0; t < tris; t++) {
            int o = 9 * t;
            for (int k = 0; k < 3; k++) {
                double x = soup[o + 3 * k], y = soup[o + 3 * k + 1], z = soup[o + 3 * k + 2];
                if (x < minX) minX = x; if (x > maxX) maxX = x;
                if (y < minY) minY = y; if (y > maxY) maxY = y;
                if (z < minZ) minZ = z; if (z > maxZ) maxZ = z;
            }
            double ux = soup[o + 3] - soup[o],     uy = soup[o + 4] - soup[o + 1], uz = soup[o + 5] - soup[o + 2];
            double vx = soup[o + 6] - soup[o],     vy = soup[o + 7] - soup[o + 1], vz = soup[o + 8] - soup[o + 2];
            double cx = uy * vz - uz * vy, cy = uz * vx - ux * vz, cz = ux * vy - uy * vx;
            area += 0.5 * Math.sqrt(cx * cx + cy * cy + cz * cz);
        }
        double extent = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
        double minCell = extent / 1_000_000.0;          // حتى لا تتجاوز الفهارس 21 بت

        boolean simplify = maxTriangles > 0 && tris > maxTriangles;
        double h = simplify ? Math.sqrt(2.0 * area / maxTriangles) : extent * 1e-5;
        h = Math.max(h, minCell);

        Result best = cluster(soup, tris, minX, minY, minZ, h);
        if (simplify) {
            // عدد المثلثات يتناسب تقريباً مع 1/h² ، نضبط h حتى نقترب من الهدف
            for (int attempt = 0; attempt < 4
                    && (best.triangles > maxTriangles * 1.3 || best.triangles < maxTriangles * 0.6); attempt++) {
                h = Math.max(minCell, h * Math.sqrt((double) best.triangles / maxTriangles));
                best = cluster(soup, tris, minX, minY, minZ, h);
            }
        }
        return best;
    }

    private static Result cluster(float[] s, int tris, double minX, double minY, double minZ, double h) {
        int corners = tris * 3;
        int[] ids = new int[corners];
        LongIntMap map = new LongIntMap(1 << 16);
        double[] sum = new double[3 * (1 << 16)];
        int[] cnt = new int[1 << 16];
        int clusters = 0;
        double inv = 1.0 / h;

        for (int i = 0; i < corners; i++) {
            float x = s[3 * i], y = s[3 * i + 1], z = s[3 * i + 2];
            long ix = (long) ((x - minX) * inv);
            long iy = (long) ((y - minY) * inv);
            long iz = (long) ((z - minZ) * inv);
            long key = (ix << 42) | (iy << 21) | iz;
            int id = map.get(key);
            if (id < 0) {
                id = clusters++;
                map.put(key, id);
                if (id >= cnt.length) {
                    cnt = Arrays.copyOf(cnt, cnt.length * 2);
                    sum = Arrays.copyOf(sum, sum.length * 2);
                }
            }
            ids[i] = id;
            sum[3 * id] += x;
            sum[3 * id + 1] += y;
            sum[3 * id + 2] += z;
            cnt[id]++;
        }

        int kept = 0;
        for (int t = 0; t < tris; t++) {
            int a = ids[3 * t], b = ids[3 * t + 1], c = ids[3 * t + 2];
            if (a != b && b != c && a != c) kept++;
        }
        int[] faces = new int[kept * 3];
        int f = 0;
        for (int t = 0; t < tris; t++) {
            int a = ids[3 * t], b = ids[3 * t + 1], c = ids[3 * t + 2];
            if (a != b && b != c && a != c) {
                faces[f++] = a;
                faces[f++] = b;
                faces[f++] = c;
            }
        }
        float[] pts = new float[clusters * 3];
        for (int c = 0; c < clusters; c++) {
            pts[3 * c]     = (float) (sum[3 * c] / cnt[c]);
            pts[3 * c + 1] = (float) (sum[3 * c + 1] / cnt[c]);
            pts[3 * c + 2] = (float) (sum[3 * c + 2] / cnt[c]);
        }

        Result r = new Result();
        r.points = pts;
        r.faces = faces;
        r.triangles = kept;
        r.inputTriangles = tris;
        r.cellSize = h;
        return r;
    }

    /** جدول تجزئة مفتوح العنونة: مفتاح long (غير سالب) -> int. أخف بكثير من HashMap. */
    private static final class LongIntMap {
        private long[] keys;
        private int[] vals;
        private int size;
        private int mask;

        LongIntMap(int capacityPow2) {
            keys = new long[capacityPow2];
            vals = new int[capacityPow2];
            Arrays.fill(keys, -1L);
            mask = capacityPow2 - 1;
        }

        private int slot(long key, int mask) {
            long h = key * 0x9E3779B97F4A7C15L;
            return (int) (h >>> 40) & mask;
        }

        int get(long key) {
            int i = slot(key, mask);
            while (keys[i] != -1L) {
                if (keys[i] == key) return vals[i];
                i = (i + 1) & mask;
            }
            return -1;
        }

        void put(long key, int val) {
            if ((size + 1) * 2 > keys.length) grow();
            int i = slot(key, mask);
            while (keys[i] != -1L && keys[i] != key) i = (i + 1) & mask;
            if (keys[i] == -1L) size++;
            keys[i] = key;
            vals[i] = val;
        }

        private void grow() {
            long[] ok = keys;
            int[] ov = vals;
            int cap = ok.length * 2;
            keys = new long[cap];
            vals = new int[cap];
            Arrays.fill(keys, -1L);
            mask = cap - 1;
            for (int j = 0; j < ok.length; j++) {
                if (ok[j] != -1L) {
                    int i = slot(ok[j], mask);
                    while (keys[i] != -1L) i = (i + 1) & mask;
                    keys[i] = ok[j];
                    vals[i] = ov[j];
                }
            }
        }
    }
}
