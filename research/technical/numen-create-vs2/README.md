# Numen × Create、Create Aeronautics、VS2 技术调查

2026-11-20 · Minecraft Java 隔离专用服 · Numen 0.1.5-beta

[源码与适配接口索引](SOURCE-MAP.md) · [复现命令、原始证据位置与 DeepSeek 实测方案](REPRODUCE.md)

## 结论

允许 Numen 接管静态外形搭建，继续让玩家用现有移动基地装配器做标记、预览和确认，是本项目最短的可用路线。要进一步让 Numen 接管机械工程，先在 1.20.1 Fabric 给它一个 Create Skill 和一个薄的运行状态插件。真正运行的粉碎机、机械轴承，以及能由插件触发装配的两种物理后端，已分别验证。复杂变形、持续驾驶和自然语言自主施工仍需针对性验收。

已读取 Conversation-Archives 和 Moonlit Halt 的 `AGENTS.md`、Numen 的 `AGENTS.md` 和 `CONTRIBUTING.md`、`ai-engineer/TASK.md`、`mobile-base/TASK.md` 主分支及载具开发分支版本。本次源码以实际发布包匹配的 1.20.1 标签提交 `2f47e7b9c111a1a116360638f97bb853b7571b18`、1.21.1 标签提交 `06422d1847e0782f91a1bacc5997e74a0249b6c0` 为准，另对照默认分支 `ce047ffb08541798ae95757877233e1a4039ff8a`。两条验证链均使用未修改的上游发布 JAR。测试插件、脚本和世界独立运行。原验证轮使用 GitHub 只读。归档轮将报告、原型和证据放入独立研究目录。  
[协作约定](https://github.com/Msipla/Conversation-Archives/blob/main/AGENTS.md) · [AI 工程助手任务书](https://github.com/Msipla/-Moonlit-Halt/blob/main/projects/minecraft-mods/experiments/ai-engineer/TASK.md) · [移动基地开发分支任务书与既有载具证据](https://github.com/Msipla/-Moonlit-Halt/blob/codex/mobile-base-vehicles/projects/minecraft-mods/experiments/mobile-base/TASK.md) · [1.20.1 发布](https://github.com/Dwinovo/minecraft-numen/releases/tag/v0.1.5-1.20.1-beta) · [1.21.1 发布](https://github.com/Dwinovo/minecraft-numen/releases/tag/v0.1.5-1.21.1-beta)

## 原版能力是怎么实现的

| 层 | 源码实现与实际含义 |
| --- | --- |
| 身体 | `NumenPlayer extends ServerPlayer`。经 `CompanionFactory` 走原版玩家入服、区块管理、存盘和重生，配有 `FakeConnection`、`FakeClient`、视角、鼠标、热栏和动作控制。单人实验可视为有身体的代理，专用服源码由 `CompanionBrains.ensure` 创建 `LlmMind`，从服务器上的玩家模型档案取配置。放入真实玩家列表能触发不少 Mod 的通用玩家钩子，专用客户端 UI、网络包和输入状态要逐项适配。 |
| 感知 | `ScanApi`、`StatusApi` 读服务端实际状态。局部地图、方块簇、实体、射线/距离、BlockState、库存以及可通过加载器传输接口读取的物品、流体、能量供模型调用。模型收到的是文本/结构化盘点，默认无需逐帧看图。状态视图和附近实体查询带有具体范围、加载与遮挡语义，任意一个 BlockState 查询也不等价于运动体上的世界坐标拾取。 |
| 机制知识 | 系统提示由 `SystemPromptComposer` 拼接人设、API/模块索引、可用 Skill、反射与记忆规则。Skill 是有 `name`、`description` 的 `SKILL.md`，模型可用 `skill` 拉正文与附件。内置内容包括合成冶炼、建筑、村民、农业、矿物、战斗和下界/末地路线。配方从游戏注册表读，源码可以看到模组配方类型，默认信息仍缺机器归属、配方概率、时间、热量、能量和流体详情。 |
| 执行 | `lua`、`skill`、`todo`、`memory` 是基础工具。Lua 5.2 沙箱每次跑一段代码，有指令与时间预算，MCP 使用同一工具层。Lua 能调用 `numen.*` 原子操作、组合模块和插件命名空间。Java 处理主线程世界操作和逐刻任务。行走、建造、采掘等长操作在任务层调度，路径算法可跳跃、攀行、游泳、按权限破障与搭方块。建造走玩家点击，无法用手达到指定状态时也可直接写入允许的格子，两种动作在回执里分开计数。 |
| 反馈与纠错 | `CompanionLoop` 组装动态身体状态和事件，`AgentLoop` 推进模型→工具→结果；`ServerPrograms` 执行脚本，世界变化、动作结果、错误类别和后续提示回到模型。Lua 能针对预期错误分支，模型也能重查、纠正计划或停止；任务内另有寻路重规划、回退和躲避/呼吸等反射。模型接口失败与世界状态错误是不同的处理路径。车轮 RPM、模组按钮结果等只有能够观测时才会出现在回执中。 |

Numen 的原版表现来自大模型已有知识、按需读取的规则和真实游戏反馈，以及已写好的动作与恢复逻辑。它能读到怎样开始、做成没有、哪里失败。源码里的 GameTest 可以验证动作机制，评测里的固定参考脚本、空跑与真实模型亦分开。本次执行的是同一世界工具入口 `/numen drive`，查验了实际游戏结果。完整自然语言任务完成率、生存材料链、长期离线自治稳定性没有由这些测试给出。  
[NumenPlayer](https://github.com/Dwinovo/minecraft-numen/blob/2f47e7b9c111a1a116360638f97bb853b7571b18/numen-api/common/src/main/java/com/dwinovo/numen/api/entity/NumenPlayer.java) · [CompanionFactory](https://github.com/Dwinovo/minecraft-numen/blob/2f47e7b9c111a1a116360638f97bb853b7571b18/numen-api/common/src/main/java/com/dwinovo/numen/api/entity/CompanionFactory.java) · [CompanionLoop](https://github.com/Dwinovo/minecraft-numen/blob/2f47e7b9c111a1a116360638f97bb853b7571b18/numen-api/common/src/main/java/com/dwinovo/numen/api/brain/CompanionLoop.java) · [Lua 与回执](https://github.com/Dwinovo/minecraft-numen/blob/2f47e7b9c111a1a116360638f97bb853b7571b18/docs/shell.md) · [试验评测结构](https://github.com/Dwinovo/minecraft-numen/blob/06422d1847e0782f91a1bacc5997e74a0249b6c0/docs/bench.md)

## Mod 如何接入

1. **原生按注册表操作。** 建造格接受带命名空间的方块 ID 和属性，例如 `create:shaft[axis=x]`。模组块可以从原版式扫描、物品或配方接口读到。点击、物品栏、可见容器依赖模组真正使用的服务端行为。
2. **Skill 提供机制说明，Lua 组合动作。** 透过 `config/numen/server/skills/<namespace>/<name>/SKILL.md` 或随插件分发。内容宜写明确的结构接口、方向、投料口、读数和修法，可配套经过验证的 Lua/局部图纸。Skill 不会注册新的 Java 游戏状态读取函数。隔离服中试验 Skill 已被实际注册表发现，能列入系统提示索引并读到正文。
3. **Java 插件补缺口。** 用 `NumenPlugins.register(namespace, api -> api.api(...))`，以 `@Fn` 和参数记录生成 Lua 帮助。API JAR 用对应 Minecraft 和加载器的 `:api` 分类作编译依赖，运行由 Numen 自己提供。1.21.1 默认分支还包含 KubeJS 接入，实查 1.20.1 Fabric 源树没有相同插件模块。MCP 可把同批工具交给外部大脑，MCP 连接本身不增加对 Create 或物理后端的感知。
4. **原生联动的清单。** 实查分支及发布包，有条件集成包括 JEI、Litematica，以及适用版本和加载器下的 FTB Quests、车万女仆、Yes Steve Model、Curios、Kaleidoscope 等。上游树和这两个发布包中均未找到专用于 Create 动力网络、Aeronautics/Sable 或 VS2 的现成 Numen 插件。JEI 可补配方详情；Litematica 可帮助静态投影；动力网络、物理状态和专用操控仍要接目标 Mod 的入口。  
[Numen API](https://github.com/Dwinovo/minecraft-numen/blob/06422d1847e0782f91a1bacc5997e74a0249b6c0/numen-api/README_EN.md) · [1.20.1 插件目录](https://github.com/Dwinovo/minecraft-numen/tree/2f47e7b9c111a1a116360638f97bb853b7571b18/plugins) · [1.21.1 KubeJS](https://github.com/Dwinovo/minecraft-numen/blob/1.21.1/docs/kubejs.md)

## 版本、能力和许可

| 组合 | 核验结果 | 许可证依据 |
| --- | --- | --- |
| Numen 0.1.5-beta + Create Fabric 6.0.8.1 build 1744 | 1.20.1、Fabric Loader 0.17.2、Fabric API 0.92.6、Java 17。在专用服验证了手段与产物。Create JAR 的 Loader 下限是 0.17.2、Fabric API 下限是 0.92.6。 | Numen 代码 LGPL-3.0、素材保留权利，独立使用其 API 的插件可自选许可。Create Fabric 代码 MIT。 |
| 上述组合 + VS2 2.4.11、Eureka 1.6.4、Trackwork Refabricated 1.2.2 | 三者同服启动，Numen 建造 + Eureka 船舵菜单 + VS2 船体运动有独立实测。Trackwork 同服加载，未在本次测试驾驶轮履。旧工程有既往测试依据可复用。VS2 Modrinth 正式 1.20.1 文件到 2.4.11，开发源码显示 2.4.12，版本需固定。 | VS2 源仓和发行页标 LGPL-3.0，测试的 Fabric 2.4.11 内部元数据却标 Apache-2.0。本报告保留差异，插件仅链接接口。Eureka Apache-2.0、Trackwork Refabricated MIT。 |
| Create: Interactive 1.2.1 Fabric | 与同一 Create、VS2、Numen 组合启动。专用服日志仍有 Mixin 写入冲突、`interact_me_not` 配方解析错误。其用途是把一部分 Create 动态结构转为可交互的 VS2 结构。普通 Create 联用 VS2 可以不装它。 | 测试 jar 元数据标 All Rights Reserved。 |
| Clockwork 0.5.6 Fabric，另查 0.5.7.1 | 0.5.6 以内含 Kelvin 0.5.0 与 Create、VS2、Numen 冷启动至可接受指令。0.5.7.1 JAR 要求 VS2 ≥2.4.8、Create ≥6.0.7、Kelvin ≥0.5.2、Architectury ≥9.1.12，Kelvin 需单装。未实测螺旋桨、襟翼或气体系统，也未确认 0.5.7.1 整套行为。 | Clockwork 代码 Apache-2.0，素材保留权利。 |
| Numen 0.1.5-beta + Create 6.0.10 + Create Aeronautics 1.3.3 + Sable NeoForge 2.0.6 | MC 1.21.1、NeoForge 21.1.247、Java 21 已运行。Aeronautics 1.3.3 含 Simulated 和 Offroad，JAR 要求 NeoForge ≥21.1.247、Create ≥6.0.10、Sable 2.x。Sable 2.0.6 对 Create 的适配范围是 [6.0.10, 6.1.0)。Sable 自己还发布 Fabric 包。官方 Aeronautics 的该发布包仅 NeoForge。 | Create 和 Aeronautics 的随包条款均为代码 MIT、对应素材保留权利。Sable 使用带竞品限制的 PolyForm Shield 1.0.0。 |

重点区别是 **Create 原生 contraption、Sable sub-level、VS2 ship 是三套不同的运行对象**。1.20.1 Fabric 上应沿用 VS2/Eureka 或考察 Clockwork。1.21.1 NeoForge 上考察官方 Aeronautics，现有移动基地并无该线路的兼容证据。  
[Create Fabric 版本](https://modrinth.com/mod/create-fabric/version/HAqwA6X1) · [VS2 正式发布](https://modrinth.com/mod/valkyrien-skies/version/qJr3y5vI) · [VS2 与 Create 兼容表](https://valkyrienskies.org/wiki/Compatibility_list) · [Eureka](https://github.com/ValkyrienSkies/Eureka) · [Trackwork Refabricated](https://modrinth.com/mod/trackwork-refabricated/version/Mh7IDZZC) · [Interactive](https://valkyrienskies.org/wiki/Interactive) · [Clockwork Kelvin 发布说明](https://valkyrienskies.org/forum/announcements/clockwork-melting-point-0-5-7-released-mwijvqdw) · [Aeronautics 1.3.3](https://modrinth.com/mod/create-aeronautics/version/demX2roU) · [Aeronautics 许可](https://github.com/Creators-of-Aeronautics/Simulated-Project/blob/main/LICENSE.md) · [Sable 许可](https://github.com/ryanhcode/sable/blob/main/LICENSE.md)

## 水车粉碎机闭环

实测环境为 1.20.1 Fabric，LabBot 运行在创造模式。试验脚本提供几何和诊断条件，管理员只布置站位、给水、向输入漏斗试喂碎石和注入故障。机器所有正常构件由 Numen 自己放，粉碎产物由 Create 真实计算。

| 步骤 | 专用服证据 |
| --- | --- |
| 施工 | `numen.build.place` 放齐 46/46，35 格走玩家操作、11 格直接写入，途中另放 7 块脚手架。后续原生调用把投料口脚手架换成输入漏斗。 |
| 识别运转 | 左右两条独立水车动力链经传动轴把粉碎轮驱动到 +8 与 −8 RPM。所采样网络应力 64 SU、容量 256 SU，不过载。控制器 `valid=true`、`facing=down`，内部处理速度约 0.16。 |
| 首次投料 | 输入漏斗放 1 碎石，下方收集木桶独立 NBT 核验 1 沙。直接扔物品的前序测试曾失败，漏斗复核隔离了投料位置和碰撞变量。 |
| 故障识别 | 先移除第二条传动轴再装一根 Y 轴方向的错误轴。左轮保持 +8、右轮降为 0、控制器失效。Lua 对比仍转动的右侧水车与停转粉碎轮，再从原生扫描读到轴为 Y。 |
| 修复 | Lua 确认三个条件后，用原生 `build.place` 清空已确认的坏轴，再装 X 轴。第二次实际放置 `set_directly=0`。控制器恢复有效，右轮回到 −8 RPM。 |
| 复验 | 再向同一个输入漏斗投 1 碎石，木桶累积 2 沙。原生 `scan.container` 和独立 NBT 两路均读到 2。 |

仅凭两台水车的 RPM 会漏判断轴，仅凭方块类型会漏判错误轴向或动态网络。原生 `scan.block` 能读到粉碎控制器的有效性、朝向以及轴的方块属性，`scan.container` 能读木桶。前序同格对照中，`scan.container` 对控制器报 `not_found`，同时 `scan.block` 能看到已生成的 Create 控制器。两者无法提供轮子的有效 RPM 或网络应力。插件只提供可核验的观察，实际施工与修复均复用 Numen。一次前序测试把错误轴原地从 X 改成 Y 后短期仍读到旧 RPM，明确拆下再装才稳定触发真实网络更新。另一轮 `work.dig` 超过 35 秒的外层观察窗，重启后还占着任务槽，最终采用新隔离存档加创造模式两步修复。  
[Create 水车实现](https://github.com/Fabricators-of-Create/Create/blob/mc1.20.1/fabric/dev/src/main/java/com/simibubi/create/content/kinetics/waterwheel/WaterWheelBlockEntity.java) · [Create 粉碎轮与控制器](https://github.com/Fabricators-of-Create/Create/tree/mc1.20.1/fabric/dev/src/main/java/com/simibubi/create/content/kinetics/crusher) · [Numen 原生扫描](https://github.com/Dwinovo/minecraft-numen/blob/2f47e7b9c111a1a116360638f97bb853b7571b18/numen/common/src/main/java/com/dwinovo/numen/tools/perception/ScanApi.java)

## 已跑通的独立物理测试

- **Create 单轴变形。** Numen 使用原生 `build.place` 搭出创造马达、机械轴承、黏液块和偏置木板（4/4）。专用服同时读到 16 RPM、`Running:1b`、连续角度 62.40° → 120.00°，并发现一个 `create:stationary_contraption`。普通交互回执没有呈现真实的角度变化。此次证明的是一个轴及其附着载荷，未验证双关节折叠、联动锁或载具运动时的变形。
- **VS2/Eureka。** Numen 搭建 9 块木板与 1 个舵（10/10），原生潜行右击打开 `ShipHelmScreenMenu`，返回零物品槽的自定义界面。薄插件检查并调用当前菜单原版组装按钮处理 `clickMenuButton(player, 0)`，按权限执行。同一 VS2 ship ID `3` 的世界 Y 从 −53.31818 到 −59.31819（下降 6.00001 格并停稳）。证明的是实际船体搬运、装配及物理运动，既有工程的前进、转向、离座、储物是该工程自己的既往验证。
- **Create Aeronautics/Sable。** Numen 搭建 8 块 Levitite、1 木板和 1 Physics Assembler（10/10）。原生 `use.block` 按住 2 秒并未生成任何 Sable 子世界。源码显示普通真人走本地客户端按住控制器和网络包，服务端空手交互仅返回消费事件，Create Deployer 有专用分支。独立插件沿原生服务端 `PhysicsAssemblerBlockEntity.assembleOrDisassemble()` 调用，通过 `SubLevelContainer` 和 `RigidBodyHandle` 核验同一子世界的 Y 从 −56.93078 到 −58.91931（下降 1.98853 格后停，初始 vy = −0.54815）。没有验证主动力起飞、舵面控制或着陆控制。  
[Create 轴承与构件源码](https://github.com/Fabricators-of-Create/Create/tree/mc1.20.1/fabric/dev/src/main/java/com/simibubi/create/content/contraptions) · [Eureka 船舵菜单源码](https://github.com/ValkyrienSkies/Eureka/blob/1.20.1/main/common/src/main/kotlin/org/valkyrienskies/eureka/gui/shiphelm/ShipHelmScreenMenu.kt) · [Aeronautics 物理装配器源码](https://github.com/Creators-of-Aeronautics/Simulated-Project/tree/main/simulated/common/src/main/java/dev/simulated_team/simulated/content/blocks/physics_assembler) · [Sable 物理接口](https://github.com/ryanhcode/sable/tree/main/common/src/main/java/dev/ryanhcode/sable/api)

## 推荐开发顺序

1. **先验和玩家配合的版本。** 让 1.20.1 Fabric Numen 根据中文要求搭静态船壳、内饰与机械安装位。把坐标、朝向、预览和冲突回执留给模型，玩家按现有标记与装配流程确认一次。用已有移动基地 Mod 的发行包做回归，无需同步修改车、船、潜艇工程。
2. **Create 功能件用一个 Skill 加一个读状态插件。** 第一版只开 `inspect(pos)`，返回有符号的实际 RPM、理论 RPM、动力源、转速要求、应力/容量、过载、粉碎控制器有效性及输出。有了读数，Skill 可告诉它查水流、传动轴朝向、轮子转向或输入输出堵塞，修复继续用 Numen 的常规动作。固定站位和施工范围，验收时要检查日志里的 `walk_placed_at`、`walk_dug_at`，它们能改变设计周边。做出清晰的输送与通道预位比引入新的 AI 建造引擎更直接。
3. **复杂变形从两步关节开始。** 用已校准的单轴／活塞组件，先把“静态位→动作→目标位→锁止→原路复位”跑十次，记录每个关节的角度或行程、碰撞、装配异常和重载后的状态。第二步做旋转加滑移两轴联锁。上车后再增加船体/子世界 ID、局部坐标→世界坐标、速度和角速度、操纵输入与断联归零。物理回路按刻留在 Mod 侧，Lua 下达动作和核验条件，模型负责段间取舍。
4. **物理后端分开做。** VS2 优先读 `ServerShipWorld`、`ShipTransform` 和载具控制组件，静态阶段照用原生建造。Eureka 走已验菜单适配，Trackwork/Clockwork 分别暴露它们独有的状态。Aeronautics 独立开 1.21.1 NeoForge 实验包，先接装配和运动观测，再用一台原生气囊或螺旋桨测试起飞／推进／停机。不提前做跨版本统一控制层。
5. **最后花模型额度。** 先让 DeepSeek 在新建创造测试世界依据一条中文指令自行找资料、施工、发现预置故障、修复并复投料。保留第一次无 Skill 对照，再测 Skill，最后加状态插件。对比有效产物、错误修改、动作耗时、工具轮数和复跑结果，再决定是否退休旧 AI 工程助手。

## DeepSeek 接入

本次环境中未发现已授权可用的 DeepSeek 配置，脚本由测试者给定，未调用真实模型。Numen 有 DeepSeek 目录项及 OpenAI 风格 Chat Completions 支持。客户端按 G 打开面板，新增模型档案，选 DeepSeek，填 `https://api.deepseek.com`，配置带 Tool Calls 的模型（例如官方当前列出的 `deepseek-flash`），在档案中测试连接并绑定给同伴。在专用服由服务端档案提供模型配置，Key 在自己的配置页面设置。源码对 DeepSeek Chat 的 `reasoning_content` 有回传兼容，本次未用真实凭据验证它在当前 API 上的表现。  
[Numen 配置与兼容源码](https://github.com/Dwinovo/minecraft-numen/blob/2f47e7b9c111a1a116360638f97bb853b7571b18/docs/llm.md) · [DeepSeek Tool Calls](https://api-docs.deepseek.com/guides/tool_calls/) · [DeepSeek Chat Completions](https://api-docs.deepseek.com/api/create-chat-completion/)

## 复验口径和关键日志

发布包 SHA-256 用于复验时确认环境。测试夹具注册的 `createlab.machine.inspect` 为只读。它从 Create `getSpeed()`、`getTheoreticalSpeed()`、`isOverStressed()`、水车 `flowScore` 及动力方块实体的序列化 `Network.{Stress,Capacity}` 取数据。控制器从真实 BlockState 和 `CrushingWheelControllerBlockEntity` 取样。正式插件宜固定目标版本、检查已加载区块，只返回明确字段，并对缺失或脱离运行网络给出独立状态。运行时应力字段和自定义菜单入口存在随版本变化的可能。1.20.1 用 Fabric Loader 加载，插件探针以 Java 17 编译；1.21.1 NeoForge 探针以 Java 21 编译。服务端终端与 NBT 为本轮证据，没有用画面替代游戏状态断言。

| 输入包 | SHA-256 |
| --- | --- |
| Numen 0.1.5 Fabric 1.20.1 | `11be402a4ae071b43678cb3a15972c17c5a804d85050e8123306a51b70713749` |
| Create Fabric 6.0.8.1 | `a613603fb08d967c5a4138d1adb7374c4a347b91e489fe21579a4cff4f40105a` |
| VS2 2.4.11 Fabric | `5854dc163849f010e57a2b01e94c4c67127df07668bfb647735e07bc7e4c45d4` |
| Numen 0.1.5 NeoForge 1.21.1 | `db9dbfea03cb20c3f1444b558bc082224d4f698111a4b19c215ccf6cd7886b94` |
| Create 6.0.10 NeoForge | `ef87fe5709f1ba1f5b8bb20a2925b5afb4669e178fd6d8bf10c167759eefe37a` |
| Aeronautics 1.3.3 / Sable 2.0.6 | `f430c52eedc1a20f7458c9f5b07cc212fe90869bd302c69c6dedf7b75a2a5c9f` / `9c199157843bf953a47dff625fc5bb41c01a90538d35ad18e6996bbcb8a617c2` |

粉碎试验使用平坦世界。左右水车中心为 `(6,-57,0)` 和 `(10,-57,2)`，轴为 `(7,-57,0)` 和 `(9,-57,2)`，轮为 `(8,-57,0)`、`(8,-57,2)`，粉碎控制器自动出现在 `(8,-57,1)`，分别在其上、下各放一台漏斗，木桶在 `(8,-59,1)`。两条竖向导水道位于 `(6,-56,1)`、`(10,-56,1)` 下面，紧邻各自水车的相对两侧，靠围挡控制落水。观察水车、轴、粉碎轮、控制器和下方木桶，投料两次，中间只更改一根轴。

```text
Create · running        RPM  8  8 -8    SU 64 / 256    VALID true  down 0.1600
Create · wrong axis     RPM  8  8  0    SU 64 / 256    VALID false down 0
SHAFT_AXIS y
Create · repair         built 1/1, cleared 1; built 1/1, placed 1, set_directly = 0
Create · after repair   RPM  8  8 -8    SU 64 / 256    VALID true  down 0.1600
Create · output         slot 0: minecraft:sand x2
Create · bearing        Running:1b, Speed:16.0, Angle:62.399994 -> 120.00003
Create · bearing        create:stationary_contraption entity count 1
VS2 #3                 Y -53.31818 -> -59.31819
Sable · stock hold      sublevels 0 -> 0
Sable · native bridge   Y -56.93078 -> -58.91931 on the same sublevel ID
```


### 已验证的 Lua 修复逻辑

下面是成功测试的调用次序，坐标对应试验台。先通过插件读两处实时数据，通过 Numen 自带扫描定位轴向，之后才做世界修改。创造模式下执行，修完重新读控制器并投料。

```lua
local inspect = createlab.machine.inspect
local source = inspect({x=10, y=-57, z=2}).kinetics
local wheel = inspect({x=8, y=-57, z=2}).kinetics
local shaft = numen.scan.block({x=9, y=-57, z=2})

if source.rpm ~= 0 and wheel.rpm == 0
    and shaft.name == "create:shaft" and shaft.properties.axis ~= "x" then
    numen.build.place({{name="minecraft:air", pos=shaft.pos}})
    assert(numen.scan.block(shaft.pos).is_air)
    numen.build.place({{name="create:shaft[axis=x]", pos=shaft.pos}})
end
```

### 试验 Skill 示例

隔离服通过了 `SKILL_AVAILABLE true` 断言，验证服务端资料库的列表和正文读取。语义和行为由未来的真实模型实验验收。

```markdown
---
name: create_kinetics
description: Build, inspect, and repair Create 6.0.8.1 water-powered crushing wheels on Fabric 1.20.1 using signed RPM, stress, controller facing, and a real recipe output.
---

Create 6.0.8.1 small water wheels supply 8 signed RPM when water moves around the perimeter. A source cell alone is not proof of flow. Put each crusher on a shaft whose axis matches the crusher axis. Put the two wheels two cells apart perpendicular to their shaft axes. For wheels along X, separated along Z, the wheel to the north should turn +8 and the one to the south −8 to feed downward. A controller block appears in the gap.

Keep the intake and outlet clear. Feed one gravel via a hopper into the controller with a collection hopper/barrel below. Verify sand in the barrel. If the build receipt reports en-route placed blocks, check and clear the feeding path explicitly.

Use `numen.scan.block(position)` to read block ID, axis, and facing. If `createlab.machine` is available, `createlab.machine.inspect(position)` reads effective and theoretical RPM, stress, capacity, water flow, crusher validity and processing speed. Use `numen.scan.container` at the output barrel to confirm the actual result.

For a stalled side, check water wheel RPM, then compare shaft axis and crushing wheel RPM. If the water wheel is turning, the wheel is at zero, and the shaft axis is wrong, replace that shaft in the approved construction area. Re-read the controller and test the recipe after repair. Treat a placement receipt as a placement result and verify machine behavior separately.
```
