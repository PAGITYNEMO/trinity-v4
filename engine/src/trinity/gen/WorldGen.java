package trinity.gen;

import trinity.core.Rng;
import trinity.poles.Axis;
import trinity.poles.Pagity;
import trinity.poles.Ramanujan;

/**
 * World generator: turns the four-pole cognitive state field into a
 * Minecraft-style terrain — heightmap, caves, ore clusters, rivers —
 * plus the vertical density slice used for the cross-section image.
 *
 * All structural decisions use the RAMANUJAN instruments (interference
 * templates, coprime sampling, totient/Mobius region weights), so the
 * complexity comes from number theory, not from extra computation.
 */
public final class WorldGen {

    public static final class World {
        public double[][] height;    // [0,1]
        public boolean[][] cave;
        public boolean[][] ore;
        public boolean[][] river;
        public double[][] density;   // 128 x w slice, [0,1]: 0 air, 1 solid
        public double caveArea;      // fraction
        public double oreArea;       // fraction
        public int riverCells;
        public double observerRatio; // P5: terrain local variance, observed/unobserved blocks
        public double observerStructRatio; // P5 secondary: structure degree ratio
        public double sampledLowS;   // structure of coprime-sampled low band
        public double fullLowS;      // structure of full low band
        public double sampleRetention; // P2: 1 - L2 relative error of sampled low band
    }

