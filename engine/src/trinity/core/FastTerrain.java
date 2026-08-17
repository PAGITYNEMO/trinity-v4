package trinity.core;

import trinity.core.Rng;
import trinity.poles.Ramanujan;

/**
 * Fast path terrain: the four-pole 3D density body as a point-evaluable
 * function. O(1) per block, infinite extent — this is what a chunk generator
 * can afford at world scale, where the full-array pipeline cannot run.
 *
 * It emulates Engine3D's accumulated state field: the bias skeleton times the
 * accumulation factor, plus the observation-pattern term and carved pockets.
 * The constants (4.0 / 3.0 / carve) are calibrated against the array pipeline
 * in Main3D --fast mode so both produce the same world statistics.
 */
public final class FastTerrain {
    public final long seed;
    public final int size;          // world height in the mod; grid size in standalone
    private final Ramanujan ram;
    private final int q1, q2, q3, q4;

    public FastTerrain(long seed, int size) {
        this.seed = seed;
        this.size = size;
        this.ram = new Ramanujan(64);
        Rng rng = new Rng(seed);
        int[] pr = ram.primes();
        q1 = pr[1 + rng.nextInt(pr.length - 2)];
        q2 = pr[1 + rng.nextInt(pr.length - 2)];
        int t3, t4;
        do { t3 = pr[1 + rng.nextInt(pr.length - 2)]; } while (t3 == q1 || t3 == q2);
        do { t4 = pr[1 + rng.nextInt(pr.length - 2)]; } while (t4 == q1 || t4 == q2 || t4 == t3);
        q3 = t3;
        q4 = t4;
    }

    public int[] templateQ() {
        return new int[]{q1, q2, q3, q4};
    }

    /**
     * Ore type at world coords: 0 none, 1 coal, 2 iron, 3 gold, 4 diamond.
     *
     * Vein structure placement: ore is NOT an intensity field. Each 16^3
     * lattice cell may host a vein BODY with a deterministic center, type
     * (depth-weighted draw), radius and anisotropy — and the body may extend
     * into neighboring cells along a deterministic direction, forming a chain
     * of connected ore bodies (整条矿脉). Totient-rich regions grow longer
     * chains. The same query returns the same answer anywhere in the world,
     * so chunk generation is consistent; bodies never cross lattice
     * boundaries uncontrolled (center jitter + radius stay inside the cell).
     */
    public int oreType(int x, int y, int z) {
        return oreType(seed, ram, q1, q2, x, y, z, size);
    }

    private static final int VEIN_P = 16;              // lattice period
    private static final double VEIN_CENTER_JITTER = 3.5; // <= 0.22 * P
    private static final double VEIN_MAX_R = 3.2;      // <= 0.20 * P (never crosses cells)
    private static final int[][] DIRS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    public static int oreType(long seed, Ramanujan ram, int q1, int q2,
                              int x, int y, int z, int h) {
        int ci = Math.floorDiv(x, VEIN_P);
        int cj = Math.floorDiv(y, VEIN_P);
        int ck = Math.floorDiv(z, VEIN_P);

        int own = veinBody(seed, ram, ci, cj, ck, x, y, z, h, false, -1, 0);
        if (own != 0) return own;

        // Chain continuations: a neighbor's vein may extend into this cell.
        for (int d = 0; d < 6; d++) {
            int ni = ci - DIRS[d][0];
            int nj = cj - DIRS[d][1];
            int nk = ck - DIRS[d][2];
            int t = veinBody(seed, ram, ni, nj, nk, x, y, z, h, true, d, 1);
            if (t != 0) return t;
        }
        return scatterOre(seed, x, y, z, h);
    }

