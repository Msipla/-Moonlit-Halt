# V1.0 空间预览与功能验证

更新：2026-10-14。唯一范围与最终验收依据：[TASK-V1.0.md](TASK-V1.0.md)。空间方向已获用户有条件认可；本轮 **1.0.0-preview.2** 局部岩壁优化及聚落扩建完整技术回归已通过，最新岩壁自然度/扩建美学与安卓 Amethyst 尚未验收。

## 本轮地貌与聚落：完整技术回归通过

源码`078a674314bbab69eec4f13bff17a47cf2d30426`，[38500421865](https://github.com/Msipla/-Moonlit-Halt/actions/runs/38500421865)全部成功，证据提交`c085f9c98d16dbdff9dd6a737baa7afa48b63bd4`。发布JAR为1.0.0-preview.2，4,835,680字节，SHA-256 `f855057e1fce107df9eeab8c5959c442f839fe939cca1a8456f6aa2c8134a8bf`；JAR及sources包校验已本地复核，完整来源见[checksums](evidence/v1/runs/38500421865/checksums.json)与[provenance](evidence/v1/runs/38500421865/provenance.json)。

布局101在原588区块内局部处理东/东南壁的倾斜断层、内凹岩棚、附壁岩肩及洞顶过渡，改变局部岩纹方向；主裂谷/西廊/尺度与冷暖光基准保留。新增烘焙屋、织工作坊、两处住宅和三格窄巷/小型公共雨棚，共11栋，单体基础依附天然岩台。原七栋保护框扩大到工坊侧塔及檐口，16,032个实体块、128暖窗逐块保持；town/natural_town/abyss_town三份历史NBT逐字节不变，旧70/75/100不自动重铺。

| 检查 | 本轮实际结果与证据 |
| --- | --- |
| 构建与Java | 19项测试、零失败/错误；发布客户端和Fabric专用服；[build.log](evidence/v1/runs/38500421865/build.log)及同目录XML |
| 两种子 | 74102002、-92345均正常生成布局101并实际启动客户端；生成后各588/588原生地表及基岩保持；[first](evidence/v1/runs/38500421865/phase7-first.json)、[second](evidence/v1/runs/38500421865/phase7-second.json) |
| 模板与照明 | 各48,730处非空气模板块无类型缺漏；原128暖窗及全部184暖窗原生12级，25格水池封闭 |
| 局部安全 | HARD、doMobSpawning=true，景观Mob=0、内部插入拒绝、外部边界插入允许；不把插入测试等同本轮专门自然刷怪统计 |
| 外围与街巷 | 22段外围+28段街巷/四处新建筑/公共空间生存按键行走，全程健康20、未飞行；[walk](evidence/v1/runs/38500421865/walk.json)、[settlement-walk](evidence/v1/runs/38500421865/settlement-walk.json)。街巷实际脚部Y45–48，外围Y23–47 |
| 梦境与记忆 | 两次真实生存睡眠，0→1夜→2醒、2→3夜→4醒；世界刻182/181、START/STOP1/1→2/2，dreams1→2、第二晚微光；[sleep-verification](evidence/v1/runs/38500421865/sleep-verification.json) |
| 新住宅床 | 原生成床而非重放夹具床，第三次生存睡眠；日4→5夜→6醒，世界刻162、事件3/3；dreams维持2、dream_day维持4、微光保留；[fixture](evidence/v1/runs/38500421865/phase7-house-sleep-fixture.json)、[before](evidence/v1/runs/38500421865/phase7-sleep-house-before.json)、[after](evidence/v1/runs/38500421865/phase7-sleep-house-after.json) |
| 缆车往返 | 900下降/899返回采样各轴零反转、乘客水平偏移0，健康20正常落站；[motion](evidence/v1/runs/38500421865/motion.json) |
| 提前下车及停服重连 | 实际潜行提前安全落站、再登车、Y142.104乘车中停服、同身份同档重连健康20离车安全地表站台、unfinished_rides=0；[transport](evidence/v1/runs/38500421865/transport/)、[reconnect](evidence/v1/runs/38500421865/reconnect/) |
| 新档重启 | 34,288个采样指纹完全相同；[reload](evidence/v1/runs/38500421865/phase7-reload.json)。生成后地表初检588/588；长期运行的原生地表样本不宣称永远静止 |
| 三种真实旧档 | 分别用正式0.7.0/0.7.5/preview.1包生成，再原生放金块；金块先改变实际采样指纹，换新包后布局70/75/100、金块及指纹保持；[70](evidence/v1/runs/38500421865/legacy-upgrade-0.7.0.json)、[75](evidence/v1/runs/38500421865/legacy-upgrade-0.7.5.json)、[100](evidence/v1/runs/38500421865/legacy-upgrade-1.0.0-preview.1.json) |

本轮直接运行完整套件，未跳过或复用旧档测试。原图为首种子20张、第二种子1张，另有交通保护原图；[本轮原图目录](evidence/v1/runs/38500421865/screenshots/)。首种子20次捕获GL均为0，摄影机位/原生天空光/区块队列/渲染器见[captures](evidence/v1/runs/38500421865/screenshots/captures.json)。已实际查看全景、东侧街巷、公共雨棚、烘焙屋/住宅/织坊室内、折向廊及第二种子原图。冷蓝、暖光、巨大落差和原建筑群保持，右侧局部岩肩/内凹与洞顶衔接已变化；不据此宣布最终美学通过。

重点审阅：[全景/岩壁](evidence/v1/runs/38500421865/screenshots/town-in-abyss.png)、[东侧街巷](evidence/v1/runs/38500421865/screenshots/east-wall-and-lane.png)、[公共空间](evidence/v1/runs/38500421865/screenshots/sheltered-public-nook.png)、[住宅室内](evidence/v1/runs/38500421865/screenshots/settlement-interior-13.png)。部分全景使用创造飞行摄影，可达性由独立50段生存按键记录证明。residence-after-sleep.png在状态翻转同刻取得，仍显示上一帧原版离床界面；真实醒来以事件/时间数据验证，该图不作醒后UI完整渲染证据。

初版38495935223（源码cedc699、证据39beb03）在最后布局100旧档金块见证采样失败：x45不在该布局x=-204+9i网格，虽放置成功且旧审计看见金块，指纹未变化。修为100及以上x48、70/75仍45，坚持先改变指纹再保护，未放宽断言。初版还遗漏工坊侧塔底部15块保护，已移巷/灯并扩大保护框；灯具补支撑/吊链/贴顶，实看原图后加强东南壁。失败日志、原图与初版包留在原目录，初版不作交付候选。

当前审图状态：用户此前仅对整体空间方向有条件认可；本轮岩壁自然度和聚落扩建仍待用户看本轮真实原图确认。安卓Amethyst、Windows/PCL2、目标GPU、Sodium/Iris/DH、光影及长时多人未测；正式1.0.0验收未完成。

以下为前轮已完成的真实技术结论。
## 已通过的空间预览

[运行38442034346](https://github.com/Msipla/-Moonlit-Halt/actions/runs/38442034346)全部成功，源码 `695004f906c4a5294db25977118ac332bfa59459`，证据提交 `e4e49fe36c1601a206f2e38d95a3b4d4b27d0cc8`。使用官方 Minecraft Java 1.20.1 发布客户端、Fabric Loader 0.14.25、Fabric API 0.85.0+1.20.1、Java 17 和独立专用服务端。

| 检查 | 实际结果与证据 |
| --- | --- |
| 发布构建与 Java 测试 | 构建成功，18项测试通过；[build.log](evidence/v1/runs/38442034346/build.log)及同目录XML |
| 两种子与原生地表 | `74102002`、`-92345`均完成588区块；各588/588地表与基岩保持；[首次审计](evidence/v1/runs/38442034346/phase7-first.json)、[第二种子审计](evidence/v1/runs/38442034346/phase7-second.json) |
| 建筑与路线 | 38,579处模板非空气块无类型缺漏；128暖窗保留且原生12级光；25格池水封闭 |
| 宁静范围 | HARD、doMobSpawning=true，景观生物0、内部插入拒绝、外部边界插入允许。此轮未专门重测外部自然刷怪，历史实跑不能改写为本轮结论 |
| 外围环线 | 生存模式按键行走22段，均健康20、未飞行；真实从镇内出发并返回；[walk.json](evidence/v1/runs/38442034346/walk.json) |
| 缆车 | 实际右键上下站铃、完整下降和上升；安全落站；[motion.json](evidence/v1/runs/38442034346/motion.json)。901个下降采样、898个返回采样各轴无反转，乘客水平偏移0；安卓抖动/乘坐舒适度待实测 |
| 梦境 | 两次真实睡眠，截图时仍在床上，授予寻梦微光；[dream-1.json](evidence/v1/runs/38442034346/dream-1.json)、[dream-2.json](evidence/v1/runs/38442034346/dream-2.json)。本轮未记录完整的入睡/醒来事件计数与逐夜状态，后续补测 |
| 新档重启 | 34,288处采样几何指纹一致；[reload](evidence/v1/runs/38442034346/phase7-reload.json) |
| 两种真实旧档升级 | 用正式0.7.0/0.7.5生成世界，再原生放置金块，换新包后布局70/75、金块与几何指纹均保留；[70](evidence/v1/runs/38442034346/legacy-upgrade-0.7.0.json)、[75](evidence/v1/runs/38442034346/legacy-upgrade-0.7.5.json) |

## 已通过功能补测

首个专项[38490264578](https://github.com/Msipla/-Moonlit-Halt/actions/runs/38490264578)构建/18项测试通过，实际两次生存睡眠与提前下车完成，但再次登车失败，未完成停服/重连。逐夜原始状态复核：0→1夜→2醒、2→3夜→4醒；累计世界刻分别增加183/180，START/STOP计数1/1→2/2，dreams1→2，第二晚实际授予微光。原始状态、失败日志/截图与包在该运行目录保留。

测试助手在END_CLIENT_TICK中同刻松潜行键并右键回响铃，角色输入仍是前一刻潜行；`a7800d1d4ee293962f69b78e6c685296f1cc0aca`把再次交互移到后续状态，等待至少一秒并确认角色不再潜行，记录reboard-input.json。[38491285454](https://github.com/Msipla/-Moonlit-Halt/actions/runs/38491285454)完整专项成功，证据提交`776cee2`。地貌、建筑、正式梦境和车辆逻辑未变。

- [睡眠核对](evidence/v1/runs/38491285454/sleep-verification.json)：0→1夜→2醒、2→3夜→4醒，世界刻182/181，实际START/STOP1/1→2/2，dreams1→2；生存模式，第二晚获得微光。
- [提前下车](evidence/v1/runs/38491285454/mechanics/emergency.json)：健康20安全落到出发站；[再次登车输入](evidence/v1/runs/38491285454/mechanics/reboard-input.json)确认非潜行，松键后等待1.109秒。
- [停服前](evidence/v1/runs/38491285454/mechanics/restart-ready.json)：玩家实际仍乘车，Y=142.143；随后真实停止服务端。
- [同档重连](evidence/v1/runs/38491285454/reconnect/recovery.json)：同身份、离车、安全地表站台、健康20；[状态](evidence/v1/runs/38491285454/reconnect/site-after-recovery.json)unfinished_rides=0；588区块审计与34,288处几何指纹仍通过。

开发侧已查看恢复与下车原图，地表背景未被地下局部天空限制改变。恢复帧仍有pC/pU队列，作为位置/健康/离车证据，不作完整渲染或美学通过证据。

本专项验证真实生存睡眠、跨日与记忆增长、寻梦微光授予、潜行提前下车、乘车中真实停服、同身份同存档重连及安全落站。使用现有脚本 `--mechanics-only`，不重复已通过的两种子空间与旧档升级整套测试。成功与失败材料均按运行ID保存。Actions手动 `suite=full` 或本地省略该参数运行完整套件；代码后续改变地貌、车辆或旧档行为时必须重新执行相应完整检查。

## 昼夜、睡眠与梦境测试规范

来源：用户2026-10-14明确补充的自动化测试规范，适用于本项目后续阶段。

- 允许原生命令准备条件，避免等待自然入夜。需要推进完整游戏日时优先 `/time add 24000`，核对操作前后实际日数。
- `/time set night`设定昼夜相位，不能证明过去了新的一天；反复执行不能当作跨日证据。`/time set 32000`是第二个游戏日的白天（相位8000，约14:00），不是夜晚。
- 分别记录累计世界刻 `getTime()`、绝对昼夜时间 `getTimeOfDay()`、`floor(dayTime/24000)`、真实START_SLEEPING/STOP_SLEEPING事件和模组dreams/dreamDays。修改昼夜时间不会代替睡眠事件，也不能当作自然流逝的世界刻。
- 隔离夹具用原生 `time add 24000`后按绝对新日数定位到夜晚13000，断言日数增长；不直接调用梦境授予命令，不改存档记忆字段。
- 最终由生存模式玩家实际右键床铺，观察真实入睡及醒来；分别核对每晚前后日数、世界刻、事件计数、dreams增加到1/2、dreamDays与醒来日一致、第二晚实际获得寻梦微光。画面变黑、alpha增长或命令返回成功都不足以证明流程通过。
- 每次客户端测试清理自己的临时捕获目录，先保存上一轮证据；不能复用上次的result.json、failure.json或截图作为当前结果。

## 前轮原图与有条件审图反馈

第五轮有12张首种子原始PNG和1张第二种子原图；[完整目录](evidence/v1/runs/38442034346/screenshots/)，机位、渲染器、GL状态见[captures.json](evidence/v1/runs/38442034346/screenshots/captures.json)。开发侧已逐项复查关键原图，远端原版亮天空带已消失；GL0/队列0不等于美学通过。

- [聚落与深渊关系](evidence/v1/runs/38442034346/screenshots/town-in-abyss.png)
- [主裂谷](evidence/v1/runs/38442034346/screenshots/main-rift.png)
- [折向侧廊](evidence/v1/runs/38442034346/screenshots/folded-gallery.png)
- [外围望向归途](evidence/v1/runs/38442034346/screenshots/warm-return.png)
- [实际步行返回镇口](evidence/v1/runs/38442034346/screenshots/walk-return-to-town.png)
- [第二种子](evidence/v1/runs/38442034346/screenshots/second-seed-overview.png)

此前部分机位使用创造飞行摄影，可行走性由22段生存记录证明。用户已认可这组原图的整体空间/冷暖光/落差/未知感及折向廊，指定局部改善右侧连续笔直岩壁；这是方向有条件通过，不是岩壁最终美学通过。本轮新增四栋与局部优化见前方新证据。

## 最小扩展接入点

现有接入点是`OverworldSite`持久化的layout版本与包络、`AbyssShape`的连通裂谷/岩廊段，以及按版本选择的建筑模板。后续区域可通过新布局版本和连接段扩展，旧已生成布局继续使用原配方。当前不建立通用区域框架；沉沦海、硫磺海的生成与交通留给后续任务。

## 历史失败与待办

首轮38425341031远端覆土要求导致4096候选全拒；第二轮38427638986睡眠夹具超时；第三轮38430433184首拐角测试块中心错误；第四轮38433900219第22段被镇口封口护墙阻挡。原日志、失败截图和包均保留，详见[devlog](devlog/2026-10-13-v1.md)。本地Gradle网络下载失败见[evidence/v1/local/recovery-20261014/build.log](evidence/v1/local/recovery-20261014/build.log)。

后续：用户验收本轮岩壁自然度与聚落原图；安卓Amethyst地貌、梦境、缆车、性能实机验收；最终正式1.0.0包、完整回归与交付。当前只验证托管Linux软件OpenGL，Windows/PCL2、目标GPU、Sodium/Iris/DH、光影、长时多人未测。


最新安装预览包见[INSTALL-V1.0-PREVIEW.md](INSTALL-V1.0-PREVIEW.md)。前轮preview.1曾本地按ZIP条目比较第五轮与成功专项JAR，差异仅`Phase7Audit.class`、`V1CaptureClient.class`两处隔离测试设施，其他生产类、NBT、纹理与配置逐字节一致；该前轮JAR与sources包校验已复核，纯诊断更新未重复完整空间套件。本轮preview.2改动生产地貌/资源，已重新完成full，当前安装来源以报告顶部为准。
