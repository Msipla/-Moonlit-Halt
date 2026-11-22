# VS/Eureka 载具控制（实机通过）

独立于 `live-lab` 的实验环境：VS 2.4.11 + Eureka 1.6.4 + Create 6.0.8.1，
Minecraft 1.20.1 / Fabric Loader 0.17.2。用真实图形客户端的集成服务端跑通
装配 → 乘骑 → 前进 → 转向 → 锚定，并接上模型自主设计。

## 实测结果

| 阶段 | 飞行船（24 格） | 水面船（15 格） | 模型自主设计（17 格） |
| --- | --- | --- | --- |
| 装配 | ship_id=9 | ship_id=12 | ship_id=15 |
| 原生乘骑 | true | true | true |
| 前进 | 4.049 m | 3.506 m | 2.300 m |
| 转向 | 5.272 m | 6.873 m | 5.395 m |
| 锚定漂移（200 tick） | 0.000 m | 0.000 m | 0.000 m |
| 其他 | — | 浮力抬升 1.92 m | 一次通过，无重试 |

证据：`evidence/air.json`、`evidence/water.json`、`evidence/autonomous-design.json`
（逐 10 tick 的位置、速度、四元数）。

## 零位移根因（Work 的 ship-bridge 缺前四步）

1. `ShipAssembler.collectBlocks` 返回 `ServerShip`，对它取 attachment 会抛
   `NotImplementedError: ... unloaded ship`。必须换成 `getShipObjectWorld().getLoadedShips()`
   里同 id 的 `LoadedServerShip`。
2. 装配后方块搬到 shipyard 坐标，舵方块位置不再是原世界坐标（实测实例中落到
   `(-28669952, 126, 12290045)`），要在 `getShipAABB()` 范围内按方块 id 重新找。
3. 传送用 `shipToWorld` 变换后的世界坐标；`BlockHitResult` 的命中向量与 blockPos
   都用 **shipyard 坐标**；传送后必须先 `setSneaking(false)`（潜行会让 `onUse` 去开界面）。
4. 校验 `player.isRiding()`，再 `EurekaShipControl.setSeatedPlayer(player)`；
   没有 seatedPlayer 时 `physTick` 不会走 `getControlData/applyPlayerControl`。

补丁正文见 [work-patch.md](work-patch.md)。

## 模型自主设计闭环

[autoship.py](autoship.py)：一句自然语言 → `deepseek-chat` 输出方块计划 JSON →
校验（白名单 / 边界 / 唯一舵 / 面连通）→ RCON 施工 → 装配 → 乘骑 → 前进/转向/锚定 →
按位移判定 ACCEPTED，失败时把校验错误回喂模型重试（最多 3 次）。

本次运行模型给出 17 格方案（9 平台 + 3 栅栏桅杆 + 4 气球 + 舵 + 锚），一次通过。

## 构建与运行

模组无 Gradle，直接 javac（类路径为 Minecraft 中介名 jar + Fabric Loader + Fabric API 模块 + Gson + DataFixerUpper）：

```bash
javac --release 17 -cp "<intermediary>:<loader>:<fabric-api/*>:<gson>:<dfu>" \
  -d classes src/ShipTest.java
jar --create --file shiptest.jar -C stage .
```

客户端启动参数加 `-Dshiptest.enabled=true`，存档内放置船体后把舵坐标写进
`shiptest.request`（`x y z`），模组依次执行装配、乘骑、前进 100 tick、转向 80 tick、
锚定 200 tick，结果写入 `shiptest-result.json`。

## 已知限制

- 没有视频编码，只有桌面抓帧；画面中相机在船上但船体在视野外，视觉证据弱于数据证据。
- 水面船在 21×21 人工水池内验证，未验证开放水域与波浪。
- 模型设计目前只覆盖 40 格以内的小型载具；大船、多人乘骑、帆/螺旋桨未验证。

## 与 live-lab 的关系

`live-lab/ship-bridge/LabShips.java` 是 Work 的施工侧实现；本目录只提供差异分析与补丁，
不重复施工框架。合入时按 `work-patch.md` 改 `steer()`，装配入口沿用现有 `assemble()`。
