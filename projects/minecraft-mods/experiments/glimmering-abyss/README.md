> v0.2 源码检查点：正在重新运行验证。构建版本为 0.2.0／Gradle 8.2.1；以下第一阶段安装文件与原始记录完整保留。新版本实际截图和报告将在验证后追加。

# Project 02 · 微光深渊（Glimmering Abyss）

Minecraft Java **1.20.1** 的独立地形／光照技术原型，版本 **0.1.0**。本阶段实现真正的区块生成、可行走的安全观景路线、原版方块光对照和实际游戏证据。

原始任务说明保留在 [BRIEF.md](BRIEF.md)。当前成果与限制见 [TEST-REPORT.md](TEST-REPORT.md)，选型、排错与提交溯源见 [开发日志](devlog/2026-10-12-phase1.md)，原始测量和运行日志在 [evidence/phase1/](evidence/phase1/)。

## 已实现

- 注册独立测试维度 `glimmeringabyss:study` 与自定义 `ChunkGenerator`。地形直接在 Minecraft 区块生成阶段产生，没有使用 WorldEdit、手工摆块或预建地图冒充生成。
- 固定研究种子 `74102002`；约 208 × 248 格的洞厅、七根岩柱、起伏底部与两段断崖；岩体边缘会随种子变化。
- 一段平整短通道与宽观景台，前方、两侧及后方入口两边具有两格高铁栏；三个固定观察位置。
- 26 个测试光源，包括洞厅岩面、近景和一个独立封闭校准通道内的光源。支持 **0／5／15** 光级对照，三种模式保持光源位置、方块形状与纹理一致。
- 使用原版 Minecraft 光照传播、材质和渲染；没有 Sodium、Iris、第三方光影、彩色光传播或屏幕后期。
- 测量工具读取真实 `ServerWorld` 的天空光、方块光、路线方块和地形指纹。可用两个全新世界核对重复生成。
- 可选客户端检查取得真实游戏帧缓冲截图、帧间隔，并让实际玩家沿通道前进测试护栏碰撞。

这是一处预设的程序化实验洞厅；尚未将生成器注入主世界自然洞穴，也未制作完整地下生态、城市、NPC 或玩法系统。

## 安装体验

1. 使用 **Minecraft Java 1.20.1 + Fabric Loader 0.14.25 + Java 17** 的独立测试实例。
2. 把 [glimmering-abyss-0.1.0.jar](artifacts/glimmering-abyss-0.1.0.jar) 与 [Fabric API 0.85.0+1.20.1](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.85.0+1.20.1/fabric-api-0.85.0+1.20.1.jar) 放进该实例的 `mods` 文件夹。
3. 新建测试世界，建议创造模式并允许作弊，聊天栏输入：

```mcfunction
/abyss enter
/abyss light glimmer
/abyss view platform
```

按 Minecraft 原有 WASD、鼠标与跳跃操作漫游。观景台护栏会挡住正常行走的玩家；创造模式飞行仍可越过护栏。

| 命令 | 用途 |
| --- | --- |
| `/abyss enter` | 进入通道观察点 |
| `/abyss view tunnel` | 通道机位 |
| `/abyss view platform` | 观景台中央机位 |
| `/abyss view edge` | 深渊边缘机位，仍位于护栏内 |
| `/abyss light baseline` | 所有测试光源为 0 级 |
| `/abyss light glimmer` | 所有测试光源为 5 级 |
| `/abyss light reference` | 所有测试光源为 15 级 |
| `/abyss audit` | 写出实际光照、路线与地形测量 JSON |
| `/abyss return` | 返回主世界出生点附近 |

切换光照后等待至少五秒再测量。光照和审计命令需要作弊／管理员权限。重新载入测试存档后，先执行一次光照切换，再做审计；审计的预期模式取自最近一次指令。

## 固定条件

| 参数 | 值 |
| --- | --- |
| Minecraft／Java | 1.20.1／17 |
| Fabric Loader／API | 0.14.25／0.85.0+1.20.1 |
| 研究种子 | 74102002，写入维度生成器 JSON |
| 自动测试主世界种子 | 74102002 |
| 测试维度高度 | Y = -64 至 127 |
| 天空光／环境亮度 | 无天空光，`ambient_light = 0.0` |
| 时间／生物 | 固定夜间；本测试生物群系没有生物生成条目 |
| 截图 | 1280 × 720，FOV 75，亮度设置 0.5 |
| 截图视距／模拟距离 | 12／6 区块 |

