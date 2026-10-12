# 《微光深渊》v0.6.0 安装与小镇探索

Minecraft Java **1.20.1**，Java **17**，Fabric Loader **0.14.25**，Fabric API **0.85.0+1.20.1**。把 [glimmering-abyss-0.6.0.jar](artifacts/glimmering-abyss-0.6.0.jar) 与 Fabric API 放入此游戏实例的 `mods/` 文件夹，仅保留一个版本的微光深渊。不要把 sources JAR 当作 MOD 安装。文件校验见 [checksums-v0.6.json](artifacts/checksums-v0.6.json)。

## 在测试世界进入

使用开启作弊的新测试存档。本阶段依然是研发维度，不会在主世界自然生成小镇。整个实验维度，包括周边洞穴和深渊，都排除普通怪物和其他生物；原版攻击型幻翼也排除，未来中立景观幻翼另行开发。保护只作用于景观范围，不改变主世界的正常刷怪规则。

```text
/abyss enter
/abyssrender blue
/abyss town
/abyss town view street
```

`/abyss town` 会明确替换局部地形及模板中覆盖的位置，包括容器状态；不要在其中保留重要的个人建造。首次放置需要等待结构与区块载入，不要连续重复输入。

从主街向北行走，可经过小幅台阶到达北侧小塔。进门后，内侧西墙上的原版木梯通往观景层，登上后向右离开梯口。工坊西侧短巷通往原铁桥与对岸平台，可正常往返。新建筑为简洁的可进入原型；钟面是静态装饰，没有 NPC 或钟表逻辑。

## 光照实验与机位

```text
/abyssrender blue
/abyssrender blue95
/abyssrender blue95-fog
```

`blue` 保留原环境参数；`blue95` 将环境光 base/radiance 乘以0.95，保持暖窗与灯具的原生亮度；`blue95-fog` 再增加仅作用于较低地形表面的有限渐变。实验不是最终定稿。配置位于实例 `config/glimmeringabyss-visuals.json`，`/abyssrender reload` 可重新读取；旧配置首次读取时仅补充缺少的两个新预设，已有自定义项保留。

`/abyss town view` 可选 `street`、`plaza`、`high`、`cliff`、`bridge`、`scale`、`facade`、`interior`。这些是落地机位；`overview` 是需要创造模式的远景。机位传送用于查看构图，不代替实际行走。`/abyss return` 返回主世界。

旧 `/abyss building`、`/abyss route`、`/abyss view`、`/abyssrender a/copper` 保留。新小镇模板较大，旧放置命令不会清除模板外的第六阶段改动；严格历史比较请用新的测试世界或归档原图。

## 源码复现

```bash
python3 tools/author_town.py
./gradlew --no-daemon build stageTestMods
xvfb-run -a python3 -u tools/run_phase6.py --accept-eula --skip-build --output evidence/phase6/new-unique-run
```

生成脚本会先复现原工坊与原路线，再输出原创小镇 NBT 和清单。使用新的证据目录，不覆盖历史。实测工具使用正式 remapped JAR、官方 Minecraft 客户端资产和独立的本机 Fabric 服务端，不需要用户账号或启动器凭据。

Linux 发布版客户端/服务端实测范围见 [TEST-REPORT-PHASE6.md](TEST-REPORT-PHASE6.md)。Windows/PCL2、目标 GPU、第三方渲染、长时性能与声音听感尚未实测；普通客户端安装和当前原生方块资源不等于这些环境已通过。暖窗仍是不透明发光立面，空间光不计算完整几何遮挡。

[第六阶段真实原图与同机位对照](https://msipla.github.io/-Moonlit-Halt/phase6.html)
