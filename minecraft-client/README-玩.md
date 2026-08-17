# 🎮 开玩指南 — TRINITY v4.0 Minecraft

完整 1.21.4 客户端 + Fabric 已预装完毕，开箱即玩。

## 目录结构

```
minecraft-client/
  .minecraft/                  ← 完整游戏目录（官方启动器 / HMCL 均可识别）
    versions/1.21.4/           ← 原版 1.21.4（client.jar + json）
    versions/fabric-loader-0.19.3-1.21.4/   ← Fabric 加载器
    libraries/ + classpath/    ← 全部依赖库（113 + 9 natives 已解压）
    assets/                    ← 完整资源（19 号索引，~420MB）
    mods/
      fabric-api-0.119.4+1.21.4.jar
      trinity-noise-4.0.0.jar  ← ★ 我们的四极噪声引擎
  start-offline.bat            ← 离线模式一键启动（免登录，可玩单机）
  HMCL/HMCL.jar                ← 正版登录启动器（v3.16.3）
```

## 方式 A：离线单机（最快，免登录）

双击 **`start-offline.bat`**。用户名 Trinity，直接进主菜单：

- 单人游戏 → 创建新世界 → **世界类型选 "Trinity"**（数据包预设）
- 世界生成：四极 3D 密度体地形 + 矿脉链 + 深板岩带 + 自然水晶
- 想看水晶心跳：`/give @s trinity-noise:trinity_crystal`，或 F3 看 `tide/season` 行

## 方式 B：正版登录（多人/成就/皮肤）

1. 双击 `HMCL/HMCL.jar`（HMCL v3.16.3，中文界面）
2. 账户 → 添加 → 微软登录（扫码/账密）
3. 设置游戏目录指向 `G:\TRINITY v4.0\minecraft-client\.minecraft`（或直接用默认目录，把 `mods/` 里两个 jar 复制过去）
4. 版本列表 → `fabric-loader-0.19.3-1.21.4`（已装好）→ 启动
5. 游戏内新建世界选 "Trinity"

> HMCL 首次启动可能要求下载 Java——本机已有 JDK 21（`G:\TRINITY v4.0\_jdk21`），在 HMCL 的 Java 设置里选择它即可。

## 世界里有啥（TRINITY v4.0 全特性）

| 特性 | 说明 |
|---|---|
| 四极地形 | PAGITY 过滤骨架 + NEMO 耦合细节 + RAMANUJAN 拉马努金和干涉模板 + AXIS 区块门控 |
| 矿脉链 | 16³ 晶格离散矿体沿确定方向连成矿脉；煤浅/铁中/金深/钻石最深（深板岩） |
| 光幕水晶 | 天然生于洞穴（深洞更密）；光照随心跳脉动 5-14；客户端渲染为随潮汐呼吸/旋转/变色的八面体 |
| 季节 | 8 游戏日一季（新芽→涨潮→落叶→冰封）；天气随季节（涨潮多雨）；天空/雾季节色温；F3 实时显示 |
| 出生点 | 每区块自然 2-7 颗水晶，种子 1976124607（server 测试用） |

## 故障排查

- 启动黑屏/闪退 → 看 `.minecraft\logs\latest.log` 尾部；多半是显卡驱动或 Java 版本（必须 21）
- 世界类型没有 "Trinity" → 确认 `mods/` 里有 `trinity-noise-4.0.0.jar`
- 想玩新种子 → 创建世界时自选种子
- 联机 → 用 `minecraft-mod\runServer`（服务端），客户端选 "Trinity" 进

## 重装说明

`minecraft-client/_install.ps1`（生成启动器）+ `_dl_libraries.ps1`/`_dl_assets_chunk.ps1`（依赖与资源下载器）保留在 `G:\TRINITY v4.0\` 根目录，需要时可重跑。