    public static World generate(Engine.Result res, int size, long seed, boolean useTemplates) {
        int w = size, h = size;
        World world = new World();
        world.height = new double[h][w];
        world.cave = new boolean[h][w];
        world.ore = new boolean[h][w];
        world.river = new boolean[h][w];

        Ramanujan ram = res.ram;
        int q1 = res.tq[0], q2 = res.tq[1], q3 = res.tq[2], q4 = res.tq[3];

        // Coprime sampling: the expensive band evaluation happens at 25% density;
        // the low (terrain-scale) band keeps its structure through interpolation —
        // the protocol's 互素采样 (large-scale patterns survive, no moire).
        boolean[][] mask = Ramanujan.coprimeMask(w, h, seed);
        double[][] sampledLow = Ramanujan.coprimeUpsample(res.low, mask, w, h);
        double[][] sampledDetail = Ramanujan.coprimeUpsample(res.detail, mask, w, h);

        // P2 check: structure retained by 25% coprime sampling on the low band.
        world.fullLowS = new Axis().structure(res.low, w, h);
        world.sampledLowS = new Axis().structure(sampledLow, w, h);
        double err = 0, e = 1e-12;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double d = sampledLow[y][x] - res.low[y][x];
                err += d * d;
                e += res.low[y][x] * res.low[y][x];
            }
        }
        world.sampleRetention = 1 - Math.sqrt(err / e);

        int caves = 0, ores = 0;
        int bs = res.blockSize;
        double richThr = res.richThreshold;

        // Precomputed Ramanujan rows: c_q is 1-D, so x and z share the table;
        // the hot loop then costs 4 cache-hot reads instead of 6 modular lookups.
        boolean tmpl = useTemplates;
        double[] r1 = ram.row(q1, w), r2 = ram.row(q2, w);
        double[] r3 = ram.row(q3, w), r4 = ram.row(q4, w);
        double invNorm2 = 1.0 / Math.pow(ram.phiOf(q1) + ram.phiOf(q2), 2);
        double invNormB = 1.0 / Math.pow(ram.phiOf(q3) + ram.phiOf(q4), 2);
        double[][] rLoc = res.rLocal;

        for (int y = 0; y < h; y++) {
            int by = y / bs;
            double cq2y = r2[y], cq4y = r4[y];
            for (int x = 0; x < w; x++) {
                double low01 = sampledLow[y][x] * 0.5 + 0.5;
                double det01 = sampledDetail[y][x] * 0.5 + 0.5;
                // Per-block observer gate: regions the observer sees patterns in
                // (top quartile of R_local) receive the richer 4-q template.
                boolean rich = tmpl && rLoc[by][x / bs] >= richThr;
                double v2 = r1[x] + cq2y;
                double tpl;
                double tpl2;
                if (tmpl) {
                    double t2 = v2 * v2 * invNorm2;
                    tpl = rich ? Math.min(1, (v2 + r3[x] + cq4y) * (v2 + r3[x] + cq4y) * invNorm2) : t2;
                    double v3 = r3[x] + r4[y];
                    tpl2 = v3 * v3 * invNormB;
                } else {
                    tpl = 0.5; // no-template baseline: flat pattern
                    tpl2 = 0.5;
                }
                double wgt = ram.regionWeight(x, y, seed);
                int mu = ram.regionMu(x, y, seed);

                // Height: filtered base + detail + interference ridges, weighted by
                // totient regions; mu==0 (square factors) stays flat.
                // ridge = sqrt(tpl): lifts the numerically small template values
                // into a full dynamic range without saturating.
                // Observer effect: in blocks the observer sees patterns in, both
                // the ridge and the detail weights are raised — the system
                // evolves toward what is being observed (v4.0 RAMANUJAN §7).
                double ridge = Math.sqrt(tpl);
                double hgt;
                if (mu == 0) {
                    hgt = 0.55 * low01 + 0.15 * det01;
                } else if (rich) {
                    hgt = 0.50 * low01 + 0.30 * det01 + 0.38 * ridge * wgt;
                } else {
                    hgt = 0.50 * low01 + 0.22 * det01 + 0.28 * ridge * wgt;
                }
                hgt = smoothstep(clamp01(hgt + 0.06)); // sea-level balance
                world.height[y][x] = hgt;

                // Caves: XOR of two interference q-pairs (fine-grained caverns)
                // + totient-rich region, no square-factor flats.
                boolean c;
                if (useTemplates) {
                    c = ((tpl > 0.34) ^ (tpl2 > 0.42)) && det01 > 0.45 && wgt > 0.45 && mu != 0;
                } else {
                    c = det01 > 0.72 && wgt > 0.50;
                }
                world.cave[y][x] = c;
                if (c) caves++;

                // Ores: strong interference + high-totient region.
                boolean o;
                if (useTemplates) {
                    o = (tpl > 0.66 && wgt > 0.72) || (tpl2 > 0.55 && wgt > 0.80);
                } else {
                    o = det01 > 0.82;
                }
                world.ore[y][x] = o;
                if (o) ores++;
            }
        }
        world.caveArea = caves / (double) (w * h);
        world.oreArea = ores / (double) (w * h);

        // Rivers: steepest descent from random high points.
        Rng rng = new Rng(seed ^ 0x51AB);
        int rivers = 0;
        for (int i = 0; i < 8; i++) {
            int x = rng.nextInt(w), y = rng.nextInt(h);
            for (int step = 0; step < 500; step++) {
                if (!world.river[y][x]) {
                    world.river[y][x] = true;
                    rivers++;
                }
                int bx = x, by = y;
                double best = world.height[y][x];
                boolean moved = false;
                for (int dy = -1; dy <= 1 && !moved; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
                            moved = true; // reached the border
                            break;
                        }
                        if (world.height[ny][nx] < best - 0.001) {
                            best = world.height[ny][nx];
                            bx = nx;
                            by = ny;
                            moved = true;
                        }
                    }
                }
                if (!moved) break; // local minimum: lake
                x = bx;
                y = by;
            }
        }
        world.riverCells = rivers;

        // Vertical density slice at z = h/2 (128 levels).
        int sliceH = 128;
        int z0 = h / 2;
        world.density = new double[sliceH][w];
        for (int x = 0; x < w; x++) {
            int surface = (int) (world.height[z0][x] * (sliceH - 1));
            for (int y = 0; y < sliceH; y++) {
                double d;
                if (y > surface) {
                    d = 0.0; // air
                } else if (world.cave[z0][x] && y > 6 && y < surface - 3) {
                    d = 0.05; // cave void
                } else if (world.ore[z0][x] && surface - y < 14) {
                    d = 0.92; // ore seam
                } else {
                    d = 0.55 + 0.25 * Rng.hash01(x, y, seed + 99); // stone
                }
                world.density[y][x] = d;
            }
        }

        // P5 observer effect: local variance of the TERRAIN (heightmap) in the
        // observed (top quartile of R_local) blocks vs the least observed ones.
        // The observer's R_local gates which blocks get the richer 4-q template
        // (see tpl above); this measures the downstream consequence on terrain.
        int gx = (w + bs - 1) / bs, gy = (h + bs - 1) / bs;
        double[] blockR = new double[gx * gy];
        double[] blockV = new double[gx * gy];
        double[] blockS = new double[gx * gy];
        for (int by = 0; by < gy; by++) {
            for (int bx = 0; bx < gx; bx++) {
                int x0 = bx * bs, y0 = by * bs;
                int bw = Math.min(bs, w - x0), bh = Math.min(bs, h - y0);
                blockR[by * gx + bx] = res.rLocal[by][bx];
                double m = 0;
                for (int y = 0; y < bh; y++) {
                    for (int x = 0; x < bw; x++) {
                        m += world.height[y0 + y][x0 + x];
                    }
                }
                m /= (bw * bh);
                double var = 0;
                for (int y = 0; y < bh; y++) {
                    for (int x = 0; x < bw; x++) {
                        double d = world.height[y0 + y][x0 + x] - m;
                        var += d * d;
                    }
                }
                blockV[by * gx + bx] = var / (bw * bh);
                double[][] sub = new double[bh][bw];
                for (int y = 0; y < bh; y++) {
                    System.arraycopy(res.psi[y0 + y], x0, sub[y], 0, bw);
                }
                blockS[by * gx + bx] = new Axis().structure(sub, bw, bh);
            }
        }
        double[] sorted = blockR.clone();
        java.util.Arrays.sort(sorted);
        int n = gx * gy;
        double thrHi = sorted[(int) (n * 0.75)];
        double thrLo = sorted[(int) (n * 0.25)];
        double hiV = 0, loV = 0, hiS = 0, loS = 0;
        int cn = 0, cni = 0;
        for (int i = 0; i < n; i++) {
            if (blockR[i] >= thrHi) { hiV += blockV[i]; hiS += blockS[i]; cn++; }
            if (blockR[i] <= thrLo) { loV += blockV[i]; loS += blockS[i]; cni++; }
        }
        if (cn == 0 || cni == 0 || loV <= 1e-12) {
            world.observerRatio = 1.0;
            world.observerStructRatio = 1.0;
        } else {
            world.observerRatio = (hiV / cn) / (loV / cni);
            world.observerStructRatio = (hiS / cn) / (loS / cni);
        }
        return world;
    }

    /** Connected components (4-connectivity) of a boolean mask. */
    public static int connectedComponents(boolean[][] m, int w, int h) {
        boolean[][] seen = new boolean[h][w];
        int[] qx = new int[w * h], qy = new int[w * h];
        int comps = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!m[y][x] || seen[y][x]) continue;
                comps++;
                int head = 0, tail = 0;
                qx[tail] = x;
                qy[tail] = y;
                tail++;
                seen[y][x] = true;
                while (head < tail) {
                    int cx = qx[head], cy = qy[head];
                    head++;
                    if (cx > 0 && m[cy][cx - 1] && !seen[cy][cx - 1]) { seen[cy][cx - 1] = true; qx[tail] = cx - 1; qy[tail] = cy; tail++; }
                    if (cx < w - 1 && m[cy][cx + 1] && !seen[cy][cx + 1]) { seen[cy][cx + 1] = true; qx[tail] = cx + 1; qy[tail] = cy; tail++; }
                    if (cy > 0 && m[cy - 1][cx] && !seen[cy - 1][cx]) { seen[cy - 1][cx] = true; qx[tail] = cx; qy[tail] = cy - 1; tail++; }
                    if (cy < h - 1 && m[cy + 1][cx] && !seen[cy + 1][cx]) { seen[cy + 1][cx] = true; qx[tail] = cx; qy[tail] = cy + 1; tail++; }
                }
            }
        }
        return comps;
    }

    public static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    public static double smoothstep(double v) {
        return v * v * (3 - 2 * v);
    }
}
