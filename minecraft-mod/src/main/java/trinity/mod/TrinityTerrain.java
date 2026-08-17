package trinity.mod;

import trinity.core.FastTerrain;

/**
 * Minecraft-world wrapper around the point-evaluable four-pole density body.
 *
 * World coordinates: y is absolute; the terrain body spans [MIN_Y, MIN_Y+HEIGHT).
 * The observer gate (R_local) is evaluated per chunk as the pattern clarity of
 * the chunk's 16x16 terrain-skeleton window: chunks the observer "sees" patterns
 * in receive the upgraded 4-q interference template — the observer effect at
 * world scale.
 */
public final class TrinityTerrain {
    public static final int MIN_Y = -64;
    public static final int HEIGHT = 320;
    public static final int SEA_Y = MIN_Y + 72; // ≈ 0.225 * HEIGHT, calibrated to surface mean
    public static final int DEEPSLATE_Y = MIN_Y + 64; // below: deepslate + deepslate ores

    /** Solid / cave thresholds — calibrated against WorldGen3D (Main3D --fast). */
    public static final double T_SOLID = 0.35;
    public static final double T_CAVE = -0.8;

    private final long seed;
    private final FastTerrain ft;

    public TrinityTerrain(long seed) {
        this.seed = seed;
        this.ft = new FastTerrain(seed, HEIGHT);
    }

    public long seed() {
        return seed;
    }

    /** World Y of the terrain surface at column (x, z). */
    public int surface(int x, int z) {
        return MIN_Y + (int) (ft.surface01(x, z) * HEIGHT);
    }

    /** Raw four-pole density at world coords; surf must come from surface(). */
    public double density(int x, int y, int z, boolean rich, int surf) {
        return ft.densityAt(x, y - MIN_Y, z, rich, (surf - MIN_Y) * 1.0);
    }

    /** Ore type at world coords: 0 none, 1 coal, 2 iron, 3 gold, 4 diamond. */
    public int oreType(int x, int y, int z) {
        return ft.oreType(x, y - MIN_Y, z);
    }

    /**
     * Observer gate: pattern clarity of the chunk's terrain-skeleton window.
     * Concentration x activity over the 16x16 surface01 grid; a chunk whose
     * skeleton shows a clear pattern is "observed" and gets the 4-q template.
     */
    public boolean chunkRich(int chunkX, int chunkZ) {
        double[] vals = new double[16 * 16];
        int k = 0;
        for (int dz = 0; dz < 16; dz++) {
            for (int dx = 0; dx < 16; dx++) {
                vals[k++] = ft.surface01(chunkX * 16 + dx, chunkZ * 16 + dz);
            }
        }
        return clarity2(vals) > 0.10;
    }

    private double clarity2(double[] vals) {
        double mean = 0;
        for (double v : vals) mean += v;
        mean /= vals.length;
        double var = 0;
        for (double v : vals) var += (v - mean) * (v - mean);
        double stdNorm = Math.min(1, Math.sqrt(var / vals.length) / 0.25);

        double best = 0;
        for (int q = 3; q <= 12; q++) {
            int[] hist = new int[q];
            for (double v : vals) {
                int r = (int) Math.round(v * (q * 8)) % q;
                if (r < 0) r += q;
                hist[r]++;
            }
            double hE = 0;
            for (int r = 0; r < q; r++) {
                double p = hist[r] / (double) vals.length;
                if (p > 0) hE -= p * Math.log(p);
            }
            double rq = (1 - hE / Math.log(q)) * stdNorm;
            if (rq > best) best = rq;
        }
        return best;
    }
}
