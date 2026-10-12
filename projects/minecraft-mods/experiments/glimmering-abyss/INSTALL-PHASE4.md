# Phase 4 安装、进入与复测

目标：Minecraft Java 1.20.1，Java 17，Fabric Loader 0.14.25，Fabric API 0.85.0+1.20.1。使用独立的测试实例和测试世界，开启作弊；本阶段不提供正式生存入口。

1. 安装匹配版本的 Fabric 实例，将 `artifacts/glimmering-abyss-0.4.0.jar` 与上述 Fabric API 放进实例 `mods`。只保留一个微光深渊版本；安装到服务器时也使用同一 JAR/API，客户端仍需安装。
2. 启动游戏，进入创造模式测试世界，输入 `/abyss enter`。
3. 输入 `/abyssrender blue` 开启第三阶段冷蓝空间光；默认 A 仍保留，不能因默认未启用而判断加载失败。
4. 输入 `/abyss building` 放置原创工坊与连接步道。此命令仅操作固定实验区域，也适用于旧实验存档；重复执行会重新放置模板并重置该区域的手动改动/箱内物品。
5. 输入 `/abyss building view facade` 到达有地板的观察位置。沿步道向建筑走，可穿过开放的入口进入一楼。`/abyss building view warmth/bridge/interior` 分别观察暖窗、连接桥和室内；`scale` 需要创造模式，使用飞行机位。
6. `/abyss view platform/overlook` 返回与第三阶段可比较的机位；`/abyssrender copper` 仅作另色能力测试，`/abyssrender a` 返回原版；`/abyss return` 返回主世界。

## 快速人工检查

- 启动日志显示 `glimmeringabyss 0.4.0`，且没有缺失资源/崩溃。
- 冷蓝远壁与巨大岩柱仍然可辨；工坊有完整坡屋顶、烟囱、塔楼、独立立面和少量暖窗。
- 观景台右侧连接步道可走，栏杆围护，入口与室内有足够净空；保存退出再进入，建筑仍在。
- 按 F2 保存原始 PNG。不要为报告调色；美学是否合格由用户看原图决定。

## 可重复工程测试

构建 `./gradlew --no-daemon build stageTestMods`。有显示器或 Xvfb、可下载官方依赖的 Linux 环境：

```bash
xvfb-run -a python3 -u tools/run_phase4.py --accept-eula --skip-build --output evidence/phase4/local-unique-run
```

测试器接受 Minecraft EULA 后，仅启动 localhost 的隔离离线测试服务器；使用官方 Minecraft 发布客户端与 Fabric，安装实际 remapped JAR。下载资产保存在忽略的 `.runtime`，不提交官方游戏依赖或测试世界。为每次测试选择不同输出路径。

建筑是原创 `data/glimmeringabyss/structures/workshop.nbt`，标准结构模板，可通过数据包替换。`tools/author_workshop.py` 可重新生成模板及清单（保留稳定顺序/压缩时间戳）；不改 Terrain/ChunkGenerator，不在主世界生成。暖窗为原创不透明 12 级发光窗格；窗不能透视，尚非完整室内材质系统。

## 验证边界

实际托管 Linux 正式客户端和正式服务端验证见 [TEST-REPORT-PHASE4](TEST-REPORT-PHASE4.md)。Windows/PCL2、目标 GPU、长时帧率、声音听感和 Sodium/Iris/Distant Horizons 尚未实测。不得把 Linux 启动成功解释为这些平台/模组兼容通过。
