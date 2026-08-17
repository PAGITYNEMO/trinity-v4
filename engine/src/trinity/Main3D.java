package trinity;

import trinity.core.FastTerrain;
import trinity.gen3d.Engine3D;
import trinity.gen3d.ImageOut3D;
import trinity.gen3d.WorldGen3D;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * TRINITY v4.0 — 3D density body entry point.
 *
 * Usage:
 *   java -cp build trinity.Main3D [--seed N] [--size N] [--rounds N] [--out DIR]
 *
 * Produces xy_slice.png / xz_slice.png / yz_slice.png / montage.png and
 * axis_report3d.txt.
 */
public final class Main3D {

    public static void main(String[] args) throws Exception {
        long seed = 1976124607L;
        int n = 64;
        int rounds = 3;
        File out = new File("out3d");

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--seed" -> seed = Long.parseLong(args[++i]);
                case "--size" -> n = Integer.parseInt(args[++i]);
                case "--rounds" -> rounds = Integer.parseInt(args[++i]);
                case "--out" -> out = new File(args[++i]);
                default -> {
                    System.err.println("unknown arg: " + args[i]);
                    System.exit(2);
                }
            }
        }
        out.mkdirs();

        StringBuilder rep = new StringBuilder();
        rep.append("============================================================\n");
        rep.append(" TRINITY v4.0 · 3D 密度体 — 四极引擎报告 (3D)\n");
        rep.append("============================================================\n");
        rep.append(String.format("seed = %d\nsize = %d^3\nrounds = %d\n", seed, n, rounds));

        long t0 = System.nanoTime();
        Engine3D engine = new Engine3D();
        Engine3D.Result res = engine.run(seed, n, rounds);
        long tEngine = (System.nanoTime() - t0) / 1_000_000;

        rep.append("templates: q1=" + res.tq[0] + " q2=" + res.tq[1]
                + " / q3=" + res.tq[2] + " q4=" + res.tq[3] + "\n\n");
        rep.append("[AXIS 3D 迭代历史]\n");
        for (String line : res.history) rep.append("  ").append(line).append("\n");
        rep.append("\n[AXIS 3D 终态]\n");
        rep.append(String.format("  噪声熵 H(t)      = %.4f\n", res.H));
        rep.append(String.format("  结构度 S(t)      = %.4f\n", res.S));
        rep.append(String.format("  图纹清晰度 R(t)  = %.4f\n", res.R));
        rep.append(String.format("  过滤效率 eta_P   = %.4f\n", res.etaP));
        rep.append(String.format("  耦合强度 kappa_N = %.4f\n", res.kappaN));
        rep.append(String.format("  终态参数: kappa=%.3f alpha=%.3f\n\n",
                res.params.kappa, res.params.alpha));

        long t1 = System.nanoTime();
        WorldGen3D.World world = WorldGen3D.generate(res, n, seed);
        long tGen = (System.nanoTime() - t1) / 1_000_000;

        // ---- Fast-path calibration: point-evaluable emulation, same grid ----
        FastTerrain ft = new FastTerrain(seed, n);
        long tF = System.nanoTime();
        WorldGen3D.World fast = buildFast(res, ft, n, seed);
        long tFast = (System.nanoTime() - tF) / 1_000_000;

        double total = (double) n * n * n;
        rep.append("[3D 世界统计]\n");
        rep.append(String.format("  平均地表高度   = %.3f / %d\n", world.surfaceMean * n, n));
        rep.append(String.format("  实心体积       = %.2f%%\n", world.solidVol / total * 100));
        rep.append(String.format("  洞穴体积       = %.2f%%\n", world.caveVol / total * 100));
        rep.append(String.format("  矿脉体积       = %.2f%%\n", world.oreVol / total * 100));
        rep.append(String.format("  水体体积       = %.2f%%\n", world.waterVol / total * 100));
        rep.append(String.format("  空气体积       = %.2f%%\n", world.airVol / total * 100));
        rep.append(String.format("  海平面         = %d\n\n", world.seaLevel));

        rep.append("[矿脉分布 (真实权重: 深度高斯剖面 + 脉状团簇 + φ/干涉门控)]\n");
        rep.append(String.format("  煤:   %.3f%% (浅层) | 铁: %.3f%% (中层) | 金: %.3f%% (深层) | 钻石: %.3f%% (最深)\n",
                world.oreCoal / total * 100, world.oreIron / total * 100,
                world.oreGold / total * 100, world.oreDiamond / total * 100));

        // P1-3D: structure vs pure fBm3 baseline.
        long t2 = System.nanoTime();
        double[][][] base = Engine3D.baselineField(seed, n);
        long tBase = (System.nanoTime() - t2) / 1_000_000;
        double sBase = Engine3D.structure3(base, n);

        rep.append("[可证伪预测检验 (3D)]\n");
        rep.append(String.format(
                "  P1-3D 结构度: TRINITY S=%.4f vs 基线 S=%.4f, 比值=%.2f  [%s] (目标 > 1.50)\n",
                res.S, sBase, res.S / (sBase + 1e-12), res.S / (sBase + 1e-12) > 1.5 ? "PASS" : "FAIL"));
        rep.append(String.format(
                "  P5-3D 观察者效应: 高R区|密度|/低R区 = %.2f  [%s] (目标 > 1.20)\n",
                world.observerRatio, world.observerRatio > 1.2 ? "PASS" : "FAIL"));
        rep.append(String.format("  R_local 分布: blocks=%d, 门控阈值=%.4f\n\n",
                res.rLocal.length * res.rLocal[0].length, res.richThreshold));

        rep.append("[快速路径校准 (FastTerrain vs 数组管线)]\n");
        double ftotal = (double) n * n * n;
        rep.append(String.format("  数组: solid=%.1f%% cave=%.1f%% ore=%.2f%% water=%.1f%% 地表=%.3f  (%d ms)\n",
                world.solidVol / total * 100, world.caveVol / total * 100,
                world.oreVol / total * 100, world.waterVol / total * 100,
                world.surfaceMean, tGen));
        rep.append(String.format("  快速: solid=%.1f%% cave=%.1f%% ore=%.2f%% water=%.1f%% 地表=%.3f  (%d ms)\n",
                fast.solidVol / ftotal * 100, fast.caveVol / ftotal * 100,
                fast.oreVol / ftotal * 100, fast.waterVol / ftotal * 100,
                fast.surfaceMean, tFast));
        double dSolid = Math.abs(fast.solidVol - world.solidVol) / (double) Math.max(1, world.solidVol);
        double dCavePp = Math.abs(fast.caveVol - world.caveVol) / total * 100;
        boolean calib = dSolid < 0.30 && dCavePp < 0.5;
        rep.append(String.format("  偏差: solid %+.1f%% (相对) cave %+.2fpp (绝对)  [%s] (目标 solid<30%% cave<0.5pp)\n",
                dSolid * 100, dCavePp, calib ? "PASS" : "FAIL"));

        rep.append("[运行时间]\n");
        rep.append(String.format("  3D 四极引擎: %d ms\n", tEngine));
        rep.append(String.format("  3D 世界构建: %d ms\n", tGen));
        rep.append(String.format("  基线场: %d ms\n", tBase));
        rep.append(String.format("  总计: %d ms\n", tEngine + tGen + tBase));
        rep.append("\n[输出文件]\n");
        rep.append("  xy_slice.png / xz_slice.png / yz_slice.png / montage.png / axis_report3d.txt\n");
        rep.append("============================================================\n");

        ImageOut3D.xySlice(world, n / 2, new File(out, "xy_slice.png"));
        ImageOut3D.xzSlice(world, world.seaLevel, new File(out, "xz_slice.png"));
        ImageOut3D.yzSlice(world, n / 2, new File(out, "yz_slice.png"));
        ImageOut3D.montage(world, new File(out, "montage.png"));
        Files.write(new File(out, "axis_report3d.txt").toPath(), rep.toString().getBytes(StandardCharsets.UTF_8));

        System.out.println("=== TRINITY v4.0 3D engine done ===");
        System.out.printf("seed=%d size=%d^3 | H=%.4f S=%.4f R=%.4f kappaN=%.2f%n",
                seed, n, res.H, res.S, res.R, res.kappaN);
        System.out.printf("world: solid=%.1f%% cave=%.1f%% ore=%.1f%% water=%.1f%% | P1=%.2f P5=%.2f%n",
                world.solidVol / total * 100, world.caveVol / total * 100,
                world.oreVol / total * 100, world.waterVol / total * 100,
                res.S / (sBase + 1e-12), world.observerRatio);
        System.out.println("outputs -> " + out.getAbsolutePath());
    }

    /** Build a world from the point-evaluable fast terrain (calibration). */
    private static WorldGen3D.World buildFast(Engine3D.Result res, FastTerrain ft, int n, long seed) {
        WorldGen3D.World w = new WorldGen3D.World();
        w.n = n;
        w.stone = new boolean[n][n][n];
        w.cave = new boolean[n][n][n];
        w.ore = new boolean[n][n][n];
        w.water = new boolean[n][n][n];
        w.densityShade = res.density;

        int bs = res.blockSize;
        double richThr = res.richThreshold;
        double[][] rLocal = res.rLocal;

        int[] surfaceY = new int[n * n];
        double surfaceSum = 0;
        int surfaceCnt = 0;
        for (int z = 0; z < n; z++) {
            for (int x = 0; x < n; x++) {
                int s = -1;
                for (int y = n - 1; y >= 0; y--) {
                    boolean rich = rLocal[z / bs][x / bs] >= richThr;
                    if (ft.density(x, y, z, rich) > 0.35) {
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

        int[] tq = ft.templateQ();
        for (int z = 0; z < n; z++) {
            for (int x = 0; x < n; x++) {
                int surface = surfaceY[z * n + x];
                boolean rich = rLocal[z / bs][x / bs] >= richThr;
                for (int y = 0; y < n; y++) {
                    double dv = ft.density(x, y, z, rich);
                    if (y > surface) {
                        w.airVol++;
                    } else if (dv < -0.8 && y <= surface - 1) {
                        w.cave[z][y][x] = true;
                        w.caveVol++;
                    } else if (dv > 0.35) {
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
        return w;
    }
}
