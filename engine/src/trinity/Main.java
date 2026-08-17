package trinity;

import trinity.core.Rng;
import trinity.gen.Engine;
import trinity.gen.ImageOut;
import trinity.gen.WorldGen;
import trinity.poles.Axis;
import trinity.poles.Ramanujan;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * TRINITY v4.0 engine entry point.
 *
 * Usage:
 *   java -cp build trinity.Main [--seed N] [--size N] [--rounds N] [--out DIR]
 *
 * Produces heightmap.png / structure.png / slice.png and axis_report.txt.
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        long seed = 1976124607L;
        int size = 1024;
        int rounds = 3;
        File out = new File("out");

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--seed" -> seed = Long.parseLong(args[++i]);
                case "--size" -> size = Integer.parseInt(args[++i]);
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
        rep.append(" TRINITY v4.0 · 噪声统一场论 — 四极生成引擎报告\n");
        rep.append("============================================================\n");
        rep.append(String.format("seed   = %d\nsize   = %d x %d\nrounds = %d\n", seed, size, size, rounds));

        // ---------- Engine run ----------
        long t0 = System.nanoTime();
        Engine engine = new Engine();
        Engine.Result res = engine.run(seed, size, rounds);
        long tEngine = (System.nanoTime() - t0) / 1_000_000;

        rep.append("templates: ").append(res.templateQ[0]).append(" / ").append(res.templateQ[1]).append("\n\n");
        rep.append("[AXIS 迭代历史]\n");
        for (String line : res.history) rep.append("  ").append(line).append("\n");
        Axis axis = res.axis;
        Axis.Params p = res.params;
        rep.append("\n[AXIS 终态]\n");
        rep.append(String.format("  噪声熵 H(t)      = %.4f\n", axis.H));
        rep.append(String.format("  结构度 S(t)      = %.4f\n", axis.S));
        rep.append(String.format("  图纹清晰度 R(t)  = %.4f\n", axis.R));
        rep.append(String.format("  过滤效率 eta_P   = %.4f\n", axis.etaP));
        rep.append(String.format("  耦合强度 kappa_N = %.4f\n", axis.kappaN));
        rep.append("  认知涌现模式      = ").append(axis.mode()).append("\n");
        rep.append(String.format("  终态参数: kappa=%.3f alpha=%.3f density=%.3f tier=%d\n\n",
                p.kappa, p.alpha, p.sampleDensity, p.templateTier));

        // ---------- World generation: templates FIRST, then no-template control ----------
        // Both paths are warmed up once; timings use the second call so JIT and
        // class loading do not pollute the P3 time-overhead comparison.
        WorldGen.generate(res, size, seed, true); // warmup
        long t1 = System.nanoTime();
        WorldGen.World world = WorldGen.generate(res, size, seed, true);
        long tGen = (System.nanoTime() - t1) / 1_000_000;

        WorldGen.generate(res, size, seed, false); // warmup
        long t2 = System.nanoTime();
        WorldGen.World control = WorldGen.generate(res, size, seed, false);
        long tControl = (System.nanoTime() - t2) / 1_000_000;

        // ---------- Baseline fBm-only field (P1) ----------
        long t3 = System.nanoTime();
        double[][] base = Engine.baselineField(seed, size);
        long tBase = (System.nanoTime() - t3) / 1_000_000;
        double sBase = new Axis().structure(base, size, size);
        // Heightmap-level comparison (fair: both are terrain height fields).
        double[][] baseH = new double[size][size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                baseH[y][x] = WorldGen.smoothstep(WorldGen.clamp01(base[y][x] * 0.5 + 0.5));
            }
        }
        double sBaseH = new Axis().structure(baseH, size, size);
        double sTrinH = new Axis().structure(world.height, size, size);

        // ---------- P2: coprime sampling retention ----------
        double p2ratio = world.sampleRetention;

        // ---------- P3: template complexity (fragmentation density) + time overhead ----------
        int compsT = WorldGen.connectedComponents(world.cave, size, size);
        int compsC = WorldGen.connectedComponents(control.cave, size, size);
        double areaT = Math.max(1, world.caveArea * size * size);
        double areaC = Math.max(1, control.caveArea * size * size);
        double p3complex = (compsT / Math.sqrt(areaT)) / (compsC / Math.sqrt(areaC));
        double p3time = tGen / (double) (tControl + 1);

        // ---------- P1: structure ratio ----------
        double p1field = axis.S / (sBase + 1e-12);
        double p1height = sTrinH / (sBaseH + 1e-12);

        // ---------- P5: observer effect ----------
        double p5 = world.observerRatio;

        rep.append("[世界统计]\n");
        rep.append(String.format("  平均高度      = %.4f\n", mean(world.height, size)));
        rep.append(String.format("  洞穴面积占比  = %.4f%%\n", world.caveArea * 100));
        rep.append(String.format("  矿脉面积占比  = %.4f%%\n", world.oreArea * 100));
        rep.append(String.format("  河流单元格    = %d\n", world.riverCells));
        rep.append(String.format("  互素采样密度  = %.2f%% (1/4 目标)\n\n", p.sampleDensity * 100));

        rep.append("[可证伪预测检验]\n");
        rep.append(String.format(
                "  P1 结构度(字段级): TRINITY S=%.4f vs 基线 S=%.4f, 比值=%.2f\n",
                axis.S, sBase, p1field));
        rep.append(String.format(
                "  P1 结构度(高度图级): TRINITY S=%.4f vs 基线 S=%.4f, 比值=%.2f  [%s] (目标 > 1.50)\n",
                sTrinH, sBaseH, p1height, p1height > 1.5 ? "PASS" : "FAIL"));
        rep.append(String.format(
                "  P2 互素采样: 25%%采样低频带 L2 保留率 = %.3f  [%s] (目标 > 0.90)\n",
                p2ratio, p2ratio > 0.9 ? "PASS" : "FAIL"));
        rep.append(String.format(
                "  P3 干涉模板: 洞穴碎片化密度提升 x%.2f, 时间开销 %+.1f%%  [%s] (目标 复杂度>1.4 且 时间<+10%%)\n",
                p3complex, (p3time - 1) * 100, (p3complex > 1.4 && p3time < 1.1) ? "PASS" : "PARTIAL"));
        rep.append(String.format(
                "  P5 观察者效应: 高R区地形局部方差/低R区 = %.2f (ψ结构度比 %.2f)  [%s] (目标 > 1.20)\n",
                p5, world.observerStructRatio, p5 > 1.2 ? "PASS" : "FAIL"));

        // R_local distribution diagnostics
        double rMin = Double.MAX_VALUE, rMax = -Double.MAX_VALUE, rSum = 0;
        int rN = 0;
        for (double[] row : res.rLocal) {
            for (double v : row) {
                rMin = Math.min(rMin, v);
                rMax = Math.max(rMax, v);
                rSum += v;
                rN++;
            }
        }
        rep.append(String.format("  R_local 分布: min=%.3f mean=%.3f max=%.3f (blocks=%d)\n",
                rMin, rSum / Math.max(1, rN), rMax, rN));
        rep.append(String.format("  观察门控: rich 区块占比 25%% (R_local >= %.4f)\n", res.richThreshold));

        rep.append("\n[运行时间]\n");
        rep.append(String.format("  四极引擎: %d ms\n", tEngine));
        rep.append(String.format("  地形生成(含模板): %d ms / 对照: %d ms\n", tGen, tControl));
        rep.append(String.format("  基线fBm场: %d ms\n", tBase));
        rep.append(String.format("  总计: %d ms\n", tEngine + tGen + tControl + tBase));
        rep.append("\n[输出文件]\n");
        rep.append("  heightmap.png / structure.png / slice.png / axis_report.txt\n");
        rep.append("============================================================\n");

        // ---------- Images ----------
        ImageOut.heightmap(world, size, new File(out, "heightmap.png"));
        ImageOut.structure(world, size, new File(out, "structure.png"));
        ImageOut.slice(world, size, new File(out, "slice.png"));
        Files.write(new File(out, "axis_report.txt").toPath(), rep.toString().getBytes(StandardCharsets.UTF_8));

        System.out.println("=== TRINITY v4.0 engine done ===");
        System.out.printf("seed=%d size=%d rounds=%d | mode=%s | H=%.4f S=%.4f R=%.4f%n",
                seed, size, rounds, axis.mode(), axis.H, axis.S, axis.R);
        System.out.printf("P1 height x%.2f (field x%.2f) | P2 keep=%.3f | P3 cave x%.2f (+%.1f%% time) | P5 obs=%.2f%n",
                p1height, p1field, p2ratio, p3complex, (p3time - 1) * 100, p5);
        System.out.println("outputs -> " + out.getAbsolutePath());
    }

    private static double mean(double[][] f, int n) {
        double acc = 0;
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) acc += f[y][x];
        return acc / (n * n);
    }
}
