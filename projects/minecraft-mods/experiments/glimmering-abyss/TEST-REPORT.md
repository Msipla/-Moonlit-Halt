# 微光深渊 · 第一阶段技术实验记录

日期：2026-10-12。范围：Minecraft Java 1.20.1 的地形生成、原版光照边界、安全观察路线与可重复测试。场景美术仍是技术原型精度。

## 结论

**程序化地下空间和安全路线可行；原版低亮方块光的远景能力有限。** 地形通过实际 Minecraft `ChunkGenerator` 生成，正式 JAR 已在独立 Fabric 服务端安装并运行。实际开发客户端进入同一维度，取得三个机位的三种光照对照，并完成真实玩家行走／护栏碰撞检查。

全新世界的重复生成检查读取实际世界中的 178,080 个采样方块。地形指纹为：

```text
730a8e888ac049180c373fa51a2e3faca44a2bb131f632b5f05f069db96ca241
```

这是两个全新世界的实际块状态结果；光源亮度模式在指纹中归一化，其余采样块状态完整参与计算。它不是对源代码文本求哈希，也不是重复读取同一份旧存档。

## 已实测与证据

| 检查 | 结果／证据 |
| --- | --- |
| JDK 17 + Gradle 8.1.1 构建 | 正式 JAR 与源码 JAR生成通过；[构建日志](evidence/build.log) |
| 4 项 JUnit 地形检查 | 全部通过：种子实际改变岩体、路线与完整护栏、光源暴露表面、洞厅深度与密封边界；[原始 XML](evidence/terrain-tests.xml) |
| 正式 JAR 安装与启动 | Fabric Loader 0.14.25 + Fabric API 0.85.0+1.20.1 的真实独立服务端通过；[服务端日志](evidence/phase1/server-first.log) |
| 三种原版光照 | 0／5／15 级，各测 0–16 格；天空光均为 0，无遮挡直线方块光符合实际衰减；[基线](evidence/phase1/audit-baseline.json)、[微光](evidence/phase1/audit-glimmer.json)、[强光](evidence/phase1/audit-reference.json) |
| 实际世界的路线与护栏 | 三机位有实体地面和两格净空，通道连续，观景台四周护栏完整（通道入口留空）；各审计 JSON 中 `route_and_rail_pass=true` |
| 实际客户端行走碰撞 | 从 Z=90.5 按住前进 8 秒，到 Z≈62.8625 被前方铁栏挡住，脚部 Y=39；[实际结果](evidence/phase1/walk-collision.json) |
| 同机位游戏画面 | 1280×720 的九张真实客户端截图，三位置 × 三模式；[原图和测量](evidence/phase1/screenshots/) |
| 全新世界复现 | 两个新存档及独立启动目录，地形指纹相同、原版光照与路线检查通过；[最终总结](evidence/phase1/summary.json)、[第二世界审计](evidence/phase1/audit-fresh-world.json) |
| 无玩家自动流程 | 服务端独立运行时同样通过，包含新世界重复校验；[独立运行总结](evidence/reproducibility/summary.json) |

截图来自正在运行的 Minecraft 客户端帧缓冲，不是概念图、其他引擎离屏重放或手工搭建地图。原图未提亮、未调色。最终人工查看了通道、观景台与边缘画面；微光与强光的远景差异按原图记录。

## 测试配置

| 项目 | 实际配置 |
| --- | --- |
| 游戏 | Minecraft Java 1.20.1 |
| 加载器／依赖 | Fabric Loader 0.14.25；Fabric API 0.85.0+1.20.1；Glimmering Abyss 0.1.0；完整 API 子模块见启动日志 |
| 测试平台 | Ubuntu 24.04，AMD EPYC 9V74，环境提供 9 个逻辑 CPU，约 10 GiB 内存 |
| JVM | OpenJDK 17 RI，服务端堆上限 1500 MiB、客户端 2 GiB，二者限制为 4 个逻辑处理器 |
| 图形 | Mesa llvmpipe（LLVM 20.1.2），CPU 软件 OpenGL；GLFW 3.4 的 X11 后端，虚拟显示通过 TCP |
| 客户端设置 | Fancy，环境遮蔽开启，1280×720，FOV 75，亮度 0.5，视距 12、模拟距离 6，60 FPS 上限、VSync 关闭 |
| 光影与渲染 MOD | 没有 Sodium、Iris 或第三方光影；Fabric API 的默认 Indigo 参与标准方块渲染 |
| 世界条件 | 独立测试维度；研究种子与测试主世界种子都是 74102002；无天空光、环境亮度 0、固定夜间 |

每个机位设置后等待 8 秒，再记录约 5 秒的真实世界渲染帧间隔。最终九组短采样为 **19.82–22.34 FPS**。原始 FPS、帧时间 P95、最大值及区块调试状态在 [captures.json](evidence/phase1/screenshots/captures.json)，汇总在 [performance.csv](evidence/phase1/performance.csv)。这些数据只代表本次云端 CPU 软件渲染，不能推断玩家独立显卡或手机性能，也不能宣称目标设备稳定 60 FPS。

首次进入维度时观察到约两秒的服务端落后提示；旧运行记录与最终日志分别保存。短时间机位采样不是持续漫游、显存峰值、发热或耗电测试。

