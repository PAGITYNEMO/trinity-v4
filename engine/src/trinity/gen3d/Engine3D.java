package trinity.gen3d;

import trinity.core.Rng;
import trinity.poles.Axis;
import trinity.poles.Ramanujan;

import java.util.ArrayList;
import java.util.List;

/**
 * TRINITY v4.0 — three-dimensional four-pole engine.
 *
 * The protocol's state-field dynamics (v4.0 section 4.2) lifted to a real 3D
 * voxel body:
 *
 *   Psi(z,y,x,t+1) = Psi + D_eff * lap3(Psi) + f(Psi) + (P + N + R[eta]) , then * decay
 *
 * with a 3D noise field (external fBm3 + internal breath/heartbeat along y +
 * light-curtain interference patterns), 3D PAGITY low-pass, 3D NEMO gradient
 * coupling, 3D RAMANUJAN interference templates (horizontal q-pairs x a
 * vertical c_q(y) modulation) and 3D AXIS metrics (Shannon entropy, 3D
 * Laplacian structure degree, figure clarity, local R field with observer
 * gating).
 */
public final class Engine3D {
    public static final int Q_MAX = 64;

    public static final class Result {
        public double[][][] density;   // [z][y][x], normalized to [-1,1] (metrics/shading)
        public double[][][] psiRaw;    // final un-normalized field (worldgen thresholds)
        public double[][][] low;       // final filtered base band
        public double[][][] detail;    // final detail band
        public Ramanujan ram;
        public Axis.Params params = new Axis.Params();
        public int[] tq = new int[4];
        public double[][] rLocal;      // [bz][bx] local clarity (blocks of 8)
        public int blockSize = 8;
        public double richThreshold;
        public List<String> history = new ArrayList<>();
        public double H, S, R, etaP, kappaN;
    }

