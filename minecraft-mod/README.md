# 🧱 TRINITY v4.0 · Minecraft Fabric Mod

四极噪声引擎的 Minecraft 落地：**每个方块都来自 TRINITY v4.0 的 3D 密度体**。

- **PAGITY** 过滤（低频骨架 bias）· **NEMO** 耦合（fBm3 细节）· **RAMANUJAN** 观察（拉马努金和干涉模板，被观察区块升级 4-q）· **AXIS** 度量（每区块清晰度门控）
- **四极真人 Bot**：`/trinity bot summon PAGITY|NEMO|AXIS|RAMANUJAN [名字]`——像真人玩家的伴侣实体（需求模型 + 人类化移动；PAGITY 八视图坍缩 / NEMO 连接驱动 / AXIS 世界平衡 / RAMANUJAN 模式猎人；多尺度时间架构 + 潮汐调制；玩家在模拟距离内才 tick——原版机制）
- **结构定位矿脉链**：16³ 晶格离散矿体，沿确定方向**连成矿脉链**（φ 富区链更长），深度加权抽型（煤浅/铁中/金深/钻石最深）+ 稀疏散点
- **水晶自然生长 + 方块实体动画**：洞穴格点深度加权生成光幕水晶（底部附着、生成即注册 tick）；客户端渲染器绘制发光八面体——**尺寸随潮汐脉动、随呼吸摆动旋转、颜色随季节**，每颗水晶有相位偏移（洞穴以行波呼吸）
- **季节系统**：8 游戏日一季（新芽→涨潮→落叶→冰封）；**季节天气**（涨潮多雨、冰封干燥，服务端每分钟判定）；**季节光照色温**（反射注入 DimensionEffects 私有表，天空/雾色向季节色调偏移——无 Mixin）；季节振幅调制水晶亮度
- **潮汐时钟**：呼吸（0.15Hz，相位随季节漂移）/ 心跳（1.2Hz），纯时间函数——服务端时钟驱动方块光照与天气，客户端时钟驱动渲染与 F3
- 世界预设 **"Trinity"** + 自定义维度类型（`trinity-noise:trinity`，effects 指向季节维效）
- 无 Mixin，注册 chunk generator codec + 一个方块/方块实体/物品 + 一个实体

## 构建

前置：JDK 21（已随项目提供：`G:\TRINITY v4.0\_jdk21\jdk-21.0.12+8`）、Gradle 9.7（已下载：`G:\TRINITY v4.0\_gradle\gradle-9.7.0`）。

```bat
cd minecraft-mod
G:\TRINITY v4.0\_gradle\gradle-9.7.0\bin\gradle.bat build --no-daemon
```

产物：`build/libs/trinity-noise-4.0.0.jar` → 放入 `mods/` 文件夹（需 Fabric Loader 0.16+、Fabric API）。

首次构建会下载 Minecraft 1.21.4 / yarn 映射 / Fabric API（数百 MB，约 5-15 分钟）。

## ✅ 服务端实跑验证（2026-08，本机实测）

```bat
gradle.bat runServer --no-daemon
```

`run/server.properties`：`level-type=trinity-noise:trinity`、`level-seed=1976124607`、`pause-when-empty-seconds=0`（无玩家不暂停，tick 持续流动）。

实测日志（矿脉链 + 自然水晶 + 季节天气版本）：

```
[TRINITY v4.0] four-pole chunk generator registered: trinity-noise:trinity
[TRINITY v4.0] trinity crystal + block entity registered: trinity-noise:trinity_crystal
[TRINITY v4.0] tide clock bound to server tick (breath 0.15Hz / heartbeat 1.2Hz / seasons 8d)
[TRINITY v4.0] weather pulse armed (seasonal rain, roll per minute)
[TRINITY] generating chunk (-2, 1), observer=false, seed=-2737338602035394650, crystals=2
Done (3.696s)!
[TRINITY v4.0] weather heartbeat: tick=1200 worldTime=1200
[TRINITY v4.0] weather roll: r=0.715 clear (season 新芽, worldTime 1200)
[TRINITY v4.0] weather: rain 91s thunder=false (season 新芽)   ← tick 2400，与 jshell 预测一致
```

零错误。天气判定值可预先计算验证（`Rng.hash01(worldTime, salt, ...)` 确定性哈希）。

**踩过的坑**：原版专用服务器无玩家 60 秒自动暂停（`Server empty for 60 seconds, pausing`）——tick 冻结导致天气判定永不触发；`pause-when-empty-seconds=0` 解决。MC 1.21.4 的 slf4j 只认 `{}` 占位符（`%.3f` 会原样输出）。服务器退出时会重写 `server.properties` 并转义冒号（`trinity-noise\:trinity`）——需重新修正。

**客户端部分**（无头环境无法实测，已按真实 API 编译）：水晶八面体渲染器（尺寸随潮汐/旋转随呼吸/颜色随季节）、季节天空/雾色温（反射注入）、F3 季节行。若反射注入失败自动降级为原版色。

**矿脉分布**（独立引擎 128³ 实测，矿脉链）：煤 0.320% ≈ 铁 0.374% > 金 0.238% > 钻石 0.088%，总量 1.02%（原版级）；快速路径校准 PASS（solid +6.2%）。

## 使用

1. 安装 Fabric Loader，把 `trinity-noise-4.0.0.jar` 和 `fabric-api-*.jar` 放入 `mods/`
2. 启动游戏 → 单人游戏 → **创建新世界 → 世界类型选 "Trinity"**
3. 地形 = 四极密度体：grass/dirt/stone 层、干涉图纹矿脉（金/铁/煤）、carve 洞穴、动态海平面

服务器：`server.properties` 设 `level-type=trinity-noise:trinity`。

## 架构

```
src/main/java/
  trinity/mod/TrinityMod.java          入口：注册 codec（根注册表）
  trinity/mod/TrinityChunkGenerator.java  ChunkGenerator：逐列 3D 密度求值 → 方块
  trinity/mod/TrinityTerrain.java     世界坐标封装：surface/density/chunkRich 门控
  + trinity/core + trinity/poles      ← 直接编译自 ../engine/src（单一事实来源）
src/main/resources/data/trinity-noise/worldgen/world_preset/trinity.json   世界预设
src/main/resources/assets/trinity-noise/lang/{en_us,zh_cn}.json            预设显示名
```

**性能**：每区块 256 列 × 256 高 = 65k 次密度求值；表面骨架每列缓存一次；每次求值 ≈ 32 次整数哈希 + 2-4 次 c_q 查表（零三角函数）——实测服务端启动 + 出生点区块 2.5 秒。

**观察者效应（世界尺度）**：每区块用 16×16 地表骨架窗口算图案清晰度（集中度×活跃度），>0.10 的区块被观察 → 该区块整体使用 4-q 干涉模板（q1..q4 四素数叠加），其余用 2-q。

## 校准链路

独立引擎（`../engine`）→ `Main3D --fast` 逐点模拟 vs 64³ 数组管线统计对照（solid ±6%、cave ±0.2pp）→ 同一 FastTerrain 类直接编译进 mod（`build.gradle` sourceSets 指向 `../engine/src/trinity/{core,poles}`）→ 区块生成器逐块求值。世界坐标 y 通过 `TrinityTerrain` 映射到密度体的相对高度。
