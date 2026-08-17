package trinity.gen;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** PNG export: heightmap, structure overlay, vertical slice. JDK built-in only. */
public final class ImageOut {

    private static final int[][] RAMP = {
            {24, 58, 120},   // 0.00 deep water
            {44, 92, 180},   // 0.26 water
            {216, 196, 138}, // 0.33 sand
            {86, 168, 84},   // 0.40 grass
            {118, 118, 126}, // 0.60 stone
            {150, 150, 158}, // 0.72 rock
            {238, 242, 246}  // 0.85 snow
    };
    private static final double[] STOPS = {0.0, 0.26, 0.33, 0.40, 0.60, 0.72, 0.85};

    public static int[] terrainColor(double v) {
        if (v <= STOPS[0]) return RAMP[0].clone();
        for (int i = 1; i < STOPS.length; i++) {
            if (v <= STOPS[i]) {
                double t = (v - STOPS[i - 1]) / (STOPS[i] - STOPS[i - 1]);
                int[] a = RAMP[i - 1], b = RAMP[i];
                return new int[]{
                        (int) (a[0] + (b[0] - a[0]) * t),
                        (int) (a[1] + (b[1] - a[1]) * t),
                        (int) (a[2] + (b[2] - a[2]) * t)};
            }
        }
        return RAMP[RAMP.length - 1].clone();
    }

    public static void heightmap(WorldGen.World world, int size, File file) throws Exception {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int[] c = terrainColor(world.height[y][x]);
                img.setRGB(x, y, (c[0] << 16) | (c[1] << 8) | c[2]);
            }
        }
        ImageIO.write(img, "png", file);
    }

    public static void structure(WorldGen.World world, int size, File file) throws Exception {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int r, g, b;
                if (world.river[y][x]) {
                    r = 70; g = 130; b = 220;
                } else if (world.ore[y][x]) {
                    r = 232; g = 190; b = 70;
                } else if (world.cave[y][x]) {
                    r = 38; g = 38; b = 44;
                } else {
                    int[] c = terrainColor(world.height[y][x]);
                    r = c[0]; g = c[1]; b = c[2];
                }
                img.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        ImageIO.write(img, "png", file);
    }

    public static void slice(WorldGen.World world, int size, File file) throws Exception {
        int sh = world.density.length;
        BufferedImage img = new BufferedImage(size, sh, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < sh; y++) {
            for (int x = 0; x < size; x++) {
                double d = world.density[y][x];
                int r, g, b;
                if (d < 0.08) {
                    // sky gradient
                    int light = 220 - (int) (y * 0.5);
                    r = 140; g = 190; b = light;
                } else if (d > 0.85) {
                    r = 232; g = 190; b = 70; // ore
                } else if (d < 0.2) {
                    r = 25; g = 25; b = 30;   // cave
                } else {
                    int shade = 70 + (int) (d * 120);
                    r = shade; g = shade; b = shade + 8;
                }
                img.setRGB(x, sh - 1 - y, (r << 16) | (g << 8) | b);
            }
        }
        ImageIO.write(img, "png", file);
    }
}