    /**
     * The vein body at lattice cell (ci,cj,ck), evaluated at (x,y,z).
     * If fromNeighbor is set, this cell's vein must extend into the queried
     * cell along direction dir (segment k of the chain).
     */
    private static int veinBody(long seed, Ramanujan ram, int ci, int cj, int ck,
                                int x, int y, int z, int h,
                                boolean fromNeighbor, int dir, int seg) {
        double cy = cj * VEIN_P + VEIN_P / 2 + (Rng.hash3(ci, cj, ck, seed + 1) - 0.5) * VEIN_CENTER_JITTER * 2;
        double n = cy / h;
        double wgt = ram.regionWeight(ci * VEIN_P, ck * VEIN_P, seed);
        double pw = typeWeight(1, n) + typeWeight(2, n) + typeWeight(3, n) + typeWeight(4, n);
        double presence = Math.min(0.85, pw * 0.9 * (0.45 + 0.55 * wgt));
        if (Rng.hash3(ci, cj, ck, seed + 2) >= presence) return 0;

        // Chain direction & length: rich regions grow longer veins.
        int dirIdx = (int) (Rng.hash3(ci, cj, ck, seed + 9) * 6);
        int len = 1 + (int) (Rng.hash3(ci, cj, ck, seed + 10) * (1 + 2 * wgt));
        if (len > 3) len = 3;
        if (fromNeighbor) {
            if (dirIdx != dir || seg >= len) return 0;
        } else if (seg != 0) {
            return 0;
        }

        int[] dvec = DIRS[dirIdx];
        // Body cell for this query: chain cell index along the direction.
        int k = fromNeighbor ? seg : 0;
        int bcx = ci + dvec[0] * k;
        int bcy = cj + dvec[1] * k;
        int bcz = ck + dvec[2] * k;

        double cx = bcx * VEIN_P + VEIN_P / 2 + (Rng.hash3(ci, cj, ck, seed + 4) - 0.5) * VEIN_CENTER_JITTER * 2;
        double czz = bcz * VEIN_P + VEIN_P / 2 + (Rng.hash3(ci, cj, ck, seed + 5) - 0.5) * VEIN_CENTER_JITTER * 2;
        double rv = 1.8 + Rng.hash3(ci, cj, ck, seed + 6) * (VEIN_MAX_R - 1.8);
        if (len > 1) {
            rv *= 1 - 0.3 * (k / (double) (len - 1)); // taper along the chain
        }
        int axis = 1 + (int) (Rng.hash3(ci, cj, ck, seed + 7) * 3);
        double aniso = 1.4 + Rng.hash3(ci, cj, ck, seed + 8) * 1.6;

        double dx = (x - cx) / rv;
        double dy = (y - cy) / rv;
        double dz = (z - czz) / rv;
        switch (axis) {
            case 1 -> dx /= aniso;
            case 2 -> dy /= aniso;
            default -> dz /= aniso;
        }
        return dx * dx + dy * dy + dz * dz < 1.0 ? veinType(seed, ci, cj, ck, n) : 0;
    }

    /** Vein type: weighted draw from the depth profile at the vein center. */
    private static int veinType(long seed, int ci, int cj, int ck, double n) {
        double[] w = {typeWeight(1, n), typeWeight(2, n), typeWeight(3, n), typeWeight(4, n)};
        double sum = 0;
        for (double v : w) sum += v;
        double r = Rng.hash3(ci, cj, ck, seed + 3) * sum;
        for (int i = 0; i < 4; i++) {
            r -= w[i];
            if (r <= 0) return i + 1;
        }
        return 4;
    }

    /** Depth-weighted type profile (normalized height n, 0 = bottom). */
    private static double typeWeight(int type, double n) {
        return switch (type) {
            case 1 -> gauss(n, 0.32, 0.10) * 1.0;   // coal, shallow
            case 2 -> gauss(n, 0.20, 0.08) * 0.8;   // iron, mid
            case 3 -> gauss(n, 0.10, 0.06) * 0.45;  // gold, deep
            default -> gauss(n, 0.04, 0.04) * 0.30; // diamond, deepest
        };
    }

    /** Rare lone blocks outside veins (vanilla-style scatter). */
    private static int scatterOre(long seed, int x, int y, int z, int h) {
        double n = y / (double) h;
        double r = Rng.hash3(x, y, z, seed + 9);
        double acc = 0;
        for (int t = 1; t <= 4; t++) {
            acc += typeWeight(t, n) * 0.004;
            if (r < acc) return t;
        }
        return 0;
    }

    private static double gauss(double n, double peak, double sigma) {
        double d = (n - peak) / sigma;
        return Math.exp(-0.5 * d * d);
    }

    public Ramanujan ram() {
        return ram;
    }

    /** 2D terrain skeleton in [0,1] — mirrors Engine3D.surface01. */
    public double surface01(int x, int z) {
        double low01 = fbm2(x, z, seed, 4, 48.0) * 0.5 + 0.5;
        double det01 = fbm2(x, z, seed + 5, 3, 12.0) * 0.5 + 0.5;
        double tpl = ram.template(q1, q2, x, z);
        double wgt = ram.regionWeight(x, z, seed);
        int mu = ram.regionMu(x, z, seed);
        double hgt = mu == 0
                ? 0.55 * low01 + 0.15 * det01
                : 0.50 * low01 + 0.22 * det01 + 0.28 * Math.sqrt(tpl) * wgt;
        hgt = smoothstep(clamp01(hgt + 0.06));
        return hgt;
    }

