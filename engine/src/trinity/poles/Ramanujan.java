package trinity.poles;

import trinity.core.Rng;

import java.util.ArrayList;

/**
 * RAMANUJAN pole — the observer.
 *
 * Ramanujan sums c_q(n) = sum_{1<=a<=q, gcd(a,q)=1} cos(2*pi*a*n/q) are computed
 * exactly through the number-theoretic identity
 *
 *     c_q(n) = mu(q/d) * phi(q) / phi(q/d),   d = gcd(q, n)
 *
 * and precomputed into tables (period q) — the protocol's "模不变量查表":
 * zero trigonometric work at generation time.
 *
 * Also provides the protocol's observation instruments:
 *  - coprime sampling masks (互素采样, ~1/4 density, no moire)
 *  - interference templates (干涉图纹模板, |c_q1 + c_q2|^2)
 *  - totient/Mobius region weights (数论函数权重)
 *  - figure clarity R(t) (图纹清晰度)
 */
public final class Ramanujan {
    public final int qMax;

    private final int[] phi;
    private final int[] mu;
    private final double[][] cq;   // cq[q][n mod q]
    private final int[] primes;

    public Ramanujan(int qMax) {
        this.qMax = qMax;
        this.phi = new int[qMax + 1];
        this.mu = new int[qMax + 1];

        // Linear sieve: phi (totient) and mu (Mobius).
        int[] lp = new int[qMax + 1];
        ArrayList<Integer> ps = new ArrayList<>();
        mu[1] = 1;
        phi[1] = 1;
        for (int i = 2; i <= qMax; i++) {
            if (lp[i] == 0) {
                lp[i] = i;
                ps.add(i);
                phi[i] = i - 1;
                mu[i] = -1;
            }
            for (int p : ps) {
                long v = (long) p * i;
                if (v > qMax) break;
                lp[(int) v] = p;
                if (p == lp[i]) {
                    phi[(int) v] = phi[i] * p;
                    mu[(int) v] = 0;
                    break;
                } else {
                    phi[(int) v] = phi[i] * (p - 1);
                    mu[(int) v] = -mu[i];
                }
            }
        }

        primes = new int[ps.size()];
        for (int i = 0; i < ps.size(); i++) primes[i] = ps.get(i);

        // Precompute c_q tables.
        cq = new double[qMax + 1][];
        for (int q = 1; q <= qMax; q++) {
            cq[q] = new double[q];
            for (int n = 0; n < q; n++) {
                int d = Rng.gcd(q, n);
                long v = (long) mu[q / d] * phi[q] / phi[q / d];
                cq[q][n] = v;
            }
        }
    }

    /** c_q(n), integer-valued, via table lookup. */
    public double c(int q, int n) {
        int m = n % q;
        if (m < 0) m += q;
        return cq[q][m];
    }

    /** Precomputed row c_q(0..n-1) — c_q is a 1-D function, so x and z share it. */
    public double[] row(int q, int n) {
        double[] r = new double[n];
        for (int i = 0; i < n; i++) {
            r[i] = cq[q][i % q];
        }
        return r;
    }

    public int phiOf(int q) {
        return phi[q];
    }

    public int muOf(int q) {
        return mu[q];
    }

    public int[] primes() {
        return primes;
    }

    /** Totient-weighted region factor in (0,1]: phi(q)/q, q chosen from coordinates. */
    public double regionWeight(long x, long z, long salt) {
        int q = 2 + (int) (Rng.hash01(x, z, salt) * (qMax - 1));
        return phi[q] / (double) q;
    }

    /** Region Mobius sign: +1 complex, -1 medium, 0 flat (square factor present). */
    public int regionMu(long x, long z, long salt) {
        int q = 2 + (int) (Rng.hash01(x, z, salt) * (qMax - 1));
        return mu[q];
    }

    /** Interference template |c_q1(x) + c_q2(z)|^2 normalized to [0,1]. */
    public double template(int q1, int q2, int x, int z) {
        double v = c(q1, x) + c(q2, z);
        double norm = phi[q1] + phi[q2];
        double t = (v * v) / (norm * norm);
        return t < 0 ? 0 : (t > 1 ? 1 : t);
    }

    /**
     * Observer-upgrade 4-q template |c_q1(x)+c_q2(z)+c_q3(x)+c_q4(z)|^2.
     * Normalized to the SAME energy scale as the 2-q template (phi(q1)+phi(q2)):
     * the upgrade adds frequency components without attenuating the pattern —
     * "被看见的区域获得更多频率成分，而不是更弱的图案".
     */
    public double template4(int q1, int q2, int q3, int q4, int x, int z) {
        double v = c(q1, x) + c(q2, z) + c(q3, x) + c(q4, z);
        double norm = phi[q1] + phi[q2];
        double t = (v * v) / (norm * norm);
        return t < 0 ? 0 : (t > 1 ? 1 : t);
    }

