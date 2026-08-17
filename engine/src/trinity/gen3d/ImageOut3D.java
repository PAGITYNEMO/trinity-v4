package trinity.gen3d;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** 3D world visualization: three orthogonal slices + a z-montage. */
public final class ImageOut3D {

    private static int[] colorOf(WorldGen3D.World w, int z, int y, int x) {
        if (w.ore[z][y][x]) return new int[]{232, 190, 70};
        if (w.cave[z][y][x]) return new int[]{28, 28, 34};
        if (w.water[z][y][x]) return new int[]{44, 92, 180};
        if (w.stone[z][y][x]) {
            double d = w.densityShade != null ? w.densityShade[z][y][x] : 0;
            int s = (int) (105 + d * 40);
            return new int[]{s, s, s + 8};
        }
        int sky = 200 - (int) (y * 0.35);
        return new int[]{140, 190, sky};
    }

    /** XY slice at fixed z (looking down the column axis). */
    public static void xySlice(WorldGen3D.World w, int z0, File file) throws Exception {
        int n = w.n;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int[] c = colorOf(w, z0, y, x);
                img.setRGB(x, n - 1 - y, (c[0] << 16) | (c[1] << 8) | c[2]);
            }
        }
        ImageIO.write(img, "png", file);
    }

    /** XZ slice at fixed y (map view). */
    public static void xzSlice(WorldGen3D.World w, int y0, File file) throws Exception {
        int n = w.n;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < n; z++) {
            for (int x = 0; x < n; x++) {
                int[] c = colorOf(w, z, y0, x);
                img.setRGB(x, n - 1 - z, (c[0] << 16) | (c[1] << 8) | c[2]);
            }
        }
        ImageIO.write(img, "png", file);
    }

    /** YZ slice at fixed x (side view). */
    public static void yzSlice(WorldGen3D.World w, int x0, File file) throws Exception {
        int n = w.n;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < n; z++) {
            for (int y = 0; y < n; y++) {
                int[] c = colorOf(w, z, y, x0);
                img.setRGB(z, n - 1 - y, (c[0] << 16) | (c[1] << 8) | c[2]);
            }
        }
        ImageIO.write(img, "png", file);
    }

    /** 4x4 montage of XY slices across z — the density body's evolution. */
    public static void montage(WorldGen3D.World w, File file) throws Exception {
        int n = w.n;
        int cols = 4, rows = 4;
        BufferedImage img = new BufferedImage(n * cols, n * rows, BufferedImage.TYPE_INT_RGB);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int z0 = Math.min(n - 1, ((r * cols + c) * n) / (rows * cols));
                for (int y = 0; y < n; y++) {
                    for (int x = 0; x < n; x++) {
                        int[] col = colorOf(w, z0, y, x);
                        img.setRGB(c * n + x, r * n + (n - 1 - y),
                                (col[0] << 16) | (col[1] << 8) | col[2]);
                    }
                }
            }
        }
        ImageIO.write(img, "png", file);
    }
}
