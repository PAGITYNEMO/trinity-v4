package trinity.core;

/**
 * Deterministic SplitMix64-based RNG plus integer hashing helpers.
 * Everything in the engine is reproducible from a single seed.
 */
public final class Rng {
    private long s;

    public Rng(long seed) {
        s = seed;
    }

    public long nextLong() {
        s += 0x9E3779B97F4A7C15L;
        long z = s;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public int nextInt(int bound) {
        if (bound <= 0) throw new IllegalArgumentException("bound must be > 0");
        return (int) Long.remainderUnsigned(nextLong(), bound);
    }

    public double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    public double nextGaussian() {
        double u, v, s2;
        do {
            u = 2 * nextDouble() - 1;
            v = 2 * nextDouble() - 1;
            s2 = u * u + v * v;
        } while (s2 >= 1 || s2 == 0);
        return u * Math.sqrt(-2 * Math.log(s2) / s2);
    }

    /** Deterministic 2D hash into [0,1). */
    public static double hash01(long x, long z, long salt) {
        long h = x * 0x9E3779B97F4A7C15L + z * 0xC2B2AE3D27D4EB4FL + salt * 0x165667B19E3779F9L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }

    /** Deterministic 3D hash into [0,1). */
    public static double hash3(long x, long y, long z, long salt) {
        long h = x * 0x9E3779B97F4A7C15L + y * 0xC2B2AE3D27D4EB4FL
               + z * 0x165667B19E3779F9L + salt * 0xBF58476D1CE4E5B9L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }

    public static int gcd(int a, int b) {
        a = Math.abs(a);
        b = Math.abs(b);
        while (b != 0) {
            int t = a % b;
            a = b;
            b = t;
        }
        return a;
    }
}