    /**
     * Figure clarity R(t): a field shows a pattern when its Ramanujan-residue
     * distribution is CONCENTRATED (deviating from uniform noise) AND the field
     * is ACTIVE (non-flat). Pure flat fields concentrate on residue 0 but have
     * no activity; pure noise is active but uniform. Both score ~0; a real
     * interference pattern scores high.
     *
     *   clarity_q = (1 - H_q / ln q) * min(1, std / 0.25),  R = max over q.
     */
    public double clarity(double[][] field, int w, int h) {
        double mean = 0;
        int cells = 0;
        for (int y = 0; y < h; y += 3) {
            for (int x = 0; x < w; x += 3) {
                mean += field[y][x];
                cells++;
            }
        }
        mean /= Math.max(1, cells);
        double var = 0;
        for (int y = 0; y < h; y += 3) {
            for (int x = 0; x < w; x += 3) {
                double d = field[y][x] - mean;
                var += d * d;
            }
        }
        double std = Math.sqrt(var / Math.max(1, cells));
        double stdNorm = Math.min(1, std / 0.25);

        double best = 0;
        int qCap = Math.min(16, qMax);
        for (int q = 3; q <= qCap; q++) {
            int[] hist = new int[q];
            int cnt = 0;
            for (int y = 0; y < h; y += 3) {
                for (int x = 0; x < w; x += 3) {
                    double v = field[y][x];
                    int n = (int) Math.round((v * 0.5 + 0.5) * (q * 8)) % q;
                    if (n < 0) n += q;
                    hist[n]++;
                    cnt++;
                }
            }
            double hE = 0;
            for (int r = 0; r < q; r++) {
                double p = hist[r] / (double) cnt;
                if (p > 0) hE -= p * Math.log(p);
            }
            double conc = 1 - hE / Math.log(q);
            double rq = conc * stdNorm;
            if (rq > best) best = rq;
        }
        return best;
    }

    /**
     * Coprime sampling mask: one cell per 2x2 block (25% density), the sampled
     * cell chosen as the coprime one (gcd(x,z)==1) when it exists. Uniform
     * coverage without periodic artifacts — the protocol's 互素采样.
     */
    public static boolean[][] coprimeMask(int w, int h, long seed) {
        boolean[][] m = new boolean[h][w];
        int[] dx = {0, 1, 0, 1};
        int[] dz = {0, 0, 1, 1};
        for (int y = 0; y < h; y += 2) {
            for (int x = 0; x < w; x += 2) {
                int sx = x, sz = y;
                boolean found = false;
                for (int k = 0; k < 4 && !found; k++) {
                    int cx = x + dx[k], cz = y + dz[k];
                    if (Rng.gcd(cx, cz) == 1) {
                        sx = cx;
                        sz = cz;
                        found = true;
                    }
                }
                m[sz][sx] = true;
            }
        }
        return m;
    }

    /**
     * Bilinear upsample of the 25%-density coprime samples back to the full grid.
     * Masked cells carry the sampled value; the rest are interpolated.
     */
    public static double[][] coprimeUpsample(double[][] field, boolean[][] mask, int w, int h) {
        // One sample per 2x2 block -> block grid W/2 x H/2.
        int bw = (w + 1) / 2, bh = (h + 1) / 2;
        double[][] g = new double[bh][bw];
        for (int by = 0; by < bh; by++) {
            for (int bx = 0; bx < bw; bx++) {
                double acc = 0;
                int cnt = 0;
                for (int dy = 0; dy < 2; dy++) {
                    for (int dx = 0; dx < 2; dx++) {
                        int x = bx * 2 + dx, y = by * 2 + dy;
                        if (x < w && y < h && mask[y][x]) {
                            acc += field[y][x];
                            cnt++;
                        }
                    }
                }
                g[by][bx] = cnt > 0 ? acc / cnt : field[by * 2][bx * 2];
            }
        }
        double[][] out = new double[h][w];
        for (int y = 0; y < h; y++) {
            double fy = y / 2.0 - 0.5;
            int by0 = (int) Math.floor(fy), by1 = by0 + 1;
            double uy = fy - by0;
            if (by0 < 0) { by0 = 0; uy = 0; }
            if (by1 >= bh) by1 = bh - 1;
            for (int x = 0; x < w; x++) {
                double fx = x / 2.0 - 0.5;
                int bx0 = (int) Math.floor(fx), bx1 = bx0 + 1;
                double ux = fx - bx0;
                if (bx0 < 0) { bx0 = 0; ux = 0; }
                if (bx1 >= bw) bx1 = bw - 1;
                double v = g[by0][bx0] * (1 - ux) * (1 - uy)
                         + g[by0][bx1] * ux * (1 - uy)
                         + g[by1][bx0] * (1 - ux) * uy
                         + g[by1][bx1] * ux * uy;
                out[y][x] = v;
            }
        }
        return out;
    }
}
