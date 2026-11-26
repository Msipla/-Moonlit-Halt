# 移动基地集成与蓝图教学 · 0.2.6-dev 接续记录

基线为 [任务书 92c4d18](https://github.com/Msipla/-Moonlit-Halt/blob/92c4d1869092b90294efd4878b0fe3fda20c0a16/research/technical/TASK-NUMEN-CREATE-GENERAL-ENGINEERING.md)、0.2.6-dev 源码 `3c6ef01` 和 [实测证据](decoupled-execution-0.2.6-dev.json)。沿用 1.20.1 Fabric、Create、Numen、VS2/Eureka 组合与现有工程权限。此次保留原安装包，产品 Java 源码和依赖均未改。Work 没有可用的执行环境，无法启动专服或图形客户端，也未能将社区文件下载到可解析的环境。下文将网页拆解、脚本测试、旧实机与新实机待验分别记账。

## 教材筛选与拆解

MC Planner 表中尺寸按发布页，尺寸、功能房间和公开预览来自作者页面。该站列出 Litematic、Sponge Schem 和 Structure NBT，1.20.1 下载需在页面选择对应构建。其免费条款允许依规定用于私人非商业构建和署名展示，限制转发文件。其他来源以各作者条款为准。全部只记录位置和设计逻辑，未复制或重新分发原文件。

| 用途 | 教材、格式与适配状态 | 可以教学的布局 |
| --- | --- | --- |
| 飞行功能 | Gorm Auslander 的 VS 原生仓库资产 The Meadowlark。页列 Minecraft 1.20.1、VS 2.4.13、Create 6.0.8.1、Clockwork、Eureka 1.6.3，作者还注明停靠用了 VS Addition。下载包和授权细节未核验，留作功能布局参考。 | 将体量最大、最轻的气囊置于上部，把货舱吊取路径引到开阔一侧。驾驶位把动力、锚和靠泊控制分开标识，吊具不占舱门或驾驶通道。 [原页](https://valkyrienskies.org/schematics/653b1de4-6d66-4eaf-8382-66310aabc740) |
| 飞行建筑 | MC Planner 的 Airship，44×36×21，2,136 格。三种格式由站点提供，静态建筑。 | 先画大气囊和窄船身的双层侧型，再在下部安排连续的可走甲板。缩小时优先保留大气囊对小船体的比例。 [原页](https://www.mc-planner.com/schematics/airship) |
| 水面建筑 | MC Planner 的 Sailing yacht，23×24×7，626 格，发布页标注舱室。三种格式由站点提供，静态建筑。 | 取一条纵向甲板作为主交通，靠舷保留转身和登船位。把桅杆这类占高构件与舱口错开。 [原页](https://www.mc-planner.com/schematics/sailing-yacht) |
| 水面建筑 | CoJeMC 的 Abri Boat，PMC 提供 Schematic；专用依赖、精确版本及授权未核验。 | 限宽小艇的外形参考。从公开图反推船首与短舱在侧视上的前后比例，实际舱面与航行交由独立样本验证。 [原页](https://www.planetminecraft.com/project/abri-boat/) |
| 房车建筑 | MC Planner 的 Camper van，16×8×19，572 格。三种格式由站点提供，无实车底盘证据。 | 用侧门标出对外入口，把床、桌面、厨房贴边排，保留中间通行。外侧材料带帮助看出驾驶前端和居住段。 [原页](https://www.mc-planner.com/schematics/camper-van) |
| 房车建筑 | SkyblockSquad 的 Detailed CAMPING Van，作者用 1.19 方块制作，说明可用 WorldEdit/FAWE 放置 schematic，允许署名用于自己的建筑。 | 完整内饰的对照样本。让驾驶段、乘坐段与后部居住段各有可辨认的窗和开口，测量时保留底盘与转向净空。 [原页](https://www.planetminecraft.com/project/camping-van-l-1-18-download/) |
| 潜艇建筑 | MC Planner 的 Research submarine，37×14×19，1,275 格。三种格式由站点提供，网站说明有床、厨房和控制室。 | 用指挥塔集中舱盖和梯口，圆筒主体在舱道两侧做住舱与操作舱。以舷窗、清晰水线、尾部舵面区分顶底与前后。 [原页](https://www.mc-planner.com/schematics/research-submarine) |
| 潜艇外形 | MC Planner 的 Submarine，47×17×12，1,462 格。三种格式由站点提供，页面无内饰标注。 | 长低艇型与高指挥塔对照。用水平尾舵和艇首梭形传达行进轴；住舱、防水和压载用其他实测参考。 [原页](https://www.mc-planner.com/schematics/submarine) |

教学收敛为轮廓和朝向、舱口与连续通道、两侧功能区、载具控制/加工区四层。仅在移动基地委托从现有 Skill 加载 [专项参考](../../mc20/config/numen/server/skills/createlab/create_kinetics/references/mobile-base.md)。回放解析前先选对应游戏版本，用既有 NBT/Litematica 解析器只读取方块、状态、层高和出入口。隔离小段导入时核查方块实体、容器、未知数据和版本；需附属控制件的 VS 原生蓝图先核对作者依赖再尝试。新实验尚无模型调用。

## 原有实机与当前限制

| 检查 | 证据和结论 |
| --- | --- |
| 0.2.6-dev 自主飞行基地 | 原阶段 50 格施工、实机推进 2.98 格、偏航 14.3°并锚停。真实客户端在锚位下降约 0.75 格，跳跃复跑仍未进舱。此次先把锚移出通道并用连续全格木板。 [原证据](decoupled-execution-0.2.6-dev.json) |
| DSH 飞行、水面、房车、潜艇 | 船舶分支的不同附属在飞行 `0.3.2-dev` 实机验证了巡航离席、舱内横移、打开桶并再乘舵；水面 `0.2.0` 记录了约 1.7 格/秒时的储物操作。房车和潜艇的技术迁移以该分支各自版本和实测边界为准。 [飞行](https://github.com/Msipla/-Moonlit-Halt/blob/d31f9fb70c9734059923ce3433da4a0b0f657721/projects/minecraft-mods/experiments/mobile-base/evidence/air-0.3.1-dev.json) · [水面](https://github.com/Msipla/-Moonlit-Halt/blob/d31f9fb70c9734059923ce3433da4a0b0f657721/projects/minecraft-mods/experiments/mobile-base/evidence/gold-boat-0.2.0.json) · [移动基地分支](https://github.com/Msipla/-Moonlit-Halt/tree/d31f9fb70c9734059923ce3433da4a0b0f657721/projects/minecraft-mods/experiments/mobile-base) |
| Numen 工程驾驶员离席 | `EngineeringShips` 每 tick 检查驾驶员是否仍乘坐，离席会调用锚停，超时、房主掉线或船体越界也会停止。先在现有驱动里验证第二名玩家行走；驾驶员离席持续行驶单列，使用可核实的既有巡航控制器和隔离组合验证。 [装配和驾驶源码](../ship-bridge/EngineeringShips.java) |
| Create 设备与船体 | 装配会按 `EngineeringSite.HULL` 筛选，Create 加工件不在当前集合中。每个船体方块要求同一助手归属，装配有独立 64 格上限。施工批量 64 格，以及站点每轴最大 16 格，都各自检查。先隔离验证兼容，依据实际可工作的方块状态及数据再决定最小接口变更。 [站点源码](../game-bridge/EngineeringSite.java) · [VS 与 Create 官方兼容说明](https://valkyrienskies.org/wiki/Compatibility_list) |

## 本次通过的脚本检查

`LAB_ENGINEERING_CABIN=1` 只影响既有 `ship` 冒烟组。飞行夹具 47 格（8 气球）、水面夹具 48 格（9 浮块），均有甲板→门口→舱内四格、两格高净空、侧置舵和独立锚位。两个方案都在授权夹具边界内，均可由舵经邻接方块遍历全部船体格，符合 0.2.6-dev 的装配数量和白名单。纯 JS 负例为每类船检出锚占地板、缺地板和门楣挡头各一次。脚本还用替身服务器回执走完飞行、水面各自的原始和舱室分支，确认舱室分支在施工前后发出 `site.plan`，保留组装、推进、转向及锚停取样。以上只有脚本与几何证据，`actualPassengerWalk` 留空。

## 恢复实机后的执行顺序

1. 在研究目录准备 0.2.6-dev 的原始 JAR、原 1.20.1 隔离 Fabric 实例、`LAB_MC20`、`LAB_GAME_BRIDGE`、`LAB_VS_MODS` 和 Java。执行下方飞行及水面组。固定夹具先收集计划、放置和推进回执。随后省略 `LAB_SHIP_PROTOCOL_OWNER` 并由真实图形客户端以 LabOwner 加入。按 [复现说明](../README.md) 的端口和隔离目录配置存日志。

   ```sh
   LAB_ENGINEERING_SMOKE=ship LAB_ENGINEERING_CABIN=1 LAB_ENGINEERING_VESSEL=air LAB_SHIP_PROTOCOL_OWNER=1 node live-lab/run.cjs engineering
   LAB_ENGINEERING_SMOKE=ship LAB_ENGINEERING_CABIN=1 LAB_ENGINEERING_VESSEL=water LAB_SHIP_PROTOCOL_OWNER=1 node live-lab/run.cjs engineering
   ```

2. 保存刚装好、锚停、航行、再锚停的世界和位姿。真实客户端用生存权限、普通按键从甲板跨过舱门走到舱尾，记实际头脚位置和碰撞。在船体有确定平移区间时，由独立玩家走同一路，记录世界及船体局部坐标和视频。驾驶员离席持续巡航、制动与再次驾驶单独取证。若现有工程脉冲只够第二名乘员通过，保留结果和安全停船，下一轮从 DSH 现成巡航路径做兼容验证。
3. 首个设备用马达、传动和磨石，空库存起步，使用同一种已验配方。分别在装配前、装配后锚停、实际位移期间、停泊后读取设备实体、转速、应力和投入/产出库存。每个要声明通过的状态独立首投和复投，取料后再记录新增产物。原组装 API 拒绝加工件时，可用已有 VS 原生装配在新世界做兼容实验，记录为独立方案。原地运行、停泊运行和运动中产出分别验收。通过后才从兼容的最少加工件着手，回归归属、库存、NBT、取消、无 VS、原生陪伴与旧载具。
4. 两道保留题。空中为山脊观测救援站，主通道连接驾驶位、医用储物与两人休息区，登舱需与教材例船方向不同。水面为河道补给站，驾驶位与后部独立休息间通过有遮挡的舱道连接，安排可从通道操作的货位。模型自选址、朝向和结构。将隔离实例的 `create_kinetics` 目录在旧版和本次待验教学版间互换并重启，同一提示、`deepseek-v4-pro`、推理、预算、世界和权限条件各跑一轮。确认日志中的 `skill` 命中与参考读取，教师只投料及实际验收。记录可辨识的侧型、外观、出入口、内饰、相对坐标、库存、总/缓存 Token、调用次数、耗时、返工与日志。无真实证据的格子继续留空。

本次无新编译、真实画面、专服运行、模型考试或玩家验收；续测优先飞行及水面，实际运行以前不扩大装配白名单或引入新的载具依赖。 [原 0.2.6-dev 安装包](https://chatgpt.com/library?file_id=libfile_67a3ea48baa4819188f8d634cd6f9a0f&account_id=personal)