研究种子与普通世界创建时填写的种子分别记录。本维度的研究种子固定，因此另建主世界仍可复现同一实验洞厅。更改生成参数时使用新测试世界，避免旧区块掩盖生成器改动。

## 构建

需要 **JDK 17**；Gradle 8.1.1 Wrapper 已包含，发行包 SHA-256 已固定，无需全局安装 Gradle。

Windows PowerShell：

```powershell
.\gradlew.bat build
```

Linux／macOS：

```sh
./gradlew build
```

可安装的正式 JAR 输出为 `build/libs/glimmering-abyss-0.1.0.jar`。`sources.jar` 用于查阅源码，不放进 `mods` 文件夹。

## 复现游戏检查

需要 Python 3、JDK 17 和联网下载官方依赖。测试软件和全新存档都放在本项目忽略的 `.runtime/` 中，服务端只监听 `127.0.0.1:25585`。

```sh
python tools/run_experiment.py --accept-eula --output evidence/local-run
```

以上执行正式 JAR 服务端、三种原版光照测量以及第二个全新世界的地形指纹比较。`--accept-eula` 表示接受这个独立测试服务端的 Minecraft EULA，链接为 <https://aka.ms/MinecraftEULA>。

在可用桌面／X 显示环境中增加 `--capture`，会启动真实 Minecraft 开发客户端，拍摄九张图片并检查实际行走碰撞：

```sh
python tools/run_experiment.py --accept-eula --capture --output evidence/local-client-run
```

已成功构建后可加 `--skip-build` 复用同一正式 JAR。单独开发客户端可用 `./gradlew runClient` 启动，再创建测试世界输入上述命令。

已有成功安装的官方 Fabric 服务端引导目录时，可加 `--server-template 路径` 复用依赖下载；脚本仍建立新的测试 MOD 目录、世界与启动缓存，保留模板原目录。此路径也用于本次云端下载暂时失败后的完整重复验证。

云端本次对 Unix 套接字及显示环境有额外限制。可选的 [cloud_loom_compat.py](tools/cloud_loom_compat.py) 只适配明确指定的私有构建缓存，不属于 MOD。具体环境适配、失败记录与测量边界见测试报告；普通桌面不需要照抄云端库路径或代理参数。

## 原始游戏截图

每列为同一个机位、同一画质设置的真实 Minecraft 客户端截图，没有提亮或调色。黑暗本身也是实验结果。点击图片看原尺寸。

| 机位 | 0 级基线 | 5 级微光 | 15 级对照 |
| --- | --- | --- | --- |
| 通道 | ![无光通道](evidence/phase1/screenshots/baseline-tunnel.png) | ![微光通道](evidence/phase1/screenshots/glimmer-tunnel.png) | ![强光通道](evidence/phase1/screenshots/reference-tunnel.png) |
| 观景台 | ![无光观景台](evidence/phase1/screenshots/baseline-platform.png) | ![微光观景台](evidence/phase1/screenshots/glimmer-platform.png) | ![强光观景台](evidence/phase1/screenshots/reference-platform.png) |
| 边缘 | ![无光边缘](evidence/phase1/screenshots/baseline-edge.png) | ![微光边缘](evidence/phase1/screenshots/glimmer-edge.png) | ![强光边缘](evidence/phase1/screenshots/reference-edge.png) |

## 文件与依赖入口

- `src/main/`：地形、维度、光照命令、实际测量与可选客户端检查。
- `src/test/`：种子变化、可达路线、完整护栏、光源表面及洞厅边界测试。
- `artifacts/`：本实验正式 JAR、源码 JAR 和校验信息。
- `evidence/phase1/`：原始截图、机位／帧时间、实际光照 JSON、运行日志与总体结果。
- `evidence/diagnostics/`：保留的失败检查记录。

官方版本资料：[Minecraft 1.20.1](https://www.minecraft.net/en-us/article/minecraft--java-edition-1-20-1)、[Fabric 安装器](https://fabricmc.net/use/installer/)、[Fabric API 发布仓库](https://github.com/FabricMC/fabric-api)、[Fabric Maven](https://maven.fabricmc.net/)。
