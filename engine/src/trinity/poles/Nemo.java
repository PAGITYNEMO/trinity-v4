package trinity.poles;

import trinity.core.Rng;

/**
 * NEMO pole — amplifying coupling.
 *
 * N[eta] = eta * (1 + kappa * |grad eta|) + resonance forcing,
 * with measured coupling strength kappa_N = <N*eta> / <eta^2>.
 * The deterministic forcing term is a stochastic-resonance analog: a weak
 * periodic drive whose phase locks onto the field's own oscillations.
 */
public final class Nemo {
    public double kappaN;

    public double[][] couple(double[][] f, int w, int h, double kappa, double resonance, long salt) {
        double[][] o = new double[h][w];
        double dot = 0, e = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double gx = f[y][(x + 1) % w] - f[y][(x - 1 + w) % w];
                double gz = f[(y + 1) % h][x] - f[(y - 1 + h) % h][x];
                double grad = 0.5 * Math.sqrt(gx * gx + gz * gz);
                double forcing = resonance * (Math.sin(f[y][x] * 10 * Math.PI)
                                            + (Rng.hash01(x, y, salt) - 0.5) * 2);
                double v = f[y][x] * (1 + kappa * grad) + forcing;
                o[y][x] = v;
                dot += v * f[y][x];
                e += f[y][x] * f[y][x];
            }
        }
        kappaN = dot / (e + 1e-12);
        return o;
    }
}
