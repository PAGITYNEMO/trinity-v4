package trinity.gen3d;

import trinity.core.FastTerrain;
import trinity.core.Rng;
import trinity.poles.Ramanujan;

/**
 * 3D world builder: the density body [-1,1] becomes a voxel world.
 *  - stone  where density > 0.02
 *  - caves  where density < -0.28 below the surface (void pockets)
 *  - ore    interference-strong, totient-rich seams near the surface
 *  - water  below sea level, where not solid
 *  - air    elsewhere
 */
public final class WorldGen3D {

    public static final class World {
        public int n;
        public boolean[][][] stone;  // [z][y][x]
        public boolean[][][] cave;
        public boolean[][][] ore;
        public boolean[][][] water;
        public double[][][] densityShade; // raw density for stone shading
        public int solidVol, caveVol, oreVol, waterVol, airVol;
        public int oreCoal, oreIron, oreGold, oreDiamond;
        public double surfaceMean;
        public double observerRatio; // P5-3D: |density| in observed blocks / unobserved
        public int seaLevel;
    }

    public static World generate(Engine3D.Result res, int n, long seed) {
        World w = new World();
        w.n = n;
        w.stone = new boolean[n][n][n];
        w.cave = new boolean[n][n][n];
        w.ore = new boolean[n][n][n];
        w.water = new boolean[n][n][n];
        w.densityShade = res.density;
        w.seaLevel = (int) (n * 0.45);

        Ramanujan ram = res.ram;
        int q1 = res.tq[0], q2 = res.tq[1];
        double[][][] d = res.psiRaw; // calibrated absolute thresholds, no min-max crushing

        final double T_SOLID = 0.35;
        final double T_CAVE = -0.8;

        // Pass 1: find surfaces, then set sea level relative to the terrain mean.
        int[] surfaceY = new int[n * n];
        double surfaceSum = 0;
        int surfaceCnt = 0;
        for (int z = 0; z < n; z++) {
            for (int x = 0; x < n; x++) {
                int s = -1;
                for (int y = n - 1; y >= 0; y--) {
                    if (d[z][y][x] > T_SOLID) {
                        s = y;
                        break;
                    }
                }
                surfaceY[z * n + x] = s;
                if (s >= 0) {
                    surfaceSum += s;
                    surfaceCnt++;
                }
            }
        }
        w.surfaceMean = surfaceCnt > 0 ? surfaceSum / surfaceCnt / n : 0;
        w.seaLevel = (int) (w.surfaceMean * n * 0.72);
        if (w.seaLevel < 4) w.seaLevel = 4;
        if (w.seaLevel > n * 0.6) w.seaLevel = (int) (n * 0.6);

        // Pass 2: assign voxel states.
        // Ore: realistic depth-weighted distribution (FastTerrain.oreType —
        // coal shallow / iron mid / gold deep / diamond deepest, vein clusters,
        // totient+interference gated).
        FastTerrain ft = new FastTerrain(seed, n);
        for (int z = 0; z < n; z++) {
            for (int x = 0; x < n; x++) {
                int surface = surfaceY[z * n + x];
                for (int y = 0; y < n; y++) {
                    double dv = d[z][y][x];
                    boolean solid = dv > T_SOLID;
                    if (y > surface) {
                        w.airVol++;
                    } else if (dv < T_CAVE && y < surface - 1) {
                        w.cave[z][y][x] = true;
                        w.caveVol++;
                    } else if (solid) {
                        int ot = ft.oreType(x, y, z);
                        if (ot != 0 && y <= surface - 1) {
                            w.ore[z][y][x] = true;
                            w.oreVol++;
                            switch (ot) {
                                case 1 -> w.oreCoal++;
                                case 2 -> w.oreIron++;
                                case 3 -> w.oreGold++;
                                case 4 -> w.oreDiamond++;
                                default -> {}
                            }
                        } else {
                            w.stone[z][y][x] = true;
                            w.solidVol++;
                        }
                    } else {
                        w.airVol++;
                    }
                    if (y <= w.seaLevel && !w.stone[z][y][x] && !w.cave[z][y][x] && !w.ore[z][y][x]) {
                        w.water[z][y][x] = true;
                        w.waterVol++;
                    }
                }
            }
        }

        // P5-3D: mean |density| in observed (top quartile R_local) blocks vs unobserved.
        int bs = res.blockSize;
        int gx = (n + bs - 1) / bs, gy = (n + bs - 1) / bs;
        double[] blockR = new double[gx * gy];
        double[] blockA = new double[gx * gy];
        for (int by = 0; by < gy; by++) {
            for (int bx = 0; bx < gx; bx++) {
                int x0 = bx * bs, y0 = by * bs;
                int bw = Math.min(bs, n - x0), bh = Math.min(bs, n - y0);
                blockR[by * gx + bx] = res.rLocal[by][bx];
                double acc = 0;
                for (int z = 0; z < n; z += 2) {
                    for (int y = 0; y < bh; y++) {
                        for (int x = 0; x < bw; x++) {
                            acc += Math.abs(d[z][y0 + y][x0 + x]);
                        }
                    }
                }
                blockA[by * gx + bx] = acc / (n / 2 * bh * bw);
            }
        }
        double[] sorted = blockR.clone();
        java.util.Arrays.sort(sorted);
        int nb = gx * gy;
        double thrHi = sorted[(int) (nb * 0.75)];
        double thrLo = sorted[(int) (nb * 0.25)];
        double hi = 0, lo = 0;
        int cn = 0, cni = 0;
        for (int i = 0; i < nb; i++) {
            if (blockR[i] >= thrHi) { hi += blockA[i]; cn++; }
            if (blockR[i] <= thrLo) { lo += blockA[i]; cni++; }
        }
        w.observerRatio = (cn == 0 || cni == 0 || lo <= 1e-12) ? 1.0 : (hi / cn) / (lo / cni);
        return w;
    }
}
