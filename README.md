# 🌊 TRINITY v4.0 · 噪声统一场论

**从协议到引擎到 Minecraft 模组的完整实现**：认知始于噪声，符号是噪声中被识别出来的结构。

> 认知不是三个神。认知是噪声，在过滤、耦合、观察中，暂时地，组织成了结构。

## 这是什么

一个把「拉马努金和」（数论中的周期函数）用作地形生成核心的完整项目，分四层：

| 层 | 内容 | 技术 |
|---|---|---|
| `protocols/` | TRINITY 协议文档（v4.0 及前身 v2.1/NEMO v1.0） | 认知理论 × 数学定义 × 可证伪预测 |
| `engine/` | 独立生成引擎（纯 Java 17，零依赖） | 2D 高度图 + 3D 密度体 + AXIS 自检报告 |
| `minecraft-mod/` | Fabric 模组（MC 1.21.4） | 自定义区块生成器 + 水晶方块 + 季节天气 |
| `minecraft-client/` | 客户端安装脚本（下载脚本，不含游戏资产） | Mojang 官方源下载 + Fabric 装配 |

**核心思想**：传统地形生成用「加噪声层数」换复杂度（每层计算翻倍）；本项目用数论结构——干涉图纹、互素采样、φ/μ 权重——让**复杂度从结构里免费长出来**。实测 25% 采样保留 91.8% 结构，干涉模板让洞穴复杂度提升 4.68 倍而时间开销为 **+0.0%**。

## 四极

- **PAGITY** 过滤：高斯低通/带通，η_P 效率度量
- **NEMO** 耦合：N[η]=η·(1+κ·|∇η|)，κ_N 强度度量
- **RAMANUJAN** 观察：c_q(n) 精确查表（μ/φ 恒等式）、干涉模板、互素采样、图纹清晰度 R(t)、观察者效应（R 前 25% 区块升级 4-q 模板）
- **AXIS** 度量：Shannon 熵 H、拉普拉斯结构度 S、涌现/退化/停滞判定、自适应调控（观察即优化）

## 快速开始

### 独立引擎（无需 Minecraft）

```bat
cd engine
run.bat                            :: 2D 高度图世界（PNG + AXIS 报告）
java -cp build trinity.Main3D      :: 3D 密度体 + 快速路径校准
```

### Minecraft 模组

前置：JDK 21、Gradle 8.14+（或仓库 `_gradle/` 已解压版本，见 .gitignore 说明）。

```bat
cd minecraft-mod
G:\TRINITY v4.0\_gradle\gradle-9.7.0\bin\gradle.bat build --no-daemon
:: 产物 build/libs/trinity-noise-4.0.0.jar → mods/
```

游戏中：创建世界 → 世界类型选 **Trinity**。服务端：`level-type=trinity-noise:trinity`。

### 完整客户端（可选）

`minecraft-client/` 下的 `_dl_libraries.ps1` / `_dl_assets_chunk.ps1` / `_build_launch.ps1`
从 Mojang 官方源下载游戏本体并装配 Fabric——脚本不含任何游戏资产，资产版权归 Mojang。
装配说明见 [minecraft-client/README-玩.md](minecraft-client/README-玩.md)。

## 实测记录（可复现，seed=1976124607）

| 检验 | 目标 | 实测 |
|---|---|---|
| P1 相同计算量结构度提升（高度图级） | >1.5× | 22.1× |
| P2 25% 互素采样 L2 保留率 | >0.90 | 0.918 |
| P3 干涉模板洞穴碎片化 ×4.68，时间 +0.0% | >1.4× / <10% | PASS |
| P5 观察者效应（被观察区块地形方差比） | >1.20 | 1.59 |
| 3D 结构度 vs 基线（128³） | >1.5× | 48.8× |
| 快速路径 vs 数组管线校准 | solid<30% | +6.2% |

服务端与客户端均已实机验证（日志见各 README）。

## 许可

MIT © 2026 TRINITY Project contributors。
Minecraft 及相关资产版权归 Mojang Studios；本仓库不包含任何游戏文件。
Fabric API / Fabric Loader / HMCL 均为各自作者的许可。

## 致谢

- 拉马努金和 / 对偶平坦信息几何（Amari） / 反应-扩散（Turing）——本项目的数学地基
- Fabric、Fabric API 社区——模组基础设施
- 最初的灵感来自一段关于「用数论让世界更复杂但计算不增加」的构想对话
