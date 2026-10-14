# V1.0 preview.4 · 安卓复核安装与旧档兼容

本轮四项反馈修复的工程验证通过，**最终美学和新包安卓 Amethyst 体验待你确认，正式1.0尚未发布**。完整原始结果见 [测试报告](TEST-REPORT-V1.0.md)，草稿PR #6保留。

## 本轮测试包

[glimmering-abyss-1.0.0-preview.4.jar](evidence/v1/runs/38580056771/glimmering-abyss-1.0.0-preview.4.jar)，5,048,148字节；SHA-256：

```text
4894fc6e504d74c831d2ae85c9ad7dfcc7e1435b5682d7024fffc09612f51b1f
```

源码 `6d0fdbc40397c5550e98bd98d69b1ebf0c966692`，[成功运行38580056771](https://github.com/Msipla/-Moonlit-Halt/actions/runs/38580056771)；[校验/源码包](evidence/v1/runs/38580056771/checksums.json)。安装主JAR，sources JAR用于阅读源码。相同文件名的历史失败候选留在各自运行目录，使用这里指定的包与SHA。

Minecraft Java **1.20.1**、Java **17**、Fabric Loader **0.14.25**、Fabric API **0.85.0+1.20.1**。备份存档后，在现有Fabric实例mods中保留一个微光深渊主JAR并替换为本包；客户端和专用服务端使用同一版。

## 新世界与旧世界

| 世界状态 | 换包后的行为 |
| --- | --- |
| 普通新世界 | 生成103景观，包含本轮岩壁、室内、微光石修复和探索指引；本轮实际验证种子74102002、-92345 |
| 已生成Phase7/7.5、preview.1/2/3景观（70/75/100/101/102） | 保持已有地貌、房屋、微光石、路线与玩家方块；本包保留历史配方，旧景观不自动重铺 |
| 既有景观内玩家改动 | 保留；本轮真实preview.3旧包生成102世界、放金块后换包，布局/金块/采样指纹已验证 |

**想看本轮景观改进，请另建普通新世界。** 后台选址、覆土预检及588区块绘制需数分钟，设备不同会有差异；使用正常主世界生成。实体手机性能仍由本次实测记录。

景观就绪后，在两个不同游戏日的夜晚用生存模式实际睡眠；梦境睡眠中淡入、醒后淡出，第二晚取得寻梦微光，右键定位入口。到站后右键回响铃乘缆车，返程同理，潜行用于提前返回出发站。创造模式可用 `/abyss debug status`、`/abyss debug entrance`、`/abyss debug town` 查询/定位已完成的景观；这是已有调试入口，不会重新生成。

## 本轮安卓复核重点

1. 沿主街、桥和观景点查看是否仍有无意义孤立深青块；与报告中的[微光石同机位原图](evidence/v1/runs/38574327630/screenshots/orphan-glimmerstone.png)对照（该对来自上一成功运行，后续仅改路牌方向字）。
2. 从镇侧断崖和主裂谷看洞壁/洞顶，判断纵纹及台阶自然度；冷蓝、局部暖光、巨大落差与安静氛围应保持。参考[全景](evidence/v1/runs/38580056771/screenshots/town-in-abyss.png)、[断崖](evidence/v1/runs/38580056771/screenshots/town-cliff.png)、[裂谷](evidence/v1/runs/38580056771/screenshots/main-rift.png)。
3. 走入住宅/工坊/商店/茶室/图书馆和四栋新建筑，检查基本陈设、门、床边、柜台和上下楼；可直接在住宅床睡眠。[工坊](evidence/v1/runs/38580056771/screenshots/interior-original-workshop-ground.png)、[住宅](evidence/v1/runs/38580056771/screenshots/interior-brick-residence.png)、[图书馆](evidence/v1/runs/38580056771/screenshots/interior-library-refuge.png)。
4. 从镇北循灯下行，经过断层观测台和下层远端，再沿东侧回程坡回到小镇，观察是否有值得停留的景观、地标与清楚归路。[节点](evidence/v1/runs/38580056771/screenshots/fault-observation-bay.png)、[远端](evidence/v1/runs/38580056771/screenshots/lower-gallery-terminus.png)、[返程](evidence/v1/runs/38580056771/screenshots/signed-homeward-junction.png)。
5. 正常乘缆车往返、两个跨日夜晚睡眠、保存退出重进；在镇内和外围各正常停留/行走约五分钟，记录视距、渲染器、常见/最低FPS及卡顿/发热/闪退，保留原图与latest.log。

加速睡眠可先用原生 `/time add 24000` 核对日数，再设到该日绝对夜晚；仍需真实右键床铺。重复set night不证明新一天，32000是第二日白天；时间命令不替代睡眠事件。

实体安卓新包与最终美学结果确认后，再冻结正式1.0、构建正式JAR并交付。全部本轮[未经后期处理的游戏原图](evidence/v1/runs/38580056771/screenshots/)与[修复前原图](evidence/v1/runs/38580056771/before-preview3/)可直接查看。
