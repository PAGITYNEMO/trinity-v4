package trinity.poles;

/**
 * AXIS pole — measurement and dynamic maintenance.
 *
 * Measures Shannon entropy H(t) of the normalized field, structure degree
 * S(t) as normalized Laplacian energy, observes the other poles' quantities
 * (eta_P, kappa_N, R), detects the emergence mode, and adapts the protocol
 * parameters (surge on degradation, observation-driven sampling reduction on
 * emergence, light-curtain injection on stagnation, template upgrade when the
 * observer sees strong patterns).
 */
public final class Axis {
    public double H;
    public double S;
    public double R;
    public double etaP;
    public double kappaN;

    /** Runtime-adjustable protocol parameters (v4.0 section 9). */
    public static final class Params {
        public double d0 = 0.015;          // base diffusion
        public double growth = 0.002;      // growth coefficient
        public double alpha = 0.6;         // light-curtain injection weight
        public double kappa = 0.6;         // coupling strength
        public double decay = 0.995;       // global decay
        public double sampleDensity = 0.25;// coprime sampling density (1/4)
        public int templateTier = 1;       // 1 = 2-q template, 2 = 4-q upgrade
        public int filterRadius = 3;       // PAGITY kernel radius

        public Params copy() {
            Params p = new Params();
            p.d0 = d0;
            p.growth = growth;
            p.alpha = alpha;
            p.kappa = kappa;
            p.decay = decay;
            p.sampleDensity = sampleDensity;
            p.templateTier = templateTier;
            p.filterRadius = filterRadius;
            return p;
        }
    }

    /** Shannon entropy of the field histogram, normalized to [0,1]. */
    public double entropy(double[][] f, int w, int h) {
        int bins = 64;
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (f[y][x] < lo) lo = f[y][x];
                if (f[y][x] > hi) hi = f[y][x];
            }
        }
        if (hi - lo < 1e-12) return 0;
        double[] hist = new double[bins];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int b = (int) ((f[y][x] - lo) / (hi - lo) * bins);
                if (b >= bins) b = bins - 1;
                hist[b]++;
            }
        }
        double hE = 0;
        double n = (double) w * h;
        for (int b = 0; b < bins; b++) {
            double p = hist[b] / n;
            if (p > 0) hE -= p * Math.log(p);
        }
        return hE / Math.log(bins);
    }

    /** Structure degree: normalized Laplacian energy ratio, clamped to [0,1]. */
    public double structure(double[][] f, int w, int h) {
        double lap = 0, e = 1e-12;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double l = 4 * f[y][x]
                        - f[y][(x + 1) % w] - f[y][(x - 1 + w) % w]
                        - f[(y + 1) % h][x] - f[(y - 1 + h) % h][x];
                lap += l * l;
                e += f[y][x] * f[y][x];
            }
        }
        double s = lap / e * 0.1;
        return s > 1 ? 1 : s;
    }

    /** Emergence mode detection (v4.0 section 5.2). */
    public String mode() {
        if (R > 0.5 && etaP > 0.3 && etaP < 0.7) return "DECOHERENCE (退相干涌现)";
        if (kappaN > 0.75 && R < 0.6) return "COUPLING (耦合涌现)";
        if (etaP > 0.7 && H < 0.35) return "FILTERING (过滤涌现)";
        return "MIXED (混合)";
    }

    /**
     * Adaptive control (v4.0 section 3.4 / 5):
     *  - emergence (dS>0, dH<0): the observer sees structure -> sampling density drops
     *  - degradation (dH>0, dS<0): surge — coupling and light curtain rise
     *  - stagnation: inject more perturbation
     *  - strong patterns (R>0.55): template upgraded 2-q -> 4-q (observer effect)
     */
    public void adapt(Params p, double dH, double dS, double R) {
        if (dS > 0 && dH < 0) {
            p.sampleDensity = Math.max(0.125, p.sampleDensity - 0.03);
        } else if (dH > 0 && dS < 0) {
            p.kappa = Math.min(1.5, p.kappa + 0.1);
            p.alpha = Math.min(1.0, p.alpha + 0.05);
        } else {
            p.alpha = Math.min(1.0, p.alpha + 0.08);
            p.kappa = Math.min(1.2, p.kappa + 0.05);
        }
        if (R > 0.55) p.templateTier = 2;
        else p.templateTier = 1;

        // Clamp to protocol ranges (v4.0 section 9.1).
        if (p.kappa < 0.3) p.kappa = 0.3;
        if (p.kappa > 1.5) p.kappa = 1.5;
        if (p.alpha < 0.1) p.alpha = 0.1;
        if (p.alpha > 1.0) p.alpha = 1.0;
        if (p.sampleDensity < 0.125) p.sampleDensity = 0.125;
        if (p.sampleDensity > 0.5) p.sampleDensity = 0.5;
    }

    /** One-line per-iteration record for the report. */
    public String line(int iter, Axis.Params p) {
        return String.format(
                "iter %d | H=%.4f | S=%.4f | R=%.4f | etaP=%.4f | kappaN=%.4f | mode=%s | kappa=%.3f alpha=%.3f density=%.3f tier=%d",
                iter, H, S, R, etaP, kappaN, mode(), p.kappa, p.alpha, p.sampleDensity, p.templateTier);
    }
}
