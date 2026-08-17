# 🌊 TRINITY v4.0 · 四极生成引擎（Java 17）

TRINITY v4.0「噪声统一场论」的可运行实现。纯 JDK 17，零外部依赖。

**核心命题**：认知始于噪声。四极（PAGITY 过滤 / NEMO 耦合 / RAMANUJAN 观察 / AXIS 度量）同时处理同一噪声场，世界从噪声中涌现结构——**计算成本不变，世界更复杂、更自然、更涌现**。

## 运行

```bat
run.bat                      :: 编译 + 默认运行 (seed=1976124607, 1024x1024)
run.bat --seed 42 --size 512 --rounds 4 --out out2
java -cp build trinity.Main3D --size 64 --out out3d   :: 3D 密度体
```

输出（`out/` 目录）：

| 文件 | 内容 |
|---|---|
| `heightmap.png` | 1024×1024 地形高度图（海洋→沙滩→草原→山岩→雪线） |
| `structure.png` | 结构叠加图（金色=矿脉，深色=洞穴，蓝色=河流） |
| `slice.png` | 垂直切面（地层/洞穴/矿脉/天空） |
| `axis_report.txt` | AXIS 报告：迭代历史、终态度量、可证伪预测检验 |

3D 输出（`out3d/`）：`xy_slice.png` / `xz_slice.png` / `yz_slice.png`（正交三切面）+ `montage.png`（4×4 z 方向蒙太奇）+ `axis_report3d.txt`（含快速路径校准对比）。

## 架构

```
src/trinity/
  core/Rng.java           确定性 RNG + 整数哈希（全引擎可复现）
  core/FastTerrain.java   快速路径：逐点可求值的 3D 密度场（O(1)/块，无限范围，
                          与数组管线统计校准一致）——Minecraft mod 的世界求值核心
  poles/Ramanujan.java    RAMANUJAN 观察极：c_q(n) 精确查表（μ/φ 恒等式）、
                          互素采样、干涉模板（2-q / 4-q）、φ/μ 区域权重、图纹清晰度 R(t)
  poles/Pagity.java       PAGITY 过滤极：高斯低通/带通、软阈值、η_P 效率度量
  poles/Nemo.java         NEMO 耦合极：N[η]=η·(1+κ·|∇η|)、随机共振项、κ_N 强度度量
  poles/Axis.java         AXIS 度量极：Shannon 熵 H、拉普拉斯结构度 S、涌现模式判定、
                          自适应调控（浪涌/观察优化/扰动注入）、参数钳制
  gen/Engine.java         四极管线：噪声场初始化（外部+呼吸心跳+光幕）→ 三轮
                          P/N/R 并行 → 反应-扩散状态更新 → AXIS 度量与适应
  gen/WorldGen.java       世界生成：高度图 + 洞穴（双干涉 XOR）+ 矿脉（φ 区域）
                          + 河流（最陡下降）+ 密度切面 + 观察者门控地形
  gen3d/Engine3D.java     3D 四极引擎：3D 噪声场（地形骨架 bias 烘焙）→ 3D 滤波/
                          3D 梯度耦合/3D 干涉模板 → 3D 反应-扩散更新 → 3D 熵/结构度
  gen3d/WorldGen3D.java   3D 体素世界：实心/洞穴/矿脉/水体/空气 + 动态海平面
  gen3d/ImageOut3D.java   3D 正交切面 + z 蒙太奇 PNG
  gen/ImageOut.java       PNG 导出（JDK ImageIO）
  Main.java               CLI 入口 + 可证伪预测检验
  Main3D.java             3D CLI 入口 + 快速路径校准
```

## 管线（对应协议章节）

