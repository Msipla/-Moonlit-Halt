# V1.0 空间预览与功能验证

更新：2026-10-14。唯一范围与最终验收依据：[TASK-V1.0.md](TASK-V1.0.md)。当前版本为 **1.0.0-preview.1**；七栋原型建筑尚未扩充为正式聚落，美学方向与安卓 Amethyst 验收待用户确认。

## 已通过的空间预览

[运行38442034346](https://github.com/Msipla/-Moonlit-Halt/actions/runs/38442034346)全部成功，源码 `695004f906c4a5294db25977118ac332bfa59459`，证据提交 `e4e49fe36c1601a206f2e38d95a3b4d4b27d0cc8`。使用官方 Minecraft Java 1.20.1 发布客户端、Fabric Loader 0.14.25、Fabric API 0.85.0+1.20.1、Java 17 和独立专用服务端。

| 检查 | 实际结果与证据 |
| --- | --- |
| 发布构建与 Java 测试 | 构建成功，18项测试通过；[build.log](evidence/v1/runs/38442034346/build.log)及同目录XML |
| 两种子与原生地表 | `74102002`、`-92345`均完成588区块；各588/588地表与基岩保持；[首次审计](evidence/v1/runs/38442034346/phase7-first.json)、[第二种子审计](evidence/v1/runs/38442034346/phase7-second.json) |
| 建筑与路线 | 38,579处模板非空气块无类型缺漏；128暖窗保留且原生12级光；25格池水封闭 |
| 宁静范围 | HARD、doMobSpawning=true，景观生物0、内部插入拒绝、外部边界插入允许。此轮未专门重测外部自然刷怪，历史实跑不能改写为本轮结论 |
| 外围环线 | 生存模式按键行走22段，均健康20、未飞行；真实从镇内出发并返回；[walk.json](evidence/v1/runs/38442034346/walk.json) |
| 缆车 | 实际右键上下站铃、完整下降和上升；安全落站；[motion.json](evidence/v1/runs/38442034346/motion.json)。安卓抖动/乘坐舒适度待实测 |
| 梦境 | 两次真实睡眠，截图时仍在床上，授予寻梦微光；[dream-1.json](evidence/v1/runs/38442034346/dream-1.json)、[dream-2.json](evidence/v1/runs/38442034346/dream-2.json)。本轮未记录完整的入睡/醒来事件计数与逐夜状态，后续补测 |
| 新档重启 | 34,288处采样几何指纹一致；[reload](evidence/v1/runs/38442034346/phase7-reload.json) |
| 两种真实旧档升级 | 用正式0.7.0/0.7.5生成世界，再原生放置金块，换新包后布局70/75、金块与几何指纹均保留；[70](evidence/v1/runs/38442034346/legacy-upgrade-0.7.0.json)、[75](evidence/v1/runs/38442034346/legacy-upgrade-0.7.5.json) |

## 当前补测

首个专项[38490264578](https://github.com/Msipla/-Moonlit-Halt/actions/runs/38490264578)构建/18项测试通过，实际两次生存睡眠与提前下车完成，但再次登车失败，未完成停服/重连。逐夜原始状态复核：0→1夜→2醒、2→3夜→4醒；累计世界刻分别增加183/180，START/STOP计数1/1→2/2，dreams1→2，第二晚实际授予微光。原始状态、失败日志/截图与包在该运行目录保留。

测试助手在END_CLIENT_TICK中同刻松潜行键并右键回响铃，角色输入仍是前一刻潜行；`a7800d1d4ee293962f69b78e6c685296f1cc0aca`把再次交互移到后续状态，等待至少一秒并确认实际角色不再潜行，记录reboard-input.json。[38491285454](https://github.com/Msipla/-Moonlit-Halt/actions/runs/38491285454)重测中。仅改测试设施，地貌、建筑、正式梦境和车辆逻辑未变。

补测真实生存睡眠、跨日与记忆增长、寻梦微光授予、潜行提前下车、乘车中真实停服、同身份同存档重连及安全落站。使用现有脚本 `--mechanics-only`，不重复已通过的两种子空间与旧档升级整套测试。结果与失败材料仍按运行ID保存。Actions手动 `suite=full` 或本地省略该参数运行完整套件；代码后续改变地貌、车辆或旧档行为时必须重新执行相应完整检查。

## 昼夜、睡眠与梦境测试规范

来源：用户2026-10-14明确补充的自动化测试规范，适用于本项目后续阶段。

- 允许原生命令准备条件，避免等待自然入夜。需要推进完整游戏日时优先 `/time add 24000`，核对操作前后实际日数。
- `/time set night`设定昼夜相位，不能证明过去了新的一天；反复执行不能当作跨日证据。`/time set 32000`是第二个游戏日的白天（相位8000，约14:00），不是夜晚。
- 分别记录累计世界刻 `getTime()`、绝对昼夜时间 `getTimeOfDay()`、`floor(dayTime/24000)`、真实START_SLEEPING/STOP_SLEEPING事件和模组dreams/dreamDays。修改昼夜时间不会代替睡眠事件，也不能当作自然流逝的世界刻。
- 隔离夹具用原生 `time add 24000`后按绝对新日数定位到夜晚13000，断言日数增长；不直接调用梦境授予命令，不改存档记忆字段。
- 最终由生存模式玩家实际右键床铺，观察真实入睡及醒来；分别核对每晚前后日数、世界刻、事件计数、dreams增加到1/2、dreamDays与醒来日一致、第二晚实际获得寻梦微光。画面变黑、alpha增长或命令返回成功都不足以证明流程通过。
- 每次客户端测试清理自己的临时捕获目录，先保存上一轮证据；不能复用上次的result.json、failure.json或截图作为当前结果。

## 原图与美学边界

第五轮有12张首种子原始PNG和1张第二种子原图；[完整目录](evidence/v1/runs/38442034346/screenshots/)，机位、渲染器、GL状态见[captures.json](evidence/v1/runs/38442034346/screenshots/captures.json)。开发侧已逐项复查关键原图，远端原版亮天空带已消失；GL0/队列0不等于美学通过。

- [聚落与深渊关系](evidence/v1/runs/38442034346/screenshots/town-in-abyss.png)
- [主裂谷](evidence/v1/runs/38442034346/screenshots/main-rift.png)
- [折向侧廊](evidence/v1/runs/38442034346/screenshots/folded-gallery.png)
- [外围望向归途](evidence/v1/runs/38442034346/screenshots/warm-return.png)
- [实际步行返回镇口](evidence/v1/runs/38442034346/screenshots/walk-return-to-town.png)
- [第二种子](evidence/v1/runs/38442034346/screenshots/second-seed-overview.png)

部分机位使用创造飞行摄影，实际可行走性另由22段生存按键测试证明。岩壁仍有连续长竖向轮廓，外围步道目前是规则轴向连接，主镇仍为原七栋尺度；不能宣布它们已达到正式天然深渊/可信聚落美学。

## 历史失败与待办

首轮38425341031远端覆土要求导致4096候选全拒；第二轮38427638986睡眠夹具超时；第三轮38430433184首拐角测试块中心错误；第四轮38433900219第22段被镇口封口护墙阻挡。原日志、失败截图和包均保留，详见[devlog](devlog/2026-10-13-v1.md)。本地Gradle网络下载失败见[evidence/v1/local/recovery-20261014/build.log](evidence/v1/local/recovery-20261014/build.log)。

后续：用户审图确认空间方向，合理扩充聚落并验证房屋/街巷/桥梁；安卓Amethyst地貌、梦境、缆车、性能实机验收；最终正式1.0.0包、完整回归与交付。当前只验证托管Linux软件OpenGL，Windows/PCL2、目标GPU、Sodium/Iris/DH、光影、长时多人未测。
