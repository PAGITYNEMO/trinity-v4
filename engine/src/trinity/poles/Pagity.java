package trinity.poles;

/**
 * PAGITY pole — selective filtering.
 *
 * Low-pass convolution (Gaussian), band-pass extraction, soft thresholding,
 * and the filter efficiency measure eta_P = retained energy / total energy.
 */
public final class Pagity {
    public double etaP;

    /** Separable Gaussian low-pass with wrap-around borders. */
    public double[][] lowPass(double[][] f, int w, int h, int radius) {
        double sigma = Math.max(1.0, radius / 2.0);
        int klen = 2 * radius + 1;
        double[] k = new double[klen];
        double sum = 0;
        for (int i = -radius; i <= radius; i++) {
            k[i + radius] = Math.exp(-(i * i) / (2 * sigma * sigma));
            sum += k[i + radius];
        }
        for (int i = 0; i < klen; i++) k[i] /= sum;

        double[][] tmp = new double[h][w];
        double[][] out = new double[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double acc = 0;
                for (int i = -radius; i <= radius; i++) {
                    acc += k[i + radius] * f[y][(x + i + w) % w];
                }
                tmp[y][x] = acc;
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double acc = 0;
                for (int i = -radius; i <= radius; i++) {
                    acc += k[i + radius] * tmp[(y + i + h) % h][x];
                }
                out[y][x] = acc;
            }
        }

        double eF = 0, eL = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                eF += f[y][x] * f[y][x];
                eL += out[y][x] * out[y][x];
            }
        }
        etaP = eL / (eF + 1e-12);
        return out;
    }

    /** Band-pass: original minus low-pass (the detail band). */
    public double[][] bandPass(double[][] f, double[][] lp, int w, int h) {
        double[][] b = new double[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                b[y][x] = f[y][x] - lp[y][x];
            }
        }
        return b;
    }

    /** Soft threshold (sigmoid) — boundary sharpening, not noise removal. */
    public double[][] threshold(double[][] f, int w, int h, double t, double soft) {
        double[][] o = new double[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                o[y][x] = 1.0 / (1.0 + Math.exp(-(f[y][x] - t) / Math.max(1e-6, soft)));
            }
        }
        return o;
    }
}