    /**
     * Raw density at integer coords (y relative to the bottom of the body).
     * rich selects the observer-upgraded 4-q pattern for this column.
     */
    public double density(int x, int y, int z, boolean rich) {
        return densityAt(x, y, z, rich, surface01(x, z) * size);
    }

    /** Same, with the surface precomputed by the caller (per-column caching). */
    public double densityAt(int x, int y, int z, boolean rich, double surf) {
        double bias = (surf - y) / (size * 0.15);
        double ext = fbm3(x, y, z, seed, 4, 40.0);
        double vertical = (ram.c(q3, y) / ram.phiOf(q3) + 1) * 0.5;
        double t = y * 0.05;
        double breath = Math.sin(2 * Math.PI * 0.15 * t);
        double heartbeat = Math.pow(Math.max(0, Math.sin(2 * Math.PI * 1.2 * t)), 8);
        double internal = breath * 0.4 + heartbeat * 0.6;
        double pattern = rich
                ? 0.6 * ram.template4(q1, q2, q3, q4, x, z) + 0.4 * vertical
                : 0.6 * ram.template(q1, q2, x, z) + 0.4 * vertical;
        double rw = (Rng.hash3(x, y, z, seed + 777) - 0.5) * 2 * 0.05;
        double init = bias + ext * 0.30 + pattern * 0.30 * 0.6 + internal * 0.05 + rw;
        double obs = (pattern * 2 - 1) * 0.5;
        double carve = Rng.hash3(x, y, z, seed + 999) < 0.10 ? -2.2 : 0;
        return 4.0 * init + 3.0 * obs + carve;
    }

    /** Rich-column gate from a local-clarity map (chunk/block grid). */
    public static boolean richAt(double[][] rLocal, int bs, double thr, int x, int z) {
        return rLocal[z / bs][x / bs] >= thr;
    }

    // ---------------- noise helpers (mirrors Engine3D) ----------------

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double vnoise2(double x, double z, long salt) {
        int xi = (int) Math.floor(x), zi = (int) Math.floor(z);
        double xf = x - xi, zf = z - zi;
        double v00 = Rng.hash01(xi, zi, salt), v10 = Rng.hash01(xi + 1, zi, salt);
        double v01 = Rng.hash01(xi, zi + 1, salt), v11 = Rng.hash01(xi + 1, zi + 1, salt);
        double ux = xf * xf * (3 - 2 * xf), uz = zf * zf * (3 - 2 * zf);
        return lerp(lerp(v00, v10, ux), lerp(v01, v11, ux), uz) * 2 - 1;
    }

    private static double fbm2(double x, double z, long salt, int oct, double scale) {
        double acc = 0, amp = 0.5, f = 1.0 / scale, tot = 0;
        for (int i = 0; i < oct; i++) {
            acc += amp * vnoise2(x * f, z * f, salt + i * 101L);
            tot += amp;
            amp *= 0.5;
            f *= 2;
        }
        return acc / tot;
    }

    private static double vnoise3(double x, double y, double z, long salt) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y), zi = (int) Math.floor(z);
        double xf = x - xi, yf = y - yi, zf = z - zi;
        double ux = xf * xf * (3 - 2 * xf), uy = yf * yf * (3 - 2 * yf), uz = zf * zf * (3 - 2 * zf);
        double v000 = Rng.hash3(xi, yi, zi, salt), v100 = Rng.hash3(xi + 1, yi, zi, salt);
        double v010 = Rng.hash3(xi, yi + 1, zi, salt), v110 = Rng.hash3(xi + 1, yi + 1, zi, salt);
        double v001 = Rng.hash3(xi, yi, zi + 1, salt), v101 = Rng.hash3(xi + 1, yi, zi + 1, salt);
        double v011 = Rng.hash3(xi, yi + 1, zi + 1, salt), v111 = Rng.hash3(xi + 1, yi + 1, zi + 1, salt);
        double a = lerp(lerp(v000, v100, ux), lerp(v010, v110, ux), uy);
        double b = lerp(lerp(v001, v101, ux), lerp(v011, v111, ux), uy);
        return lerp(a, b, uz) * 2 - 1;
    }

    private static double fbm3(double x, double y, double z, long salt, int oct, double scale) {
        double acc = 0, amp = 0.5, f = 1.0 / scale, tot = 0;
        for (int i = 0; i < oct; i++) {
            acc += amp * vnoise3(x * f, y * f, z * f, salt + i * 101L);
            tot += amp;
            amp *= 0.5;
            f *= 2;
        }
        return acc / tot;
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    private static double smoothstep(double v) {
        return v * v * (3 - 2 * v);
    }
}