```
外部噪声 + 呼吸/心跳振荡 + 光幕（拉马努金和）       ← §2 噪声场
        ↓
PAGITY 低通/带通 ── NEMO 梯度耦合 ── RAMANUJAN 观察（R_local 场）
        ↓                                 ↑ 观察者门控：R 前 25% 区块
Ψ(t+1) = Ψ + D_eff·∇²Ψ + f(Ψ) + (P+N+R[η])  升级为 4-q 干涉模板
        ↓                                 ← §4 状态场动力学
AXIS: H ↓ 且 S ↑ → 涌现 → 降采样密度（观察即优化）
      H ↑ 且 S ↓ → 退化 → 浪涌（κ↑、光幕α↑）
      停滞 → 注入扰动                             ← §3.4 自适应
        ↓
世界生成（高度/洞穴/矿脉/河流）→ 四张图 + AXIS 报告
```

## 可证伪预测检验（seed=1976124607, 1024²，实测）

| # | 预测 | 目标 | 实测 | 状态 |
|---|---|---|---|---|
| P1 | 相同计算量下结构度提升 | >1.5× | 高度图 22.1×（字段级 69×） | PASS |
| P2 | 25% 互素采样保留大尺度结构 | >0.90 | L2 保留率 0.918 | PASS |
| P3 | 干涉模板：洞穴复杂度↑、时间开销<10% | >1.4× / <10% | 碎片化 4.68× / **+0.0%** | PASS |
| P5 | 观察者效应：被观察区块更复杂 | >1.20 | 地形局部方差比 1.59 | PASS |

**3D 密度体（seed=1976124607, 128³）**：

| 度量 | 实测 |
|---|---|
| P1-3D 结构度 vs 基线 | 48.8× PASS |
| P5-3D 观察者效应 | 2.99 PASS |
| 世界构成 | solid 28.3% · cave 0.8% · ore 0.84% · water 1.4% |
| 矿脉分布（16³ 晶格离散矿体，深度加权抽型） | 煤 0.263% ≈ 铁 0.305% > 金 0.197% > 钻石 0.071% |
| 快速路径校准（FastTerrain vs 数组管线） | solid +6.2% · cave +0.43pp PASS |

诚实的边界：P1 字段级比值被拉普拉斯能量放大（噪声场对比），报告同时给出高度图级比值作为公平对比。P3 时间开销在预热后实测为零——行表化后 4-q 模板只多 2 次数组读取。快速路径是数组管线（三轮反应-扩散累积）的统计校准模拟，非逐格拷贝。

## 接入 Minecraft（Fabric 路径）

1. **核心层已与 Minecraft 解耦**：`WorldGen.generate()` 的输入是 `Engine.Result`（四极状态场），输出是高度/洞穴/矿脉/河流掩膜——这些就是区块生成器需要的全部信息。
2. Fabric 侧实现 `ChunkGenerator`：对每个区块（16×16 列）调用 `Engine.run(seed, 区块坐标)` 的一个局部窗口版本（或按需逐区块缓存 `Engine.Result`），把 `height/cave/ore/river` 映射为方块：`height` → 地表 Y；`cave` → 空气/岩浆；`ore` → 金/铁矿石；`river` → 水。
3. 光幕注入与呼吸/心跳振荡对应服务端刻（tick）级刷新：`breath(t)=sin(ω_b t)` 可映射为区块重新生成的节律。
4. 性能：互素采样（25%）+ 行表查表（c_q 无三角函数）让每区块成本与原版噪声同阶；AXIS 的采样密度自适应可直接控制区块内评估列数。
5. 需要 LoRA/接口层说明可继续扩展：`Engine` 的 `Params`（§9.1 全部参数）可在配置文件中暴露给玩家。

## 参数（`Axis.Params`，全部可外部覆盖）

D₀=0.015 · γ_growth=0.002 · α(光幕)=0.6 · κ=0.6 · decay=0.995 · 采样密度 0.25 · 滤波半径 3 · 模板层级 1（R_local 前 25% 区块自动升 4-q）

## 复现性

同一 seed 生成完全一致的世界与报告（SplitMix64 + 全确定性管线）。`run.bat --seed 1976124607` 应复现上表结果。
