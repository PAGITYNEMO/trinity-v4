package trinity.gen;

import trinity.core.Rng;
import trinity.poles.Axis;
import trinity.poles.Nemo;
import trinity.poles.Pagity;
import trinity.poles.Ramanujan;

import java.util.ArrayList;
import java.util.List;

/**
 * TRINITY v4.0 four-pole pipeline.
 *
 * 1. Noise field eta(x,t) initialized from external noise + internal
 *    breath/heartbeat oscillations + light-curtain injection
 *    (Ramanujan sum pattern; v4.0 section 2).
 * 2. Iterations of the reaction-diffusion state update (v4.0 section 4):
 *      Psi(t+1) = Psi(t) + D_eff * lap(Psi) + f(Psi) + I_total, then * decay
 *    with I_total = P[eta] + N[eta] + R[eta] * R(t).
 * 3. AXIS measures H/S/R/etaP/kappaN each round and adapts parameters.
 *
 * The final cognitive state field Psi is what the world generator consumes.
 */
public final class Engine {
    public static final int Q_MAX = 64;

    public static final class Result {
        public double[][] psi;      // final cognitive state field, normalized to [-1,1]
        public double[][] low;      // final filtered base band
        public double[][] detail;   // final detail band
        public Ramanujan ram;
        public Axis axis;
        public Axis.Params params;
        public List<String> history = new ArrayList<>();
        public String[] templateQ;  // q pairs chosen from seed
        public int[] tq;            // q1..q4 chosen from seed
        public double[][] obs;      // final observation-pole response field
        public double[][] rLocal;   // local figure-clarity field R_local(x,z)
        public int blockSize;       // R_local block size
        public double richThreshold; // R_local quartile gate for template upgrade
    }

    public Result run(long seed, int size, int rounds) {
        int w = size, h = size;
        Rng rng = new Rng(seed);
        Ramanujan ram = new Ramanujan(Q_MAX);
        Axis axis = new Axis();
        Axis.Params p = new Axis.Params();

        // Template q-pairs chosen deterministically from the seed (tier 1 / tier 2).
        int[] pr = ram.primes();
        int q1 = pr[1 + rng.nextInt(pr.length - 2)];
        int q2 = pr[1 + rng.nextInt(pr.length - 2)];
        int q3, q4;
        do { q3 = pr[1 + rng.nextInt(pr.length - 2)]; } while (q3 == q1 || q3 == q2);
        do { q4 = pr[1 + rng.nextInt(pr.length - 2)]; } while (q4 == q1 || q4 == q2 || q4 == q3);
        Result res = new Result();
        res.ram = ram;
        res.params = p;
        res.tq = new int[]{q1, q2, q3, q4};
        res.templateQ = new String[]{"q1=" + q1 + " q2=" + q2, "q3=" + q3 + " q4=" + q4};

        double[][] eta = initNoiseField(w, h, seed, ram, p, q1, q2);
        double[][] psi = eta;

        double prevH = 0, prevS = 0;
        double[][] obs = null;
        double[][] rLocal = null;
        double richThr = 0;
        final int bs = 64;
        for (int it = 0; it < rounds; it++) {
            // --- L1: four poles in parallel ---
            Pagity pag = new Pagity();
            double[][] lp = pag.lowPass(psi, w, h, p.filterRadius);
            double[][] bp = pag.bandPass(psi, lp, w, h);

            Nemo nem = new Nemo();
            double[][] coupled = nem.couple(bp, w, h, p.kappa, 0.01, seed + it);

            double R = ram.clarity(psi, w, h);

            // Observation pole: local figure-clarity field R_local(x,z) drives
            // the observer effect (v4.0 RAMANUJAN section 7) — the most
            // pattern-rich quartile of blocks automatically receives the richer
            // 4-q interference template ("系统会向被观察的方向演化").
            int gx = (w + bs - 1) / bs, gy = (h + bs - 1) / bs;
            rLocal = new double[gy][gx];
            double[] rVals = new double[gx * gy];
            for (int by = 0; by < gy; by++) {
                for (int bx = 0; bx < gx; bx++) {
                    int x0 = bx * bs, y0 = by * bs;
                    int bw = Math.min(bs, w - x0), bh = Math.min(bs, h - y0);
                    double[][] sub = new double[bh][bw];
                    for (int y = 0; y < bh; y++) {
                        System.arraycopy(psi[y0 + y], x0, sub[y], 0, bw);
                    }
                    double v = ram.clarity(normalize(sub, bw, bh), bw, bh);
                    rLocal[by][bx] = v;
                    rVals[by * gx + bx] = v;
                }
            }
            java.util.Arrays.sort(rVals);
            richThr = rVals[(int) ((gx * gy - 1) * 0.75)];
            obs = new double[h][w];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    boolean rich = rLocal[y / bs][x / bs] >= richThr;
                    obs[y][x] = rich
                            ? ram.template4(q1, q2, q3, q4, x, y)
                            : ram.template(q1, q2, x, y);
                }
            }