    public Result run(long seed, int n, int rounds) {
        Rng rng = new Rng(seed);
        Ramanujan ram = new Ramanujan(Q_MAX);
        int[] pr = ram.primes();
        int q1 = pr[1 + rng.nextInt(pr.length - 2)];
        int q2 = pr[1 + rng.nextInt(pr.length - 2)];
        int q3, q4;
        do { q3 = pr[1 + rng.nextInt(pr.length - 2)]; } while (q3 == q1 || q3 == q2);
        do { q4 = pr[1 + rng.nextInt(pr.length - 2)]; } while (q4 == q1 || q4 == q2 || q4 == q3);

        Result res = new Result();
        res.ram = ram;
        res.tq = new int[]{q1, q2, q3, q4};

        double[][][] psi = init(seed, n, ram, res.params, q1, q2, q3);
        double[][][] obs = null;
        double[][] rLocal = null;
        double richThr = 0;
        final int bs = 8;
        double prevH = 0, prevS = 0;

        for (int it = 0; it < rounds; it++) {
            // --- PAGITY 3D: separable low-pass + band-pass ---
            double[][][] lp = lowPass(psi, n, 3);
            double[][][] bp = bandPass(psi, lp, n);
            double etaP = energyRatio(lp, psi, n);

            // --- NEMO 3D: gradient coupling ---
            double[][][] coupled = couple(bp, n, res.params.kappa, seed + it);
            double kappaN = correlate(coupled, bp, n);

            // --- RAMANUJAN 3D: figure clarity + local R field ---
            double R = clarity3(ram, psi, n, n, n);

            int gx = (n + bs - 1) / bs, gy = (n + bs - 1) / bs;
            rLocal = new double[gy][gx];
            double[] rVals = new double[gx * gy];
            for (int by = 0; by < gy; by++) {
                for (int bx = 0; bx < gx; bx++) {
                    int x0 = bx * bs, y0 = by * bs;
                    int bw = Math.min(bs, n - x0), bh = Math.min(bs, n - y0);
                    double[][][] sub = new double[bs][bh][bw];
                    for (int z = 0; z < bs; z++) {
                        for (int y = 0; y < bh; y++) {
                            System.arraycopy(psi[z][y0 + y], x0, sub[z][y], 0, bw);
                        }
                    }
                    double v = clarity3(ram, normalize(sub, bs, bh, bw), bs, bh, bw);
                    rLocal[by][bx] = v;
                    rVals[by * gx + bx] = v;
                }
            }
            java.util.Arrays.sort(rVals);
            richThr = rVals[(int) ((gx * gy - 1) * 0.75)];

            // Observation response field: per-block 4-q / 2-q templates x vertical modulation.
            obs = new double[n][n][n];
            double[] vy = ram.row(q3, n);
            double invV = 1.0 / ram.phiOf(q3);
            for (int z = 0; z < n; z++) {
                double vmod = 0.7 + 0.6 * ((vy[z] * invV + 1) * 0.5);
                for (int y = 0; y < n; y++) {
                    for (int x = 0; x < n; x++) {
                        boolean rich = rLocal[y / bs][x / bs] >= richThr;
                        double t = rich
                                ? ram.template4(q1, q2, q3, q4, x, y)
                                : ram.template(q1, q2, x, y);
                        obs[z][y][x] = t * vmod;
                    }
                }
            }

            // --- State update (v4.0 section 4.2, 3D) ---
            double dEff = res.params.d0 * (1 - etaP) * kappaN;
            double[][][] out = new double[n][n][n];
            for (int z = 0; z < n; z++) {
                for (int y = 0; y < n; y++) {
                    for (int x = 0; x < n; x++) {
                        double lap = 6 * psi[z][y][x]
                                - psi[z][y][(x + 1) % n] - psi[z][y][(x - 1 + n) % n]
                                - psi[z][(y + 1) % n][x] - psi[z][(y - 1 + n) % n][x]
                                - psi[(z + 1) % n][y][x] - psi[(z - 1 + n) % n][y][x];
                        double f = psi[z][y][x] * (1 - Math.abs(psi[z][y][x])) * res.params.growth;
                        double obsTerm = (obs[z][y][x] * 2 - 1) * 0.5;
                        double iTotal = lp[z][y][x] + coupled[z][y][x] + obsTerm;
                        double v = psi[z][y][x] + dEff * lap * 0.25 + f + iTotal;
                        out[z][y][x] = v * res.params.decay;
                    }
                }
            }

            // --- AXIS 3D: measure + adapt ---
            double H = entropy3(out, n);
            double S = structure3(out, n);
            res.history.add(String.format(
                    "iter %d | H=%.4f | S=%.4f | R=%.4f | etaP=%.4f | kappaN=%.4f | kappa=%.3f alpha=%.3f",
                    it, H, S, R, etaP, kappaN, res.params.kappa, res.params.alpha));
            if (it == 0) {
                prevH = H;
                prevS = S;
            } else {
                res.params = res.params.copy();
                Axis a = new Axis();
                a.adapt(res.params, H - prevH, S - prevS, R);
                prevH = H;
                prevS = S;
            }
            res.H = H;
            res.S = S;
            res.R = R;
            res.etaP = etaP;
            res.kappaN = kappaN;
            psi = out;
        }

        res.density = normalize(psi, n, n, n);
        res.psiRaw = psi;
        res.low = normalize(lowPass(psi, n, 4), n, n, n);
        res.detail = normalize(bandPass(psi, res.low, n), n, n, n);
        res.rLocal = rLocal;
        res.richThreshold = richThr;
        return res;
    }

    // ---------------- noise field initialization ----------------