## 实际光照结果与视觉边界

| 光源级别 | 同高度无遮挡测量 | 原图观感 |
| --- | --- | --- |
| 0 | 天空光 0，方块光 0 | 洞厅和栏杆仅剩很暗的轮廓与原版最低显示亮度 |
| 5 | 距离 0–4 格：5、4、3、2、1；距离 5 格后为 0 | 很局部，百米外岩柱主体仍暗，光源标记不够醒目 |
| 15 | 距离 0–14 格递减至 1；距离 15 格后为 0 | 岩柱上的局部亮区更清晰，仍没有照亮整座洞厅 |

该测量发生在独立封闭校准通道，沿一条无遮挡空气路径进行，保持光源高度一致；不是对所有洞穴条件的通用亮度预测。[light-profile.csv](evidence/phase1/light-profile.csv) 汇总三种模式的全部 51 个原始采样。

三个光源方块只有自身 `luminance` 不同，均使用原版 sculk 纹理与相同立方体模型。蓝色纹理是表面颜色，原版方块光仍是标量传播，未实现 RGB 彩色照明或更远距离的光传播。

原版视距与黑色距离雾也会限制最远端。12 区块视距覆盖主要岩柱，观景台至洞厅最远墙面约 240 格，不能预设整个洞厅远端都在同一机位完整加载。

## 下一轮只解决的一个瓶颈

**微光条件下远处岩体的可辨识层次。** 当前代码已经能生成与漫游，但 5 级灯光无法担当大空间整体照明，不能把这批暗图标作已经完成的美术目标。

建议下一轮在同一地形、三机位与相同画质下，做低强度环境显示照明的受控对照，同时继续读取方块光／天空光。判断能否让柱体、断崖和远景分层显现；明确这种可见性调整与光照传播数值的区别。暂不扩展洞厅或生态内容。

## 环境排错与修正

- 初选的新版本 Fabric API 带有旧 Loom 不支持的构建元数据。查阅实际发布 JAR 的清单和加载器约束后，将依赖固定为上述相互兼容版本，实际构建／启动验证通过。
- 云端拒绝 Unix 套接字，Loom 1.2.8 的平台检测因此异常；只在本任务私有构建缓存中让该检测返回“不支持”。[可选适配脚本](tools/cloud_loom_compat.py)保留原包、校验官方 SHA-1；[适配记录](evidence/build-host-compat.json)记录包哈希。Minecraft 类和 MOD 渲染／光照算法没有因此修改。
- 虚拟显示与客户端必须在同一执行环境中启动，使用 TCP X11；LWJGL 原生 GLFW 替换为兼容现有 Java 接口的官方 3.3.6 发行库（GLFW 3.4）。这里只改变云端窗口后端，未增加游戏光影。
- 重启复用 Fabric 启动目录出现重映射文件冲突，保留[失败日志](evidence/diagnostics/fabric-repeat-cache-failure.log)。脚本改为第二世界使用新的启动目录，复用官方引导依赖，不复制世界或 `.fabric` 缓存。
- 无玩家时校准区块会卸载，光照查询返回 0。测试模式保持两个校准区块加载，修正后独立服务端流程通过；普通游戏模式没有启用这项测试驻留。
- 当前运行使用开发身份连接隔离的 localhost 测试服务器，外部账号服务连接错误不代表完成了在线账号验证。云端无音频设备，音频未验证。

## 尚未验证／已知范围

- Windows／PCL2 等普通启动器里的最终 JAR 客户端安装、真实玩家电脑 GPU 帧率、长时间漫游、显存峰值、发热与耗电。
- 与 Sodium、Iris、Distant Horizons、其他地形 MOD 或资源包的兼容性。
- 生存模式中的完整互动、保存再载入后的所有行为、多玩家操作竞争。重新载入后先切换一次灯光再审计。
- 自然主世界洞穴注入、无限生成的地下生态、完整艺术品质。这些未在本阶段实现。

## 调研来源

均为官方发布资料或 API；版本选择还核对了 Fabric Maven 中实际 JAR 的依赖与清单。

- Minecraft 1.20.1 官方版本说明：<https://www.minecraft.net/en-us/article/minecraft--java-edition-1-20-1>
- Minecraft 1.20 技术变更／数据包版本 15：<https://feedback.minecraft.net/hc/en-us/articles/16499677456781-Minecraft-Java-Edition-1-20-Trails-Tales>
- Fabric 1.20／1.20.1 与 Loom／Gradle 说明：<https://fabricmc.net/2023/05/25/120.html>
- Yarn 1.20.1 build 10，ChunkGenerator：<https://maven.fabricmc.net/docs/yarn-1.20.1+build.10/net/minecraft/world/gen/chunk/ChunkGenerator.html>
- Yarn ConnectScreen：<https://maven.fabricmc.net/docs/yarn-1.20.1+build.10/net/minecraft/client/gui/screen/ConnectScreen.html>
- Fabric Loader 与 Fabric API 的官方 Maven：<https://maven.fabricmc.net/>
- LWJGL 官方 Maven 原生库：<https://repo.maven.apache.org/maven2/org/lwjgl/lwjgl-glfw/3.3.6/>