            // --- L2: state update (v4.0 section 4.1/4.2) ---
            // I_total = P[eta] + N[eta] + R[eta]: the observation response field
            // sculpts the state at its own strength (Psi = P + N + R per 3.4).
            double dEff = p.d0 * (1 - pag.etaP) * nem.kappaN;
            double[][] out = new double[h][w];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    double lap = 4 * psi[y][x]
                            - psi[y][(x + 1) % w] - psi[y][(x - 1 + w) % w]
                            - psi[(y + 1) % h][x] - psi[(y - 1 + h) % h][x];
                    double f = psi[y][x] * (1 - Math.abs(psi[y][x])) * p.growth;
                    double obsTerm = (obs[y][x] * 2 - 1) * 0.5; // [0,1] -> [-0.5,0.5]
                    double iTotal = lp[y][x] + coupled[y][x] + obsTerm;
                    double v = psi[y][x] + dEff * lap * 0.25 + f + iTotal;
                    out[y][x] = v * p.decay;
                }
            }

            // --- L3: AXIS measures and adapts ---
            axis.H = axis.entropy(out, w, h);
            axis.S = axis.structure(out, w, h);
            axis.R = R;
            axis.etaP = pag.etaP;
            axis.kappaN = nem.kappaN;
            res.history.add(axis.line(it, p));
            if (it == 0) {
                prevH = axis.H;
                prevS = axis.S;
            } else {
                double dH = axis.H - prevH;
                double dS = axis.S - prevS;
                axis.adapt(p, dH, dS, R);
                prevH = axis.H;
                prevS = axis.S;
            }
            psi = out;
        }

        // Final projection bands for the world generator.
        res.psi = normalize(psi, w, h);
        res.obs = obs;
        res.rLocal = rLocal;
        res.blockSize = bs;
        res.richThreshold = richThr;
        Pagity pag = new Pagity();
        res.low = normalize(pag.lowPass(res.psi, w, h, 4), w, h);
        res.detail = normalize(pag.bandPass(res.psi, res.low, w, h), w, h);
        res.axis = axis;
        return res;
    }

    /**
     * Noise field initialization (v4.0 section 2.1/2.3):
     * external value-noise fBm + internal breath/heartbeat + light curtain.
     */
    private double[][] initNoiseField(int w, int h, long seed, Ramanujan ram,
                                      Axis.Params p, int q1, int q2) {
        double[][] f = new double[h][w];
        double[] qs = new double[]{q1, q2};
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double ext = fbm(x, zOf(y), seed, 4, 48.0);
                double t = (x + y) * 0.01;
                double breath = Math.sin(2 * Math.PI * 0.15 * t);
                double heartbeat = Math.pow(Math.max(0, Math.sin(2 * Math.PI * 1.2 * t)), 8);
                double internal = (breath * 0.4 + heartbeat * 0.6);
                double q = qs[(x + y) % 2];
                double light = ram.c((int) q, x) / ram.phiOf((int) q);
                light = light * 0.5 + 0.5;
                double rw = (Rng.hash01(x, y, seed + 777) - 0.5) * 2 * 0.2;
                f[y][x] = ext * 0.6 + internal * 0.15 * p.alpha + light * 0.5 * p.alpha + rw;
            }
        }
        return f;
    }

    private static int zOf(int y) {
        return y;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** Smooth value noise in [-1,1]. */
    private static double vnoise(double x, double z, long salt) {
        int xi = (int) Math.floor(x), zi = (int) Math.floor(z);
        double xf = x - xi, zf = z - zi;
        double v00 = Rng.hash01(xi, zi, salt), v10 = Rng.hash01(xi + 1, zi, salt);
        double v01 = Rng.hash01(xi, zi + 1, salt), v11 = Rng.hash01(xi + 1, zi + 1, salt);
        double ux = xf * xf * (3 - 2 * xf), uz = zf * zf * (3 - 2 * zf);
        return lerp(lerp(v00, v10, ux), lerp(v01, v11, ux), uz) * 2 - 1;
    }

    /** Fractal Brownian motion. */
    private static double fbm(double x, double z, long salt, int oct, double scale) {
        double a = 0, amp = 0.5, f = 1.0 / scale, tot = 0;
        for (int i = 0; i < oct; i++) {
            a += amp * vnoise(x * f, z * f, salt + i * 101L);
            tot += amp;
            amp *= 0.5;
            f *= 2;
        }
        return a / tot;
    }

    /** Min-max normalize to [-1,1]. */
    public static double[][] normalize(double[][] f, int w, int h) {
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (f[y][x] < lo) lo = f[y][x];
                if (f[y][x] > hi) hi = f[y][x];
            }
        }
        double range = hi - lo;
        double[][] o = new double[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                o[y][x] = range < 1e-12 ? 0 : (f[y][x] - lo) / range * 2 - 1;
            }
        }
        return o;
    }

    /**
     * Baseline field for falsifiable prediction P1: pure fBm value noise with a
     * heavier budget (8 octaves) and no Ramanujan structure at all.
     */
    public static double[][] baselineField(long seed, int size) {
        double[][] f = new double[size][size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                f[y][x] = fbm(x, zOf(y), seed + 31337L, 8, 24.0);
            }
        }
        return normalize(f, size, size);
    }
}