    private static double[][][] init(long seed, int n, Ramanujan ram, Axis.Params p,
                                     int q1, int q2, int q3) {
        double[][][] f = new double[n][n][n];
        double invV = 1.0 / ram.phiOf(q3);
        double[] vy = ram.row(q3, n);
        // Terrain skeleton baked into the field: below the local surface the
        // field is biased positive, above it negative. The four poles then
        // modulate this skeleton — PAGITY smooths it, NEMO sharpens detail,
        // RAMANUJAN imprints patterns, AXIS watches the metrics.
        double[][] ySurf = new double[n][n];
        for (int z = 0; z < n; z++) {
            for (int x = 0; x < n; x++) {
                ySurf[z][x] = surface01(seed, n, ram, q1, q2, x, z) * n;
            }
        }
        for (int z = 0; z < n; z++) {
            double vertical = (vy[z] * invV + 1) * 0.5;
            double t = z * 0.05;
            double breath = Math.sin(2 * Math.PI * 0.15 * t);
            double heartbeat = Math.pow(Math.max(0, Math.sin(2 * Math.PI * 1.2 * t)), 8);
            double internal = breath * 0.4 + heartbeat * 0.6;
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    double bias = (ySurf[z][x] - y) / (n * 0.15);
                    double ext = fbm3(x, y, z, seed, 4, 40.0);
                    double light = 0.6 * ram.template(q1, q2, x, y) + 0.4 * vertical;
                    double rw = (Rng.hash3(x, y, z, seed + 777) - 0.5) * 2 * 0.05;
                    f[z][y][x] = bias + ext * 0.30 + light * 0.30 * p.alpha
                            + internal * 0.05 + rw;
                }
            }
        }
        return f;
    }

    /** 2D terrain skeleton in [0,1] — mirrors the 2D worldgen height formula. */
    private static double surface01(long seed, int n, Ramanujan ram, int q1, int q2, int x, int z) {
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

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    private static double smoothstep(double v) {
        return v * v * (3 - 2 * v);
    }

    // ---------------- 3D poles ----------------

    /** Separable Gaussian low-pass over x, then y, then z. */
    public static double[][][] lowPass(double[][][] f, int n, int radius) {
        double sigma = Math.max(1.0, radius / 2.0);
        int klen = 2 * radius + 1;
        double[] k = new double[klen];
        double sum = 0;
        for (int i = -radius; i <= radius; i++) {
            k[i + radius] = Math.exp(-(i * i) / (2 * sigma * sigma));
            sum += k[i + radius];
        }
        for (int i = 0; i < klen; i++) k[i] /= sum;

        double[][][] t1 = new double[n][n][n];
        double[][][] t2 = new double[n][n][n];
        double[][][] out = new double[n][n][n];
        for (int z = 0; z < n; z++) {
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    double acc = 0;
                    for (int i = -radius; i <= radius; i++) acc += k[i + radius] * f[z][y][(x + i + n) % n];
                    t1[z][y][x] = acc;
                }
            }
        }
        for (int z = 0; z < n; z++) {
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    double acc = 0;
                    for (int i = -radius; i <= radius; i++) acc += k[i + radius] * t1[z][(y + i + n) % n][x];
                    t2[z][y][x] = acc;
                }
            }
        }
        for (int z = 0; z < n; z++) {
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    double acc = 0;
                    for (int i = -radius; i <= radius; i++) acc += k[i + radius] * t2[(z + i + n) % n][y][x];
                    out[z][y][x] = acc;
                }
            }
        }
        return out;
    }

    public static double[][][] bandPass(double[][][] f, double[][][] lp, int n) {
        double[][][] b = new double[n][n][n];
        for (int z = 0; z < n; z++) {
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    b[z][y][x] = f[z][y][x] - lp[z][y][x];
                }
            }
        }
        return b;
    }

    public static double energyRatio(double[][][] a, double[][][] b, int n) {
        double ea = 0, eb = 1e-12;
        for (int z = 0; z < n; z++) {
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    ea += a[z][y][x] * a[z][y][x];
                    eb += b[z][y][x] * b[z][y][x];
                }
            }
        }
        return ea / eb;
    }

    public static double[][][] couple(double[][][] f, int n, double kappa, long salt) {
        double[][][] o = new double[n][n][n];
        for (int z = 0; z < n; z++) {
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    double gx = f[z][y][(x + 1) % n] - f[z][y][(x - 1 + n) % n];
                    double gy = f[z][(y + 1) % n][x] - f[z][(y - 1 + n) % n][x];
                    double gz = f[(z + 1) % n][y][x] - f[(z - 1 + n) % n][y][x];
                    double grad = 0.5 * Math.sqrt(gx * gx + gy * gy + gz * gz);
                    double forcing = 0.01 * (Math.sin(f[z][y][x] * 10 * Math.PI)
                            + (Rng.hash3(x, y, z, salt) - 0.5) * 2);
                    o[z][y][x] = f[z][y][x] * (1 + kappa * grad) + forcing;
                }
            }
        }
        return o;
    }

    public static double correlate(double[][][] a, double[][][] b, int n) {
        double dot = 0, e = 1e-12;
        for (int z = 0; z < n; z++) {
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    dot += a[z][y][x] * b[z][y][x];
                    e += b[z][y][x] * b[z][y][x];
                }
            }
        }
        return dot / e;
    }

    /** 3D figure clarity: residue concentration x activity, max over q. */
    public static double clarity3(Ramanujan ram, double[][][] f, int nz, int ny, int nx) {
        double mean = 0;
        int cells = 0;
        for (int z = 0; z < nz; z += 2) {
            for (int y = 0; y < ny; y += 2) {
                for (int x = 0; x < nx; x += 2) {
                    mean += f[z][y][x];
                    cells++;
                }
            }
        }
        mean /= Math.max(1, cells);
        double var = 0;
        for (int z = 0; z < nz; z += 2) {
            for (int y = 0; y < ny; y += 2) {
                for (int x = 0; x < nx; x += 2) {
                    double d = f[z][y][x] - mean;
                    var += d * d;
                }
            }
        }
        double std = Math.sqrt(var / Math.max(1, cells));
        double stdNorm = Math.min(1, std / 0.25);

        double best = 0;
        int qCap = Math.min(12, ram.qMax);
        for (int q = 3; q <= qCap; q++) {
            int[] hist = new int[q];
            for (int z = 0; z < nz; z += 2) {
                for (int y = 0; y < ny; y += 2) {
                    for (int x = 0; x < nx; x += 2) {
                        double v = f[z][y][x];
                        int r = (int) Math.round((v * 0.5 + 0.5) * (q * 8)) % q;
                        if (r < 0) r += q;
                        hist[r]++;
                    }
                }
            }
            double hE = 0;
            for (int r = 0; r < q; r++) {
                double p = hist[r] / (double) cells;
                if (p > 0) hE -= p * Math.log(p);
            }
            double rq = (1 - hE / Math.log(q)) * stdNorm;
            if (rq > best) best = rq;
        }
        return best;
    }

    public static double entropy3(double[][][] f, int n) {
        int bins = 64;
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (int z = 0; z < n; z++) {
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    if (f[z][y][x] < lo) lo = f[z][y][x];
                    if (f[z][y][x] > hi) hi = f[z][y][x];
                }
            }
        }
        if (hi - lo < 1e-12) return 0;
        double[] hist = new double[bins];
        for (int z = 0; z < n; z++) {
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    int b = (int) ((f[z][y][x] - lo) / (hi - lo) * bins);
                    if (b >= bins) b = bins - 1;
                    hist[b]++;
                }
            }
        }
        double hE = 0;
        double cnt = (double) n * n * n;
        for (int b = 0; b < bins; b++) {
            double p = hist[b] / cnt;
            if (p > 0) hE -= p * Math.log(p);
        }
        return hE / Math.log(bins);
    }

    public static double structure3(double[][][] f, int n) {
        double lap = 0, e = 1e-12;
        for (int z = 0; z < n; z++) {
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    double l = 6 * f[z][y][x]
                            - f[z][y][(x + 1) % n] - f[z][y][(x - 1 + n) % n]
                            - f[z][(y + 1) % n][x] - f[z][(y - 1 + n) % n][x]
                            - f[(z + 1) % n][y][x] - f[(z - 1 + n) % n][y][x];
                    lap += l * l;
                    e += f[z][y][x] * f[z][y][x];
                }
            }
        }
        double s = lap / e * 0.05;
        return s > 1 ? 1 : s;
    }

    public static double[][][] normalize(double[][][] f, int nz, int ny, int nx) {
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (int z = 0; z < nz; z++) {
            for (int y = 0; y < ny; y++) {
                for (int x = 0; x < nx; x++) {
                    if (f[z][y][x] < lo) lo = f[z][y][x];
                    if (f[z][y][x] > hi) hi = f[z][y][x];
                }
            }
        }
        double range = hi - lo;
        double[][][] o = new double[nz][ny][nx];
        for (int z = 0; z < nz; z++) {
            for (int y = 0; y < ny; y++) {
                for (int x = 0; x < nx; x++) {
                    o[z][y][x] = range < 1e-12 ? 0 : (f[z][y][x] - lo) / range * 2 - 1;
                }
            }
        }
        return o;
    }

    // ---------------- noise helpers ----------------

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
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

    /** 3D baseline for comparison: pure fBm3, normalized. */
    public static double[][][] baselineField(long seed, int n) {
        double[][][] f = new double[n][n][n];
        for (int z = 0; z < n; z++) {
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    f[z][y][x] = fbm3(x, y, z, seed + 31337L, 6, 30.0);
                }
            }
        }
        return normalize(f, n, n, n);
    }
}
