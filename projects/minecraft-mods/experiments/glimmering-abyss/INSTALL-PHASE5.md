# Phase 5 · 安装与短路线

Minecraft Java **1.20.1** / **Java 17** / **Fabric Loader 0.14.25** / **Fabric API 0.85.0+1.20.1**。

下载 `artifacts/glimmering-abyss-0.5.0.jar`，与 Fabric API 放进同一游戏实例的 `mods` 文件夹；移走该实例中的本 MOD 旧版本，其他实例不需要修改。不要同时加载多个版本。正式服务端使用同样的版本和依赖，冷蓝显示命令在每个客户端选择。

使用开启作弊的独立测试存档，或在多人测试服取得管理员权限。无需改变系统 Java 或注册表。

1. `/abyss enter` 进入实验维度。
2. `/abyssrender blue` 选择已认可的冷蓝光照。
3. `/abyss route` 放置第五阶段的工坊、局部竖井、铁桥与小观景台。
4. `/abyss route view interior` 从工坊一楼开始。走出正面入口，在平台左转，从西侧铁桥走向远端小观景台，再回望暖窗工坊。
5. `/abyss return` 返回主世界。

可直接查看 `/abyss route view facade`、`core`、`down`、`departure`、`arrival`、`warmth`、`interior`。`span` 是创造模式空中远景机位。`/abyssrender a` 返回原版控制，`/abyssrender copper` 保留铜金能力；它们没有成为当前地下主色。

## 放置与存档

竖井为有限局部空间，桥面 y=39，底面 y=-63；没有扩大世界高度或新增虚空伤害。铁桥面由关闭的上半部铁活板门构成，边缘为双层铁栏杆。

正常保存退出、重进后，建筑与路线仍在，不需要重新放置。`/abyss route` 是研发模板重置操作，会替换模板明确覆盖的位置，包括玩家改动和容器内容；在独立测试存档使用。修改过的旧测试世界先备份。没有自动改造主世界。

`/abyss building` 仍可单独放置第四阶段原工坊，但它与第五阶段工坊覆盖同一位置；需要恢复完整第五阶段时再用 `/abyss route`。第三阶段地形、光照与旧观景入口保留。

## 源码与复现

```sh
python3 tools/author_route.py
./gradlew --no-daemon build stageTestMods
xvfb-run -a python3 -u tools/run_phase5.py --accept-eula --skip-build --output evidence/phase5/local-new-run
```

选择新的证据目录，不覆盖历史。第二条构建输出在 `build/libs`；交付目录 `artifacts` 中的包来自实际实跑。生成脚本会可重复产出原创标准 NBT，没有第三方结构资产或新增运行库。

完整事实以 [第五阶段测试报告](TEST-REPORT-PHASE5.md) 为准。托管 Linux 正式客户端/服务端验证不等于 Windows/PCL2、你的 GPU、长时间游玩或 Sodium/Iris/Distant Horizons 兼容测试。
