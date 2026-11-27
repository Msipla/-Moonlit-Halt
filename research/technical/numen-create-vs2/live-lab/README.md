# Numen × DeepSeek｜中文自主施工实测

2026-11-20 · Minecraft Java 1.20.1 · Fabric Loader 0.17.2 · Fabric API 0.92.6 · Numen 0.1.5-beta · Create Fabric 6.0.8.1（build 1744）· Java 17.0.21.1 · Node 24.22.3

[阶段任务书](../TASK.md) · [上一阶段的复现与前置依赖](../REPRODUCE.md) · [固定提交源码索引](../SOURCE-MAP.md) · [依赖与 SHA-256](../dependencies.tsv)

## 0.2.7-dev 基础候选体验

在 Minecraft 1.20.1 Fabric 使用[晚间无损拆架候选包](https://chatgpt.com/library?file_id=libfile_8eed9be5f7b881918e5bc3adf7478f7d&account_id=personal)。需要回退时使用[原安装包](https://chatgpt.com/library?file_id=libfile_3d55b1b626188191b32535153026e56a&account_id=personal)，按包内 `INSTALL.md` 配置独立测试实例。另将 [create-survey.lua](create-survey.lua) 保存为 `config/numen/players/<房主UUID>/modules/my/create_survey.lua`，与 `create_kinetics` 参考一同使用。房车和潜艇另装 DSH Mobile Base 0.3.4-dev 与 Trackwork 1.2.2，沿用 VS2 2.4.11 和 Eureka 1.6.4。原生 DSH 核心由创造模式空手点击，两次间隔须在 200 游戏刻内，临时踏台应与待装配结构分离。

| 载具 | 已验收范围 |
| --- | --- |
| 水面 | 原取样艇与 385 格中型轮船实装、操控、在途离席及移动加工。 |
| 飞行 | 已复验 162 格巡检站的悬停、离席巡航和移动加工。 |
| 房车 | 182 格装配、四轮、行驶和停车。模型定向调整舵位后，独立生存客户端在巡航中转入侧舱并返座转向。 |
| 潜艇 | 模型无损自拆并实装 157 格。独立验证下潜、定深、上浮、水下步行与返座。独立夹具解锚后玩家用原生升降键上浮。干舱与玩家自行解锚待验。 |

Windows 玩家验收仍待完成。房车和潜艇的当前 DSH 装配白名单拒绝随船搬运 Create 磨石。 [实测、存档和复现说明](evidence/mobile-base-blueprints-0.2.6-dev.md#中型轮船与-dsh-陌生载具2026-11-27)

## 真实模型握手

DeepSeek 官方端点返回 HTTP 200 和 `check_fixture` 工具调用，使用 389 token，用时 9.65 秒。随后用同一个模型通过游戏聊天发出问题，模型自行生成 `return numen.scan.block({x=3, y=-59, z=3})` 对应调用，游戏回执返回 `minecraft:emerald_block`，模型准确报出 ID 与坐标。独立读取服务器关停后的 Anvil 存档得到相同方块。游戏内握手使用 21,657 token，自发问到模型回复约 17 秒。

首次读盘时遇到 Minecraft 1.20.1 零字节的空实体区文件。修正读取器后直接重读握手存档并验证成功。A 组首次启动也在读基线时遇到同一问题，尚未发出请求、未产生模型用量，修正后在新世界开始计分。

## 自主施工

| 组别 | 投料前独立结果 | 注障/修复 | 用时 | API 请求 | 总 token | 估算美元 |
| --- | --- | --- | ---: | ---: | ---: | ---: |
| A · 原生 | 无 Create 器件 | 未触发 | 610.8 秒 | 25 | 382,325 | 0.00550 |
| B · 原生 + Skill | 两只粉碎轮均为 0 RPM，最终无水车或控制器 | 未触发 | 610.5 秒 | 68 | 1,608,392 | 0.02136 |
| C · 原生 + Skill + 探针 | 四台水车 ±8 RPM，粉碎轮均为 0 RPM，缺控制器 | 未触发 | 610.4 秒 | 64 | 1,665,758 | 0.02013 |

 
### A · 原生 Numen

模型调用 `numen.api.help` 修正了 Lua 参数，但选择先建 41×41 平滑石头底板。第一次超时后，续试中仍继续原任务或等待。最终快照中有 779 块平滑石头、104 格水和 1 格草地转成的泥土，共 884 格变更。40 格水越过施工范围，分别由水源扩散产生。无粉碎轮、水车或控制器。没有投料、故障注入或修复指标。服务器寻路回执没有报告路上额外搭方块或挖方块；大面积地面施工来自模型主任务。该任务未产生完成回执，无法核算手放与直写的比例。

### B · 加载原有 Create Skill

首个模型工具调用成功加载了 `create_kinetics`。模型用 `numen.build.place` 放了一根轴，原生回执显示这 1 格采用直接写入。它随后选择经 `numen.mc.run` 调用 `setblock`、`fill` 施工，并尝试用 `data get block` 读 NBT。第一张快照有水车两只，转速均为 0，另有 12 根轴。续试结束时两只水车已不在存档，留下 2 个均为 0 RPM 的粉碎轮、32 根轴、齿轮和机壳。粉碎控制器未出现。最终较基线改动 1,603 格，其中 1,089 格是草地替换为石头。在独立取样的外圈未发现越界改动。原生路径回执报告 3 格额外垫块。模型的多个全场扫描脚本撞到 200 次调用上限。此次亦未投料或进入注障阶段。

### C · Create Skill 和只读探针

模型成功加载 Skill，随后在真实游戏中调用 `createlab.machine.inspect`。回执记录西侧两台水车为 `+8 RPM / flow_score=+1`，东侧两台为 `-8 RPM / flow_score=-1`。独立存档一致，每台是单机网络，0/256 SU。两只粉碎轮和间隙里的轴仍为 0 RPM。粉碎轮间隙 `(0,-60,5)` 实际是传动轴。四台水车的所在网络没有连接到粉碎轮，游戏也没有生成控制器。夹具因此没有投料或注障。模型实际使用了探针，但本次未把读数转化为可运行的布局。

最终较基线有 820 格变更，其中 198 格由模型的 `fill` 把获准高度下方两层的泥土替换为石头。已收到的 Numen 建造完成回执记录 53 次放置，其中 14 次走直写分支。另有 94 次 `setblock` 和 614 格 `fill` 命令变更回执，均按操作累计。寻路到达回执未报告额外垫块。这些数字与净世界变更是两种不同口径。

## 已有机器的故障修复对照

夹具从上一轮保存为真实可运行的机器复制。两组先独立投一颗碎石，桶内沙子都从 2 增到 3，再将 `(9,-57,2)` 的传动轴由 X 改为 Y。受影响粉碎轮从 −8 降到 0 RPM，另一侧维持 +8 RPM，水车仍有动力。控制器 `(8,-57,1)` 仍朝下，`valid` 变为 `false`。

- **默认专用服的 B。** 模型找到竖装的轴并尝试拆除。回执明确是 `denied by hard rule break(other)`。启用 `bypass` 时硬规则依旧先判定。模型称两片轮子均无动力，独立读数只显示单侧归零。保留为定位成功、修复未通过。
- **默认专用服的 C。** 模型扫描多个单元，尝试批放粉碎轮，未恢复原机器。测试到期后不满足二次投料条件。
- **已授权隔离副本的 B。** 模型实际调用 `numen.work.dig` 清除错轴，随后用 `numen.build.place` 写入 `create:shaft[axis=x]`，并读回控制器有效。独立验证粉碎轮恢复 +8/−8 RPM，两侧应力/容量 64/256 SU，桶中沙子从 3 增到 4。模型又增加水源并报告缺水，但独立注障快照显示该水车仍有 −8 RPM。和故障快照相比，最终另有 155 格水变更、6 格变成泥土及 3 格石头变更。机械闭环通过，最小改动和故障解释仍有缺陷。
- **已授权隔离副本的 C。** 模型成功调用探针读到控制器无效，却继续试加水，错轴保持 Y，受影响的粉碎轮仍为 0 RPM。故障快照到验收点新增或改变 246 格水、12 格泥土和 3 格石头，未恢复到可投料状态。每种配置只有有限次实测，无法据此判断探针一定会改善或降低修复成功率。

已授权复测仅在新副本内设置 `unknownAsOther:false`，保留 `hardRules:true`，并在消息里明确可修理该试验机器。第一次并行授权尝试出现掉刻和房主超时，B 因断线后停止模型循环而无法完成，C 在断线后发生 `AgentLoop.callModel` 空指针。中断原始日志和崩溃报告留存。随后降低并发负载和 JVM 堆，按相同授权条件再测。权限处理和供应商接入均用现有配置，未修改 Numen 核心。

已验证的 B 组原样动作分散在两次模型 Lua 请求里。第一次的挖除已经执行，随后构造的设计表缺少 `pos` 字段。第二个请求纠正设计表后才放入正确朝向，完整语句、回执及后续额外加水均可在 `repair-unowned-B-1795179871557/model.jsonl` 和 `events.jsonl` 核对。

## 中文静态船壳

Flash 的宽泛指令试验留下高墙和大量施工材料。加入「使用已有平地、直接分层施工」后，另一轮 Flash 生成了合理的尺寸，但选择了施工区外的 Z 坐标，放置被拒，续试没有完成。两个现场和模型回执都已归档。

V4 Pro 使用同一模型服务和原点附近的施工区。第一次输出纯设计过程并触发完成长度限制，续试实际形成 7×13 橡木船体。夹具在仍有未完成细节时保存了快照。随后把这个模型实际建出的半成品复制到新的隔离服，授权模型补齐现有船壳。续建阶段用时 **469.8 秒**，实际制作了南侧入口、六个舷窗、储物桶和双色标记，并清掉两块圆石垫块。该过程包含两次独立游戏会话，且 Pro 试验增加了场地说明，因而无法单独归因于模型版本差异。

| 核验项 | 独立存档结果 |
| --- | --- |
| 船壳 | 282 个相连的结构方块。外包盒 X −3..3、Y −61..−57、Z −6..6，共 7×5×13。船首在北面 Z=−6，船底为 Y=−61。 |
| 入口及通道 | 南侧 Z=6，X −1..1，Y −60/−59 两格均为空。静态连通检测从任一入口格可达 55 个双层净空格，中轴 `x=0,y=-60` 可走到 Z=−5 的前舱。 |
| 玻璃与储物 | 两舷 X=±3 在 Z=−3、0、3 各有一块朝南北连接的玻璃板。储物桶在 `(-1,-60,-4)`，朝上。 |
| 两个安装位 | `(-2,-60,5)`、`(2,-60,5)` 都有连续两格净空，且从入口可达。红、蓝羊毛分别在 `(-2,-60,4)`、`(2,-60,4)`，比地板高一格，会占去旁边的一格。 |
| 未通过项 | 甲板有 5 个缺口，坐标为 X=−2、Y=−57、Z=−5、−3、−1、1、3。外侧 `(3,-58,7)` 仍有 1 块圆石。最终施工区外取样没有发现方块变化。 |

模型自述甲板封严、标记嵌在地板、脚手架清完。以上坐标以服务器存档为准。此验收使用服务端仿真和静态净空检查。玩家图形实机、审美、移动基地装配及行驶均待验证。

下载包内的 `saves/DeepSeek-Boat` 是上述最终世界，`saves/DeepSeek-Boat-Pro-Partial` 是续建前的模型成果。复制进 Minecraft Java 1.20.1 实例的 `saves`。管理员可在创造模式前往 `/tp @s 0 -60 8` 对准南入口检查。

## 工程判断

保留旧 AI 工程助手负责需要验收的机械任务。Numen 已具备中文指令驱动的真实施工和具体错轴修复能力。下一次优先在现有 Create Skill 中明确三处验收读数和决策顺序：先检查水车有转速，再要求同支路末端粉碎轮有转速，最后要求间隙空出且控制器有效。任何一关不通过就收窄到单支路查轴向和连接。把施工范围、最大改动和成功验收交给现有执行侧检查，可直接针对本次的大面积整地、越界和多余水源。继续沿用只读探针即可验证这一小改动。

日志里确有 `setblock` 和 `fill`。Numen 的命令工具按房主权限执行，试验的房主是 OP。原生 `build.place` 又可以在手放失败时直写。这里的结论适用于隔离创造条件。这些写入与普通玩家手持放置分别记录。

## 消耗和证据

[独立取样及逐组计量](evidence/summary.json) · [模型实际发出的 Lua 和回执节选](evidence/lua-excerpts.jsonl)

含连通性、三组空场、故障对照、船壳和中断前已计量的请求，共 **7,223,032 token**，其中输入 **7,159,225**、输出 **63,807**。基于平峰价估算 **0.19874 美元**。Pro 船体首轮和续建共 894,697 token，估算 0.10857 美元。成功的授权 B 修复用时 306.7 秒，644,714 token，估算 0.00938 美元。

完整日志、服务端取样、原生对话、故障夹具与船壳世界见随任务交付的 `Numen-DeepSeek-Live.zip`。没有将 API 密钥或请求认证头写入报告、存档或 Git 提交。

## 复现

在研究目录按照前置复现文档准备原有 `mc20`，执行 `npm ci --prefix mc20`。Fabric 服启动器、`mc20/mods/` 和派生的加载器缓存由各测试世界复用。C 组使用上阶段存档的 `fabric-probe/probe.jar`，SHA-256 为 `03590122bd22a761ce1c5e6b45377fab2c0a98039c2b2407121f1e8d95035e11`。B 和 C 的 Skill 取自同一份 `mc20/config/numen/server/skills/createlab/create_kinetics/SKILL.md`。源码和 JAR 均沿用上一阶段版本。

在 Bash 的非回显输入中配置有效密钥，然后在研究目录运行。端口默认为 25576 游戏、25577 RCON、25578 模型中继。目录用 `LAB_MC20` 和 `LAB_RUNS` 指定，游戏端口用 `LAB_MC_PORT` 指定，JDK 可用 `LAB_JAVA` 指定，C 组探针可用 `LAB_PROBE` 指定。

```bash
read -rs DEEPSEEK_API_KEY
export DEEPSEEK_API_KEY
export LAB_JAVA=/path/to/jdk17/bin/java
node live-lab/run.cjs smoke
for phase in A B C boat; do node live-lab/run.cjs "$phase" || break; done
unset DEEPSEEK_API_KEY
python3 live-lab/summarize.py live-lab/runs/*/
```

故障夹具由下载包中的 `fixtures/Create-Verified` 提供。也可按 [上一轮夹具说明](../REPRODUCE.md) 生成同一设备的存档。分别以 B、C 为首个参数运行，第二个参数指向原始存档，夹具仅复制其世界数据。为复现授权条件，在新的隔离服使用 `LAB_REPAIR_UNOWNED=1`。这会保留硬规则并关闭专用服 `unknownAsOther`。中文船壳可用 `LAB_BOAT_MODE=focused` 重现明确施工指令，或用 `LAB_BOAT_MODE=pro` 配置原点附近的场地及 Pro 模型。`LAB_BOAT_RESUME=/path/to/preliminary/run/world` 与 `LAB_BOAT_MODE=pro` 同用时复制半成品再续建。放在原运行目录下可自动复用该轮的空白基线。独立服务器可用 `LAB_JAVA_HEAP=1200m` 减少占用。实验首次运行时同时启动过多个独立服，最终故障重试和 Pro 船壳采用较小堆配置并减少同时驻留的实例。

```bash
node live-lab/run.cjs B /path/to/fixtures/Create-Verified
node live-lab/run.cjs C /path/to/fixtures/Create-Verified
LAB_REPAIR_UNOWNED=1 node live-lab/run.cjs B /path/to/fixtures/Create-Verified
LAB_REPAIR_UNOWNED=1 node live-lab/run.cjs C /path/to/fixtures/Create-Verified
LAB_BOAT_MODE=focused node live-lab/run.cjs boat
LAB_BOAT_MODE=pro node live-lab/run.cjs boat
```

Numen 由 `/numen companion LabBot profile lab:deepseek` 绑定服务器档案。夹具进游戏时，以房主 `LabOwner` 身份发送 `/numen chat LabBot <中文目标>`。配置在各运行目录的 `config/numen/server/provider/lab/deepseek.json`。档案键是 `provider=deepseek`、`protocol=chat`、`model=deepseek-flash`、`base_url=http://127.0.0.1:25578`、`reasoning_effort=off` 和 `ctx=65536`。Numen Keychain 的本地档案用 `loopback-only` 占位。仅 `relay.py` 接收运行时凭据，使用 HTTPS 请求 DeepSeek 官方 Chat Completions，统一关闭思考模式并要求流尾返回 usage。游戏内 Lua、Skill 调度、模拟玩家和回执均由 Numen 自身执行。若在玩家客户端面板直接配置，参见上一阶段的 [DeepSeek 配置](../REPRODUCE.md)。本轮验证的是带本地凭据中继的路径。

A、B、C 均从相同世界种子、超平坦施工区、空局面和创造权限开始。每组独立启动专用服。NPC 和玩家均留在本机，实验世界用边界围住平面测试区域，任务范围是 `x/z=-20..20`、`y=-61..-40`。设为 `bypass` 使无客户端界面的房主无需处理人工确认卡。它属于隔离环境的实验条件。夹具用同一句中文目标，未向模型提供任何构件坐标、蓝图或预写 Lua。首轮建造的等待上限为 340 秒，允许一次 270 秒的续试。首轮提示时间不含专用服启动。独立读盘覆盖 `x/z=-24..24`、`y=-64..-35`，检查范围内的方块、实体和方块实体 NBT。它记录 Create 保存字段的有符号 `Speed` 和 `Network.Stress/Capacity`（默认应力条件下，超载时有效转速为零） 和控制器 `valid/facing`。有效控制器和两侧有符号 RPM 同时成立时才统一投一颗碎石，记录物品实体和容器内砂子的增量，成功后才注入错误轴向。分析按模型回执记录直接写入、清除、额外施工和失败。模型口头结论单独留在请求记录中。

`model.jsonl` 保存 DeepSeek 流式回包组装后的模型回复、工具名、参数（含实际 Lua）、最近工具回执及官方 usage。`events.jsonl` 保存管理端操作和独立验收结果。完整包内 `*-build*.json` 等为原始快照，`minecraft.log` 对应游戏侧实际执行，`relay.log` 只记录中继启动与错误诊断。所有凭据及请求认证头均排除在证据之外。

按请求 UTC 时间和 DeepSeek 公开价表估算成本。Flash 平峰每百万缓存命中输入、未命中输入和输出分别为 0.003、0.15、0.60 美元，Pro 分别为 0.022、0.66、1.98 美元。峰时加倍。估算只覆盖官方回包明确返回的 token。实际扣费以账户账单为准。[DeepSeek 模型与计价](https://api-docs.deepseek.com/quick_start/pricing/) · [Chat Completions](https://api-docs.deepseek.com/api/create-chat-completion/) · [工具调用](https://api-docs.deepseek.com/guides/tool_calls/)

## 代码出处

- 档案绑定与游戏聊天由 1.20.1 发布对应提交的 `BindingCommands`、`ConversationCommands#sayOnce`、`ProviderLibrary#profileFromJson` 和 `Keychain` 提供。关键类的固定源码位置见 [源码索引](../SOURCE-MAP.md)。
- 原生建造回执中的“written straight in”来自 `BuildCompanionTask#tally`。直接写入分支由 `BuildPlacer#setDirectly` 执行 `setBlock(..., Block.UPDATE_ALL)`，再走放置回调与邻块更新。
- C 组只读探针来自 [CreateProbe.java](../fabric-probe/src/lab/CreateProbe.java)，仍通过原有插件登记，不需修改 Numen 发布包。

## 2026-11-21 Create Skill 迭代与实机对照

基线为 [任务书提交 5b30f06](https://github.com/Msipla/-Moonlit-Halt/commit/5b30f06a9b4e369de42aa6a0c5a52ec88142d2f9)。本阶段采用 [Skill 迭代任务书](../TASK-SKILL-ITERATION.md)，复用上一阶段的 Minecraft、探针、只读世界取样和真实 DeepSeek 接入。旧试验的结论与原始证据可通过上文访问。查看 [本轮独立取样和 API 计量](evidence/skill-iteration.json)。

本阶段只改现有 `create_kinetics`。将上一轮记录到的大范围填地、空转和错轴误判分别落成五个检查步骤：最小施工、水车运行、支路传动、控制器有效、产物和清理。增加了最小的状态名与读取接口说明，继续由模型自行选位置和写 Lua。精确 diff 见本次提交。对照用的旧文件临时从基线取出。历史 `probe-skill.jar` 的固定措辞测试用基线 Skill 复现。本轮全程用只含 `inspect` 的 `probe.jar`。

### 从空场建造

Minecraft Java 1.20.1、Fabric Loader 0.17.2、Create Fabric 6.0.8.1（build 1744）、Numen 0.1.5-beta。两组均使用未修改的 `fabric-probe/probe.jar`、`deepseek-flash`（关闭思考，同一流式中继）、种子 192834、同样场地和中文目标、创造模式、同样的 OP 房主与 `bypass`。输出上限为每次请求 4,096 token，Lua 引擎每段最多 200 次调用。建造等待上限 340 秒，未通过时有一次 270 秒的续试。施工边界补丁另测。每组独立游戏世界各一次，未复用对话。

| 指标 | 旧 Skill | 新 Skill |
| --- | ---: | ---: |
| 水车运行 | 14 台中 1 台到 −8 RPM | 1 台，0 RPM |
| 粉碎轮有效 RPM | 两台均为 0 | 未放置 |
| 控制器有效、试车产沙 | 无控制器，未投料 | 无控制器，未投料 |
| 自主建造成功率 | **0/1** | **0/1** |
| 取样区最终净变更 | 289 格 | 46 格 |
| 取样区内的越界变更 | 5 格，含 3 根轴 | 7 格，均低于施工面 |
| 最终新增水格 | 133 格，另有 2 格草地被水替换 | 0 格，先前有水，试验最后使用了海绵 |
| 实际取得 Skill 正文 | 1 次 | 1 次 |
| 引用同一探针的 Lua 提交 | 12 段 | 5 段 |
| 寻路结果报告的步数 / 挖 / 放 | 148 / 6 / 4 | 486 / 5 / 7 |
| 从目标到最后取样 | 610.4 秒 | 610.7 秒 |
| API 请求 / token / 估算美元 | 60 / 1,261,725 / 0.01703 | 51 / 1,033,503 / 0.01299 |

旧组在末次读盘出现水车转速，但之前模型扫描的多台水车一直读到 0 RPM，未实现通向两台粉碎轮的有效传动。新组围绕单台水车反复尝试桶操作、走位、架块和清水。探针实际输出过 `flow_score=0`、`rpm=0`。模型出现过命名空间写错、挖掘被流体安全规则拒绝和尝试不存在的 `/look`。截止时仍停在动力源一步。两组均未出现可用控制器，未获得试车、产物和从本次新建机器注障的机会。两组本轮都没有成功的 `setblock` 或 `fill` 模型命令写入回执，越界可由原生建造、寻路后的世界变化发生。

新版这次缩小了净施工面，减少 243 格变更，同时增加了走位和低于施工面的越界数。单组一次的随机实测无法证明成功率或成本有稳定差异。净变更取自 `save-all flush` 后的 Anvil 快照，独立取样区 X/Z ±24、Y −64..−35。有效 RPM、应力和容量沿用上一阶段的独立读盘口径。探针频率以生成的 Lua 段计，包含失败调用和一段内多次读取。完整回执在日志包中可核查。

### 独立施工边界测试

原生世界边界只约束水平位置，旧组也实际写到了 Z=21。Numen 的现有命令权限按命令根名裁决。本轮使用可选的 [Fabric 服务器测试补丁](scope/BlockBounds.java)，在 1.20.1 的 `World.setBlockState` 拦住施工区外或其他维度的写入。模型对话开始前移除实验房主 OP，原版 `commandModificationBlockLimit` 限制管理员一次批量命令的体积为 64。Numen 发布包和只读探针沿用原件。

| 独立实机检查 | 结果 |
| --- | --- |
| 管理员在界内 `fill` 两格 | 命令成功，读盘确认两格石头 |
| 管理员 `fill` 一百格 | 原版拒绝并报上限 64，读盘无目标块 |
| 管理员在 Y=−62 或 X=21 `setblock` | 两次均被阻止，读盘无目标块 |
| 取消房主 OP 后由 Numen 执行命令 | `fill` 和 `execute` 回执均为拒绝 |
| Numen 原生建造 | 界内成功放一格木板。可达的 Y=−39 目标回执放置数为 0，读盘无目标块 |
| 从 X=20 放水 | 读盘共有 64 格水，全部位于允许范围内 |

本组使用管理员 `numen drive` 发出明确测试动作，未产生模型用量。首个验证实例在等待异步回执且地下目标寻路时超时。调整为从服务端控制台收回执，并给越界目标设置近处可达平台后，全部断言通过。补丁限制已测试的运行时写入路径。原生建造每任务目标数仍沿用 Numen 自身限制，原版命令的 64 格上限不适用于原生建造任务。规划可以对越界请求继续寻路。其他模组直接改区块、区块生成及生产服使用不在本次验收范围。

### 已有机器的错轴补测

另复制两份已验证的机器，启用相同施工边界、取消房主 OP，并使用同样的 `LAB_REPAIR_UNOWNED=1`。两侧先实际将一颗 `minecraft:gravel` 粉碎，桶内沙由 2 增到 3。独立读盘确认两侧原始粉碎轮为 ±8 RPM，网络均为 64/256 SU。随后同样把 `(9,-57,2)` 的轴从 X 改为 Y，受影响粉碎轮变为 0 RPM，另一侧仍为 +8，控制器无效。两组模型各有 340 秒排障，代码独立核对原轴向和再次产物。

| 指标 | 旧 Skill | 新 Skill |
| --- | ---: | ---: |
| 原轴向恢复 | 否，Y 轴被水填充 | 否，最终仍是 Y 轴 |
| 最终两侧粉碎轮 | 0 / 0 RPM | +8 / 0 RPM |
| 控制器和产出 | 无效，未二次投料，桶内为 3 | 无效，未二次投料，桶内为 3 |
| 错轴修复成功率 | **0/1** | **0/1** |
| 注障之后的净格变更 | 275 格，其中 206 格最终为水 | 139 格，其中 94 格最终为水 |
| 取样区越界变更 | 0 | 0 |
| 实际取得 Skill 正文 / 引用探针的 Lua 段 | 1 / 5 | 1 / 9 |
| 用时 / API 请求 / token / 估算美元 | 340.4 秒 / 20 / 290,242 / 0.00524 | 340.3 秒 / 42 / 763,123 / 0.01076 |

旧组读到了水车仍有动力和故障支路归零，随后依然反复加水和换材料，最后原本正常的一边也失去转速。新组读到轴向不符并拆了正确的轴，但反复把 `axis` 放进设计项的独立 `properties` 字段，回执显示放置成功，实际轴仍是 Y。该接口需要在方块状态串中表达朝向，例如 `create:shaft[axis=x]`。原生任务的放置回执证实了目标格被写入。最终轴向和是否恢复动力仍取自独立存档。

### 本轮总用量及交付

四组实测均收到 HTTP 200，共 173 次模型请求，合计 **3,348,593 token**。输入 3,321,252，输出 27,341。输入包含缓存命中 3,187,584、未命中 133,668。按 UTC 请求时间适用的 DeepSeek Flash 平峰价估算 **0.04602 美元**。此值只计 API usage 回包，实际扣费以账单为准。[DeepSeek 计价](https://api-docs.deepseek.com/quick_start/pricing/)

完整日志、Lua 回执、独立快照和最终试验世界见随任务交付的 `Numen-Create-Skill-Iteration.zip`。仓库记录已编译边界补丁、服务器实际启动和 API 已收到回包等证据。玩家实机验收待进行。

### 本轮复现

下面的两个正式组要在研究目录分开启动，并需要私有运行时 `DEEPSEEK_API_KEY`。为避免把旧 Skill 写回当前项目，从 Git 读取到临时文件：

```bash
base=5b30f06a9b4e369de42aa6a0c5a52ec88142d2f9
skill=mc20/config/numen/server/skills/createlab/create_kinetics/SKILL.md
old=$(mktemp)
git show "$base:research/technical/numen-create-vs2/$skill" > "$old"

LAB_JAVA_HEAP=1200m LAB_SKILL_FILE="$old" LAB_RUNS="$PWD/runs/old" node live-lab/run.cjs C
LAB_JAVA_HEAP=1200m LAB_SKILL_FILE="$PWD/$skill" LAB_RUNS="$PWD/runs/new" node live-lab/run.cjs C
```

复用已经启动过的 Fabric 1.20.1 服务端及其 intermediary JAR 编译测试护栏。下列命令在同一目录执行：

```bash
mkdir -p /tmp/createlab-scope-classes
mcjar=mc20/.fabric/remappedJars/minecraft-1.20.1-0.17.2/server-intermediary.jar
mixin=$(find mc20/libraries/net/fabricmc/sponge-mixin -name '*.jar' -print -quit)
javac -proc:none --release 17 -cp "$mcjar:$mixin" -d /tmp/createlab-scope-classes live-lab/scope/BlockBounds.java
jar --create --file /tmp/createlab-scope.jar -C /tmp/createlab-scope-classes . \
  -C live-lab/scope fabric.mod.json -C live-lab/scope createlab-scope.mixins.json

LAB_SCOPE_JAR=/tmp/createlab-scope.jar LAB_RUNS="$PWD/runs/guard" node live-lab/run.cjs guard-check
```

错轴补测用前一轮包里的 `fixtures/Create-Verified` 作为第二个参数。对两种 Skill 分别运行：

```bash
LAB_SCOPE_JAR=/tmp/createlab-scope.jar LAB_REPAIR_UNOWNED=1 \
  LAB_JAVA_HEAP=1000m LAB_SKILL_FILE="$old" LAB_RUNS="$PWD/runs/repair-old" \
  node live-lab/run.cjs C /path/to/fixtures/Create-Verified
LAB_SCOPE_JAR=/tmp/createlab-scope.jar LAB_REPAIR_UNOWNED=1 \
  LAB_JAVA_HEAP=1000m LAB_SKILL_FILE="$PWD/$skill" LAB_RUNS="$PWD/runs/repair-new" \
  node live-lab/run.cjs C /path/to/fixtures/Create-Verified

python3 live-lab/summarize.py runs/*/*/
```

### 下一步

下一轮补一个隔离施工区内的定点放水动作。让模型选一个格，执行侧单格放水并回传实际水源落点和邻近水车的 RPM，先验收一台水车稳定起转。此次空场的共同问题发生在动力源与传动接合前后，现阶段的通过率不足以扩大到更长的 Create 施工任务。

## 2026-11-21 Create 基础动作闭环

[本轮任务书](../TASK-BASIC-ACTIONS.md) · [独立读盘、模型实际 Lua 和用量](evidence/basic-actions.json)

Minecraft 1.20.1、Fabric Loader 0.17.2、Fabric API 0.92.6、Numen 0.1.5-beta、Create Fabric 6.0.8.1 build 1744。沿用种子 192834 的隔离创造服、已发布的只读探针和施工护栏。本轮修改 `create_kinetics` 中的轴向和注水说明、隔离服测试脚本和可选的受限单格水源动作。每个模型样本用独立世界，使用官方 DeepSeek Flash 和 V4 Pro，均关闭思考。模型发问前取消房主 OP，原版单次命令上限 64，服务器端施工范围 X/Z −20..20、Y −61..−40。夹具通过 Anvil 独立取样至 X/Z ±24、Y −64..−35。

### 原生能力校准

- **放水。** 上轮 `skill-iteration` 的新版空场记录中，Lua 正常返回或点击回执无变化的同时，有时目标邻格才出现水。瞄准空气与空点正前方右键都无法指定实际受水面，积水还会让步行搜索报 `WOULD_FLOOD` 或 `no_path`。Numen 的 `BuildApi.place` 明确排除液体，`UseApi.block` 经 `BlockActionOps.interactAt` 执行模拟玩家的右键。原版桶射线决定受水面，`numen.scan.block` 能读实际落点。校准中，模型之外的固定 Lua 先用 `numen.build.place` 尝试水方块，回执 `placed=0`。随后用 `numen.use.block({x=6,y=-56,z=2},{item="minecraft:water_bucket"})` 点墙，回执说墙未改变，但相邻 `(6,-56,1)` 扫描和读盘均为水源 `level=0`，水轮由 0 达到 −8 RPM、`FlowScore=-1`。有合适站位和墙面时，原生桶可以完成注水。该无模型校准渠未完全围合，取样发现 110 格水且均在护栏内。正式任务仍需模型自己做接水槽。
- **轴向。** 上轮新版错轴补测请求 7 和 10 使用了独立的 `properties={axis="x"}`，放置回执各为 1，读盘仍是 Y。Numen 的 `Design.CODEC.decode` 只从每项读坐标和 `name`，`BuildStates.resolve` 用原版方块状态解析器读取 `name`。本轮无模型校准中，独立 `properties` 复现了相同的成功回执和 Y 轴。把轴向改写到 `name="create:shaft[axis=x]"` 后，回执和 Anvil 均确认 X。施工模型只接手由夹具置入的 Y 轴，正式改成 X 的操作由模型发起。读取探针仍用 `createlab.machine.inspect` 的实时 `getSpeed()` 和 `flowScore`。

### 定点动作和护栏实测

无模型校准证明原生桶可以把水倒进特定邻格。三次真实原生组暴露了可靠性缺口。尤其是 Pro 两次瞄准同一墙，转向与站位变化会让桶落到墙背或墙顶。Numen 原生接口参数只有瞄准格，未提供受水面参数。水轮虽能被意外水流带转，源点与水域仍未受控。

沿用原有护栏增加可选动作 [`createlab_water.source.place(cell, wheel)`](scope/WaterAction.java)。它只在显式启用且施工范围已配置的隔离创造服登记。它检查主世界、施工范围、已加载区块、小型水轮轴向和轮缘上方一格空气，调用 Numen 现有放置权限，获得许可后再次核对条件。执行侧用原版 `setBlock` 单格写入 `minecraft:water[level=0]`，邻块和流动由游戏 tick 处理。回执读取实际落点、即时有效 RPM 和流分数。即时转速可能为 0，待水流稳定后使用原只读探针复验。首次打包把入口放进了 Mixin 专用包，Fabric 拒绝启动。移到普通包并重新编译后通过校验，失败实例没有模型调用。

无模型边界校准的三种拒绝回执分别为 `outside_construction_scope`、`target_not_air` 和 `source_must_be_above_rim`，拒绝前后的方块快照完全一致。边缘合法目标 `(20,-56,-9)` 回执为 `placed=true`，即时 RPM 0，原生扫描返回 `minecraft:water`、`level="0"`。5 秒后读盘只有该水源，合计 80 格水均在边界内，轮心 `(20,-57,-10)` 为 +8 RPM。重新编译的护栏另跑原有 `guard-check`，命令权限、64 格上限、原生方块写入和边界扩散复测均通过。原版命令的 64 格限制与原生建造的批量上限各自生效。

### 模型实测

| 独立样本 | 读盘结果 | 判定 | HTTP 200 / token / 估算美元 |
| --- | --- | --- | ---: |
| Flash · 原生水桶初试 | 1 台水轮，0 RPM。水源在轴端 `(3,-59,11)`。最终 132 格流动水 | 失败。放水位置错，积水令寻路耗尽预算 | 18 / 259,115 / 0.00437 |
| Flash · 澄清轮缘与接水后 | 5 台轮均为 0 RPM。末次读盘无水源，73 格流动水 | 失败。读取了通用建筑 Skill，扩建 15 格长的槽并尝试多轮，超时 | 20 / 339,451 / 0.00529 |
| Pro · 原生水桶 | 轮心 `(0,-58,4)` 三次均为 −8 RPM。墙背 `(0,-57,1)`、墙顶 `(0,-56,2)` 各有一处源头，188 格流动水 | 失败。实际起转，预定源点 `(0,-57,3)` 在终点只是 level 8 流动水，另外接了两根轴 | 19 / 326,770 / 0.04484 |
| Pro · 启用单格动作 | 轮心 `(4,-52,11)` 三次均为 **+8 RPM**、流分数 +1。唯一水源 `(4,-51,10)`，轮缘 `(4,-52,10)` 的流动水为 level 8 | **通过基本动作**。取样无越界，槽内水仍溢流 | 25 / 439,050 / 0.04955 |
| Flash · 传动轴 | `(0,-60,9)` 从 Y 改为 X，读盘净变化仅这一格 | **通过**。模型重新扫描确认 X | 5 / 57,542 / 0.00077 |

水轮原生组三次均未达到单水源验收。启用单格动作和相应 Skill 后的一次 Pro 样本通过。此轮样本量和指令差异只支持单次可行性结论。轴向有一个有效样本并通过。首次轴向运行的夹具把求模前的就绪回复误判成任务完成，随后关停，中断了下一轮模型处理。已修复等待逻辑，要求先见到当前目标，再接受该轮结束。误判样本原始日志留存，排除在轴向验收分母外。

Pro 自行选址和安排轮缘水道，从空场建造。它先放了一个临时轴并以水轮替换，独立存档无额外传动轴。它发现向下迭代的 Lua 缺少负步长后修正水道侧壁，读到干态 0，再实际调用下列接口：

```lua
local source_cell = {x=4, y=-51, z=10}
local wheel = {x=4, y=-52, z=11}
local res = createlab_water.source.place(source_cell, wheel)
```

第 21 个模型请求发出放水调用。回执为 `placed=true`、`landed_at=(4,-51,10)`、`rpm_now=0`。下一次 Lua 等待 2 秒后读到有效、理论和生成转速均 +8 RPM。第 23 个请求重读水源、轮缘和转速。夹具在游戏刻 **5063、5145、5227** 分别刷新并独立读取 Anvil，三次都记录 **+8 RPM** 和相同水域，首末间隔 164 游戏刻。档内共有 146 格水，其中 145 格为流动水，水域外包范围为 X −3..11、Y −60..−51、Z 4..19。水从接水槽流到地面平层，取样区发现两处草块变为泥土，另有 22 格圆石。模型称接水槽完全兜住，读盘显示该项仍有缺陷。最后一次取样距发问 259.1 秒。

轴向组先有 Y 轴的固定注障夹具。随后模型在其第 3 个请求执行 `numen.build.place({{name="create:shaft[axis=x]",pos=pos}})`，原生回执显示替换并直接写入 1 格，同段扫描读到 `axis="x"`，第 4 个请求单独读取了 `numen.scan.block(pos).properties.axis`，第 5 个请求向玩家报告 X。夹具只负责注障、采样和计时。最后一次取样距发问 29.7 秒。

本轮中继共记录 88 次 HTTP 200、**1,432,723 token**。输入 1,417,363，其中缓存命中 1,290,240、未命中 127,123，输出 15,360。按请求 UTC 时间和各模型平峰价估算 **0.10494 美元**，包含无效轴向组的 1 次有计量的就绪请求和超时后观察窗口内完成的请求。早期轴向中断的模型请求未取得 usage，无法确定是否计费。实际账单以提供商为准。[DeepSeek 计价](https://api-docs.deepseek.com/quick_start/pricing/)

两项基本动作已有服务器和真实 API 证据。单格水源动作及护栏用 JDK 17 编译并在 Fabric 服务端启动。玩家实机验收待进行。下一阶段具备尝试单水车、单轴、单片粉碎轮联动的基础，先修正接水槽的溢流，再实测三段 RPM 和应力。完整双轮粉碎机留待联动通过后再评估。

### 复现

从研究目录运行以下命令。配置沿用已启动过的 Minecraft 1.20.1 Fabric 服务端、`npm ci --prefix mc20` 的 Node 依赖和上阶段的只读探针。旧阶段的实验及编译命令应在旧阶段提交执行。

```bash
build=$(mktemp -d)
mcjar=mc20/.fabric/remappedJars/minecraft-1.20.1-0.17.2/server-intermediary.jar
loader=$(find mc20/libraries/net/fabricmc/fabric-loader -name '*.jar' -print -quit)
mixin=$(find mc20/libraries/net/fabricmc/sponge-mixin -name '*.jar' -print -quit)
unzip -p mc20/mods/numen.jar META-INF/jars/numen_api-fabric-1.20.1-0.1.5.jar > "$build/api.jar"
unzip -p mc20/mods/create.jar META-INF/jars/base-2.3.13+1.20.1.jar > "$build/base.jar"
unzip -p mc20/mods/create.jar META-INF/jars/Ponder-Fabric-1.20.1-1.0.91.jar > "$build/ponder.jar"
mkdir "$build/classes"
javac -proc:none --release 17 \
  -cp "$mcjar:$loader:$mixin:$build/api.jar:mc20/mods/create.jar:$build/base.jar:$build/ponder.jar" \
  -d "$build/classes" live-lab/scope/*.java
jar --create --file "$build/scope.jar" -C "$build/classes" . \
  -C live-lab/scope fabric.mod.json -C live-lab/scope createlab-scope.mixins.json

export LAB_SCOPE_JAR="$build/scope.jar"
export LAB_RUNS="$PWD/runs/basic-actions"
export LAB_JAVA_HEAP=1200m

node live-lab/run.cjs basic-check
LAB_BASIC_WATER_ACTION=1 node live-lab/run.cjs basic-check
node live-lab/run.cjs guard-check

# 以下模型测试使用私有运行时环境变量 DEEPSEEK_API_KEY。
node live-lab/run.cjs basic-axis
node live-lab/run.cjs basic-water
LAB_BASIC_MODEL=deepseek-v4-pro node live-lab/run.cjs basic-water
LAB_BASIC_MODEL=deepseek-v4-pro LAB_BASIC_WATER_ACTION=1 node live-lab/run.cjs basic-water
python3 live-lab/summarize.py "$LAB_RUNS"/*/
```

`events.jsonl` 的 `basic_water_sample` 给出相隔至少 80 游戏刻的独立水域状态，`basic_water_result` 给出单轮、单水源、稳定非零 RPM 和边界检查的合并结果。`basic_axis_result` 记录原状态、新状态及取样区净变动坐标。`model.jsonl` 的 `calls[].id` 与后续请求的 `context[].tool_call_id` 对应工具参数和回执。完整日志、Anvil 快照及地表存档见随本轮交付的 `Numen-Create-Basic-Actions.zip`。

## 2026-11-21 水磨坊 Skill 与连续工具调用

沿用 Minecraft 1.20.1、Fabric Loader 0.17.2、Create Fabric 6.0.8.1 build 1744、Numen 0.1.5-beta、既有只读动力探针和受限单格水源动作。施工范围及取消房主 OP 的规则与基础动作阶段一致。实验未重建粉碎机。[本轮证据](evidence/watermill.json)

### 思考模式

在隔离世界放入用于校验的绿宝石块和金块。DeepSeek Flash 使用 `thinking.type=enabled` 与 `reasoning_effort=high` 独立调用两轮 Lua。第二轮根据第一轮读到的绿宝石块，选择读取金块所在的位置。三次 HTTP 200，终态报告与保存后的 Anvil 一致。前两条助手工具回合的思考流分别为 436、69 字节。回传到接下来的请求时，工具调用 ID 和 SHA-256 均精确匹配。日志只记字节数、哈希和 token 用量。测试用去 33,314 token，其中 141 为思考 token。从发问到独立取样 16.7 秒。

Pro 的完整磨坊审图试验中，两次回复都以 `finish=length` 结束，8192 个输出 token 全部是思考 token，没有可供审查的方案。主施工试验恢复原来的非思考模式和每次 4096 输出 token。合并施工指令也有一次在发出放置调用前耗尽 4096 输出 token。后续保留原模型档案，通过分阶段目标降低每轮生成工作量。最早一次中继因缩进错误未启动，修复后进行了正式握手，该次无模型请求。

### 参考与审图

仅在现有 `create_kinetics` 增加 [水磨坊参考](../mc20/config/numen/server/skills/createlab/create_kinetics/references/watermill.md)。参考使用现有 Create Ponder 的轮缘、齿轮和投取料说明，核对齿轮箱的连接面、磨石底部连接和目标 JAR 的小麦配方。地板、立面、材料、位置和朝向由模型选择。原生 `numen.build.place` 执行模型生成的 Lua；夹具负责审图、读盘和实物投料。

审图发现屋内轮心、脚位高度不符、槽底低于护栏和齿轮箱与末根轴重叠。夹具支持与 Lua 相同的坐标对象及数组。一次补充反馈超出 RCON 1446 字节上限，长聊天改走同一房主身份的服务端控制台。非思考 Pro 续写自己的草案，删除重叠轴，提交 7×7 东侧动力、北入口的可施工方案。审图从 Anvil 确认各次方案试验均未改变方块。审核通过后的方案用作实测输入，未用人工脚本部署整座磨坊。

### 真实施工

基础方案由模型选择屋体 X/Z −3..3，地板 Y=−60，北入口 `(0,-59,-3)`，相邻站位 `(1,-59,0)`。东侧轮 `(4,-59,0)`，沿 X 轴穿东墙，经 `gearbox[axis=z]` 向上驱动 `(2,-58,0)` 的磨石。定点水源 `(4,-58,1)`。首段已经验证水轮、轴、齿轮箱为 +8 RPM、磨石为 −8 RPM，共用同一网络，应力 32/256 SU。模型提前放水，实际扩散为 149 格，夹具拦截后续作业。

在该存档的隔离副本继续，保留已装动力件。读盘提示 11 个缺失隔水格，模型自行生成 Lua 放置隔墙。随后读盘只剩槽内一个源头和两格落水，场外水格清零。模型施工前后使用同一动力轴线。后续按干态传动、封槽、单格供水、建筑外壳的顺序验收。

工作额度中断发生在屋顶施工开始时。落盘复查仍有 49 格橡木地板、外墙和已封水槽，屋顶部分尚未落盘。恢复后在该存档副本只续做屋顶。模型加载原生 `building_design`，自行写 Lua 放置 63 格云杉楼梯，屋脊实际到 Y=−49。原检查只扫描到 Y=−51，误判覆盖不足。按斜屋顶跨度修正检查后，沿用原屋体继续验收，未再生成或放置屋顶。

基础样例通过。独立存档检查覆盖全部 25 个室内顶面投影列，入口至检修站位有 24 个连通的两格净高站位，磨石上方两格为空。夹具从上方投掉落的 1 份小麦。11.1 秒后磨石 `OutputInventory` 由零变为 **2 份 `create:wheat_flour`**，并有 1 份种子。水轮、轴、齿轮箱保持 +8 RPM，磨石 −8 RPM，应力 32/256 SU。供水仍为一源与两格落水，取样内无槽外水。验收包保留投料后的基础存档副本。投料走真实 Minecraft 实体下落路径，通路和净高采用独立方块读盘的静态检查。

寻路过程曾放置额外圆石，模型随后清理了部分垫块。前段溢流也有地形痕迹。此样例验证了真实传动、封水与磨粉，建筑外观、玩家亲自走入及取货待实机验收。

### 在同一存档重新规划

保留已通过磨粉验收的原存档，改造只向其隔离副本写入。首次改造审图以 4096 输出 token 截断。改问变动坐标后，模型先把水轮放在墙线上，后将站位与齿轮箱重叠，再次提交时又选错站位高度。夹具和复核逐项指出问题，最终复用原始回复中的模型草案，审核后才允许施工。原版随机刻曾使 `(0,-61,-4)` 从泥土自然长成草方块，触发审图期间的全图差异检查。工具记录中只有读 Skill 和一段返回空值的 Lua。复核该单格差异后，固定隔离世界的 `randomTickSpeed=0`，避免误报，水流和机械仍由常规游戏刻驱动。

新方案保留 7×7 地板、原墙线与斜屋顶。模型把入口从北面 `(0,-59,-3)` 改到东面 `(3,-59,0)`。北侧轮位 `(0,-59,-4)`，沿 Z 轴穿原北门的脚位，接 `gearbox[axis=x]`，磨石移到 `(0,-58,-2)`。室内站位 `(0,-59,-1)`，轮缘新水源 `(1,-58,-4)`。审查要求清除东门外原东侧水槽的顶石，以保留两格净高。原建筑和轴线都取自实机存档；入口和轴线由模型重新选择。

模型首次实际施工封闭了旧水源，读盘已经无水，四个旧部件均停止。但模型尝试用 `numen.work.dig` 拆四件旧机械时，Numen 以 `break(other)` 拒绝。分阶段复制存档时曾只复制区块，导致早期四格的归属记录遗失。原始起转轮的 `numen_placed.dat` 留有四格及原房主 UUID。根据两次快照校验原方块状态后，只把这四条原记录恢复到改造副本内，地区文件逐字节保留。物主规则和施工护栏依旧启用。

归属恢复后模型调用了 `numen.work.dig`。随后中继的上游连接失败，执行环境又在写完区块、返回验收事件前终止会话。离线读盘确认旧四件、旧水源及落水全部消失，地板与屋顶未动。挖掘也清掉附近几格旧墙和槽壁。后续从已退役的副本接续，保护检查仍以最初验收快照为基准。

改造副本通过。模型新装四件干态传动，补齐检查给出的 8 格槽壁，再调用一次单格供水。北轮、Z 轴、齿轮箱和磨石都以 −8 RPM 运转，共用一个新网络，应力 32/256 SU。实测只有 3 格槽内水。模型开出东墙两格高门洞并清掉外侧阻挡。北侧原门脚位变成穿墙轴，上面仍有一格空隙，原处已无法作为两格高入口。25 个室内顶面投影列仍有屋顶覆盖，从东门能到达室内检修站位。逐格对比地板和屋顶相关方块，零变化。夹具再次投下 **1 份小麦**，11.1 秒后新磨石库存增加 **2 份面粉**。这一续跑从发出干态施工任务到独立投料验收用 173.4 秒，17 次 HTTP 200，224,356 token。

原建筑固定，同一次改造把入口换到了东墙，把传动从东侧 X 轴移到北侧 Z 轴。模型选择新的终端和站位，再使用原有施工与挖掘动作。水磨坊参考可复用于这一类动力与建筑的组合。现有证据覆盖创造模式下的一种建筑和一次改造。需要使用者审图及夹具分阶段核验，外墙与寻路垫块仍需打磨，手动投料、取货与走入体验留待客户端验收。

### 模型用量

用量取自 DeepSeek 的实际 `usage`，包括失败方案、工具回合和定向重试。HTTP 秒数为有记录的请求累计时间。原版投料和读盘不消耗模型 token。

| 范围 | 请求尝试 | HTTP 200 | 输入 token | 输出 token | 总 token | HTTP 秒 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Flash 思考与工具续接 | 3 | 3 | 33,018 | 296 | 33,314 | 16.0 |
| 基础磨坊审图、施工与修复 | 76 | 76 | 1,204,568 | 45,916 | 1,250,484 | 856.6 |
| 改造审图、施工与续跑 | 56 | 50 | 685,528 | 11,405 | 696,933 | 434.6 |
| 合计 | 135 | 129 | 1,923,114 | 57,617 | 1,980,731 | 1307.2 |

基础屋顶续跑用去 23 次请求、494,816 token，约 300.6 秒。全部记录合计 16,578 个思考 token。没有返回用量的传输失败只计为尝试。

### 复现

沿用本页「基础动作」的服务端 JAR、探针与施工护栏编译命令。在研究目录运行以下命令。

```bash
export LAB_SCOPE_JAR=/path/to/scope.jar
export LAB_RUNS="$PWD/runs/watermill"
export LAB_JAVA_HEAP=1200m

# 模型凭据由私有运行时的 DEEPSEEK_API_KEY 提供。
LAB_THINKING=1 node live-lab/run.cjs think-smoke
node live-lab/run.cjs watermill
```

`watermill` 在 `mill_plan_review` 暂停并输出审核文件路径。检查草案中的方块、轴线、隔水格、地板、门洞和通路后，在该路径写 `{"approved":true,"reason":"审图意见"}` 放行，或者写 `{"approved":false,"reason":"具体几何问题"}` 退回。单轮至多给一次定向修改。已验收的屋体可用 `LAB_MILL_WORLD` 指向完整地表存档，用 `LAB_MILL_REVIEWED` 指向初始审图轮的 `events.jsonl`，并通过 `LAB_MILL_VARIANT=1` 仅执行改造。夹具先从副本独立核验基础动力、可达性和已产面粉。需要接续已生成设计时可用 `LAB_MILL_DRAFT` 给出原始模型 JSON，仍经过同一审图。历史证据包中的基础存档早于归属传递修正。仅复现历史改造时，将 `ownership-repair/numen_placed.dat` 放进该存档的副本，在 `LAB_MILL_DRAFT` 指定包内的 `reviewed-variant-draft.json`。保留原地表区域文件。若从原传动已退役的实测存档继续，另用 `LAB_MILL_BEFORE` 指向验收时的 `initial-fed.json`，以原始地板和屋顶校验副本。所有轮次均按独立运行目录记录。

用于复核关键结果的事件包括 `mill_plan_review`、`mill_review_decision`、`mill_powered`、`mill_built` 和 `mill_result`。原始记录中，`model.jsonl` 的工具 ID 可在后续请求的 `context[].tool_call_id` 中找到回执，`*-fed.json` 记录真实磨石方块实体，`accepted-world/` 保存通过磨粉验收的原始水磨坊地表存档。建筑保护由改造前后逐格比较地板和屋顶一带的方块状态判定。

## 2026-11-21 PatchouliAI 静态审查与 Numen 自主复测

### PatchouliAI 1.6.6

最初的临时下载请求返回 HTTP 403，未收到 RAR。响应头包含 Cloudflare 请求 ID，拒绝的具体规则未知。随后按用户提供的 GitHub 源码仓库检出 `b1d929fe9bcf0a3901b396f4d03aae3e3a265913`，共有 751 个跟踪文件、15.31 MiB，`git fsck --no-reflogs --full` 通过。所见内容是该提交的源码，无法与最初的 RAR 核对同一性。此次采用源码、配置与测试用例静态阅读。 [源码快照](https://github.com/Msipla/PatchouliAI-source/tree/b1d929fe9bcf0a3901b396f4d03aae3e3a265913)

| 目录 | 作用 |
| --- | --- |
| `src/main/java/com/cuptu/patchouliai` | Java 模组主体。 `llm` 管对话、模型请求及特殊建造入口，`tasks` 管建造 DSL、蓝图、执行及恢复，`pathfinding` 管移动，`client` 和 `network` 管界面与同步，`voice` 管语音。 |
| `src/main/resources`、`src/test` | Fabric 元数据、Mixin 和游戏资源，以及 JUnit 源码测试。 |
| `runtime/f5tts`、`vendor`、`docs` | Python F5-TTS 运行时源码，三份本地 JAR，游玩指南。该 Git 检出没有 `vendor/localvoice` 的模型文件。 |

构建配置指定 Minecraft 26.1.2、Java 25、Fabric Loader 0.19.3、Fabric API 0.152.1+26.1.2、Loom 1.15.5 和 Gradle Wrapper 9.5.1。还有 Java-WebSocket、音频 JNI、sherpa-onnx 及 Python F5-TTS 组件。语音依赖与建造调用链分离。模组元数据显示作者 Cuptu、Anrry，声明 MIT，仓库未找到独立模组 LICENSE 正文。第三方依赖仍各自适用其许可。当前 Numen 试验在 Minecraft 1.20.1 / Java 17 上，源码移植涉及游戏及加载器 API 版本差异。 [版本配置](https://github.com/Msipla/PatchouliAI-source/blob/b1d929fe9bcf0a3901b396f4d03aae3e3a265913/gradle.properties) · [构建依赖](https://github.com/Msipla/PatchouliAI-source/blob/b1d929fe9bcf0a3901b396f4d03aae3e3a265913/build.gradle) · [模组元数据](https://github.com/Msipla/PatchouliAI-source/blob/b1d929fe9bcf0a3901b396f4d03aae3e3a265913/src/main/resources/fabric.mod.json)

1. **从设计到清单。** `BuildCommandInterceptor.tryIntercept` 识别建造意图，`ConversationManager` 将文本建造送入 `FourPhaseBuild.generate`。主体、立面与屋面、室内和场地逐批生成，每批只增补程序，沿用同一原点和前序函数。 `BuildCodeInterpreter` 解释受限的类 JavaScript 几何 DSL，把 `fill`、`setBlock` 和形状函数展开成 `Placement`，图片另有分层蓝图通道。 `MultiPlaceTask.parseBuildCodePlacements` 将相对坐标加到世界原点，处理注册表 ID 与方块状态，同坐标保留最后一次写入。 [文本建造入口](https://github.com/Msipla/PatchouliAI-source/blob/b1d929fe9bcf0a3901b396f4d03aae3e3a265913/src/main/java/com/cuptu/patchouliai/llm/ConversationManager.java#L681) · [四阶段](https://github.com/Msipla/PatchouliAI-source/blob/b1d929fe9bcf0a3901b396f4d03aae3e3a265913/src/main/java/com/cuptu/patchouliai/tasks/FourPhaseBuild.java#L57) · [坐标转换](https://github.com/Msipla/PatchouliAI-source/blob/b1d929fe9bcf0a3901b396f4d03aae3e3a265913/src/main/java/com/cuptu/patchouliai/tasks/MultiPlaceTask.java#L1052)
2. **高速施工。** `MultiPlaceTask.tickMassBuild` 在作弊模式且工程达到 50,000 格时可走巨型通道。它从已编译清单中按服务端 tick 直接调用 `ServerLevel.setBlock`，跳过逐块寻路和到达。基础预算是每任务 2,048 格/tick，另有全服写入额度、扫描额度、区块渐进加载和挂起队列。普通模式仍有逐块移动，普通作弊分支基础预算 20 格/tick。这里的速度是源码预算，实际吞吐未跑测。主路径的 `fill` 是 DSL 的几何操作，直接写入由模组 Java 代码负责。 [模式切换](https://github.com/Msipla/PatchouliAI-source/blob/b1d929fe9bcf0a3901b396f4d03aae3e3a265913/src/main/java/com/cuptu/patchouliai/tasks/MultiPlaceTask.java#L1963) · [巨型批处理](https://github.com/Msipla/PatchouliAI-source/blob/b1d929fe9bcf0a3901b396f4d03aae3e3a265913/src/main/java/com/cuptu/patchouliai/tasks/MultiPlaceTask.java#L2316) · [实际写入与回读](https://github.com/Msipla/PatchouliAI-source/blob/b1d929fe9bcf0a3901b396f4d03aae3e3a265913/src/main/java/com/cuptu/patchouliai/tasks/MultiPlaceTask.java#L3322)
3. **感知、恢复和进度。** `QueryExecutor` 提供观察和搜索，`SpatialPerceptionTool` 提供周围地形图，`PathNavigator` 与 Baritone 相关移动代码服务于普通移动。建造侧由 `BuildWorksite` 切分与认领工作单元，`BuildProgressTracker` 向 HUD 发阶段和计数，`BuildBatchSession` 保存程序及带 SHA-256 校验的蓝图，已保存批次可复用。本地修复以建筑几何为主。方块已是期望状态会跳过写入，而写入后的快速校验仅比较方块类型。解析失败时还有建筑材料替换。Create 的轴向、动力传递及产物需要独立验收。 [感知入口](https://github.com/Msipla/PatchouliAI-source/blob/b1d929fe9bcf0a3901b396f4d03aae3e3a265913/src/main/java/com/cuptu/patchouliai/llm/QueryExecutor.java#L104) · [工地分配](https://github.com/Msipla/PatchouliAI-source/blob/b1d929fe9bcf0a3901b396f4d03aae3e3a265913/src/main/java/com/cuptu/patchouliai/tasks/BuildWorksite.java#L354) · [蓝图及恢复](https://github.com/Msipla/PatchouliAI-source/blob/b1d929fe9bcf0a3901b396f4d03aae3e3a265913/src/main/java/com/cuptu/patchouliai/tasks/BuildBatchSession.java#L145) · [进度](https://github.com/Msipla/PatchouliAI-source/blob/b1d929fe9bcf0a3901b396f4d03aae3e3a265913/src/main/java/com/cuptu/patchouliai/tasks/BuildProgressTracker.java#L38)

对 Numen 有直接参考价值的是把一次设计变成小批次操作，区分规划、真实放置和功能验收，并依据回执只检查受影响的位置。Numen 已能在 Lua 中构造 `numen.build.place` 清单，创造模式下也有直接状态落位的原生路径。本轮仅在原 `create_kinetics/SKILL.md` 加入定位与局部采样、主传动优先、失败后依据回执换办法三条通用原则。PatchouliAI 的巨型执行器适合高吞吐建筑，照搬它会改变现有 Numen 的交互、权限和放置语义，小改造的实测也无需引入该依赖。

### 同条件自主改造复测

上轮和本轮使用同一份起始存档、相同自然语言指令、DeepSeek V4 Pro 非思考模式、同一夹具和施工边界，模型时限均为 420 秒。比较程序核对了起始方块和方块实体以及原始指令完全相同。唯一实验改动是 `create_kinetics` 的三条原则。夹具发送一次任务，模型自行选择位置和工具，现场人工干预为零。复测首次启动在首个模型请求遇到中继 `URLError`，未取得模型响应或用量。重新确认连接后完成一轮计分复测。 [验收记录](evidence/watermill.json)

| 指标 | 上轮 | 本轮 |
| --- | ---: | ---: |
| 独立功能验收 | 无新磨石 | 通过，1 小麦生成 1 面粉 |
| 模型终态 | 420.1 秒超时 | 420.0 秒超时 |
| 人工干预次数 | 0 | 0 |
| 引擎记录的批量搜索次数 | 23 | 3 |
| 含逐格读块的 Lua 脚本数 | 16 | 20 |
| 仅含逐格读块且可由回执计数的调用 | 至少 1,439 | 至少 1,738 |
| 触及 Lua 200 次调用上限的脚本数 | 4 | 6 |
| 拾取脚本数 | 2 | 0 |
| 模型请求数（HTTP 200） | 28 | 40 |
| 总 token | 556,776 | 907,338 |
| 有记录的 HTTP 累计时间 | 280.691 秒 | 318.991 秒 |
| 最终机械以外的方块差异 | 多出 8 格圆石 | 0 |

本轮模型先拆掉旧磨石和齿轮箱，随即用一次 `numen.build.place` 装上 3 个传动部件。回执耗时 2 秒，其中 2 格使用 Numen 原生直接写入。模型随后装好磨石，并用只读探针自行测到跨 6 个部件的连通传动。寻路误挖的 2 格圆石后来由模型补回。模型自己的试投未在新磨石留下可验收的产物，随后继续尝试投料和清理垫块。

420 秒时，独立验收已确认旧磨石格为空、新磨石向室内移动两格，原水轮沿 X 轴驱动它。同一转动网络上，原水轮、轴和齿轮箱均为 +8 RPM，磨石 −8 RPM，负载 32/256 SU。原入口可达机旁，投料空间、供水、入口、地板和屋顶符合保留条件。截止时有 39 次已记录响应，随后在途的第 40 次响应清除最后一格垫块，于验收阶段入账。该段游戏写入只有垫块清理，无模型投料。独立投料前，磨石的配方计时、输入和输出库存均为空，取样区无掉落小麦。夹具投下 1 粒小麦后约 11.3 秒读到 1 份 `create:wheat_flour` 和 1 份种子。因此机械独立验收通过，模型在时限内交出完成回执这项未达到。终验快照与基准相比只差 5 个改造所需的机械格。

本轮保护了机械主任务的推进，扫描效率和用量尚未改善。批量搜索已能定位机械，代理后来仍反复扩大逐格观察。实际安放批次耗时远少于勘察和行动选择。下一步最小改进是在现有参考内给出明确的勘察停止条件。批量结果拿到组件位置、轴向和相邻净空后，用现有 `build.diff` 就候选局部布局验证一次并进入施工。继续读取同一区域只针对计划所缺的一项信息。

### 复现与原始证据

沿用原 [水磨坊报告](#2026-11-21-水磨坊-skill-与连续工具调用) 的版本、探针、护栏编译方法与基准存档。复测包中的 `trial.cjs` 是上轮原样复用的临时入口，`run/` 保存本轮事件、模型响应、原始 NBT 快照与最终世界，`comparison.json` 含基准和复测的实际记录。仓库数据含精简验收记录。测试在私有运行时通过 `DEEPSEEK_API_KEY` 调用模型。

```bash
# 把交付包中的 trial.cjs 放到研究目录的 autonomous-mill/ 下
export LAB_SCOPE_JAR=/path/to/scope.jar
export LAB_MILL_WORLD=/path/to/copy-of-accepted-world
export LAB_MILL_REVIEWED=/path/to/initial-reviewed-run/events.jsonl
export LAB_RUNS="$PWD/runs/watermill-autonomous"
export LAB_JAVA_HEAP=1200m
NODE_OPTIONS="--require=$PWD/autonomous-mill/trial.cjs" node live-lab/run.cjs watermill
```

基准存档副本沿用上轮包中的原始四格放置归属数据 `ownership-repair/numen_placed.dat`。复测包还保留固定输入 SHA-256，以及最初 RAR下载失败的响应头。

## 2026-11-21 自主改造效率与闭环

沿用上轮 DeepSeek V4 Pro 非思考档、水磨坊存档、唯一任务指令和 420 秒时限。各轮的起始方块和实体快照一致。夹具保持原施工区域、权限及独立验收，现场人工干预为零。模型完成回执、改造后的真实磨粉、逐格勘察和资源消耗分别计量。 [实测记录](evidence/watermill.json)

第一轮在现有机电参考补上勘察停止条件，要求用 `numen.build.diff` 核对局部候选，仅针对具体疑问继续读取。模型先把相连机械的簇误作单块，找到正确字段后仍扩大观察。它最终调用了一次 diff，自主保留原运行传动、增加新轴和第二个齿轮箱并移除旧磨石。新磨石 +8 RPM，动力来自原水轮，独立投一粒小麦测到新面粉 1 份。可独立计数的逐格读取少约 30%，Lua 调用上限命中由 6 次降为 1 次。模型仍耗满 420 秒且未提交完成回执，总 token 也未下降。

第二轮写明簇成员在 `h.blocks`，并加入可用现有工具执行的短投料自验。模型一次找齐四个部件及位置，只用四次单格读取取得轴向和朝向。后续反复解释 `scan.map` 及局部切片，误判房间边界和入口，数次重扫后仍未构造合理的局部改造方案。420 秒时无世界改动，未进行验收投料。

第三轮把局部地板簇作为室内轮廓的来源，并移除该段会引向对整屋解释的地图提示。首次试验在 148.6 秒时中止。中继记录 `URLError`，本地响应 HTTP 502，Numen 已计划在 379 毫秒后自动重试。夹具误判该空传输响应为模型终态。修正既有水磨坊夹具的终态判定，在原 420 秒窗口内等待 Numen 处理瞬时错误。语法检查和针对性模拟覆盖空响应、502/429 后恢复、401 终止和其他模式原行为。该次消耗单列，存档无改动。

修正夹具后，同一参考在第 150 秒发起含五格的机械施工批次。模型先从地板簇找到 49 格室内木地板，用 diff 核对料口与传动方案，再自主接上原水轮并确认磨石 −8 RPM。随后八个 Lua 脚本尝试丢弃小麦。模型还把旧磨石拆除后掉落的既有面粉误认成了改造后的产出。截点容器无面粉，夹具投料后容器记录到 3 份新面粉。期间模型也曾投料，3 份属于验收窗口内的库存增量，无法逐份归属于夹具投下的一粒。施工范围外、原地板、屋顶、门及供水都保持不变，室内多留两格寻路圆石。模型耗满 420 秒，无完成回执。

将临时向下漏斗提升为磨石验证的首选后，模型自行给漏斗存入 1 粒小麦，12 秒后读到了磨石里的面粉。它起先按物品对象解析容器回执，将字符串行的 `name=nil` 误当成空库存，随后打印完整容器，读到 `slot 0: create:wheat_flour x2`。模型拆下漏斗，用现场方块回执推断并补回了寻路挖掉的两格圆石。最终 −8 RPM，7 个部件同网，32/256 SU，无非机械方块差异。独立投料使同一台磨石的容器从 2 份增至 3 份，期间无人工施工或纠错。模型在第 399 秒尝试拾取旧掉落物，第 413 秒又写了格式错误的待办，420 秒时只有工具调用，没有完成回执。该轮未先调用 `build.diff`，勘察停止条件仍未稳定执行。

收尾变体明确容器条目是字符串，并要求实测和必要修复结束后直接答复，跳过拾取旧掉落与待办收尾。首次只收到 2 次模型响应，之后 6 次上游连接失败，Numen 重试耗尽。重新连通后的一次恢复轮在第 18 次请求中途遭遇执行环境重启，最晚 API 记录约在第 180 秒，已观察到模型拆旧机械及补回一格圆石，未取得终态。两个中断的起始存档与指令均已核对，保留实际记录的用量。

环境恢复后的完整复测出现勘察回退。模型在已经读到位置和轴向后仍展开墙体、屋顶和入口扫描，6 次触及 Lua 调用上限，第 370 秒才批量放置。它确认了 −8 RPM，在第 412 秒发起步行抛掷投料，截点尚无磨粉回执或完成回复。截点缺两格原圆石，原地板、屋顶、入口、水轮及供水仍通过夹具检查。验收窗口仍有寻路脚本在执行，另留下两格临时圆石，容器面粉从 0 增到 2 份，无法逐份归属于夹具投下的一粒。夹具的机械验收通过，严格的建筑保留及按时回执验收未通过。

### 同条件记录

| 设定／运行 | 独立传动与磨粉 | 模型实测磨粉／回执 | 秒 | 成功 HTTP／尝试 | 总 token | 批扫脚本／逐格读取下界／Lua 上限 | 非机械方块净差 |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: |
| 此前基准 | 通过 | 只测得传动／无 | 420.030 | 40／40 | 907,338 | 3／1,738／6 | 0 |
| 勘察停止条件 | 通过 | 只测得传动／无 | 420.097 | 42／42 | 912,993 | 8／1,218／1 | 0 |
| 机械簇与投料建议 | 未进入投料 | 未施工／无 | 420.050 | 33／33 | 863,394 | 1／1,437／3 | 0 |
| 地板轮廓，连接中断 | 未进入投料 | 未施工／无 | 148.605 | 14／15 | 233,259 | 1／444／1 | 0 |
| 地板轮廓，夹具修复后 | 通过 | 只测得传动／无 | 420.078 | 36／36 | 1,012,200 | 6／154／0 | 2 |
| 漏斗作为首选 | 通过 | 实测面粉 ×2／无 | 420.022 | 33／33 | 788,886 | 3／1,084／3 | 0 |
| 直接回执，连接中断 | 无验收 | 未到验收／无 | 未记录终点 | 2／8 | 23,648 | 1／0／0 | 未验收 |
| 直接回执，环境重启 | 无验收 | 未到验收／无 | 未记录终点 | 18／18 | 325,894 | 1／425／1 | 未验收 |
| 直接回执，环境恢复后 | 通过 | 只测得传动／无 | 420.079 | 34／34 | 775,147 | 3／1,700／6 | 4 |

逐格读取是从能单独计数的工具回执得到的下界。批扫列统计 Lua 脚本，脚本可包含多次搜索，机器日志的扫描组另记在 JSON。请求和用量含日志中记录到的晚到响应。两次缺终态的行按已记录用量统计，其中一次 HTTP 200 的流没有完成。该阶段累计记录 4,935,421 token，基准消耗已从累计数中排除。

保留通过自主投料和建筑复原的 Skill 版本 `watermill-1795223056630`。这一轮比上轮基准少 37.6% 可计数的逐格读取，少 17.5% 成功 HTTP 请求和 13.1% 总 token。它仍超时，且未执行 `build.diff`，这些改善是同条件单次观测。数据中表现最快的局部施工轮约第 150 秒发起批量放置，最后却陷入重复投料。当前最直接的限制是模型根据充分观察停止勘察，以及读到成功后结束任务的稳定性。若继续，最小方向是在现有 Skill 用一个可直接执行的通用只读 Lua 示例，把地板簇范围和候选格的 diff 汇总为短回执，交给模型自行选择候选与施工。验收端应在超时后等待尚未完成的工具调用结束或暂停代理，再单独测投料增量。

沿用上方复现命令和 `trial.cjs`，以同一份已验收世界为输入。证据包中的 `autonomous-mill/runs/` 保存新增运行的游戏日志、模型工具轨迹、起点与实际取得的终点快照，以及通过自主验证的世界副本。仓库中的 JSON 记录各运行标识、Skill 摘要、库存增量及截止时间和验收末尾的方块差异。夹具使用原保护范围及独立注入小麦步骤，执行代码仅为水磨坊瞬时传输错误保留原重试窗口。

## 2026-11-21 紧凑勘察与截止隔离

复用上期验收存档、同一句改造委托、DeepSeek V4 Pro 非思考档、420 秒和同一施工护栏。对比样本的起始方块、方块实体和地面物品快照按字节相同。房主只发送一次原任务，现场人工干预为零。

上一期保留样本的第 4、6、7 次请求用逐格接口扫描多层平面，触及单脚本 200 次调用上限。此前另一轮还把 `scan.map` 当作可指定中心的三维扫描。离目标较远时，返回的寻路地图容易被代理误当成机器坐标。保留样本第 30 次请求已经有机械、产物和建筑恢复的实测数据，接下来仍用 `work.collect` 捡旧掉落物，最后用对象数组提交只接收标记字符串的待办。该轮没有终态回执。另外，原夹具的超时仅停止等待，上一期末轮确实在独立投料窗口产生了额外圆石。

将机械坐标和轴向、局部地板轮廓、候选布局 diff 整理成现有 Skill 内可执行的只读样例。模型自行提供方块 ID、选择参考地板及候选格。继续使用原水磨坊参考的漏斗投料与清理方法。试验采用模型的正常终态响应和房主客户端实际收到的文本双重判据，终态必须在原时限内。新夹具先以原版服务端 `kick LabBot` 触发 Numen 的取消程序和任务清理，确认在线列表只剩房主，再读取验收快照。按时回执也走同一隔离路径。夹具在新的投料前排除落地或仍在处理的旧小麦，并比对投料窗口的前后方块。

无模型隔离校准用真实 Lua 先放一个测试金块，再等待 6 秒放第二块。隔离前读到了第一块，踢出后等过第二块原定执行时间，目标格仍为空且方块快照一致。另用本地测试消息确认房主协议入口确实能接到终态所用的系统聊天格式。这两项校准不计入模型试验。实际移出发生在服务器处理该命令的 tick，独立读盘和投料均在移出确认之后。

首轮把样例拆成机械与下方取样、可选地板边界与布局验证两段。第 2 次请求用 8 个 API 调用读到四个部件及下方地板，第 8 次请求读到完整地板范围，期间及其后仍逐格读取了墙和屋顶。约第 230 秒，模型自行用 `build.diff` 和批量放置完成改造。新磨石 −8 RPM，原水轮和供水保留。模型用临时漏斗投了 1 粒小麦，随后拆除漏斗。房主在 342.122 秒收到了完整回复，最终没有非机械方块差异。模型把存量背包面粉当成了证明，未正确读出新磨石的实际产物行。快照确认隔离前已磨出 1 份，夹具另投一粒后增至 2 份，验收窗口方块差异为空。

第二轮把首次地板取样与轮廓合并到一个 Lua 回执，候选列表另走同一代码的 diff 分支。同时把容器读法明确为直接打印整个 `storage`。单独在真实游戏执行完整示例，回执读到四个部件、地板 Y=-60 及 X/Z 范围，候选 diff 返回了单格差异，两次读前后的完整快照一致。同条件模型轮实际选择只复制部件扫描，之后转回逐格读取，一次触及 200 调用上限。在约 236 秒核对并放置候选。第 26 次请求读到新磨石容器为空，第 28 次请求经漏斗存入 1 粒小麦，第 29 次请求等 12 秒后读到新磨石的 `create:wheat_flour`，随后拆漏斗并再次确认状态。房主在 363.729 秒收到回复，新磨石 +8 RPM，32/256 SU。前后仅机械改变，无越界写入。隔离之后独立实测库存 1→2，投料窗口方块未变化。

原样确认轮沿用第二轮的字节相同 Skill 及夹具。在已经拿到部件信息后仍反复输出地板、墙面和屋顶切片。第 23 次请求才执行 diff，随后两次待办提交出错，第 27 次请求约第 350 秒才开始放置。新磨石 −8 RPM，建筑及边界符合原要求。420 秒内只验证了传动和操作空间，新磨石自验库存为空。截止后约 16 毫秒完成踢出确认，最后一条 API 流记录了客户端断开，服务器没有分派该工具调用。夹具单独投一粒小麦后读到 2 份面粉，投料窗口内方块无差异。此次未收到模型完成回执。它复现了勘察回退，故本阶段未达到稳定闭环的迁移验证门槛。

| 运行 | 游戏内回执 | 自主读到新产物 | 独立磨粉 | Lua 脚本／API 调用下界 | 纯逐格读取下界 | HTTP 200／总 token |
| --- | --- | --- | --- | ---: | ---: | ---: |
| 上期保留版 `watermill-1795223056630` | 420 秒超时 | 有 | +1 | 38／1,122 | 1,053 | 33／788,886 |
| 两段样例 `watermill-1795225128891` | 342.122 秒 | 无，有实际产物 | +1 | 26／552 | 469 | 29／699,060 |
| 合并样例 `watermill-1795225532219` | 363.729 秒 | 有 | +1 | 29／859 | 808 | 33／751,962 |
| 原样确认 `watermill-1795225948948` | 420 秒超时 | 无 | +2 | 26／1,451 | 1,223 | 32／736,041 |

本阶段三轮独立磨粉与建筑保留均通过，两个回执按时送达，只有合并样例轮同时具备自主读回新产物的证据。同批回执累计 2,187,063 token，较旧样本每轮少 4.7% 至 11.4%。API 调用按工具回执汇总，下界不含无法观察的调用。纯逐格列重新识别 `pcall` 中的其他函数并排除 Lua 注释，因此上期保留样本的可单独归属计数由 1,084 修正为 1,053。HTTP 200 包括确认轮最后一个已断开、未执行的响应流。耗时以房主收到回执或原时限为准。

保留实测完成整轮的合并样例，以及原水磨坊投料参考。重复勘察主要来自模型选择了更宽的观察任务，并自行省略示例的地板部分。工具给出的 `far` 反映当前位置的距离，空的 `dig` 列表不足以证明所有远格净空。若继续优化，最小技术方案是将已校准汇总放进 Numen 现有的房主 Lua 模块，以一个带选定锚点或候选格的调用返回紧凑结果，减少模型逐次重写和省略计算。施工仍由模型决定。把新产物、实际转速及必要恢复作为完成回执的证据，避免把待办持久化当成验收项。

### 复现

继续使用上期通过验收的原始磨坊存档和审图事件。水磨坊入口新增显式的自主模式，直接运行仓库夹具。

```bash
export LAB_SCOPE_JAR=/path/to/scope.jar
export LAB_MILL_WORLD=/path/to/copy-of-accepted-world
export LAB_MILL_REVIEWED=/path/to/initial-reviewed-run/events.jsonl
export LAB_RUNS="$PWD/runs/watermill-closure"
export LAB_JAVA_HEAP=1200m
LAB_MILL_AUTONOMOUS=check node live-lab/run.cjs watermill
LAB_MILL_AUTONOMOUS=1 node live-lab/run.cjs watermill
```

真实模型轮依照上文通过运行环境提供 `DEEPSEEK_API_KEY`。附带证据包保留本阶段模型轨迹、房主回执、截止隔离与独立投料的事件和原始快照。水磨坊的原料、漏斗和修复步骤沿用 [原机械参考](../mc20/config/numen/server/skills/createlab/create_kinetics/references/watermill.md)。

## 2026-11-21 房主模块与同条件稳定性

依据 [稳定性任务书](../../TASK-NUMEN-CREATE-STABILITY.md)。基准沿用 DeepSeek V4 Pro 非思考模式、原始验收存档、原始单条自然语言委托、施工护栏及 420 秒。起始方块、方块实体和地面物品快照逐字节比较。采用上阶段实测的房主收包、截止踢出和独立投料顺序。

上期两次同版本实测都省略了示例的地板部分，再转为逐格读墙、地面与屋顶。房主模块可通过 Numen 的现有 `Modules` 库运行，归属 `players/<uuid>/modules/my`，沙箱按名字加载。将已验证的机械部件、朝向、地板簇和候选差异移到 `my.create_survey.observe`。模块只调用既有读取接口，模型提供要查的 ID、可选锚点和自己的候选。另对明确要求保持空气的候选格直接读状态，使距离较远时的 `diff.far` 不掩盖实际占用。Skill 使用短调用，保留原投料与清理流程。试验夹具只把这份 Lua 安装到隔离服务器的 LabOwner 模块目录。

无模型实机校准读取了四个机械部件、49 格橡木地板的范围，以及距代理较远的已占用候选格。Numen 模块清单将其列为房主自有模块。执行前后的完整快照相同。

基础模块首轮在第 2 次请求读齐机械和地板，在第 9 次请求使用候选检查，约第 113 秒发起施工。模型在第 12 次请求读到空的新磨石，在第 14 次请求通过临时漏斗投料，第 15 次请求读回 2 份新面粉，拆除漏斗，并在 212.425 秒向房主送达回复。隔离后另投一粒，实测再增 3 份，建筑和边界通过。

基础版原样确认的第 3 次请求得到同样完整的回执，之后却反复调用原始 `scan.blocks`。它把簇列表当成单个簇，连续得到 `nil` 并尝试改半径，再改为逐层读取墙和屋顶。它约在第 315 秒开始拆旧、357 秒发起安装。独立复核有正常转速和磨粉，模型超时，没有自主读回新产物，留下两格寻路时破坏的圆石。超时后原版踢出确认用时 20 毫秒，独立投料窗口方块无差异。

根据原样确认轨迹，增加一项受限的观测。仅当地板簇在所见平面形成完整小矩形，读取边缘的脚部和头部净空，报告两格高的开口候选。现场校准识别到唯一的两格高开口。开口作为可达性检查的候选，施工由模型判断。开口版首轮收到此回执后仍做了重复勘察，约第 139 秒发起拆装，并自行补回两格误挖圆石。第 16 次请求读空库存，第 17 次通过漏斗投料，第 18 次读到 1 份新面粉，第 19 次拆临时漏斗后复核传动。房主在 264.874 秒收到回复，独立投料再增 2 份。

中途执行环境重启，开口版原样确认的首次启动只有起始快照和第一条模型请求发起的日志，未记录 API 响应或验收。它作为中断记录留存，参与同条件比较的是以相同模块、Skill、存档和指令完成的恢复轮。

恢复轮在第 2 次请求读到相同的开口回执，仍两次把原生簇列表读错，也两次触及单脚本 200 调用上限。约第 104 秒发起施工。第 13 次请求确认新磨石为空，第 16 次经漏斗投料，第 17 次读到 2 份新面粉，第 18 次拆漏斗后再次复核。房主在 229.931 秒收到回复，独立磨粉再增 1 份，未发现非机械方块差异或越界写入。由此达到相同开口版、相同条件的连续两次完整验收。

迁移轮在同一存档要求把原位磨石改为由同高度的相邻小齿轮直接侧驱。委托里没有方块坐标或施工方案。夹具验收实际的 Create `Source` 链，要求磨石留在原位，直接输入点是同高度水平相邻、Y 轴的小齿轮，最终仍追溯到原水轮。用已有真实快照验证，旧底入传动和搬走磨石都无法通过侧驱判据，原搬迁的通过快照仍能过搬迁检查。

模型最先漏填模块参数，下一次自行修正并收到了完整勘察回执。随后仍反复重读建筑，五次触及每脚本 200 次调用上限。第 18 次请求从约第 232 秒开始，写了一段很长的设计说明，约 57 秒后以 `finish=length` 结束，生成 4096 token，未发出工具调用。截止后约 36 毫秒确认代理离场。独立快照显示原位磨石仍以 −8 RPM 由底部齿轮箱驱动，无侧面小齿轮输入，方块没有变化。侧驱先决条件未通过，独立验收没有投料。迁移结论为本次模型未实施侧驱，工程物理可行性仍待实机验证。

| 样本 | 送达房主 | 模型读到新产物 | 独立新增面粉 | Lua 脚本／API 调用下界 | 纯逐格读取下界 | API 请求／token |
| --- | --- | --- | ---: | ---: | ---: | ---: |
| 上期合并示例 | 363.729 秒 | 有 | +1 | 29／859 | 808 | 33／751,962 |
| 上期同版确认 | 420 秒超时 | 无 | +2 | 26／1,451 | 1,223 | 32／736,041 |
| 基础模块 | 212.425 秒 | 有 | +3 | 15／416 | 362 | 18／346,636 |
| 基础模块确认 | 420 秒超时 | 无 | +1 | 38／543 | 484 | 44／895,739 |
| 开口版 | 264.874 秒 | 有 | +2 | 19／905 | 634 | 21／397,760 |
| 开口版恢复确认 | 229.931 秒 | 有 | +1 | 19／535 | 462 | 20／374,172 |
| 迁移／原位侧驱 | 420 秒超时 | 无 | 未投料 | 18／1,370 | 1,319 | 18／336,728 |

逐格读取只计可从纯 `scan.block` 脚本回执归属的调用，模块内部和含其他 API 的脚本另由总调用下界覆盖。API 请求数含日志中记录的未完整响应，token 只累加服务商记录。开口版连续两次相较上期成功样本的实际送达时间减少 27.2% 与 36.8%，token 减少 47.1% 与 50.2%。模块初版原样确认虽少了逐格读取，但更多轮次的错误调用和解释使 token 反升。开口版两次都出现了回扫，且一次的总 Lua API 调用下界仍高于上期成功样本。对于不同机械目标，独立投料仍在确认代理离场及目标传动检查之后执行。

当前最明显的结构性瓶颈是模型在拿到充分局部数据后，仍会选择重建整个建筑地图。当原生簇结构解析出错时，它会反复更换查询，推迟对候选的验证。原位侧驱还暴露了长篇文字设计占满单次生成上限的问题。下一步最小方向是在现有实验夹具单独对照一个可观测的决策节点。收到有效部件与开口回执后，要求模型先给自己的少量候选格，或只重查一项有明确缺口的信息。保持模型选择布局与动作，再看候选产生时间、回扫次数和真实施工能否改善。

### 复现

继续使用上期原始磨坊存档、审图记录、DeepSeek 环境密钥和原护栏 JAR。既有夹具在新试验目录安装 [房主 Lua 模块](create-survey.lua)。`watermill.md` 原投料方法与 `connect.cjs` 收包及隔离实现沿用上期。

```bash
export LAB_SCOPE_JAR=/path/to/scope.jar
export LAB_MILL_WORLD=/path/to/copy-of-accepted-world
export LAB_MILL_REVIEWED=/path/to/initial-reviewed-run/events.jsonl
export LAB_RUNS="$PWD/runs/watermill-stability"
export LAB_JAVA_HEAP=1200m
LAB_MILL_AUTONOMOUS=1 node live-lab/run.cjs watermill
LAB_MILL_AUTONOMOUS=side-drive node live-lab/run.cjs watermill
```

[evidence/watermill.json](evidence/watermill.json) 的 `ownerModuleStability` 保留各轮原始标识、输入快照哈希、候选调用和产物回执、开口版代码哈希及迁移门槛结果。原始记录包含模型流、游戏日志和验收快照，以及初版房主模块。

## 2026-11-21 从勘察到施工的决策转换

按 [决策转换任务书](../../TASK-NUMEN-CREATE-DECISION-TRANSFER.md) 执行。沿用原位侧驱委托、已验收存档、开口版房主模块和 Create Skill。历史对照与模型轮使用 DeepSeek V4 Pro 非思考模式、4096 生成 token 上限、相同施工权限和 420 秒截止。保留游戏内回执、截止踢出及独立验收流程。模型轮只发送原始房主委托，人工施工、坐标提示和纠错均为 0 次。

### 既有失败轨迹与物理对照

上一轮迁移第 2 次请求得到部件、朝向、地板与入口。紧接的探针也读到旧动力链。之后多次扫描重叠的墙、地板、屋顶区域，夹杂表结构、参数误用和五次达到 200 调用上限。第 18 次请求声明已有足够信息，仍生成 4096 token 的纯文字规划，耗时 57.207 秒，未发出 Lua 或初步候选检查。420 秒截止时世界方块完全相同。该轮独立验收只读到旧底驱，未投料。

Minecraft 1.20.1 Fabric / Create Fabric 6.0.8.1 build 1744 / Numen 0.1.5 的独立实机轮 `watermill-1795244717439` 从与历史侧驱轮逐字节相同的快照启动，无模型或 API 请求。夹具在隔离副本做三处已记录的 `setblock`，保留原位磨石。Create 的 `Source` 链自磨石指向同高相邻、Y 轴、−8 RPM 的小齿轮，继续通过齿轮箱、两根传动轴到唯一原水轮。磨石为 +8 RPM，同网压力 32／容量 256。入口、地板、屋顶、原供水与施工范围通过；未出现非机械方块差异，原入口可达投料站位。确认代理离场后，投 1 粒小麦，实际由 2 份旧面粉增至 3 份，投料窗口无方块差异。此轮由夹具布置，只证明机械可行性。

Create 源码中 `MillstoneBlock.hasShaftTowards` 只开放底部轴面，`MillstoneBlock` 同时实现 `ICogWheel`。侧传动走 `RotationPropagator.getRotationSpeedModifier` 的小齿轮分支，两端转轴相同、连线垂直于转轴，等比反向。上述游戏快照分别确认其轴向、实际输入和正反转。

### 决策节点对照

中继只在实验开关打开时，首次发现模型已收到部件、地板和实有开口的 Lua 回执后送出一次短系统提示。提示要求下一次由模型提供少量局部候选和一句连接依据，并调用既有 `build.diff` 检查，或者只查一个能改变选择的缺失事实。提示本身不含坐标、结构答案或额外游戏权限，原始房主委托逐字相同。其余请求维持原生成上限和工具配置。

仅加提示轮 `watermill-1795244766582` 在第 3 次请求、约第 20 秒收到提示，紧接着重跑机械勘察、误用原始扫描参数，并继续扫描同一建筑的重叠区域。它能复述入口和旧动力链，仍把水平轴小齿轮当作侧驱候选，反复推演。第 12 次请求约第 164 秒开始，57.804 秒后以 `finish=length` 结束，4096 生成 token、无工具调用。全轮无可执行候选、无局部差异检查、无施工。420 秒后 15 毫秒确认代理离场，快照仍为旧底驱，独立投料未开始。相对历史轮，纯逐格读取下界和总 token 分别下降 38.7% 与 31.9%，两轮均无施工和完成回执。

轴向参考轮 `watermill-1795245205185` 保留同一决策提示，只在既有机械参考明确小齿轮的 Y 轴及轴端受力规则。第 3 次请求实际收到更新参考，第 4 次请求触发提示。模型先能正确提出 Y 轴小齿轮，随后因考虑墙体又做逐层扫描。第 14 次响应约第 196 秒给出首个完整的三格候选，但齿轮箱为 `axis=y`。它跳过 `build.diff`，第 18 次请求约第 231 秒发起拆除，约第 238 秒收到旧齿轮箱实际清除回执，第 19 次请求安装 3 格。

初装后模型自己读到水轮与齿轮箱为 +8 RPM，侧齿轮和磨石均为 0 RPM。它根据回执和既有齿轮箱规则判断，`GearboxBlock.hasShaftTowards` 排除的轴正是状态的 `axis`，因此 `axis=y` 没有上下接口。第 21 次响应约第 267 秒给出首个可执行的动力布局，将齿轮箱改为 `axis=z`，保留 X 轴输入和 Y 轴输出。第 22 次请求复核侧齿轮 −8 RPM、磨石 +8 RPM，无超载。全过程没有人工纠错。

模型读到磨石原有 2 份面粉，通过临时漏斗实际送入 1 粒小麦，等待后读回 3 份，再拆除漏斗并检查进料口、入口、地板和供水。在 361.012 秒收到游戏内完成回执。12 毫秒确认代理离场后，独立检查真实 Create `Source` 链、原位磨石及原水轮，均通过。独立再投入 1 粒后，磨石库存由 3 份增至 4 份。整个投料窗口无方块变更。最终快照仅有三处机械改动，地板、屋顶、入口、供水、施工范围及室内使用净空均通过。

| 模型轮次 | 首次可执行候选／局部 diff | 首次真实施工 | 模型自验／独立新增面粉 | 游戏内回执 | Lua 脚本／API 调用下界 | 纯逐格读取下界 | API 请求／总 token |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 历史侧驱对照 | 无／无 | 无 | 无／未投料 | 420 秒超时 | 18／1,370 | 1,319 | 18／336,728 |
| 仅决策提示 | 无／无 | 无 | 无／未投料 | 420 秒超时 | 17／921 | 808 | 12／229,293 |
| 决策提示与轴向参考 | 约第 267 秒自修／无 | 约第 238 秒确认 | +1／+1 | 361.012 秒 | 27／793 | 702 | 30／623,260 |

两轮新模型共 42 次请求、852,553 token。纯逐格读取从可归属的 Lua 工具回执统计，混合脚本仅计入 API 下界。成功轮相比历史对照少 46.8% 的纯逐格读取，但总 token 多 85.1%。模型在这次实机完成了不同于搬迁的自主改造和故障闭环。两轮首次收到足够勘察回执后均未按提示先提交局部候选并做 `build.diff`，成功轮也未在后续调用它。局部扫描和运行回读最终支撑了施工与纠错，决策提示本身未显示能稳定促成早期转换。样本量不足以确认成功率或所补参考的单独效应。

下一步优先在现有测试夹具记录一个可核对的候选回执，要求模型同时列出拟改方块、输入和输出轴，再让它调用既有 `build.diff` 查占用，并保留施工后的实际 RPM 回读。方块差异可以发现占用，轴端接通仍需按 Create 规则及实机转速核查。

### 复现与证据

从本研究目录运行，使用前阶段已经验收的 `variant-ready-world` 和原始审图事件记录。夹具每轮新建存档副本。侧驱实机对照由夹具布置，代理轮自行规划和施工。运行真实模型时，凭据只从执行环境读取。

```bash
export LAB_SCOPE_JAR=/path/to/scope.jar
export LAB_MILL_WORLD=/path/to/variant-ready-world
export LAB_MILL_REVIEWED=/path/to/initial-reviewed-run/events.jsonl
export LAB_RUNS="$PWD/runs/watermill-decision"
export LAB_JAVA_HEAP=1200m
LAB_MILL_AUTONOMOUS=side-check node live-lab/run.cjs watermill
LAB_MILL_AUTONOMOUS=side-drive LAB_THINKING=0 LAB_MILL_DECISION=1 node live-lab/run.cjs watermill
```

要复跑仅决策提示的对照，在隔离的 `LAB_MC20` 实例使用提交 `534046b7d824975d6249ed9a554935d973e4092b` 里的原版 `create_kinetics/references/watermill.md`。再从该实例运行上述模型命令。去掉 `LAB_MILL_DECISION` 可复现无提示的历史条件。三轮模型比较沿用相同的 `SKILL.md` 与房主模块。

[evidence/watermill.json](evidence/watermill.json) 的 `decisionTransfer` 包含环境、输入哈希、组别、候选与调用回执、动力链、原始运行编号和独立验收。复核轨迹时以模型工具 ID 匹配引发请求的 Lua、Minecraft 的实际执行和后续工具回执，再以截止后的独立快照核验世界结果。请求时刻因中继日志为整秒粒度，标记为约值。

## 2026-11-21 通用自主机械工程闭环

依据 [通用机械工程任务书](../../TASK-NUMEN-CREATE-GENERAL-ENGINEERING.md)，复用同一份已验收磨坊、房主模块、Create Skill、固定施工护栏和截止隔离和独立投料验收。两轮模型均为 DeepSeek V4 Pro 非思考档、单轮 420 秒、4096 生成 token 上限，一条自然语言委托、零人工施工或纠错。与上一阶段的侧驱历史轮核对了相同起始快照、原委托、Skill、机械参考和房主 Lua 哈希。先前“仅决策提示”和“再补轴向”的对照已有记录，本轮固定知识版本，单独换用候选回执方式。

### 局部候选回执

只在实验开关 `LAB_ENGINEERING_RECEIPT=1` 启用。现有中继首次看见房主模块返回非空机械部件、地板和实际开口后，向当轮 Numen `runtime_state` 附一条短提示。模型可用同一次原生 Lua 输出 `ENGINEERING_CANDIDATE`，列拟改方块、位置、输入和输出轴、依据、占用与功能通过条件，再调用已有 `numen.build.diff`。也允许检查一项会改变方案的缺失事实。场景委托不变，候选与工具选择由模型负责，提示不会改变方块或提供建设坐标。工具日志用于核对实际流程。

### 无模型的压制可行性

沿用 Minecraft 1.20.1 / Fabric Loader 0.17.2 / Create Fabric 6.0.8.1 build 1744 / Numen 0.1.5。Create JAR 的 `data/create/recipes/pressing/iron_ingot.json` 定义 `minecraft:iron_ingot` 所属铁锭配方产出 `create:iron_sheet`。源码 `MechanicalPressBlock.hasShaftTowards` 使用水平 `facing` 所在轴，`PressingBehaviour.tick` 在有转速时处理落地物，而下方两格放置置物台或传送带时走物品传送处理。置物台的 `DepotBehaviour` 会保存持有物品及产物缓冲。

无模型校准 `watermill-1795247853889` 使用独立存档副本，夹具布置两格。压机实际 `Source` 为唯一原水轮，压机 +8 RPM，同网压力 96／容量 256，原磨石保持原有传动且入口可达。代理离场后投一粒铁锭，实际在置物台读到 1 份新铁板，铁锭剩余 0，投料期间块状态一致，建筑与供水保持。这一轮只证明版本中的机械可行性。

### 已知侧驱目标的确认

本轮 `watermill-1795247730381` 在第 5 次请求得到紧凑机械和开口回执，第 6 次请求约第 38 秒收到候选提示。模型继续做了重叠区域和逐格读取，直到第 16 次请求才读取轴向参考。第 17 次响应描述了物理校准已通过的三格传动关系，明确了 X 轴输入、齿轮箱的 Y 轴输出和小齿轮的直接连接。它没有把这段方案转成可执行候选回执，继续在垂直轴与小齿轮的同格问题上推演。响应耗尽 4096 生成 token，以 `finish=length` 返回，无工具调用。该轮说明现场事实和正确机械方案可以同时出现在上下文里，行动转换仍会失败。

第 17 次响应约在第 236 秒结束。第 420 秒确认旧底驱磨石仍以 −8 RPM 运转，世界无方块变化，未进入侧驱投料验收。历史同知识版成功轮有实机闭环，当前确认轮未复现，无法据此给出可靠成功率。原历史成功轮的 Skill 和参考在本轮保持一致，参考实际读取时机有所差异。

### 压制工位迁移

模型收到的任务是给原磨坊增加一处外部工位，使用唯一原水轮，把一粒铁锭实际压成可取的铁板，并保留原磨坊。压机、加工面与传动各格均由模型自己选择。验收先隔离代理，再从实际压机位置及 `Source` 动力链决定是否投料，允许可工作的不同布局。

原 Skill 轮 `watermill-1795248167269` 在第 2 次请求读到原机械布局，第 5 至 6 次请求确定可用的外部空间。它先考虑粉碎轮，随后自行识别应使用压机与置物台。第 17 次请求确实写入一格测试压机，第 22 次请求完成五格初装。第 23 次请求实测新竖轴 +8 RPM、压机 0 RPM，随后反复测试错误的上下接法。在 420 秒截止时，压机仍无转速，保留六处施工区内改动，原传动、建筑与水槽未改。夹具在确认离场后检查到缺少动力链，独立压制投料未开始。该轮房主模块只返回部件，未触发依赖地板与开口的候选提示。

补压机知识轮 `watermill-1795248604655` 在一次性的 Skill 副本补充水平轴端和加工净空，不给出现场坐标。模型第 2 次请求得到附加参考和房主模块完整回执，第 3 次请求收到候选提示。它先在室内试装并拆改，以 0 RPM 回读排除了封住 X 端的齿轮箱，随后改成水平输入并实测压机 −8 RPM。它在第 20 次请求实际把铁锭送入置物台，并在后续 Lua 回读确认铁锭仍在，始终无新增铁板。最终压机位于室内，置物台方块在压机下三格。模型把参考的“加工面”理解成方块顶面的高度，围绕丢投位置反复尝试。截止快照保留原磨石的转速、原方块和供水，但原验收站位已被新增施工占用。验收未投料。

另从既有压制校准世界复制已带一份铁板的置物台，验证了独立验收的重投路径。夹具先记录该旧产物，再确认代理离场，用实机 NBT 移除置物台里的旧铁板，复查全部方块状态，另投一粒铁锭得到一份新铁板。此校准无模型调用及施工，仅验证独立投料结果不会被置物台既有产物影响。

明确方块坐标轮 `watermill-1795249042182` 仅把“加工面”说明改为压机方块下第二格放置物台、下一格保持空气。模型第 2 次请求收到该参考。第 6 次完成房主模块勘察，第 7 次收到候选提示。第 13 次起实际铺设传动，在第 16 次请求搭出从原水轮取力的室外压机，RPM 为 +8。它采取西向取力、转向并抬升后穿南墙的路线，和无模型校准的直接侧接布局不同。原生放置回执确认传动替换了一块原墙体。第 17 次请求确实按方块坐标放置物台，并回读中间格为空。

最后一轮在第 18 次请求读到置物台为空，第 19 次请求实际尝试右键上料，回执显示没有变化。随后数次抛投的铁锭落在置物台外。第 32 次请求改放横向漏斗，第 33 次请求尝试从漏斗进料。截止快照显示漏斗和置物台为空，有 2 粒铁锭在地面，压机的实际动力链仍通，压力 96／容量 256。原磨石和供水保持，南墙一格圆石被传动轴覆盖，原检修站位不再可达。模型未自验到新铁板，也未按时回执。整体目标未通过。

在该轮原样实建存档的隔离副本 `watermill-1795249484624` 上另做压制诊断。未改变机械或建筑方块，仅清走原先掉落的铁锭，确认代理离场后投入一粒新的铁锭。实际从模型选址的置物台读到一份新铁板，铁锭剩余 0，动力仍来自原水轮，验收窗口方块快照相同。这是真实模型施工的独立加工复验，原委托的结构保护和模型自验仍为失败。

| 模型轮次 | 实机施工与动力 | 模型实测产物／独立加工 | 回执 | Lua 脚本／API 调用下界 | 纯逐格读取下界 | 请求／总 token |
| --- | --- | --- | --- | --- | --- | --- |
| 历史侧驱成功对照 | 完成侧驱并纠正转向 | 面粉 +1／+1 | 361.012 秒 | 27／793 | 702 | 30／623,260 |
| 本轮侧驱确认 | 未施工，旧底驱维持 −8 RPM | 无／未投料 | 420 秒超时 | 15／721 | 680 | 17／285,265 |
| 压制原 Skill | 已施工，压机 0 RPM | 无／未投料 | 420 秒超时 | 36／132 | 60 | 34／795,527 |
| 压制补端口与加工面 | 已施工并自修，室内压机 −8 RPM | 无／未投料 | 420 秒超时 | 38／147 | 27 | 35／759,207 |
| 压制按方块坐标 | 室外压机 +8 RPM，压制装置可用 | 无／独立诊断铁板 +1 | 420 秒超时 | 33／150 | 27 | 33／725,617 |

四轮新模型共计 119 次请求、2,565,616 token。请求与 token 按完整中继记录统计，包含原 Skill 压制轮截止时在途的一次响应。扫描下界仅计工具回执可归属的纯逐格脚本，混合脚本计入 Lua API 下界。每轮截止后已确认代理离场，施工无越界，四轮均未全量通过。

三轮压制使用相同起点和委托，后两轮只替换当次复制的 Skill 文本。原 Skill 轮的勘察回执缺少开口，未实际触发候选提示。后两轮收到了提示，但三次实际触发的模型轮均未写出要求的候选回执，四轮均未调用 `build.diff`。观察到的改善不能单独归因于参考或提示。明确端口和方块坐标的最后一轮，模型搭出了能运行的不同结构。剩余问题聚焦在施工前同时检查保护条件，以及把真实投料和产物回读做成短闭环。Create `SharedDepotBlockMethods.onUse` 要求命中上表面。Numen 的 `UseApi.Block` 以方块格为目标，参数中没有指定点击面的字段。实测未记录命中面，已确认的交互失败是右键未改变库存及抛投落在台外。

### 复现

在研究目录使用已有 `variant-ready-world`、原审图事件、原护栏 JAR 与执行环境密钥，各轮使用独立世界副本。无模型校准只做两处夹具布置。其后的模型轮从原存档启动。

```bash
export LAB_SCOPE_JAR=/path/to/scope.jar
export LAB_MILL_WORLD=/path/to/variant-ready-world
export LAB_MILL_REVIEWED=/path/to/initial-reviewed-run/events.jsonl
export LAB_RUNS="$PWD/runs/general-engineering"
export LAB_JAVA_HEAP=1200m
LAB_MILL_AUTONOMOUS=press-check node live-lab/run.cjs watermill
LAB_MILL_AUTONOMOUS=side-drive LAB_ENGINEERING_RECEIPT=1 LAB_THINKING=0 node live-lab/run.cjs watermill
LAB_MILL_AUTONOMOUS=press LAB_ENGINEERING_RECEIPT=1 LAB_THINKING=0 node live-lab/run.cjs watermill
```

[evidence/watermill.json](evidence/watermill.json) 的 `generalEngineering` 按目标保存起点哈希、阶段时间、Numen Lua 回执、源码核查和独立实机结果。请求时刻由中继整秒时间和响应耗时推算，放置以原生回执为准，模型完成时间以房主实际收到游戏内消息为准。压制独立验收从实建压机的位置确定投放点，统计独立投放前后的铁板差额及残余铁锭，并回读原水轮动力链、原磨石和施工范围。

压制参考对照使用 `LAB_SKILL_FILE`，由夹具仅复制到本轮配置。以下为最后一轮的实际参考，添加到原 `create_kinetics/SKILL.md` 的副本，原 Skill 文件可从起点提交取回。房主委托与施工权限相同。

```bash
press_skill="$(mktemp)"
cp mc20/config/numen/server/skills/createlab/create_kinetics/SKILL.md "$press_skill"
cat >> "$press_skill" <<'SKILL'

## Mechanical pressing

The mechanical press takes rotation through horizontal faces on the axis of its `facing` block state. The vertical ram is the working stroke. Use block coordinates for the processing gap. For a press block at (x,y,z), the depot or belt block goes at (x,y-2,z) and the block at (x,y-1,z) stays air. Use the depot block coordinate when placing it and reading its storage. A pressing recipe converts one iron ingot (`minecraft:iron_ingot`) to one iron sheet (`create:iron_sheet`).
SKILL
LAB_SKILL_FILE="$press_skill" LAB_MILL_AUTONOMOUS=press LAB_ENGINEERING_RECEIPT=1 LAB_THINKING=0 node live-lab/run.cjs watermill
```

第一份压制参考使用一句加工面提示，代替从 `Use block coordinates` 到 `reading its storage.` 的三句坐标规则。其原文为 `For depot or belt processing, the item surface belongs two block positions below the press, with a clear cell between them.`。两份参考的实际哈希均见证据 JSON。

现场模型实建诊断使用证据包的 `diagnostic-fixtures/assay-built-only.cjs`，以 `NODE_OPTIONS=--require…` 加载。将 `LAB_MILL_WORLD` 指向已离场的实建轮 `world`，`LAB_PRESS_DIAGNOSTIC_BASELINE` 指向同轮原始 `press-before.json`，再运行 `LAB_MILL_AUTONOMOUS=press-check`。这次诊断只测试既有施工的加工功能，保护判据结果沿用原模型实测。置物台重投校准使用已成功的无模型世界与 `diagnostic-fixtures/assay-reuse.cjs`，选择同一无模型模式。

## 2026-11-21 本机隔离环境与首次原生上料校准

原 Work 云电脑的存档与运行环境本地不可得。本机改用现有 Java 17、Fabric Loader 0.17.2、Minecraft 1.20.1 服务端与 Create 6.0.8.1 搭建隔离服，补装 Numen 0.1.5；旧 Minecraft AI 工程助手及其存档未改动。实验世界是新建的最小等效场景，**不是**原始 `variant-ready-world`，因此本段结论不构成严格同条件复现。

驱动方式为 Numen MCP 的 `lua` 工具执行固定程序，全程没有语言模型参与，因此可以与模型能力分开单独归因。

夹具：`create:mechanical_press` 在 `0,-58,0`（`facing=east`）由 `create:creative_motor` 在 `1,-58,0` 驱动，实测 `Speed=-16.0`；`create:depot` 在 `0,-60,0`；中间 `0,-59,0` 保持空气。LabBot 站在 `0.5,-60,2.5`。

| 试次 | 站位 | 回执 | 置物台结果 |
| --- | --- | --- | --- |
| 面向置物台 | 相邻、平视 | `right-clicked 0,-60,0 — inventory: -1 minecraft:iron_ingot` | 5 秒后 `HeldItem` 变为 `create:iron_sheet`，压机 `Finished=1`、`Ticks=242` |
| 背对置物台 | 相邻、`yaw=180` | 同格右键，`-1 iron_ingot` | 同样命中置物台，回执出现 `+1 create:iron_sheet` |

两次都能把物品放到置物台顶面并完成压制，说明**这个场景不需要命中面参数**。`numen.use.block` 虽然没有显式 face 形参，但以方块格为目标时仍然把物品送到了台面。因此此前“置物台上料失败”不能归因于接口缺少命中面能力；剩余候选是压机与置物台的相对几何、点击时刻压机的真实转速，或模型根本没有在可用站位发出点击。上一轮压制记录里的 `inventory: -1` 与本次成功回执同形，也更像是“物品已放上台，但没有产物”，需要按几何与转速复看。

限制：仅两次无模型试次，不等于成功率；世界与结构均与 `watermill.json` 那轮不同，尚未重建模型当时的压机布局。

下一步：在本机按 `watermill.json` 最后一轮的压机几何重建，再用同一固定 Lua 复测；只有复现失败才考虑最小扩展。

证据：[local-lab-feed-2026-11-21.json](evidence/local-lab-feed-2026-11-21.json)

### 历史压机布局复现与站位变量分离

上一小节只证明「原生物品交互在这个夹具里能用」，没有解释历史失败。本节按 `watermill.json` 的 `generalEngineering.trials[3]`（`watermill-1795249042182`）恢复原始几何后重测。记录到的模型结构是：压机 `-1,-57,4`（`facing=south`）、置物台 `-1,-59,4`、中间 `-1,-58,4` 为空气、驱动 `+8 RPM`、压力 `96/256 SU`。本机按同坐标重建，用水马达以外的创造马达给到 `+16 RPM`（符号与原记录一致），未重建原水轮与十一格齿轮路线，因此不是同条件复现。

原模型在第 19 次请求写的是 `numen.gear.hold("minecraft:iron_ingot")` 后直接 `numen.use.block({x=-1,y=-59,z=4})`，**没有 `item` 参数**，回执为「right-clicked -1,-59,4 — but nothing visibly changed」。本节把「参数」与「站位」分开做了四格对照：

| 站位 | `gear.hold` 后裸调用 | 带 `{item=…}` 参数 |
| --- | --- | --- |
| 脚在 -60（低于抬高一格的置物台顶面） | 失败，复现历史回执，背包铁锭未动 | 失败，置物台无 `HeldItem` |
| 脚在 -59（视线高于置物台顶面） | 通过，`inventory: -1`，随后压成铁板 | 通过，同样压成铁板 |

结论：决定成败的是**站位高度**，不是调用形状。视线位于置物台顶面以下时右键不产生效果；抬高一层后两种写法都能上料并被压机加工（压机 `Finished=1`，`Ticks=202`）。`item` 参数既不是成功的必要条件，也不能弥补站位问题。历史那四轮失败可以由几何与站位独立复现，无需模型参与。

这也修正了上一小节容易读偏的地方：接口确实没有命中面形参，但失败原因不是「缺少命中面参数」。模型当时站在地面高度，置物台比地面高一格，视线够不到台面。

限制：未重建原水轮与齿轮链，只证明机制；模型当时的精确站位没有记录，低站位是依据地板几何的推断而非实测。下一步应把「视线需高于置物台顶面」这一事实写进勘察回执或 Skill，再做一次模型轮次验证。

### 站位事实写入 Skill 与模型轮次的前置阻塞

按上节结论，只改一处文字：在 `create_kinetics/SKILL.md` 的既有条目后加一条「Stand above a depot to feed it」，说明视线要高于置物台顶面、置物台放在地面时应站高一层，不新增接口或机制。

随后尝试一次模型自主轮次，但在发出任何 API 请求前就被阻断。实际步骤与结果：

1. 通过 `/numen key deepseek …` 保存服务端密钥，回执 `Saved your deepseek key`。
2. 解散旧同伴，改由玩家上下文重新召唤一个全新同伴，避免先前 MCP 会话留下的 `halt EXTERNAL` 状态。
3. 房主连续两次发出聊天查询，同伴队列确实开轮，但服务端报 `can't reach the model: 没绑模型配置`。

进一步确认：`/numen settings` 与 `/numen debug` 都返回 `This needs the Numen client, and yours does not have it`。也就是说，服务商绑定（provider/model）是**由 Numen 客户端 GUI 写入的每玩家设置**，仅靠服务端密钥不足以让内置大脑工作。本机只有服务端，没有运行带 Numen 客户端的真实客户端，因此本阶段没有完成模型轮次，也没有消耗任何 token。

后续有两条路，需要选择其一：安装 Numen 客户端并在真实客户端里绑定一次模型；或改由 MCP 大脑桥接驱动模型。后者可以复用同一套工具与 guide，但传输层与原实验不同，结论必须按不同条件标注。

### 真实客户端实例、单机兼容性与绑定阻塞

按方案一建立了独立客户端实例 `D:\NumenLab\client`，复用本机旧工程已有的 Minecraft 客户端 jar、libraries、assets 与 natives，只替换游戏目录，并放入三个模组：Fabric API 0.92.12、Create 6.0.8.1、Numen 0.1.5。测试世界由专用服存档复制而来，用 nbtlib 改写 `LevelName`，创造模式并允许命令，用 `--quickPlaySingleplayer` 直接进入。

**单机集成服务端兼容性（本轮可验证的部分）**：Numen 0.1.5 在集成服务端正常初始化，日志依次出现 `[numen] registered 4 tool(s)`、`Numen mod initialised on Fabric.`、`[numen-config] wrote default template`、`[numen-task] scheduler heartbeat online`，玩家正常加入。这只是加载与初始化结论，未做任何游戏内行为验证。

**阻塞**：模型绑定需要在客户端 `numen settings` 界面里选择服务商与模型并填入密钥，而本次远程会话下合成鼠标与键盘输入没有送达 LWJGL 窗口——辅助功能提示页点不动，进入世界后按 T 也打不开聊天栏，因此无法到达 `/numen settings`。模型仍未绑定，未发出任何模型请求，未消耗 token。

下一步需要有人在物理机前打开一次 `numen settings`，绑定 provider=deepseek、模型与密钥；完成绑定后同一份参数文件即可重复启动，不需要再改环境。

**需要披露的副作用**：第一次启动时参数文件的引号导致 `--gameDir` 未被替换，客户端实际以旧工程的 `client-yacht` 目录启动，加载了 `saves/Engineer-Yacht-Lab`。进程在约一分钟后被终止，未在该世界执行任何命令，也早于 5 分钟自动保存；但 `level.dat`、`session.lock` 与若干 region 文件的时间戳与写回已经发生（19:02:27–19:02:37）。是否恢复需要另行决定，因为任何恢复动作本身也会写入旧工程。

### 桌面控制能力核查与游戏窗口输入结论

先核查了“缺少桌面控制能力”这一假设，结论是能力存在，问题在游戏窗口。

- 当前 DSH 会话已加载 `windows-mcp`（stdio，握手版本 4.2.4），截图、窗口切换、对普通窗口的点击都实际生效。
- 该 profile 的 MCP 配置只有一条 `mcp-manager / windows-mcp`，`disabled: false`，没有发现被禁用或未加载的桌面控制服务。
- 进程核对：Minecraft 客户端、`windows-mcp.exe` 与 DeepSeek Harness 都在 session 1、Medium 完整性级别，不存在跨会话或 UIPI 阻挡。
- 但合成输入没有送达 Minecraft 的 LWJGL 窗口：`GetForegroundWindow` 返回的就是 `Minecraft* 1.20.1 - 单人游戏`，而 Escape、F3、T 都没有反应，游戏画面内点击也无效果；同一条输入通道对普通窗口却有效。

因此这不是“缺少桌面控制能力”，而是该游戏窗口在当前会话下不接受合成输入。模型绑定仍然需要在物理机前完成一次：打开 `numen settings`，绑定 provider=deepseek、模型与密钥。本轮没有修改 Numen 源码，也没有另建 MCP 模型循环。

### 模型绑定的存储位置与可移植性

由用户在真实客户端完成绑定后，配置落在**客户端实例**的 `config/numen/server/` 下：`provider/my/deepseek.json`（`{"provider":"deepseek","model":"deepseek-flash","reasoning_effort":"off"}`）与 `keys/deepseek.key`。绑定条件为非思考档，与历史基线一致。

把这两份文件原样复制到专用实验服的同一路径后，同伴大脑仍然报 `can't reach the model: 没绑模型配置`；在 `players/<uuid>/prefs.json` 中补 provider/model 字段并重启也没有改变该结果。因此这套绑定不能在服务端之间直接搬运，模型轮次只能在持有客户端绑定的单人存档里运行。

这也意味着：模型轮次必须由真实客户端发起对话（`/numen player summon` 与 `/numen chat`），而本会话的合成输入无法送达游戏窗口，需要物理机前的键盘输入配合。

### 首次 DeepSeek 模型轮次：上料、压制与自验（2026-11-21）

绑定只在客户端实例里生效，所以本轮在单人集成服务端跑。模型绑定为 `provider=deepseek, model=deepseek-flash, reasoning_effort=off`，与非思考基线一致。任务是自然语言委托：把一粒铁锭放到压机置物台上压成铁板并读回结果。

过程：同伴先误报自己在高空坠落（这是历史夹具遗留的坏坐标），`/tp` 修复；随后它自己找到压机和置物台，报告没有铁锭；创造模式取物被拒，因为它仍是生存模式；`/gamemode creative` 后重发目标，**6 秒内完成**上料、压制、读回并回话，明确说手里是 `create:iron_sheet`、铁锭已消失。

四类记录：

| 类别 | 结果 |
| --- | --- |
| 模型自主操作 | 通过（自己选址、自己取料、自己右键置物台） |
| 模型自验 | 通过（读回产物为铁板，铁锭消失） |
| 独立实机验收 | **待做**（需要在同伴离线后检查存档中的置物台与压机状态） |
| 按时回执 | 通过（约 6 秒） |

行为上值得注意的是：它在前两次都没有编造完成，而是如实报告"我在下坠""我手上没有材料""我还是生存模式"，被纠正后立刻恢复。这与我们要区分的"模型自验"能力一致。

限制：结构是现成的，本轮验证的是上料、压制与自验，**不是自主施工**；过程包含一次传送修复、一次游戏模式修正和两次环境澄清，不是零干预运行。

**独立验收结果（受影响）**：19:53 正常保存后把存档挂到专用服离线检查——同伴背包确有 1 个 `create:iron_sheet`、无铁锭；置物台 `0,-60,0` 上的 `HeldItem` 仍是铁板；压机 `Finished=1, Ticks=242`。但该置物台在此前无模型校准中**就已经留着一块铁板**，而置物台被占用时右键会把物品拿起来。因此同伴背包里那块铁板不能归因于这次新压制，本轮独立验收结论为**不成立/受污染**，需要清空置物台后重跑一次。

**干净重跑与独立验收**：19:56:13 清空置物台、19:56:22 清空同伴背包后，19:56:28 重发同一委托，**19:56:35 完成（约 7 秒，5 轮模型调用）**，回话为「压好了——置物台上现在躺着一粒铁板，铁锭已经消耗掉了」。保存后离线复核：置物台 `0,-60,0` 上有 `create:iron_sheet`，同伴背包为空，压机 `Finished=1`。由于开工前置物台与背包都为空，这块铁板只能来自本次压制，**四类验收全部通过**：模型自主上料、模型自验产物、独立实机验收、按时回执。

这一轮仍然只覆盖「上料—压制—自验」，结构是现成的；自主建造另属下一轮。

### 自主建造压机并完成压制（2026-11-21 夜间轮）

委托：在附近空地自己设计并造一台能工作的机械压机，自己解决动力，把一粒铁锭压成铁板并读回产物。不给坐标、不给结构方案。

过程：第一次尝试失败——它回到旧夹具区域、反复寻路去 `6,-61,7`（地面以下）并循环两分钟。人工传送至 `31,-60,31` 并补一句"就在这里从零建，不要用旁边的旧压机"后，20:05:49 开始 `build.place`，20:14:00 交付回执：

> 我脚下这块空地上从零建好了：一台创造马达带着齿轮和传动轴，把一台机械压机转过来了。置物台就压在压机底下……置物台上静静躺着一粒 create:iron_sheet，铁锭没了。机器是新建的，没用旁边那台旧的。

客户端在 20:25 前后卡死（画面停在暂停帧、输入无响应），但世界已存盘。离线验收用 20:27 的存档在专用服完成，逐格核对：

| 位置 | 方块 | 关键数据 |
| --- | --- | --- |
| `28,-60,31` / `28,-59,31` | `create:motor` ×2 | Speed 16 |
| `29,-60,31` / `30,-60,31` / `30,-59,31` / `31,-58,31` | `create:shaft` | Speed 16 传动网络 |
| `31,-59,31` | `create:mechanical_press` | Speed 16, Finished 1, Ticks 242, Source 30,-59,31 |
| `31,-60,31` | 空气 | 加工间隙符合要求 |
| `31,-61,31` | `create:depot` | HeldItem = `create:iron_sheet` |

由于该置物台是本轮新建、开工时尚不存在，台上那块铁板只能来自本次压制。**四类验收全部通过**：自主选址与自主施工、模型自验、独立实机验收、按时回执。

需要记录的行为特征：**未加约束时，模型倾向复用现场已有的结构**（第一次尝试即为此），这与"最小改动"直觉一致，但在"必须新建"的任务里构成失败模式；一句环境澄清即可纠正。

### 水车驱动搅拌机首轮：结构性失败（2026-11-21 深夜）

委托：自己设计并造一套水车驱动搅拌机，最终产出安山合金。模型建成了水车、传动、搅拌机、盆和溜槽，但**没有产物**，60 轮以上、约 7.4 万 token。离线逐格核对（存档挂专用服）后，原因不在交互层：

| 位置 | 方块 | 实测 |
| --- | --- | --- |
| `-40,-60,-50` | `create:water_wheel` | **Speed 0.0，完全不转** |
| `-40,-59,-50` / `-40,-58,-50` / `-39,-58,-50` / `-38,-58,-50` | 传动轴 | Speed 0.0 |
| `-37,-58,-50` | `create:mechanical_mixer` | Speed 0.0 |
| `-37,-59,-50` | `create:basin` | 位置正确，正在搅拌机正下方 |
| `-36,-58,-50` | `create:chute` | `facing=down`，但**在盆东侧一格** |

两处独立的结构错误：

1. **溜槽与盆错位一格**：物品从 `-36,-58,-50` 落下会到 `-36,-59,-50`，而盆在 `-37,-59,-50`。所以丢进去的安山岩直接落进旁边的水里——现场观察到的"扔到水里"由此而来。
2. **水车不转**：轮子周围 7×7×7 范围内有 41 格水，但 `Speed` 仍为 0。水位在、流动方向不对，动力链从一开始就没起来。

据此，本轮前面反复出现的"右键点不到盆""溜槽喂不进去""要征求同意"都是**症状**，真正的根因在结构：进料口没对准容器，动力源也没转起来。这也说明模型的空间推理在"相邻一格"级别的对齐上并不可靠，而它自己没有通过读数发现这一点——它始终没有报出"水车 0 RPM"这个关键事实。

四类判定：自主施工 有、模型自验 无、独立验收 不通过、按时回执 无。

补充（供只读诊断）：本轮委托原文、时间线、四条原始报错（consent 过期、gui 无窗口、模型自述）、以及"模型全程未读取水车 RPM"这一事实，均已记入 `evidence/local-lab-feed-2026-11-21.json` 的 `mixerTrial` 字段（`taskText`、`timeline`、`keyLogLines`、`monitoredFacts`）。

### 水车搅拌站最小复核（2026-11-21）

对上一轮记录作两处静态勘误。搅拌机在 `y=-58` 时按 `BasinOperatingBlockEntity.getBasin()` 查找 `y=-60` 的盆，上一轮的 `y=-59` 高了一格。其水车保存转速为 0 确实成立，附近水格总数无法定位轮缘的实际停转原因。下向溜槽横向偏离一格的观察保留。

对 Create Fabric 6.0.8.1 build 1744 的 JAR、源码和实验服配置核对。搅拌机无直轴端，Y 轴小齿轮可在同高侧面啮合。搅拌机速度门槛调用 `MEDIUM`，本轮两个隔离实例的 `mediumSpeed` 均为 30 RPM。大齿轮驱动同轴向、同平面对角的小齿轮时速度幅值加倍。现有 `create_kinetics` Skill 增加搅拌段，要求先回读水车、传动与应力，再核对盆与进料口的实际格位，最后用库存前后差证明产物。建造、布置和排障仍由模型自行决定。

无模型校准在独立新建平地。夹具布置供水、两级增速和侧接漏斗，实际读取为干水车 0 RPM、湿水车 +8 RPM、`FlowScore=1`、中段 −16 RPM、末段小齿轮 +32 RPM、搅拌机 −32 RPM，网络应力 128／256。夹具通过 Numen 原生 `creative.give` 与 `inv.store` 向漏斗各交付一份安山岩和铁粒，实际快照从零到一份安山合金，两种原料均消耗，投料期间块状态一致。此轮没有模型请求。

#### 自主实机轮

- 无模型校准 `watermill-1795274037222`。独立自主轮 `watermill-1795274074013`，使用另一份空白平地存档，开场快照与校准完全一致。
- DeepSeek V4 Pro，关闭思考，限时 420 秒。沿用施工护栏。模型只收到一条自然语言目标、现有 Skill 和真实工具回执。人工干预 0 次，无人工审图、坐标提示、代建或代排障。
- 前八次请求读取 Skill 和水磨参考、勘察及更新任务清单。第九次请求约在第 76 秒发出，耗时 60.921 秒，回复描述了 8→16→32 RPM 和侧齿轮驱动的思路，但在 4,096 个完成 token 处以 `finish=length` 结束，没有工具调用。后续至截止没有新的模型请求。
- 9 个 API 请求均返回 HTTP 200。共 130,600 token（输入 125,176，输出 5,424，命中输入缓存 96,896）。工具调用为 2 次 Skill、5 次 Lua 和 2 次任务清单更新，其中可从回执识别出至少 6 个 Lua API 调用和至少 2 次单格读取。

| 验收项 | 实际结果 |
| --- | --- |
| 自主施工 | 未进入施工。没有发出放置请求，前后存档快照逐字节一致，范围内外均无方块变化。 |
| 模型自验 | 未发生。没有实测动力、投料或库存差。 |
| 独立实机验收 | 截止后先将 LabBot 踢出并检查在线名单，隔离耗时 38 毫秒。读档未发现水车、搅拌机或合金。无机器可供独立投料，功能验收未通过。 |
| 按时回执 | 没有收到游戏内完成回执，耗满 420 秒。 |

校准证明这条传动与投料路径在当前环境可用。模型轨迹证明它读到了关键机械条件并形成了一条可行的传动思路。自主轮在把方案转为第一次工具动作之前中断，因此无法据此评价后续装配或排障。若继续，优先试验可回读的一段式开工策略，让模型先完成并测量水车动力，再规划传动和投料。保留总时限和独立验收，对照首次施工时间、是否再出现截断与最终产物。

#### 复现与证据

从研究运行目录使用现有 Java、Create/Numen、只读动力探针和施工护栏。从证据包提取 `test-fixture/trial.cjs` 并设置其绝对路径。夹具复用既有启动、聊天回执、截止隔离和读档机制。安装后的 `create_kinetics/SKILL.md` 与实验加载的版本逐字节一致。

```bash
export LAB_SCOPE_JAR=/path/to/existing-scope.jar
export LAB_RUNS="$PWD/runs/mixer-verification"
export LAB_JAVA_HEAP=1200m
export LAB_SKILL_FILE="$PWD/mc20/config/numen/server/skills/createlab/create_kinetics/SKILL.md"
export NODE_OPTIONS="--require /path/to/test-fixture/trial.cjs"
unset LAB_MILL_WORLD LAB_MILL_DECISION LAB_ENGINEERING_RECEIPT
LAB_MIXER_STAGE=calibration LAB_MILL_AUTONOMOUS=side-check node live-lab/run.cjs watermill
LAB_MIXER_STAGE=autonomous LAB_MILL_AUTONOMOUS=side-drive LAB_THINKING=0 node live-lab/run.cjs watermill
```

模型轮从执行环境读取既有模型授权。校准阶段的机器坐标仅用于夹具布置。证据 JSON 的 `mixerFollowup` 附实际校准读数、请求统计和截止结果，旧 `mixerTrial` 原始条目保留。

### 水车搅拌站分段开工对照

基线 `7e9ca03` 的模型在第一次放置前耗尽单次输出上限。本轮只在 `create_kinetics` 增加一段通用的开工顺序。局部确认地面与净空后，先用现有放置工具建水车或水道，测到实际 RPM 再延伸传动。轴向、坐标与连接由模型现场决定。沿用上轮空白平地的相同快照、委托原文、DeepSeek V4 Pro 非思考档、4,096 单次输出 token、420 秒截止、施工护栏和独立实机验收。复用机理校准结果和原夹具，不重复校准。

本轮 `watermill-1795276762603`，人工干预 0 次。首次真实施工约在第 **117 秒**。第 9 次请求调用原生 `build.place`，放置 1 个小水车并回读朝东。模型先用现有受限水源工具放水，实测 0 RPM。随后扫描发现自建石砖堵住落水格，第 14 次请求自行移除，约第 **189 秒**回读 −8 RPM、`flow_score=-1`。沿轴确认 −8 RPM，再经第一级大齿轮驱动小齿轮得到 +16 RPM。

第二级先后经历同平面直邻不啮合（0 RPM）、小齿轮反驱大齿轮（−8 RPM）。模型根据读数改成沿同一旋转轴传递 +16 RPM，再由大齿轮啮合小齿轮，约第 **407 秒**测得 −32 RPM。第 27 次请求约在第 419 秒发出搅拌机的放置调用，截止时代理尚在寻路，放置被隔离中止。

| 验收项 | 结果 |
| --- | --- |
| 自主施工 | 部分通过。自行建造水车和两级增速并修复供水和传动。搅拌机、盆和进料未完成。 |
| 模型自验 | 部分通过。模型回读水车 −8、第一级小齿轮 +16、第二级小齿轮 −32 RPM。未验搅拌或产物。 |
| 独立实机验收 | 部分动力通过，整站未通过。代理隔离耗时 21 毫秒，读档确认末级与水车在同一动力网络，转速仍为 −32 和 −8 RPM。现场无搅拌机、盆和合金，未进行独立投料。 |
| 按时回执 | 未收到。420 秒截止。 |

范围检查记录 117 格变化，全部在施工范围内，其中 102 格为水。水路虽已起转，但现场仍有明显水流扩散。模型开工前仍有 4 次只读 Lua 和 3 次任务清单调用，其中 2 次因清单格式被拒。

27 次 API 请求全部返回 HTTP 200，未出现 `finish=length`。共 539,968 token（输入 525,973、输出 13,995、输入缓存命中 480,896），实际调用 23 次 Lua、6 次任务清单和 2 次 Skill。基线是 9 次请求、130,600 token、无施工。这一轮验证了从规划到施工、逐级读数和自修；完整投料闭环与总体效率仍待改善。若继续，优先减少第二级的无效布局尝试。把轴向同速接续与大小齿轮啮合的变速区分清楚，并以各级真实 RPM 作为继续施工依据。

复现沿用上一节的同一 `trial.cjs`，只执行 `autonomous`，使用本次提交的 Skill。证据 JSON 的 `mixerFirstBuild` 保存关键游戏回执、模型消耗、截止后动力链和隔离结果。原始日志与前后快照见 `Numen-Create-Mixer-First-Build.zip`。

## 直接批量施工与原生验收（2026-11-22）

### 源码审查与选型

| 项目和审查版本 | 源码结论与取舍 |
| --- | --- |
| Create 官方 `37ea4af`（6.0.11、MC 1.21.1 NeoForge）及 Fabric 端 `4c3fe77`（1.20.1 开发分支源码）。实机使用 Fabric 6.0.8.1 build 1744 JAR | 源码 MIT，资源目录有独立的保留权利声明。 `SchematicItem.loadSchematic` 把 gzip NBT 交给原生 `StructureTemplate`，`SchematicPrinter.loadSchematic` 装入预览世界供打印。 `RotationPropagator.handleAdded` 和 `propagateNewSource` 处理真实转速。`CreativeMotorBlockEntity.initialize` 启动动力，`ScrollValueBehaviour` 读写配置。直接使用安装版引擎。源码快照与发布 JAR 分开标识，未声称二进制由该快照构建。 |
| Create Architect `3deae4a` | 该提交没有许可证文件，未复用其代码。 `server.js` 实际使用 Groq 的 `llama-3.3-70b-versatile` 输出意图，`layoutEngine.js` 根据类别走硬编码布局，`physicsSolver.js` 做重量与升力估算。独立调用 `encodeSchematic` 时原样触发 NBTWriter._long 的无符号低 32 位范围错误。仅固定诊断进程的时间戳后能解码，结果是根 `Schematic` 下的 Sponge v2 `Palette/BlockData`，与 Create 目标版结构 NBT 的根 `size/palette/blocks` 不匹配。样本齿轮箱还写了 `facing`，本版实际用 `axis`。本轮未做 Schematicannon 实机打印，不能据此声称直接兼容或能运行载具。可参考的是结构化意图与清单边界。 |
| MineAI `d78abe6`（0.1.4 开发配置、MC 1.21.1、Java 21、LGPL-3.0） | 检出了实际 `plugins/create`，其构建依赖 NeoForge Create 6.0.10。`NumenCreate.install` 只注册 Skill，GameTest 源码验证安山机壳的挖放。`KbApi.search` 本地索引 GuideME、Patchouli、FTB Quests。`VerifyApi` 可核对库存、方块和邻近目标。`plugins/litematica` 同步本机房主的文件后交给 Numen 建造。可参考插件边界和只读核验，未看到现成的 Create 动力验证 API 或 VS 联动，本轮未移植。 |
| Numen `2f47e7b`、PatchouliAI 1.6.6 `b1d929f` | Numen 为 LGPL-3.0。 `BuildApi` 和 `BuildPlacer` 已能处理状态清单，创意模式个别状态允许直写并更新邻格。已有实测仍会为施工任务寻路。Patchouli 的 Fabric 元数据声明 MIT，所审源码快照未找到独立项目许可正文。`MultiPlaceTask.tickMassBuild` 在巨型工程作弊分支按 tick 直写，已有清单编译、限额和进度管理。其 `StructureExporter` 明确写入原生结构使用的 `size/palette/blocks`，蓝图存储的 `BLUEPRINT:id` 引用留在 Patchouli 协议。沿用清单与回执的设计。 |
| VS2 `1de3cc3`（2.4.12、MC 1.20.1、LGPL-3.0）与 Eureka `18344ae`（1.6.3、MC 1.20.1、Apache-2.0） | Eureka 的 `ShipHelmBlockEntity.assemble` 按黑名单筛选相邻块，经 `ShipAssembler.collectBlocks` 的 BFS 调 VS `assembleToShip`。VS 自己创建船、迁移方块并提供船对象。Eureka 此快照配置依赖 2.4.10，需重新核对具体配对。后续可先让模型提交船体清单，原型继续负责受限施工，再从游戏主线程触发原生装配并读实际船 ID、位置、变换和位移。此轮未运行 VS 或加载动态船上的 Create 动力。 |

[Create 官方源码](https://github.com/Creators-of-Create/Create/tree/37ea4afcfe5bf85875d4b0749798a45330070a1f) · [Create Fabric 源码](https://github.com/Fabricators-of-Create/Create/tree/4c3fe779d135d592548e4cb09da04d742d2637b7) · [Architect](https://github.com/twentyss2/create-architect/tree/3deae4a94449bf8e4ae1089a69a7d4d6cea205b3) · [MineAI](https://github.com/ailiujiarui/MineAI/tree/d78abe60be51d41cd1ca7b8ef0657475da73feed) · [VS2](https://github.com/ValkyrienSkies/Valkyrien-Skies-2/tree/1de3cc3c1d26caaafaf7fb9654d68be812d46213) · [Eureka](https://github.com/ValkyrienSkies/Eureka/tree/18344aebe29267ef162b6311c8db5fc67d09ac7f)

从实际 JAR 读取配方 JSON，水洗 `create:wheat_flour` 产 `create:dough`，研磨小麦产小麦粉并有额外随机产物。解包 Ponder 的 `fan/processing.nbt` 和 `millstone.nbt`，分别读到 17、13 项状态调色板以及 46、38 条方块记录。配方和静态方块状态可直接提取。场景脚本 `FanScenes.processing` 和 `ProcessingScenes.millstone` 会生成示意物品、设置展示转速或改写演示方块实体，所以从源码提炼连接和操作知识后，还要用真实动力与物品验收。原型运行时只读取目标配方，未用任何场景布局或坐标作为模型输入。

补充对照来自用户提供的 DSH 实验，环境基线 `d1aa7ec`，本轮未复跑。Numen 批放 20 格石砖约 86 秒且角色发生长距离位移，10 格轴约 59 秒，其中 9 格标为直接写入。近处 5 格约 3 秒。独立的服务端 `/fill` 放 10 格玻璃约 1.05 秒。压机的 `/data get block` 在该例写入后即读到 −16 RPM 和动力源，3 秒后相同。结合本轮原生回读，直写能解除角色移动开销。仍应在服务器 tick 后确认更新并读真实结果，流体、动力负载和加工时间各自有延迟。

按本轮安装 JAR 的 `assets/create/ponder/` 前缀实际枚举，有 178 个 NBT、37 个目录。该范围内总计 215 项，DSH 报告中的总条目数 301 未在该口径复现。英文资源 `assets/create/lang/en_us.json` 有 167 个 Ponder 场景标题键，并存在模拟拉杆的分步文本。可把 NBT、旁白和 Java 场景动作关联成按需检索的知识，正确性继续用游戏测量。

### 隔离原型

[direct.cjs](direct.cjs) 复用原试验服启动资产、RCON 和 `world.cjs` 的 Anvil 读档器。DeepSeek V4 Pro 非思考档提交受限 JSON 清单，模型自行决定位置、状态和测试进料点。原型先检查整批坐标、冲突和字段，再在无玩家的新服中用原版 `setblock … replace` 直接写入，等候游戏 tick 后读取实际方块属性、马达 `ScrollValue`、RPM、动力源和应力。世界的边缘障壁、最大批次 64 格与有限状态表只用于隔离研究。马达转速是唯一可写的方块实体配置，模型没有通用服务器命令或任意 NBT 写入权限。每次 `test` 输入恰好一份任务物料，程序检查真实库存或世界物品的输入消耗、产物增量及机器转速。所有模型调用结束后，自动再送入一份独立样品，检查新的产物增量和边界差异。

### 上机结果

三轮使用三个独立空地快照，起始块与实体数据的 SHA-256 一致。首轮水洗批次越界，后续三次都把水源与木桶写到同一格，程序按连续无效回执停止。没有写入任何方块，38.05 秒，4 次模型请求，8,132 token。随后给冲突回执加具体坐标和状态，并在通用清单约定中明确一格一个最终状态。未放宽边界。水洗复测和磨粉迁移使用同一份冻结代码、知识和动作定义，各自的新会话只有该次任务和现场回执。

| 任务与运行 ID | 自主施工和自验 | 独立验收 | 批次／回复／独立验收耗时 | 模型 |
| --- | --- | --- | --- | --- |
| 风扇水洗 `washing-zPc6Tv` | 一批提交 23 格，状态及 NBT 回读无差异。风扇 32 RPM，应力 64／524288。第一次进料后原料仍在，模型改用水所在的进料格，原料消耗，生成 1 份面团。 | 模型回执后复投 1 份小麦粉，存档中的面团由 1 增到 2，原料为 0。 | 0.558 秒批次，49.644 秒回复，60.721 秒独立验收 | 4 次，6,498 token（输入 6,009、输出 489、输入缓存命中 4,352） |
| 磨石研磨 `milling-QPgxbA` | 一批提交 19 格，其中 3 格实际新增机器和传动，其余为空气状态校验。磨石 32 RPM，应力 128／524288。磨石输出库存从 0 增为 2 份小麦粉。 | 复投 1 份小麦，真实磨石输出库存由 2 增到 4，输入为空。研磨产量存在随机项。 | 0.572 秒批次，33.462 秒回复，44.517 秒独立验收 | 3 次，4,259 token（输入 3,721、输出 538、输入缓存命中 2,432） |

两次通过场景均无人手代建、代选进料点或截图判定，人工干预为 0。马达配置和实际驱动均回读为 32 RPM，传动及负载在同一动力网络。边界之外的采样无改动。总计 11 次模型请求、18,889 token，包含首次失败。批次耗时包含 RCON 逐格更新、500 毫秒等待和读盘；回复和验收从提交任务起计，不含冷启动。此结果验证两种小型装置的直接施工与一次机制迁移，未测生存物料约束、动态船装配、大型产线或其他版本。水洗中的自修是进料点纠错，未验证已损坏大型动力网的自主维修。与旧 420 秒搅拌实验的任务和模型接口不同，不作同任务速度对照。

### 复现

在研究目录复用 [前置依赖](../REPRODUCE.md) 的 `mc20`、Java 17 和 `npm ci --prefix mc20` 安装的 RCON/NBT 依赖。使用 Node.js 24、Python 3 和 `unzip`。实机安装 Fabric Loader 0.17.2、Fabric API 0.92.6、Create Fabric 6.0.8.1 build 1744。运行前由环境提供已授权的 `DEEPSEEK_API_KEY`。

```bash
node --check live-lab/direct.cjs
node live-lab/direct.cjs washing
node live-lab/direct.cjs milling
```

每次启动保存在独立的新目录，默认 `live-lab/runs/`，可通过 `LAB_RUNS` 改位置。已有 `mc20` 路径和 Java 路径可通过 `LAB_MC20`、`LAB_JAVA` 指定。服默认用本地 25586，RCON 使用后一端口。首次失败、两次通过的指令、用量、关键回执及独立快照摘录见[既有证据文件](evidence/local-lab-feed-2026-11-21.json)中的 `directBuildPrototype`。

## 复杂产线、盲修、VS 与游戏内原型（2026-11-22）

在新建隔离服使用 Minecraft 1.20.1、Java 17、Fabric Loader 0.17.2、Fabric API 0.92.6、Create Fabric 6.0.8.1 build 1744。真实模型均为 DeepSeek V4 Pro。游戏内组复用 Numen 0.1.5-beta 和 `numen_api` 0.1.5。VS 装配组实际加载 VS2 2.4.11、Eureka 1.6.4（声明 VS2 `^2.4.10`）及 Fabric Language Kotlin 1.11.0。源码审查版本、许可证和依赖区别见上一节。全部新建试验世界，未修改原存档。计时从任务发出到对应验收，不含服务器冷启动。

| 试验 | 真正执行与验收 | 模型消耗 |
| --- | --- | --- |
| 固定产线校准 | 只在入料桶加 3 份小麦。漏斗、磨石、智能溜槽、水洗工位和成品桶完成自动转运。独立校准一轮产 5 面团，后续盲修所用夹具先产 4 面团。结束时小麦与面粉均为 0。 | 无模型 |
| 从空地自主产线 | 两轮完整布局被非法状态、越界或同格冲突挡住。思考档一轮请求超时，延长 transport 后两次均 `finish_reason=length` 且无可执行动作。按动力优先分批的最后一轮在 8.101 秒首次提交 9 格，磨石最终 64 RPM，风扇 0 RPM。横向传动用了 X 轴无接头的齿轮箱，朝西风扇的接轴面也与传动错开。未投料。 | 两轮 8 次／15,584 token／56.065 秒、12 次／48,678 token／95.944 秒。思考档超时轮用量未知，截断轮 2 次／13,818 token／142.880 秒。分批轮 12 次／63,976 token／81.933 秒 |
| 产线盲修 | 夹具移除电机下游一根 Z 轴传动轴、确认两个负载均为 0 RPM，再只交给模型故障描述。首次模型改错齿轮箱，未恢复。加入读取电机输出端相邻两格的现场回执后，模型自行定位。它请求 7 格，只有缺轴那一格发生实际写入。电机 64、磨石 64、风扇 −64 RPM，三者同动力网，应力 384／1,048,576。模型自验 3 麦→6 面团，独立复验 3 麦→4 面团，均无剩余小麦或面粉。最终方块快照与故障前一致。固定产线与断轴由测试程序布置，模型任务期间人工介入 0 次。 | 首轮 5 次／13,646 token／33.500 秒。通过轮 4 次／11,769 token，76.288 秒回执，112.969 秒完成独立验收 |
| VS/Eureka 飞行载具 | 模型自行画出船体并提交施工。最终实测装配出原生船 ID `3`，1 舵、5 气球、1 锚。实际位置 `[8.5,-56.004624,9.077279]`、旋转四元数 `[0,0,0,1]`。两次控制位移为 0，速度为 0，锚停读数也未达标。早期服日志出现船坞区块未开始 tick，最后一次读到物理管线运行及 15 个 voxel chunks，仍不能证明控制链有效。历史同版本原生舵菜单测试已观测被动下落约 6 格，可作对照。最新协议观察者复查发现客户端无法解码 VS 环境的命令树，以 `impossibleCommandTree` 断开，未触发新的模型请求。 | 最后一次模型轮 8 次／13,534 token／60.398 秒，未通过 |
| 游戏内工具 | 从连接的房主协议客户端向 Numen 发自然语言目标。非 OP 房主的伴侣自主选址、建造、读 RPM、导出标准 NBT 并异地重建。两台风扇均独立读到 −16 RPM，导出结构 `1×1×2`、2 格、无实体及方块实体 NBT，施工前备份 2 份。越界写入和生存调用被拒绝，世界未变。验收前伴侣下线，最终边界外差异 0。 | 12 次模型请求、16 个工具调用、162,050 token，104.059 秒游戏内回执，人工代建 0 次 |

游戏内初版直写成功但 Numen 未登记施工归属，模型尝试拆改自己放的方块被拒。改为调用 Numen 的 `PlacedBlocks.placedBy`／`forget`。第二轮两台风扇均 16 RPM、NBT 往返成功，但草地受原版邻格更新变成泥土，严格边界验收判失败。最后用石材地基排除草地变化后通过。实测使用连接的房主协议客户端和专服。图形客户端界面、大型产线的游戏内接入及正式存档部署尚未验收。

[direct.cjs](direct.cjs) 与 [line.cjs](line.cjs) 复用已有隔离施工，新增有类型的溜槽过滤器、实际模型回执和独立流转验收。[repair-fixture.cjs](repair-fixture.cjs) 只用于固定产线的校准及盲修，结构与故障位置均不注入模型。[ship-bridge](ship-bridge/) 用 Eureka `ShipAssembler.collectBlocks`、VS `LoadedServerShip`、`SeatedControllingPlayer` 和 Eureka 锚状态做受限试验，它目前只能证明装配及回读。[game-bridge](game-bridge/) 通过 Numen 插件 SDK 注册 `engineering.site.apply`、`inspect`、`export`、`import_at`，沿用原版放置更新、Create `KineticBlockEntity` 与原版 `StructureTemplate`。它默认关闭，只授权显式指定的创造区域，检查旧状态、限制 64 格一批、调用 Numen 动作授权、保存原生结构备份，不授予模型通用命令或原始 NBT 写入。批次异常时尝试恢复方块状态，完整方块实体恢复仍需使用备份。隔离夹具用 Numen bypass 运行，故本轮通过的是插件范围/模式约束和非 OP 路径。服务端保护插件组合、权限和客户机图形交互需要单独实测。

回归中重放上一阶段磨石的 3 个冻结响应，在新的真实世界重新施工、自验和投料。22.675 秒独立复验通过，两轮输出库存 0→1→3，磨石 32 RPM，无新 API 请求。未重跑上一阶段模型实验。

### 复现与接续

复用上一节环境和运行时凭据。运行如下命令时默认各自创建独立存档，产线从空地运行，盲修从已校准固定夹具运行。

```bash
export LAB_MC20=/absolute/path/to/mc20
node live-lab/direct.cjs line
node live-lab/direct.cjs repair

export LAB_VS_MODS=/absolute/path/to/vs-mods
bash live-lab/ship-bridge/build.sh
node live-lab/direct.cjs ship

bash live-lab/game-bridge/build.sh
LAB_GAME_BRIDGE="$PWD/live-lab/game-bridge/game-bridge.jar" node live-lab/run.cjs engineering
```

`LAB_VS_MODS` 下使用 `vs.jar`、`eureka.jar`、`kotlin.jar`。游戏内试验同时依赖已有 `mc20/connect.cjs` 和 Numen 配置，试验启动器设置 `-Dengineering.site=-7,-60,5,8,-55,20`，部署时由服主设定授权范围。原始读取数据由每一轮的 `events.jsonl`、`model.jsonl`（游戏内组）和验收快照产生。仓库[证据](evidence/local-lab-feed-2026-11-21.json)的 `engineeringNext` 收录关键原始记录与运行 ID。

产线下一步只补原生接轴面的闭环回执。使用已失败方案证明断口两侧实际接轴面，再让模型自行修正，共用动力测通后再接入物料。VS 先复用已经验证过的原生舵菜单路径和实际乘坐状态，对照当前直写船坞区块的 tick 与控制附件再验证位移。语音和外围功能留待核心阶段通过。

## 夜间接轴诊断、产线与物流盲修（2026-11-22）

在 Minecraft 1.20.1、Create Fabric 6.0.8.1 build 1744、Fabric Loader 0.17.2 的隔离新存档中验证。游戏内使用 Numen 0.1.5-beta 和连接的房主协议客户端。所有实测模型请求使用 DeepSeek V4 Pro。使用既有实验场和直写/游戏内桥接，VS 控制由 DSH 独立负责。

### 真实接轴与知识来源

[CreateReadings.java](game-bridge/CreateReadings.java) 在服务端查询 Create 的 `IRotate.hasShaftTowards`、`RotationPropagator.isConnected`、动力源、实际转速及网络。区分接轴面和齿轮啮合，风扇还读出原生气流方向。由玩家或模型声明部件及需要的接轴面，工具在原生合法状态中选择朝向。模型仍需自行选择方块坐标、传动拓扑和物流。对照重放上夜失败方案（`line-9oaB2e`）时，原生读数确认磨石 64 RPM、风扇 0 RPM。负载之间既有横向分支无接轴面，也有风扇接轴面背向来轴的问题。真实服务端拒绝含非法轴值的整批施工，拒绝前后世界快照相同。

[ponder.cjs](ponder.cjs) 只接三条逐条核实的 Create 教学场景和安装版 JAR 配方条目。实际验证风扇、磨石和智能溜槽 NBT 路径，以及磨小麦、洗面粉的配方查询。演示动画与真实动力验收分别记录。仓库和运行环境没有 DSH 本地 Ponder 索引，本轮接入没有复制其提取器。

### 实机结果

| 阶段 | 实际测得 |
| --- | --- |
| 空地自主面团产线 | 模型曾在 47.450 秒接通同一动力网，电机 64、风扇 −64、磨石 64 RPM。该轮随后重复改同一齿轮箱，未投料。后续简化回执与动力阶段检查仍遇到模型要求一个齿轮箱同时连接三个轴，或尝试从磨石上方接轴。思考档两次写满 10,000 输出 token，均未给出可执行 JSON。复制共享动力存档另起模型续做时，模型写入了进料桶与漏斗，但随即重复这一批次，未接成品侧或投料。 |
| 空地自主石料产线 | 切换至不同真实配方链圆石→砂砾→燧石，工具沿用同一套原生接口。两轮隔离模型测试未让两台磨石都接入同一动力网，未投料。 |
| 物流盲修 | 夹具校准后，将进料漏斗从朝西改成朝东。故障时动力仍正常，3 小麦实际堵在漏斗、成品不增。首次模型反复观察，加入堵料和各面相邻方块后，模型找出正确改动。旧 `setblock` 改向清空了堵住的库存，未通过产量验收。改用原生同方块状态更新保留漏斗实体后，模型仅校正这一处，原有 3 小麦产 3 面团，独立再投 3 小麦新增 6 面团。方块状态、马达设定、过滤器与故障前一致，边界外变化为 0。人工代建、代选修复坐标均为 0。 |
| 游戏内设备与 NBT | 在连接的非 OP 房主协议客户端旁，校验越界、生存与权限门槛。桥接实际写入 64 RPM 马达、部件、智能溜槽过滤与测试输入，保存并异地重建。两个风扇实测均 64 RPM，马达设定和过滤条件随原版 NBT 保留，桶内测试物料未随蓝图复制。 |
| 游戏内双工位实机校准 | 无模型固定夹具在游戏内通过同一桥接写入，两个磨石分别由侧小齿轮驱动。原件和从原版 NBT 重建的副本，各喂 3 圆石，各有 3 燧石自动进入成品桶，圆石、砂砾均清空。四个磨石实测均为 −64 RPM，每条线独立共享一个马达。原始快照回读边界外差异为 0。 |
| 游戏内模型产线 | 第一轮真实建出由同一马达驱动的两台 64 RPM 磨石，随后模型在规划物流时输出截断，未投料。分阶段轮在动力阶段限时内未接通两台磨石。完整自然语言自主投料、导入复验尚未通过。 |

本轮通过的盲修模型使用 5 次请求、20,109 token，50.838 秒完成回执，100.648 秒完成独立验收。时间从模型任务开始计算，夹具校准另计。完整模型试验用量、回执、故障前后对照和运行 ID 见[证据 JSON](evidence/local-lab-feed-2026-11-21.json)的 `engineeringNight`。游戏内验证使用房主协议连接及 Numen bypass 夹具，检查了非 OP 房主、插件区域和创造模式。图形客户端、生产级权限组合及正式存档部署仍待验收。

### 复现

沿用上一节版本、Java 17 与已授权的运行时凭据。以下命令从研究目录启动并生成独立存档。

```bash
export LAB_MC20=/absolute/path/to/mc20
bash live-lab/game-bridge/build.sh
node live-lab/direct.cjs ports-check
node live-lab/direct.cjs line
node live-lab/direct.cjs stone-line
node live-lab/direct.cjs repair-logistics
export LAB_GAME_BRIDGE="$PWD/live-lab/game-bridge/game-bridge.jar"
LAB_ENGINEERING_SMOKE=1 node live-lab/run.cjs engineering
LAB_ENGINEERING_SMOKE=line node live-lab/run.cjs engineering
LAB_ENGINEERING_LINE=1 node live-lab/run.cjs engineering
```

`ports-check` 和两个 `LAB_ENGINEERING_SMOKE` 组无模型。固定校准结构只在夹具中使用。模型轮利用原生状态检查、有限方块与参数集合，备份并尊重原有授权。标准结构文件只保留受支持方块状态、马达转速和智能溜槽筛选标量，导入逐格检查，库存和实体不复制。`LAB_LINE_RESUME` 可读取先前研究运行的 world 并在新目录复制后继续模型产线。盲修中的原料样本来自测试入口，生产由 Create 和原版物流完成。


## 交接整合与工程附属 0.2.0（2026-11-22）

`handoff/numen-ponder-ship-2026-11-22` 的固定提交为 `935da3f15676a6c14c20681dd645bd5c432b8b15`。核对基线和两次交接提交后，保留 Ponder 重建脚本、查询和船体证据。Ponder 的 109 条候选资产记录合计 102 个不同 NBT 路径，全部存在于安装版 JAR，匹配包含启发式。许可、原文学源及统计见 [Ponder 索引](../ponder/README.md) 和 [第三方许可](../ponder/THIRD-PARTY.md)。

DSH 飞行船、水面船样本转向阶段真实四元数分别变化约 0.236°、0.272°。原报告的 5.272、6.873 格为改变推进方向后的位移。接续试验另用原生 `leftImpulse` 作为偏航输入。专服接入真实 Fabric 图形客户端，Numen 伴侣 LabBot 通过原生舵乘骑后，固定校准船实际前进 4.113 格、偏航 128.38°，锚停观测约 200 tick，位置与旋转无变化。此轮是无模型的底层校准。游戏内 API 与产线的验证运行分别记录，船体校准布局不用于训练或代替模型设计。


### 本轮验收

| 项目 | 结果 |
| --- | --- |
| Ponder 与真实配方 | 隔离服实际查询风扇、磨石、溜槽、机械臂、关键词截断和两项安装版配方。Numen 另从游戏内调用同一知识入口，随后完成原生装置设置和 NBT 往返。 |
| 游戏内 VS/Eureka | 在连接真实 Fabric 客户端的专服，经 `engineering_vessels.ship` 由伴侣施工、装配船 ID `3`、乘舵前进约 2.233 格、施加原生偏航约 66.39°、自动离座锚停。随后观察 10.25 秒，位置与旋转不变。测试夹具预置测试区域，伴侣操作时房主非 OP，Numen 使用隔离的 bypass 配置。 |
| 游戏内自然语言施工 | 在隔离专服用 Numen 接收风扇委托。上一轮模型自行选择了隔开的两格，动力未连通。用相邻格和已实现的原生接轴工具说明复测后，模型自行选址并施工、回读 RPM、导出 2 格原版结构、异地重建和回执。原件及副本均为 128 RPM，两个备份，边界外差异为 0。17 次请求、233,759 token，约 132 秒完成。 |
| 固定石料产线与原版 NBT | 同一 JAR 由游戏内接口施工的原件和 NBT 副本各 3 圆石→3 燧石。每条线的两台磨石实测 −64 RPM，同一动力网，原料和中间品清空。 |
| 空地自主石料产线 | DeepSeek V4 Pro 在 4 次请求中自行选择坐标、物流和传动路径，工具只检查接续并生成对应合法状态。两台磨石同网，实际 −64 RPM；模型首投 3 圆石→3 燧石，独立复投再增 3 燧石。无人工代建、人工转运或边界外块变，首轮 13,386 token，43.813 秒回执，60.455 秒完成独立验收。 |
| 动力盲修 | 在模型自行建成的石料产线移除电机下游一根竖轴，两台磨石停转。模型恢复且仅写入断轴格，错误选择反投端时测试工具依据现场物流拒绝投料，保留既有成品。模型改用实际入口并产 3 燧石，独立复投再增 3 个，最终方块和设置与健康快照一致，77.406 秒完成独立验收。 |
| 物流盲修 | 固定夹具的进料漏斗被改朝向，已有小麦堵在漏斗。模型只修改该格而保持库存。模型测得成品增加 3 个，独立复投增加 5 个面团。无人工代修，4 次请求、15,831 token、96.342 秒完成独立验收。 |
| 自主面团产线 | 多次尝试未提供能联通两工位及成品桶的物料路径，部分方案漏掉装置或采用非法出料朝向。自主迁移未通过。 |

证据、模型提交的坐标与线路、各批次回读、失败与修复轨迹见 [本轮 JSON](evidence/integration-0.2.0.json)。空地自主产线使用隔离直写工具，游戏内产线校准采用明确固定的验证布局。这两个结果分别验证自主设计和安装版工具，完整的游戏内自然语言复杂产线仍需复验。

### 安装 0.2.0 测试版

- 使用 Java 17、Minecraft 1.20.1、Fabric Loader 0.17.2、Fabric API 0.92.6、Numen 0.1.5-beta（内含 `numen_api` 0.1.5）及 Create Fabric 6.0.8.1 build 1744。
- 将本附属 JAR 放进实例或专服的 `mods`，由需要提供工程工具的一端加载。单机在客户端实例安装，专服在服务端安装，并让客户端装好该世界所需的模组。按 Numen 原有流程配置模型和召唤伴侣。
- 在启动 JVM 参数中设置 `-Dengineering.site=x1,y1,z1,x2,y2,z2`，使用主世界已加载的测试区域，每轴 1..16 格。例如测试服使用 `-Dengineering.site=-7,-60,5,8,-49,20`。工具要求主人在线，且主人和伴侣同维度、均为创造模式。默认关闭。
- 可选载具依赖为 VS 2.4.11、Eureka 1.6.4 和 Fabric Language Kotlin 1.11.0。额外设置 `-Dengineering.ships=true`。由伴侣用 `engineering.site.apply` 在区域内造小船，船体限 64 格、有一个橡木舵和一个锚。 `engineering_vessels.ship` 提供装配、检查、短时控制和锚停。当前一世界会话只维护一艘船，选定船 ID 随服务端重启失效。区域边缘、结束时限、离座或房主离线触发结束控制。仅在独立测试世界开启。
- `engineering.site` 提供施工、检查、测试喂料、知识查询、原版 NBT 导出和导入。换块需提供旧状态，原生权限仍执行。每批写入前生成备份，导出的可复用结构只带白名单方块状态和受支持的装置设置，去掉实体和库存。

构建复用已启动过的 Fabric 1.20.1 Java 17 服务端夹具类路径。从研究目录执行：

```bash
export LAB_MC20=/absolute/path/to/mc20
export JAVA_HOME=/absolute/path/to/jdk17
export LAB_VS_MODS=/absolute/path/to/vs-mods
bash live-lab/game-bridge/build.sh
```

`LAB_VS_MODS` 包含 `vs.jar`、`eureka.jar`、`kotlin.jar`。省略该变量可构建仅 Create/Numen 的版本。生成的 `live-lab/game-bridge/game-bridge.jar` 已包含 Ponder 索引和许可。旧 `ship-bridge/build.sh` 仅构建隔离服用实验命令，日常安装只需统一的工程附属 JAR。冒烟测试可用 `LAB_ENGINEERING_SMOKE=1`、`line`、`ship` 和 `live-lab/run.cjs engineering`。其中载具组需用配置齐全的 Fabric 客户端以 `LabOwner` 连接隔离服。正式安装不需要实验命令或 RCON。

## 0.2.1 稳定性复验（2026-11-22）

功能源码与可安装测试 JAR 对应 `98aa6c1bc6ed842320a26a4d73eba0fb3cd154b4`，JAR SHA-256 为 `31d7e76bebbd428dd192d4dd3a8531921c23a459e7c13a1551ff43cd74a27cd1`。下载件名为 `Numen-Engineering-0.2.1-test.zip`，内含 JAR、安装说明与校验和。用上节同一构建命令可复现，在单机客户端或专服 `mods` 中替换旧版 JAR。测试环境使用 Java 17、Minecraft 1.20.1、Fabric 0.17.2、Fabric API 0.92.6、Create Fabric 6.0.8.1 build 1744、Numen 0.1.5-beta。可选载具仍使用 VS 2.4.11、Eureka 1.6.4 及 Fabric Language Kotlin 1.11.0。启用站点和载具的 JVM 参数同上节。

[DSH 单机历史结果](acceptance-single-player-0.2.0.md)及日志合入自 `7faa863b`。历史日志验证有转速及故障修复，不能证明实际压制产物或完整物流。其站点范围记录与聊天的实测坐标不一致，区域验收以下列独立复测为准。基线源码已有 `engineering.ships` 条件白名单，修订采用统一的施工、蓝图导入和工具许可显示。相邻围栏等原版形状更新允许回读实际状态，模型显式指定的状态与有效方向仍校验。

- `engineering.site.info()` 一次显示范围、可用方块、投料项、船体和批次上限。Lua 用 `print` 显示返回值，`inspect_many` 一次读至多 16 个选点，单点与批量观察均返回可直接作为 `expected` 的实际方块状态。批次 `cells` 是实际写入格数，`resolved` 只列自动选向或邻接更新后的状态。失败回执包含实际状态或模式原因，Ponder 支持带 `create:` 的名称与空格输入。
- `feed` 保持整数返回，校验授权前后的目标一致、空桶、实际写入槽位和数量。专服原生 `ask` 模式下，空闲桶保留 3 小麦，接漏斗的桶确认写入 3 个，随后输入桶、漏斗和收集桶分别为 0、0、3。错误目标、已占用与满槽、拆除有物的桶被拒。另在真实 `observe` 禁止模式下试放，原生策略拒绝，世界快照不变。
- 真实图形客户端的 Fabric 集成服务器，无 VS、`ask` 权限下，以游戏内 Lua 执行风扇施工及结构导出导入，原件和副本均为 64 RPM。隔离专服的固定双磨石及原版 NBT 副本，各自处理 3 圆石并自动收集 3 燧石，原料及中间品清空。
- 飞行船、水面船均由模型在独立的单机测试世界内建造并完成装配，模型航行的工具结果与随后脚本发出的独立脉冲分别记录。独立验证中，飞行船前进 2.857 格、偏航 9.02°，水面船前进 2.061 格、偏航 52.08°。两艘船锚停后观察 10.25 秒，位置和四元数无变化。两次模型轮均超出回执窗口。另用当前 JAR 的预设飞行船夹具验证了白气球、舵、锚和自动连接的围栏，真实前进和偏航同样通过。当前 JAR 的预设水面船也通过推进、偏航和锚停。两次模型在实际服务器中都获得了推进及偏航成功回执，独立脉冲数据用于上述运动验收。载具开发试验使用隔离权限配置。

- 不同物流链复用既有面团校准夹具，经游戏内工具施工和投料。水源由隔离试验夹具准备。初轮因一格水流超边界未通过。限定水流后，首批 3 小麦产 3 面团，独立再加 3 小麦产 4 面团，原料、面粉清空，风扇及磨石绝对值均为 64 RPM，同一动力网，边界外差异为 0。

### 模型执行效率与未完成项

以实际 DeepSeek V4 Pro、相同种子和初始地形、同一权限及同组内逐字相同的委托测试。模型接口响应时长与整项经过时间分别记录。剩余时间包括游戏调度、等待和其他开销，现有记录无法逐项分解。DSH 的 43 次压机记录含模式拒绝重试，研磨和维修的 71 次为合计，历史数字仅用作定位问题。

| 对照 | 结果 |
| --- | --- |
| 压机初版委托 | 基线 17 次 / 246,251 token，150 秒内未建成。修订试验 JAR 18 次 / 257,177 token，实际有转速但压机与置物台间没留空气，功能验收失败。 |
| 同一句委托明确压机与台面净空 | 基线 16 次 / 250,502 token，150 秒内未建成，接口响应累计 137.362 秒。交付 JAR 15 次 / 205,514 token，116.140 秒回执，接口响应累计 113.981 秒，实测 −64 RPM，独立投一铁锭产出铁板。各一轮有效样本。另有一次双方各 8 次请求后宿主过载、协议房主掉线，保留为中止记录。 |
| Numen 游戏内复杂产线 | 测试三轮自然语言任务。前两次分别输出截断、造出两台未得动力的磨石后超时，后一次在竖轴路线委托中于施工前耗尽单次输出。完整自然语言复杂产线仍未通过。 |

记录中多次出现不输出返回值的裸 Lua 帮助调用、反复查询知识和长计划挤满输出。修订让可用范围、合法方块、精确状态及批量观察更直接，但调用次数仍接近旧版，尚无稳定减半证据。接续重点是让 Numen 把已有物料路径和动力连通性检查用于施工前验证，并用新空地验收全流程。既有空地石料试验和断轴/物流盲修仍可复现，证据见上一节。

试验用图形客户端和单机集成服务端实跑，采用本地测试夹具执行玩家侧命令及独立复核；玩家手动安装后的体验、第三方保护插件组合及正式存档仍待用户验收。详细证据见 [stability-0.2.1.json](evidence/stability-0.2.1.json)。

---

## 0.2.2 反馈与执行效率修订（2026-11-22）

源码在 `efficiency/numen-engineering-0.2.2`，基于 0.2.1（`98aa6c1`）。全部改动只作用于现有 `engineering.site` 工具组，不新增平行系统：

- `fill(from,to,state)`：一次填满轴对齐盒（1..64 格）；若状态是 `create:shaft` 且盒子为直线，自动取该轴做 `axis`。用于地板、立柱、轴排，替代逐格列表。
- `check(work)`：施工前干跑，逐格返回 `rejects`（原因或实际状态），不写方块、不触发同意。
- `ports(pos)`：用 Fabric Transfer API 逐面报告物品/流体能力与 insert/extract，物流按真实端口规划。
- `apply` 增加 `warnings`：已放置但隔离（0 转速）的动能块，或"有转速但六个接轴面都没有相邻动能块"的动力源，都在同一回执里点名。
- 未写属性的 `create:shaft` 与 `create:cogwheel` 会朝唯一的相邻动能块自动定向。
- Ponder 的磨石说明加入实测端口事实：动力取**正下方竖直轴**，输出在下方空闲时向下弹出。

### 实机对照（同一句自然语言委托，单机创造，站点 -8..7 / -60..-51 / -8..7）

| 轮次 | 构建 | 请求 | 峰值上下文 | 结果 |
| --- | --- | --- | --- | --- |
| e1 | 0.2.1 基线 | 117 | 49,352 | 马达旁轴 0 RPM，重建两次后停止 |
| e2 | +fill/check/warnings | 210 | 49,889 | 两台磨石 −64 RPM、圆石→砂砾自动；模型误判产物不能自动抽取，停 |
| e3 | +ports/磨石说明 | 22 | 23,318 | 原生命令触发房主同意，无人应答，卡死 |
| e4 | 关闭同意（测试） | 64 | 31,305 | 传动未接通；模型自述同格重复写入、状态乱了 |
| e5 | +动力源告警 | 100 | 39,509 | 输出面贴轴仍 0 RPM，十几种接法后放弃 |
| e6 | +轴自动定向 | 88 | 47,771 | 马达旁轴/齿轮转、磨石 0，清理后停止 |
| e7 | +齿轮自动定向（磨石说明仍是错的"输入在上方"） | 153 | 49,485 | 传动轴整条 64 RPM，磨石对侧齿轮/下方横轴/齿轮箱均 0；模型停下询问取力面 |
| e8 | 最终 JAR（修正说明） | 94 | 46,130 | 大齿轮同步到 −16 RPM 后客户端原生崩溃，无结论 |
| e8b | 最终 JAR（修正说明） | 216 | 49,787 | **部分通过**：两台磨石均 −16 RPM，一级自动进料，二级由模型手动转运，成品桶实测 7 燧石；报告后 14 秒客户端再次原生崩溃 |

修正说明后模型行为明显变化：e8b 里它自行完成了"竖轴 + 侧面齿轮"的动力布置，两台磨石都转起来，并把砂砾磨成燧石。仍缺的是两段之间的自动转运——它把砂砾手动送进二级磨石，因此不是完整自动线。

独立复核（只读命令，重启存档后执行）：一级磨石 (4,-56,5) `Speed -16.0f`、二级磨石 (3,-55,5) `Speed -16.0f`、成品桶 (3,-57,5) `Items=[{Slot:0b,id:"minecraft:flint",Count:7b}]`。

崩溃：客户端两次原生 `EXCEPTION_ACCESS_VIOLATION`，同一帧 `0x00007ffd52bf69e1`，分别在 3m49s 与 12m03s；stderr 有 `PhysX FdFoundation.cpp(180) Foundation destruction failed`。崩溃与模型任务结束时刻接近，`hs_err_pid9440.log`、`hs_err_pid32816.log` 留在测试客户端目录。

模型轮次均未出现 `finish=length` 截断（0.2.1 记录的长计划截断未复现）；失败点集中在磨石取力面与段间物流，而非上下文或输出长度。

### 人工实测补足

- 磨石取力面：`creative_motor[facing=up]` → `shaft[axis=y]` → `millstone`，三者同一网络 `Speed 16.0f`、Size 3。磨石吃**正下方竖直轴**。
- 输出：磨石下方放 `create:chute` + `minecraft:barrel`，运行后桶内出现 3 个砂砾，说明产物可从下方自动收集。
- 两个事实都已写入 Ponder 工具回执；模型在 e7 之前未尝试"正下方竖直轴"这一面。

### 结论与未完成

效率改动把失败点从"接不上动力"推进到"段间物流"：e8b 用修正后的说明自行完成了竖轴加侧面齿轮的动力布置，两台磨石 −16 RPM，成品桶实测 7 燧石。但二级投料仍由模型手动完成，**全自动多步产线仍未通过**，也没有完成独立复投。另发现客户端在任务结束前后两次原生崩溃（同一 PhysX 帧），需要单列稳定性排查。下一轮：先看住崩溃，再把段间转运（溜槽/漏斗方向与端口）做成可查询、可干跑的事实数据，重跑同一委托直至无需人工转运的独立复投通过。失败轮次与指标见 [efficiency-0.2.2.json](evidence/efficiency-0.2.2.json)。

## 0.2.2 工程案例与 A/B（2026-11-22）

### 社区蓝图可行性

| 来源 | 内容 | 许可 | 结论 |
| --- | --- | --- | --- |
| ZwerOxotnik/CreateSchematics | 真实机械 NBT（面包厂、齿轮厂、安山岩机） | 仓库无 LICENSE 文件 | 不能随仓库分发 |
| createmod.com 等聚合站 | 用户上传蓝图 | 站点代码 MIT，上传件无逐文件授权 | 不能安全入库 |
| PlanetMinecraft / CurseForge | 存档/蓝图 | 多为保留所有权利 | 不使用 |

结论：**没有找到许可干净的社区蓝图源**，本轮没有把任何社区文件下载进仓库。

### 改用官方 Ponder 演示结构

DSH 使用安装的 Create JAR 内对应版本的 `assets/create/ponder/**/*.nbt` 作为演示数据。资产许可与源码 MIT 有区别，整合版改为在读取已安装 Create 时提取案例。下述文件路径为 DSH 验收分支当时的实现。`ponder/cases.py` 提取设备方块与相对坐标，生成 `ponder/knowledge/cases.json`（6 个场景：millstone、press/pressing、press/compacting、fan/processing、chute/smart、mixer/mixing），`PonderGuide` 在 `guide()` 命中对应场景时附带该案例，不新增工具组。

官方磨石案例的拓扑（相对磨石 `[0,0,0]`）：

- 侧面 `create:cogwheel[axis=y]` 在 `[1,0,0]`
- 下方 `create:large_cogwheel[axis=y]` 在 `[2,-1,1]`，再经 `[0,-2,3] axis=z`、`[3,-2,1] axis=x` 的大齿轮
- 进料：`[-2..-1,-1,0]` 的皮带 + `[-1,0,0]` 朝西的 andesite_belt_funnel

即：**侧面齿轮驱动、底面留给产出**。

### 工具修复

- `apply()` 按同批次相邻格推断轴/齿轮的 `axis`，水平轴排不再默认 `axis=y`。
- 写入后调用 `KineticBlockEntity.initialize()` 并置 `updateSpeed`，放置的动能块在首次回读前就加入网络。
- `chute` / `smart_chute` 知识条目：溜槽只能向下，不会提升。

### A/B（同一句自然语言委托）

| 轮次 | 案例 | 额外修复 | 请求 | 峰值上下文 | 结果 |
| --- | --- | --- | --- | --- | --- |
| e9 | 无 | — | 83 | 41,105 | 失败：模型称放置后动力网络不重建 |
| e10b | 有 | — | 99 | 39,993 | 失败：模型判断轴/齿轮接法异常，提前停止 |
| e11 | 有 | 轴向推断 | 90 | 49,810 | 失败：模型称所有工程方块 0 转、网络 none |
| e12 | 有 | 轴向推断 + 动能初始化 | 138 | 49,393 | 失败：正下方动力已能带动磨石（32 RPM），侧向抽取与自动进级未验证 |

案例卡没有带来可测的成功率或调用次数改善；主要阻塞在工具侧动力网络挂接与模型对磨石端口/物流的判断。

### 结论

- 案例库**技术上可行**（官方 Ponder 结构，MIT、同版本），但**当前没有明显帮助**，按要求**不继续扩大蓝图库**。
- 泛化测试（改布局/尺寸）暂缓：还没有可复现的成功自动线作为基线。
- 证据：[case-library-0.2.2.json](evidence/case-library-0.2.2.json)。
## 0.2.3 动力网络复验与侧驱拓扑（2026-11-22）

### 受控复验：apply 后的动能块能稳定读到转速

让伴侣用一次 `engineering.site.apply` 放 `creative_motor[facing=up]` → `shaft[axis=y]` → `millstone`，再分两次读取：

- 三块同一网络，全部 **16 RPM**；
- 隔几秒重读磨石仍是 16。

结论：写入后调用 `KineticBlockEntity.initialize()` 的修复有效，此前“工程工具放下的方块一律 0 转”的假阴性已消除（k1：7 次请求、12,457 token、12 秒）。

### 官方侧驱拓扑可直接驱动磨石

命令实测：磨石 (10,-56,10)、侧面 `cogwheel[axis=y]` (11,-56,10)、下方竖轴 (11,-57,10)、马达 (11,-58,10)：

| 方块 | Speed |
| --- | --- |
| 磨石 | -16.0 |
| 侧面齿轮 | 16.0 |
| 竖轴 | 16.0 |

即官方 Ponder 结构里的“侧面 axis=y 齿轮啮合、底面留给产出”是真的；之前模型报告“侧齿轮带不动”是**我们的自动定向把齿轮轴改成了朝邻面的 axis=x**。

### 回归修复

- 自动定向只保留给 `create:shaft`，齿轮轴向交回调用方；
- 新增磨石产出告警：下方被动能块占用时提示“产出被堵，改用侧面齿轮驱动”，下方为空时提示“产出从底部离开，放溜槽/桶”。

### 复跑结果

| 轮次 | 请求 | 峰值上下文 | 耗时 | 结果 |
| --- | --- | --- | --- | --- |
| e13 | 70 | 33,139 | 3m30s | 失败：模型称侧齿轮带不动磨石（自动定向回归） |
| e14 | 139 | 49,714 | 3m28s | 失败：去掉自动定向后模型仍判断不出电机输出面，未完成传动 |

### 崩溃核查

三次原生崩溃（`EXCEPTION_ACCESS_VIOLATION`，帧 `0x00007ffd52bf69e1`）都发生在**渲染线程**（栈上有 `class_310.method_1514`），崩溃栈里没有任何工程插件的 Java 帧；崩溃前世界已在约 40 秒内落盘，重启即可读回结果，模型轮通常也已结束。按环境/图形问题处理，仍需在干净客户端复现确认。

### 结论

工具侧两个缺陷（动能块未初始化、齿轮被错误定向）已定位并修复，隔离验证通过；但自然语言全自动两级产线仍未通过，失败点已收敛到“模型判断电机输出面与段间物流”。证据：[kinetic-0.2.3.json](evidence/kinetic-0.2.3.json)。
### check 施工前告警（0.2.3 续）

`engineering.site.check` 现在除了逐格 `rejects`，还返回 `warnings`：

- 电机 `facing` 那一面没有规划轴/齿轮/齿轮箱；
- 磨石底部被规划成动能块（堵住向下产出）或底部为空（提示放溜槽/桶）；
- 溜槽下方不是空气、溜槽或桶。

受控校验（k2，3 次请求、12,029 token）：方案 A 报“电机朝向不匹配 + 磨石底部空”，方案 B 报“齿轮被拒 + 溜槽下方是磨石”。说明告警能到模型。

完整委托 e15（82 次请求、46,719 token、2 分 01 秒）仍失败：模型自己引用了告警原文“faces meet the output face of a powered block”，但没能据此摆出正确动力链，最后提出只搭无动力的重力骨架。失败点已从“缺反馈”转为“模型落位决策”。证据：[check-warnings-0.2.3.json](evidence/check-warnings-0.2.3.json)。
### 最小可运行单元（0.2.3 续）

把任务缩到“一台磨石 + 侧驱 + 底部溜槽收集 + 3 圆石投料”，e16（132 次请求、49,888 token、4 分 11 秒）结果：

- 模型先把磨石从**底部**驱动到 64 RPM；
- 自己发现“底部那格既要接轴又要出料”，尝试把磨石抬高让位；
- 随后发现“侧面齿轮转 64 但带不动磨石”，卡住并停下（客户端第 4 次原生崩溃）。

但它漏了一条已实测的接线规则：**侧面 `cogwheel[axis=y]` 要沿自身轴向驱动**——竖轴直接放在该齿轮正下方即可（实测：磨石 −16、齿轮 16、竖轴 16）。模型把跨越齿轮面的轴当成动力源，所以判断“侧驱无效”。

这条规则已补进 Ponder 的磨石条目。证据：[minimal-unit-0.2.3.json](evidence/minimal-unit-0.2.3.json)。
### 无模型验证：两级垂直级联跑通（2026-11-22）

为把“工具/机制是否可靠”和“模型是否会搭”分开，本轮用命令在干净单机世界搭了完整两级链，全程无模型调用：

| 部件 | 坐标 / 状态 | 实测 |
| --- | --- | --- |
| 一级磨石 | (0,-55,0) | Speed **-16.0** |
| 一级侧齿轮 | (1,-55,0) `cogwheel[axis=y]` | 16.0 |
| 一级驱动 | (1,-56,0) `shaft[axis=y]` → (1,-57,0) `creative_motor[facing=up]` | 16.0 |
| 一级出料 | (0,-56,0) `chute` | — |
| 二级磨石 | (0,-57,0)，正好接一级出料 | Speed **-16.0** |
| 二级侧齿轮 | (-1,-57,0) `cogwheel[axis=y]` | — |
| 二级驱动 | (-1,-58,0) `shaft[axis=y]` → (-1,-59,0) 马达 | — |
| 收集 | (0,-58,0) `chute` → (0,-59,0) 桶 | **3 燧石** |

- 投入 3 圆石 → 成品桶 3 燧石，输入桶清空、两级磨石输出库存为空。
- **独立复投**再投 3 圆石 → 桶内 6 燧石，输入桶再次清空。
- 每批约 60 秒。

确认的规则：

1. 磨石由侧面 `cogwheel[axis=y]` 驱动，该齿轮必须**沿自身轴向**（正下方竖轴）取力；
2. 两级动力柱要放在**相反两侧**，否则第二级的齿轮位会被第一级的电机柱占用（本轮第一次布局就是这样让二级 0 转）；
3. 磨石产物从底部进溜槽，溜槽把物品送进正下方的磨石；溜槽不能向上，多级必须向下建。

模型复跑 e16（132 次/4.99 万 token）与 e17（143 次/4.94 万 token）仍失败：e16 已把磨石驱到 64 RPM 并自己发现“底部既要动力又要出料”，但不会给侧齿轮沿轴供力；e17 卡在电机输出面与竖轴端面的对齐，最后请主人给接法。

结论：**机制与工具端到端可靠，缺口在模型的 3D 接线决策**。下一步应做一个最小的确定性执行能力（如给定源与负载就给出接轴/齿轮坐标的规划回执），而不是继续加提示或告警。证据：[cascade-verified-0.2.3.json](evidence/cascade-verified-0.2.3.json)。

## 0.2.3 全自动产线验收通过（2026-11-22）

同一句自然语言委托，工具面与知识库与 e16/e17 完全一致，只把模型从 `deepseek-chat` 换成 `deepseek-reasoner`（high）：

| 项 | 结果 |
| --- | --- |
| 模型 | deepseek-reasoner，high |
| 请求 / 峰值上下文 / 耗时 | **20 次 / 34,840 token / 2 分 15 秒** |
| 产出 | 入口桶 (0,-54,0) → 一级磨石 (0,-56,0) → 二级磨石 (0,-58,0) → 成品桶 (0,-60,0) |
| 动力 | 创造马达 (0,-60,1) 朝上，两台磨石同网 |
| 独立复核 | 两台磨石均 **−64.0 RPM**；成品桶 **3 燧石**，输入桶清空 |
| 独立复投 | 再投 3 圆石 → 成品桶 **6 燧石**，输入桶再次清空 |

模型还自行铺了石头地板与玻璃罩。跨级转运全程自动，无人工搬运。

### 模型能力对照

| 模型 | 轮次 | 请求 | 结果 |
| --- | --- | --- | --- |
| deepseek-chat | e16 | 132 | 磨石驱到 64 RPM，但侧齿轮接线失败 |
| deepseek-chat | e17 | 143 | 电机输出面与竖轴对不上，停止求助 |
| **deepseek-reasoner** | **e18** | **20** | **整线通过，3→3 燧石并复投通过** |

结论：工具、知识、机制在 e16/e17 时已一致，差别只在模型——**3D 接线决策是模型能力问题，不是工具缺口**。证据：[acceptance-pass-0.2.3.json](evidence/acceptance-pass-0.2.3.json)。

### e18 当时待验

- deepseek-reasoner 仅一轮样本，后续见 e19；
- 手动安装与真实玩家体验未测；
- 其它配方链、水面船未重测；
- 客户端渲染线程原生崩溃仍未解释。

### 重复与泛化（2026-11-23）

同一委托再跑一轮（e19，21 次请求、34,101 token、1 分 51 秒），模型给出**不同布局**：

- 主体移到 x=-4 单列：进料桶 (-4,-51,0) → 漏斗 → 一级磨石 (-4,-53,0) → 溜槽 → 二级磨石 (-4,-55,0) → 溜槽 → 成品桶 (-4,-57,0)；
- 单马达 (-3,-58,0) 朝上，两台磨石同网。

独立复核：两台磨石 **−64.0 RPM**，成品桶初值 3 燧石；再投 3 圆石后 **6 燧石**，输入桶清空。

因此 `deepseek-reasoner` 目前 **2/2 通过**，且两轮布局不同（e18 用竖直溜槽进料、e19 用漏斗），不是背同一张图；`deepseek-chat` 在同工具同知识下为 0/2。

---

## 0.2.3 统一测试版整合（2026-11-23）

来源为 Work `7941717` 与 DSH `0a1266b`。保留 DSH 验收使用的 `fill`、`check`、`ports`、施工回读、轴向、动能初始化及实测端口说明。六张 Ponder 案例在运行时读取安装版 Create 的演示结构，卡片的锚点、尺寸、相对坐标、设备和属性均与 DSH 基线逐项相同。来源字段标注安装的 Create 资产路径，包内附源码索引和[第三方许可](../ponder/THIRD-PARTY.md)。

| 验收环境 | 结果 |
| --- | --- |
| DSH 单机自然语言基线 | `deepseek-reasoner`（high）e18/e19，两种布局分别 20/21 次请求，135/111 秒。每条线首投 3 圆石产 3 燧石，独立复投后累计 6 燧石。两轮均确认同网 −64 RPM 和全自动物流。旧 `deepseek-chat` e16/e17 留存为失败对照。 |
| Work 整合版隔离服，无 VS | 固定双磨石原件与原版 NBT 副本各 3 燧石、两台磨石均 −64 RPM。独立小麦链首次投 3 小麦得 6 面团，再投 3 小麦增 5 面团，输入与中间物料清空。夹具提供水源。 |
| Work 整合版原版 NBT | 原件和副本风扇均为 64 RPM，保留马达设置与筛选器，副本桶为空。游戏内同时核对磨石 Ponder 卡片、干跑、填充和桶的 6 个物品端口。 |
| Work 整合版权限和库存 | 区域外和生存伴侣操作被拒。原生 `observe` 拒绝施工与填充，世界快照不变。`ask` 模式验证空闲桶接受 3 小麦，实际物流从原料桶经漏斗流入成品桶，最终库存 0/0/3；占用、满槽、错误目标和替换有库存的方块均被拒。 |
| Work 整合版专服，真实 Fabric 图形客户端 | 固定飞行船与水面船均经伴侣装配、乘舵、推进、偏航、自动锚停；锚停观察 10.25 秒，位置和四元数不变。 |

[整合版测量数据](evidence/integration-0.2.3.json)单列无模型的回归。模型自然语言结果引用[固定 DSH 基线](evidence/acceptance-pass-0.2.3.json)。Windows 单机新装、历史原生渲染崩溃、第三方保护组合与整合 JAR 上的模型自主迁移交给 [DSH 接续验收](../../TASK-NUMEN-CREATE-GENERAL-ENGINEERING.md#dsh-接续验收)。`check` 的布局告警属于启发式，已验证的溜槽直连下级磨石也可能触发提醒，以实时 `ports`、转速和实际出料为准。

### 安装与校验

[下载 Numen-Engineering-0.2.3-test.zip](https://chatgpt.com/library?file_id=libfile_09c6835168a481919fec14002369d1e7&account_id=personal)，内含统一 JAR、`INSTALL.md` 与 `SHA256SUMS`。JAR SHA-256 为 `90703d5637eb3c11d5eebf19d67e860253d08a095dcd2673b7e4ff3dcfbdfa2c`。

- 基础依赖是 Java 17、Minecraft 1.20.1、Fabric Loader 0.17.2、Fabric API `0.92.6+1.20.1`、Numen 0.1.5-beta（内含 `numen_api` 0.1.5）、Create Fabric `6.0.8.1+build.1744-mc1.20.1`。
- 载具另装 1.20.1 Fabric 的 VS 2.4.11 和 Eureka 1.6.4，启用 `-Dengineering.ships=true`。VS 该发行包已内置 Fabric Language Kotlin `1.11.0+kotlin.2.0.0` 及 Forge Config API Port 8.0.3。
- 把解压后的 JAR 放进 `mods` 并移出旧版附属 JAR。按安装说明设置 `-Dengineering.site=x1,y1,z1,x2,y2,z2`，从新建的创造测试世界开始。
- 构建命令沿用上文。0.2.3 统一构建的 `LAB_VS_MODS` 目录提供 `vs.jar` 和 `eureka.jar` 即可，编译从该 VS 发行包提取它自带的 Kotlin 标准库。载具冒烟测试用 `LAB_ENGINEERING_SMOKE=ship`，水面组另设 `LAB_ENGINEERING_VESSEL=water`。无需额外的隔离实验命令模组。

## 2026-11-25 开源技术选型与通用工程能力对照

**结论｜维持 0.2.3。** 保留 Numen 原生设计、受限施工与建筑蓝图，继续用 Create 的实际状态验收机械。外部项目带来的主要价值是“验证后复用经验”和“结构化检查”，本轮没有证据支持替换执行器、追加模型链路或上线记忆框架。数据和脚本属于隔离研究。目标分支调查起点 `bad7b324adb24cfdde2411d76f556aab736f652d`，实测沿用 `25dde6a` 的发布 JAR。执行前已回读双方 AGENTS、任务书和 Minecraft HANDOFF。

### 可复用的现有能力

| 环节 | 0.2.3 已有的接口与实际含义 |
| --- | --- |
| 自由设计与建筑施工 | Numen `SkillRegistry` 和 `SkillTool` 分层加载，`building_design` 提供布局与施工知识，Lua `shape` 生成 Cells，`build.diff/place` 负责占用差异和实际放置。原生可选 `LitematicaApi` 由房主客户端提供投影、分层 Cells 和预览。房车 A/B 已实测程序化 Cells 与 `place`，没有实测 Litematica 文件导入。 |
| 可确定的工程动作 | `EngineeringSite` 的 `info/check/apply/inspect_many/ports/feed` 提供区域、白名单、检查、备份和受控施工。Create 的 `IRotate.hasShaftTowards`、原生转速/应力/来源与 Fabric Transfer API 提供实际设备状态。端口方向、占用、权限和写入范围由确定性代码检查，设计选址和整条方案交给模型。 |
| 知识和蓝图 | `PonderGuide` 复用 110 个教学场景索引、安装版配方和 6 个正式设备案例。教学转速是演示值。附属原版结构 NBT 去掉实体与库存，只保留批准的设备设置。格式、方块朝向和游戏功能分别验收。 |
| 已有回归 | e18/e19 的自动双磨石采用不同布局，两段均 −64 RPM，输出桶由 3 增到 6 个燧石且没有人工转运。本轮引用原始通过证据，未重刷同题。统一 JAR 的固定双磨石、面团、NBT、权限及飞行/水面船继续按既有记录计算。 |

[原生 Numen 源码入口及许可证](https://github.com/Dwinovo/minecraft-numen/tree/2f47e7b9c111a1a116360638f97bb853b7571b18) · [本仓库源码映射](../SOURCE-MAP.md) · [Ponder 版本和知识来源](../ponder/README.md) · [DSH 通过记录](evidence/acceptance-pass-0.2.3.json) · [0.2.3 集成记录](evidence/integration-0.2.3.json) · [历史房车验收](https://github.com/Msipla/-Moonlit-Halt/commit/c217dd44ac37ccf490c8484d8843e003fd208f47)

### 外部源码与取舍

下表日期为各次检出的源码提交日期。许可证以对应树的文件为准。`mcbuild` 的纯软件渲染单列测试，其他候选的上游演示或现有发布版不计入本轮 0.2.3 实机通过。

| 候选 | 查证与取舍 |
| --- | --- |
| **Voyager** | MIT，2023-07-27。 `voyager/agents/skill.py` 用 OpenAI embeddings + Chroma 检索成功技能，保存可执行 Mineflayer JS。轨迹另有 critic / feedback。原有 Mineflayer、Python、OpenAI/Chroma 工具栈与当前 Fabric 伴侣重复。借鉴“验收后登记、参数和前提可见、失败时回读”，用原生 Numen Skill 实现该思路。论文的原版迁移实验证明研究构想可行，无法代表 Create。 [固定源码](https://github.com/MineDojo/Voyager/blob/55e45a880755d0c8c66ca7fb5fe7962ac8974f89/voyager/agents/skill.py) · [许可证](https://github.com/MineDojo/Voyager/blob/55e45a880755d0c8c66ca7fb5fe7962ac8974f89/LICENSE) |
| **MineMind** | MIT，2026-07-08。 `agent/planner.py` 从 FTB Quests 前沿规划 JSON，`agent/actions.py` 定义有限原语及等待条件，`agent/daemon.py` 分发。上游记录 Forge 1.20.1 / Reclamation 采集和多步合成；运行依赖 Python、AgentLink、PlayerEngine / Player2NPC 与 Forge，部分走 Minescript、Baritone。借鉴规划与执行分离、可等待后置条件。任务 DAG 没有提供 Create 动力拓扑和装配真值。 [固定源码和许可](https://github.com/Boyan253/minemind/tree/8b9a1a309158f561222d1b746df09c500d6d957f) |
| **mcbuild** | MIT 代码，2026-07-10；包内 Mojang 材质与方块状态另有 NOTICE。Python ≥3.11，沙箱 DSL、体素、切顶/多视图、Sponge 导出和视觉回路在 `dsl/sandbox.py`、`render/views.py`、`export/schem.py`、`agent/loop.py`。游戏桥接 NeoForge，视觉模型另有开销。离线运行上游 162 项通过，包括 DSL、切视图与导出测试。初次 4 项被继承的 SOCKS 配置阻塞，隔离纯离线单测环境后全通过。可作为外部原型审图参考，当前优先用实际结构做入口/净空检查。 [固定源码](https://github.com/harrynull/mcbuild/tree/34ce86372d4c23f4d83d4e24fc256cb399d6c3e3) · [素材许可边界](https://github.com/harrynull/mcbuild/blob/34ce86372d4c23f4d83d4e24fc256cb399d6c3e3/src/mcbuild/assets/NOTICE.md) · [测试所用 Pro 不支持图像](https://api-docs.deepseek.com/quick_start/pricing/) |
| **Create Architect** | 2026-05-07 检出树无许可证正文或包许可字段，未确认再利用许可。README 写 Forge 1.20.1 / Create 0.5.1f 和 Aeronautics，源码是 Node/React/Express/Groq，`layoutEngine.js` 按载具类型套布局，`suSolver.js` 用手录表估算供需，`physicsSolver.js` 静态估算质量和升力。运行源码时，场地不连通、无水的水轮仍为远处钻头报告 4,096 容量、16 需求和充足余量；压机未列入应力表。解析导出字节得到 Sponge v2 `Schematic` 容器。0.2.3 的原版结构 NBT 不能直用该文件。淘汰为施工、动力验收和蓝图直入依赖。 [固定源码树](https://github.com/twentyss2/create-architect/tree/3deae4a94449bf8e4ae1089a69a7d4d6cea205b3) |
| **Minecraft Agent Builder** | 建筑 Skill 2026-08-18，无独立 LICENSE。MCP 配套仓库另有 Apache-2.0，2026-10-19，Node + Mineflayer，面向较新 Minecraft；README 所列批量操作、移动和读回另有测试源码。素建筑脚手架、不要封死施工入口、写后核对有参考价值。无需复制另一套执行器或未经许可移植 Skill。 [Skill 树](https://github.com/Spacecer2/minecraft-agent-builder/tree/9b6b15bb524768c7e4f93e9ca086463206603402) · [MCP 源码及许可](https://github.com/Spacecer2/minecraft-mcp-server/tree/82cc13ed946f010c7b53af50a1754dc9d40f0334) |
| **Litematica-GPT** | LGPLv3，2026-07-03。 `HighEffortAIWorkflow.java` 规划再转 DSL，`OpenAISchematicDSLParser.java` 解析结构，投影来自 Litematica。Fabric 1.21.1 / MaLiLib 0.21 系列和额外模型调用成本，不匹配 1.20.1 基线。借鉴“独立计划、可检查的数据、施工”组合即可。 [固定源码和许可证](https://github.com/lukeclaw/litematica-gpt/tree/4cd35109306baa97330869bbb367457a99bd1bec) |
| **Create Fabric 原生** | 主选。发行 6.0.8.1+build.1744，1.20.1 / Fabric API。对应版本的发布准备源码 `4c3fe77` 含 `KineticBlockEntity`、`RotationPropagator` 和 Schematicannon，代码 MIT，`assets` 权利另列。优先直接复用已接通的动力和配方，不转抄动力学表。Schematicannon 可作玩家可选打印流程，继续遵循它自身耗材与放置权限，本轮未把它接入自动施工。 [源码和许可](https://github.com/Fabricators-of-Create/Create/tree/4c3fe779d135d592548e4cb09da04d742d2637b7) · [已安装发行](https://modrinth.com/mod/create-fabric/version/HAqwA6X1) |
| **上游 Litematica / WorldEdit** | Litematica 官方 0.15.4 可选给 Fabric 1.20.1 房主装，依赖匹配 MaLiLib。LGPLv3，源码对照为后续维护分支 `afb3feb`（2026-07-26，源码版本 0.15.6-beta.1，和发行版分开）。`SchematicVerifier` 回读差异。WorldEdit GPLv3，7.2 源码 `b490447`（2024-02-29），`SpongeSchematicReader/Writer` 可作外部格式转换。优先沿 Numen 现有可选投影，WorldEdit 仅作离线互通或人工工具。两者方块对齐和材料清单不能证明玩家可走或机械有产出。 [Litematica 源码](https://github.com/maruohon/litematica/tree/afb3feb258aacdc52901217bec233e2bee880b21) · [1.20.1 发行](https://modrinth.com/mod/litematica/version/1ZTpW1lr) · [WorldEdit 源码和许可](https://github.com/EngineHub/WorldEdit/tree/b490447f9343f8374bd4271ac70eb7b88e175754) |
| **OR-Tools / MineAI** | OR-Tools v9.15 `551ad10`、Apache-2.0，`ortools/sat/cp_model.h` 提供 CP-SAT，适合成熟的多约束优化。Create 规则仍需自己建模，原型仅需小图搜索和原生回读，暂不引入原生求解器依赖。另查 `ailiujiarui/MineAI` 的 `d78abe6`（2026-10-22，LGPLv3），代码命名空间及 Skill、BuildApi、Litematica 和 GoalVerifier 仍在 Numen 家系内，本次未找到独立、已实测的 Create/VS 通用规划器。不作为另一套底座。 [OR-Tools 源码](https://github.com/google/or-tools/tree/551ad10d94835c99e5e1e684500d3db398c0e345) · [MineAI 源码](https://github.com/ailiujiarui/MineAI/tree/d78abe60be51d41cd1ca7b8ef0657475da73feed) |

### 隔离方法

- 四个独立、同种子创造世界，两道题分别采用 A/B 配对。1.20.1、Java 17、Fabric Loader 0.17.2、Create 6.0.8.1、Numen 0.1.5、未重编的 Engineering 0.2.3。两组同一工地区和权限，房主登录为真实协议玩家，发起委托前取消 OP，伴侣 `ask`。不挂载 VS/Eureka。实际模型回执 `deepseek-v4-pro`，关闭推理，4096 单次输出 Token，同题一个预算。与历史 high 推理数字不能做因果对比。
- A 使用现有配置。B 唯一变量是用 Numen 现有加载机制增加一条可选工程经验 Skill。正文由 e18/e19 提炼，只有观察停止条件、最小修复、回读与终结条件，无位置、方向、布局、机器模板和硬编码配方。模型可按同一自然语言自行检索。
- 盲修题是人工校准的共享动力压机/磨石夹具，断点不随委托提供。故障修复由伴侣完成，独立验收人为两工位各投 1 份原料，记录实际产物。空地题要求自主建可进入的带双工位小工坊，磨圆石和压铁锭，单动力。观察员只在停止模型后做独立验收。方块数量、模型回复与功能通过分别记录。
- 运行时需检查同一 Network、实际 RPM、压头和置物台净空及产物。静态房车用既有实测存档回放检查门口。Pro 没有图像输入，模型施工过程中也没有收到任何截图。

### 真实对照结果

| 任务与组别 | 模型请求 / 总 Token / 峰值输入 | 自然委托耗时 | 独立结果 |
| --- | --- | ---: | --- |
| 未告知故障的共享动力压机 · A | 15 / 233,713 / 19,759 | 140.1 秒 | 通过。只改 1 格，压机和磨石各 −64 RPM，同网。木桶 7 铜锭保留。独立试投得到 1 铁板、1 砂砾。 |
| 同一盲修 · B | 21 / 316,682 / 19,721 | 193.6 秒 | 同样通过。实际没有调用新增 Skill。差值只表明本次请求路径不同。 |
| 空地双工位工坊 · A | 26 / 509,255 / 25,882 | 294.5 秒，单次输出 `length` | 未通过。162 格外壳，地板 y=-52（比地表高 9 格）。无机器和可验入口，未达到试投前提。 |
| 同一空地工坊 · B | 29 / 656,250 / 35,640 | 420.4 秒，到点 | 未通过。第一轮加载了 Skill。把误放的高层地板移到 y=-60，落地 73 格，有压机、置物台和磨石。停机时两个马达各 +32 RPM、两台磨石与压机均 0；压头和置物台只差 1 层，未达到净空和试投前提。 |

各组的改动都在授权区内。总 Token 汇总回执中的 `usage.total_tokens`，包含重复上下文和缓存命中。峰值是单次请求最大输入 Token，不等于总消耗。盲修终结消息送达两组房主。工坊 A 的截断文字和 B 的未收尾都不计为完成。各题每组一次，严格任务通过数均为 1/2，不把这四次当作普遍成功率。工坊 B 比 A 落地更多机械功能部件，但未证实新增 Skill 能提高通过率或节省调用。

两组盲修均先读取当前轴朝向，再通过 `check/apply` 修复同一轴。两组都看到 `apply` 当刻的未连通警告，随后原生回读已恢复同网转速。试验前另有两次未启动模型的夹具校准，直接换错轴后，轴的 RPM 已是 0，压机仍保存 −64 并指向该轴；改用先移除目标压机、换错轴、再还原压机的夹具准备，确认压机 0、磨石 −64 后才发相同盲修委托。这个例子表明，单看某一刻的 Speed 或 Network 无法验证实际连接。追加游戏更新后的回读与真实产物校验价值比再叠一层设计提示更明确。

结构检查回放取两个已有房车世界快照。四分钟时双格门存在，但门内同高度下格为白混凝土，再往上站会碰顶。终局同一门下格为烟熏炉。程序准确区分了门尚在但邻位不可通行、门已被覆盖两种结构，零模型调用、零世界写入。只覆盖这个已知门口的离散几何，楼梯、开门动作、路径可达和玩家真走仍需 Minecraft 实机分别验证。新工坊的局部站位扫描也只作提示，没有当作玩家通行通过。

[采样指标、方块状态与关键回执](evidence/open-source-comparison-2026-11-25.json) · [历史静态房车 A/B 存档](https://chatgpt.com/library?file_id=libfile_e2d6f2145a348191adb0fcbbdec840de&account_id=personal)

### 取舍与接续

1. **先保持发布版，优先验证回执。** 如获正式改动授权，第一项仅在现有检查路径试验区分“施工完成等待动力稳定”“稳定后仍断开”“试产已确认”。用原生接面、Source 邻接和 RPM/应力核对，再在规定窗口后回读。保留对过载、传动断开、即时库存被物流移走的不同解释。验证延迟要有上限，避免为每次写入增加逐格等待。先用本轮盲修与既有双磨石回归，再测试多动力或隔离网络，避免无条件要求单源。
2. **建筑继续由 Numen 原生设计，工程工具按需介入。** 只有建到可检查的门口和通道之后，才封闭外壳。可把“两格净空、地面支撑、施工前后入口位置”留成隔离验收清单，或者在现有蓝图/施工工具回执中按需读取。模型仍自行设计布局。附属提供实际动力、物流、装配与验收，不给模型一个预制应答。只有局部站位反复失败时再验证是否需要可复用的小型检查，目前没有采用并行建造框架、外部视觉模型或新依赖的证据。
3. **对照还可进一步拆开。** 目前只验证了经验可选加载及结构回放，未证明 Skill 可迁移到陌生混合工程。若继续，优先用同一推理档对照“动力回读是否稳定”这个可测单变量，跨至少两种从未见过的机械或布局。待真实通过且复投、权限、库存均通过再沉淀经验。构建发布、正式集成、Windows 玩家验收和第三方领地兼容均另行确认。

## 2026-11-25 工程规划、施工前自检与真实功能闭环

候选 JAR `0.2.4-dev` 对应 `05ec6aff976c3fd076039a09ebadf850f091bb38`，SHA-256 `0186ac6f05ea2baa1b1e19ef6fa8d04ebcd3be08d0774003491b034b08f90389`。运行环境为 Java 17、Minecraft 1.20.1、Fabric Loader 0.17.2、Fabric API 0.92.6、Numen 0.1.5-beta、Create Fabric 6.0.8.1。可选 VS2 2.4.11、Eureka 1.6.4。0.2.3 发布件 SHA-256 `90703d5637eb3c11d5eebf19d67e860253d08a095dcd2673b7e4ff3dcfbdfa2c`。 [候选下载与安装](https://chatgpt.com/library?file_id=libfile_320f61dc3c088191b251dcdedd5a4635&account_id=personal)

- `apply` 给动力更新留 4 个服务端刻，批量回读最多 16 个位置，包括邻接负载。应用后核对 Create 的实际接面、来源链、Network 和过载。保留原始 RPM 并指出断开、过载、残留来源及未验证状态。来源超出已加载工地或 32 跳时按未验证处理。普通方块无需等待。独立网络分别判断。
- `info` 带中心地面和站位高度，`survey` 查看其它列。修正磨石和溜槽“下方”的读取方向。`check` 与写入共用朝向、重复格、旧状态与库存预检。`plan` 对当前世界和候选改动一起检查入口脚位、头部净空、支撑、压头下空气和第二格置物台。
- `route` 编译模型给出的相邻路径，`connect` 按模型选择的端点和避让格找小型正交路径。两者只读，现有 `apply` 执行施工。延用 64 格施工批次、现有权限和白名单、原版 NBT 安全过滤及船体机制。静态检查和动力回执只证明各自的条件，通行、加工和运输用游戏过程验收。

### 自主委托与独立试产

隔离专服使用同一实际 `deepseek-v4-pro`，按样本分别使用高推理或关闭推理。单次上限 8192。玩家与伴侣均为创造模式，派工时主人非 OP，伴侣为原有 `ask` 权限。成功工坊的提示给出压头间距、平地基准和施工顺序，坐标、路径、外壳及设备由模型自行确定。面团实验在委托前只额外提供一只水桶。验收脚本在模型停止后独立投料或行走，无设计和施工介入。

| 自主样本 | 请求 / Token / 模型墙钟 | 实际结果 |
| --- | --- | --- |
| 共享动力盲修 | 14 / 201,780 / 127.675 秒 | 一根错轴修复；压机和磨石 −64 RPM。独立得到 1 铁板、1 砂砾，原桶 7 铜锭保留。 |
| 空地工坊，候选早版及简短提示 | 24 / 485,009 / 480.433 秒 | 274 格，两负载同网 −32 RPM。压头紧贴置物台，入口不通，未满足完整验收。 |
| 空地工坊，最终 JAR、通用几何条件 | 16 / 283,770 / 216.528 秒 | 模型完成 196 格、有屋顶的工坊并回复；同网压机 −32、磨石 +32 RPM。独立试产 1 铁板、1 砂砾，静态有到两工位的可通行路线。 |
| 自动面团，候选早版 | 45 / 1,028,797 / 540.619 秒 | 实际磨出面粉；关服取样 RCON 超时，整线未验收。 |
| 自动面团，最终 JAR、施工顺序提示 | 38 / 1,123,063 / 510.401 秒 | 模型搭过动力网，发现风扇朝向问题后拆掉，截止时仍未完成重建，未从输入桶产出。 |

0.2.3 高推理 8K 工坊轮无施工，另一个关闭推理的 0.2.3 轮在关服前取样超时。候选早版高推理 8K 终止于 `length`。完整记录包含另一次最终 JAR 空地轮，其中压机产出铁板，但顶面被错误放置的圆石堵住了磨石，也没有建完外壳。调整包含工具、任务提示和每轮时限，样本少，完成轮的请求和 Token 下降只代表该样本。生产模型默认配置未调整。

恢复后使用同一候选 JAR 的另一个空场重复了工坊实际试产（27 请求、623,193 Token、320.398 秒；缓存输入 566,784，峰值输入 30,126）。模型独立搭起石墙和屋顶，修改 619 格且无越区，压机 −32、磨石 +32 RPM。独立试投得到 1 铁板、1 砂砾；截止前未收到模型最终回执。 独立生存玩家在真实 Fabric 图形客户端从门外穿过入口并走至两个工位。只在室外起点定位，后续全程按正常移动键，保留真实服务器坐标与截图。 在该存档的副本中，验收员只清空前次产物后重投两份原料，原装置再次各产出一份铁板与砂砾。

面团恢复轮提前给出通用轴向和物流条件，仍在施工前因 `length` 结束（15 请求、328,322 Token、314.700 秒；缓存输入 270,080，峰值输入 31,593）。同 JAR 的独立固定机械样本两次各投 3 小麦，成品桶 0→7→11，磨石与风扇 −64 RPM，均清空小麦和面粉。固定样本由测试夹具搭建，验证当前配方、动力和运输机制。模型自主面团整线继续作为接续目标。

最终 JAR 的两套原生专服载具夹具也重新通过，飞行船及水面船分别偏航 66.4° 与 65.7°，随后锚停。

### 已验证回归与阶段边界

| 用例 | 独立游戏结果 |
| --- | --- |
| 断开、恢复及异网 | 错轴后 Create 原始显示 −64 RPM，候选识别 `stale_source`。复正后压机 −64、同网磨石 +64，另一电机风扇 +32 RPM。 |
| 过载 | 固定夹具临时限制马达容量，原始 RPM 64，实际停止，Network 应力 512 / 容量 256。默认容量未改。 |
| 施工预检及寻线 | 阻塞脚位和缺置物台会被定位。候选线路避让物流并在独立夹具编译 17 格，压机、磨石实际各 −64 RPM。 |
| 库存、权限及 NBT | 越界、非创造、观察权限写入、满槽、带库存容器替换均被拒绝。小麦即时转运得到 3。双磨石原件和副本各在两轮试投后从 3 增到 6 燧石，两台 −64 RPM，输入清空。 |
| 飞行和水面船 | 候选系列在真实 Fabric 客户端和专服完成装配、乘舵、移动、偏航与锚停。飞行用前一构建，水面用最终 JAR。 |

此前临时世界和原始日志在执行环境重启后不可恢复。仓库保留先前读回指标的摘要，恢复后新跑的原始记录单独随证据保留。既有 0.2.3 保持可回退，Windows 安装及玩家验收待确认。本阶段取得候选交付所需的工程改进，并保留尚未达标的自主工程指标，后续优先进入现有装置小步调试、及时验证方向和物流，避免清空有用施工及重复查已知配方。

[阶段指标](evidence/planning-and-execution-0.2.4-dev.json) · [新跑原始记录、存档与截图](https://chatgpt.com/library?file_id=libfile_66f23e3189808191a5d905194e242e25&account_id=personal)

## 2026-11-25 通用物流规划、多阶段产线与现有工程改造

测试候选 `0.2.5-dev` 对应源码 [`23dc232`](https://github.com/Msipla/-Moonlit-Halt/commit/23dc232d09af97bec391864ce0b23ef36edfb336)，JAR SHA-256 `d936a60cbacd51d7941d76b7d5c2195a304119223e0b58d47b250327ddfc091c`。Minecraft Java 1.20.1、Java 17、Fabric Loader 0.17.2、Fabric API 0.92.6、Numen 0.1.5-beta、Create Fabric 6.0.8.1。统一 JAR 保留可选 VS2/Eureka，安装与 `build.sh` 用法见包内说明。 [测试安装包](https://chatgpt.com/library?file_id=libfile_0c5988a473d88191b6fe6058d1afa8bb&account_id=personal) · [原始日志、读回、存档及图形证据](https://chatgpt.com/library?file_id=libfile_9c886875a15c8191bd24c81e8435ed6f&account_id=personal)

- `overview()` 一次返回已加载工地内的设备、桶、漏斗和溜槽。`recipe(配方类型,输入ID)` 从实际服务端配方管理器读取，匹配标签并以短 JSON 返回概率及产量，避免 Lua `print(table)` 只给地址和 Ponder 反复搜索。
- `flow` 从模型选择的端点规划下行物流，支持桶、磨石、置物台、漏斗与垂直溜槽，最多四条短链。支持避让、候选覆盖、对指定路径读回、复用现有输送件和检查锁定。输出仍用 `apply` 的预检、审批、备份和 64 格批次施工。上行、传送带或其他设备留给原生玩法与后续验证。
- 新风扇读回调用原生 AirCurrent，分开干燥气流和水洗是否到台。新增批量智能溜槽遵循原生邻接红石状态。动力寻线保留显式 `axis/facing`，尝试匹配的其它物理接法。`connect` 可从已转且有空闲接面的原传动追加支路，并保留原件。

### 自主实机

使用 `deepseek-v4-pro`。空地同条件对照均为 8192 单次上限、关闭推理、非 OP 创造主人、伴侣 `ask`、480 秒。分段引导组另列，含通用工具接口与施工顺序，由模型选址和布置。面团空地委托额外给一只水桶。独立验收员在模型施工停止后只做试投或走路。盲修夹具由验收员事先设置一处未知故障，初始快照水仍在扩散，差异单独核对为 130 格原生水体演化和一格漏斗改向，施工无越界。

| 工况 | 请求 / 总 Token / 模型窗口秒 | 实际结果 |
| --- | --- | --- |
| 面团候选初构建 | 0 / 0 / — | Lua 注册参数 `type` 与内置名冲突，修正后重新构建 |
| 面团 0.2.4-dev 同条件 | 35 / 835,286 / 480.4 | 截止时 4 格改动，尚无完整物流 |
| 面团初版物流工具，同委托 | 21 / 552,852 / 480.4 | 动力和部分布局落地，风扇显式朝向被寻线覆盖 |
| 面团，加分段提示 | 38 / 897,178 / 540.7 | 生成运输候选。风扇误朝下仍需改支路 |
| 面团，最终 JAR，关闭推理 | 42 / 885,900 / 480.4 | 截止时 1 格改动，未具备完整独立投料条件。结束 `wall-clock timeout`，无最终回执 |
| 面团，最终 JAR，高推理 | 11 / 224,572 / 413.2 | 截止时 0 格改动，未具备完整独立投料条件。结束 `length`，无最终回执 |
| 空地石料线 | 34 / 722,242 / 421.9 | 自主试投得 3 燧石，独立两轮到 6、9，原料与中间库存清空。按时回执，实际桶坐标见世界读回 |
| 现有面团线物流盲修 | 24 / 600,645 / 310.4 | 仅一格漏斗东→西，7 铜锭保留，独立两轮成品 0→5→8，截止前未回复 |
| 原工坊首轮改造 | 18 / 430,451 / 480.5 | 独立取样未完成，另跑场景。No reply within 45000ms，未核得完整结果 |
| 原工坊补跑，最终 JAR | 15 / 215,524 / 420.5 | 结束 `wall-clock timeout`，无最终回执。No reply within 45000ms，未核得完整结果 |
| 最终 JAR，接续模型的半成品 | 28 / 513,239 / 289.6 | 仅复制物理存档，未带 Numen 归属。现有机件被正常保护，模型回执 `break other` |
| 最终 JAR，保留归属接续 | 17 / 726,071 / 275.3 | 独立投料未通过完整首投及复投。结束 `wall-clock timeout`，无最终回执 |

同条件新工具轮将面团探索由 35 次请求降至 21 次，并落地更多设备，但两轮均未完成，无法据此推断通用效率。实际安装的 Numen 包已包含原生 Compactor 和上下文窗口管理。本次复用现有上下文机制，加入简短位置、配方和物流读回。实测仍存在重复查询，未确认原生压缩带来的额外收益。供应商返回的每轮用量、缓存、峰值输入和粗粒度工具耗时保留在原始证据。表内使用封存时已取得的用量，独立试投耗时另计。

接续试验中，单独复制世界区域会遗漏 `numen_placed.dat` 与伴侣身份。保留原隔离世界归属记录可避免把同一伴侣的已施工格变成外部产权，且审批规则保持原状。完整归属接续轮保留旧伴侣背包，启动夹具额外发放一只备用水桶。

### 真游戏固定回归

| 用例 | 读回与验收 |
| --- | --- |
| 最终 JAR 物流 | 配方、5 个规划运输件、干燥和水洗回读、两轮独立试投。成品桶 0→6→9 |
| 最终 JAR 动力和支路 | 原 17 格线路两设备 −64 RPM。复用旧轴的支路 5 格，新增负载 +64 RPM，不改旧轴。显式错误朝向被拒绝 |
| 最终 JAR 断轴及异网 | 原始 −64 RPM 断轴识别为 stale_source，复正后旧负载恢复，异网风扇 +32 RPM，批量动力回读仍为 4 刻 |
| 最终 JAR 双磨石与 NBT | 双磨石原件和 NBT 副本分别 3→6 燧石，共网且无越界 |
| 最终 JAR 原生观察权限 | observe 模式拒绝 apply 和 fill，世界无变化 |
| 最终 JAR 过载 | Network 应力 512、容量 256，原始转速 64，候选报告实际停止 |
| 最终 JAR 供料库存 | 即时转运仍核对到 3，满槽、错误目标和带库存替换被拒绝 |
| 旧工坊扩建固定夹具 | 验收员用最终 JAR 接出支路及两段物流，新增 10 格，原有改动 0 格，铜锭保留 7。新桶独立复投 3→6，原压机及磨石仍产出。真实生存玩家同时实测入口与两原有工位。 |

保留 0.2.4-dev 的双工位、真实入口通行和 VS/Eureka 飞行／水面船已验收证据。最终两包的 7 个可选船舶类逐字节相同，本轮主测新物流与传动。Windows 玩家安装仍待验收。

完整归属接续中，同一模型局部改了 4 格并调整马达。最终风扇朝北且 +64 RPM、磨石 −64 RPM，真实快照没有水格，残留面粉停在置物台。空地面团的下一步是先锁定最小水洗段，复用已成立的动力和进料，仅在观察到明确阻塞时修转运或风扇。

原工坊自主改造两轮结束取样遇 RCON 超时，原始日志保留，不能核定自主成败。已有设备是否能留在原位接轴仍是下一轮优先验证点，当前 connect 的目标负载需要空位置。

[阶段指标](evidence/logistics-and-retrofit-0.2.5-dev.json)

## 2026-11-26 工程专项教学与 Skill 迭代

按任务书 `a7a466b` 冻结 `23dc232` 的 0.2.5-dev。JAR SHA-256 为 `d936a60cbacd51d7941d76b7d5c2195a304119223e0b58d47b250327ddfc091c`。实测使用 Minecraft Java 1.20.1、Fabric Loader 0.17.2、Numen 0.1.5-beta、Create Fabric 6.0.8.1。使用独立新世界和 loopback 服务端，实际 API 收据为 `deepseek-v4-pro`，关闭推理，单次输出上限 8192，非 OP 创造主人，伴侣 `ask`。同题对照共用题面、出生条件和权限，两个版本均让模型主动读取技能。裸基线没有额外 Create Skill 或读技能句子，单列。小单元限时 260 秒，面团和石料 420 秒，盲修 310 秒。考试无教师施工，既有故障件仅在开考前布置，考官在封存后投料、读取存档。

### 教材与操作练习

- 用户提供的《如何让AI帮你建造你无法完成的超巨型建筑？》自动字幕约 6:18–7:29、8:55、10:25 和 12:11 讲拆解、评阅、拿新样本复验和教师先掌握施工。《GPT 6-Astra耗10亿token建造巨型空中城市》约 2:14–2:36 讲专项迭代也可能因目标含混变差。采用这些方法组织教学，未取得作者完整 Skill、网盘或教学资产。
- Colsat 的 [2x speed automatic encased fan](https://createmod.com/schematics/2x-speed-automatic-encased-fan) 标记 MC 1.20.x / Create 0.5.1。NBT 是 6×5×12，143 格，两个相向风扇共享每道介质，轴端接链式传动。隔离用原版 `place template` 导入仅方块状态版本，先清空全部实体和动态方块实体字段，包括库存、过滤器、动力网络缓存和传送带数据。目标安装版仍加载 8 个风扇、两个静水源，8 个风扇均转动，实测 ±16 RPM。没有验证产物或原带物流，原 NBT 和导入存档没有散发。 [网站条款](https://createmod.com/terms-of-service)
- Skymiku 的 [Manual Processing Hub](https://createmod.com/schematics/manual-processing-hub) 标记 MC 1.20.x / Create 6.0.8。NBT 是 14×6×17，172 格，链传动、朝下风扇、介质与下层置物台分层。其中一路介质位原件是树叶，按作者说明需要放水。只静态解析，该模板一轴长 17，超出本次 16 格工地边长，含传送带、混合机等未进入本题批量工具的机件，不扩大测试工地。两个社区 NBT 均有动态方块实体字段，无证据表明获得二次分发许可。
- [Bread Processing Machine](https://createmod.com/schematics/bread-processing-machine) 标记 MC 1.18.x / Create 0.5.0。只把作者描述的工序分层作为参考，未将其计入目标环境蓝图或实测。
- 从实际安装的 Create Fabric 6.0.8.1 读取风扇、压机、磨石、智能溜槽四件 Ponder 结构及配方。Ponder 是示意教材。运行能力以游戏内的转速、气流、库存和实投为准。官网公开源码许可并不覆盖玩家上传的蓝图。 [Create Fabric 源码](https://github.com/Fabricators-of-Create/Create)

教学按单元到产线推进。老师用真实附属接口搭了两套不同朝向和高度的 9 格受控水洗点，原生水桶点水格下面的实体底板。两套分别读回静水、风扇 +64 RPM 和 `washing_reaches_depot`。第一次用掉落实体投料的东向测试没有落到台面。改用考官直接向置物台输入槽投一份面粉后，未改动东向工位的副本与南向工位都独立首投和复投各得到 1 面团，均无越界。验收员只在教师用例设置站位、搭建工位和投料，学生用例自行站位及施工。蓝图不用作学生输入，也没有提供任何考试的绝对坐标。

现有 `create_kinetics` 入口通过原生 `skill` 工具挂一份按需读取的生产参考，保留旧水磨坊文档。V1 从空气、动力、物料和产物分项验收入手。V2 加入已复验的相对位置和先定端点打法。V3 根据失败缩短为 318 英文词，集中讲工位抬高两格、完整配方类型和过滤器字段。V4 针对回放中的寻路垫入水道，补上创造伴侣飞抵干侧围挡，以及点击后直接读取静水格。教师用 `numen.move.fly` 在南向工位飞 9 格，读取站位后原生放水，气流实测覆盖置物台。服务端日志与发给 DeepSeek 的工具结果都确认实际加载了参考文本。

### 考试与独立验收

| 工况 | 请求 / 总 Token / 模型窗口秒 | 最终读回 |
| --- | --- | --- |
| 水洗，裸基线 | 31 / 708,960 / 260.3 | 独立两次各 1 面团，3 格越界，未通过围水 |
| 水洗，原 Skill | 21 / 382,000 / 174.2 | 3 格施工，风扇 +32 RPM，无置物台或水 |
| 水洗，V1 | 28 / 549,158 / 260.3 | 12 格施工，有围挡及置物台，风扇 +16 RPM，无水 |
| 水洗，V2 | 24 / 410,019 / 260.5 | 无施工；收到一次 HTTP 503 后重试 |
| 水洗，V3 首跑 | 20 / 382,737 / 未封存 | 动力和水源读回；置物台低了两格，末次独立读回未取得 |
| 水洗，V3 重跑 | 28 / 476,660 / 260.2 | 15 格施工，风扇 +32 RPM；寻路垫入水格，无水 |
| 水洗，V4 | 23 / 489,348 / 260.5 | 风扇 +256 RPM，放出 1 个水源，4 格水越界；首投仍是面粉 |
| 空地面团，裸基线 | 40 / 1,097,052 / 420.4 | 218 格变更，9 格越界，无独立产线试投 |
| 空地面团，原 Skill | 36 / 969,038 / 420.5 | 18 格施工，马达、磨石和风扇转动，无水 |
| 空地面团，V2 | 28 / 681,304 / 420.6 | RCON 超时；仅恢复自动存档，无最终验收 |
| 陌生安山岩，V2 | 30 / 477,493 / 420.3 | 仅查配方，无施工 |
| 陌生安山岩，V3 首跑 | 4 / 52,358 / 未封存 | 确定真实两道配方，伴侣断连且无封存读回 |
| 陌生安山岩，V3 重跑 | 43 / 1,072,892 / 420.4 | 10 格施工，双磨石各 +32 RPM；原存档独立投 3 安山岩，仍在输入桶，成品 0 |
| 旧石料题，V3 回归 | 15 / 259,751 / 未封存 | 完成方案，施工调用期间断连，未核验回归 |
| 过滤盲修，V2 | 31 / 561,842 / 310.3 | 过滤仍为小麦种子，无改动，首投 0 面团 |
| 过滤盲修，V3 | 37 / 836,775 / 310.6 | 截止前定位到错误过滤器，未修改；首投 0 面团，保留 7 铜锭 |

同一安装版实测 `recipe("create:milling","minecraft:andesite")` 返回 200 tick、1 圆石，`recipe("create:milling","minecraft:cobblestone")` 返回 250 tick、1 砂砾，两道产物概率均为 1。只用 `milling` 或猜测 `crushing` 的轮次在查配方阶段反复返回空数组。V3 安山岩复跑读完参考后即找到了正确链条，但该次模型在施工前断连。原 V2 安山岩轮 30 次调用、477,493 Token，封存零改动。该次回执核实了配方查法和模型读取参考后的局部变化。

面团 V2 结束取样遇到 RCON 45 秒超时。恢复的是截止前的自动存档，其中风扇、磨石在同一动力网，分别为 +64、−64 RPM。没有输入和成品桶，快照范围读到 97 格水，1 格新增水越界。该轮没有完整独立试投。原 Skill 面团轮有动力和部分设备但无水。模型持桶在低位交互可误点侧面，气流可在水源放好后仍因置物台太低而错过。受控水洗需同时看静水、置物台高度、气流及界外副作用；位于围挡上方的正确站位必须实际读回，不能从水桶动画推定。

安山岩 V3 重跑采用两路本地竖轴驱动，两个磨石实测均为 +32 RPM。模型投放的 3 安山岩仍在输入桶，桶紧贴磨石，直接叠放没有自动取料。原取样脚本额外要求一个马达，题面没有这项要求。考官复制封存世界，以实际运行的两路动力验收，只向原桶投料，没有增加漏斗或替模型改线。75 秒后 3 安山岩仍在输入桶，砂砾为 0，未进入复投。

在原版附属机制内修智能溜槽配置时，`apply().cells=0` 可能仍成功，因为这个字段统计方块状态写入，过滤值是方块实体数据。要读取 `inspect(pos).filter` 或前后的 Filter NBT，继而首投和复投。V2 盲修承接旧线，只读观察，修前后均为小麦种子过滤；首批面团为 0。教师只改这一个过滤值，保留 7 铜锭和原方块配置。教师独立两批使成品桶从 0 到 5 再到 11，原方块状态变化 0 格。教师和学生分开统计。

模型用时与独立试投分开记录。请求数包含 provider 无用量回执的 503；Token 和缓存是有用量回执请求之和。粗工具耗时由服务端 dispatch/result 秒级日志配对，跨线程等待纳入、并行项可能重叠。原 Skill 水洗的 provider reason 为 `stop`，发回的文字仍未完整报告气流和置物台。进程中断、取样失败与完成失败都单独标记。候选参考按需读取，默认工程资料与水轮教学沿用此前基线。

**阶段判断。** 已形成可复用的实机课件。教师水洗与过滤修复闭环均通过，模型能把真实的配方类型用在陌生题，也能在修复题中指出出料过滤错误。现有对照没有证实稳定提高自主施工通过率或总耗时。继续使用原默认 Skill 和交付 JAR，V4 供隔离教学复试。0.2.5-dev 已验收的旧石料和旧物流成功记录依旧有效。V3 的旧石料回归在模型调用施工期间断连，本轮未核得新的通过结果。Windows 玩家安装验收仍独立。

下一轮按可独立验收的单元接续。先把考官已验证的侧面站位、单格空腔和气流检查作为水洗开工条件，在第二个新布局重复；然后再串接自动进料和取料。过滤维修先读第一个出料口的过滤值并做一轮小剂量复投，再决定是否调查后续物流。陌生双磨线用同网分段读回，实际传动没通之前不用成品桶状态推断进展。实测服务优先串行，建成一个区段就保留快照，结束时先确认模型已停、然后独立投料。恢复世界需一起保留伴侣与施工归属 NBT。

### 试用与接续

沿用 [0.2.5-dev 安装包](https://chatgpt.com/library?file_id=libfile_0c5988a473d88191b6fe6058d1afa8bb&account_id=personal)；这轮没有重编 JAR。候选 Skill 的精确文本在 [原始证据与隔离存档](https://chatgpt.com/library?file_id=libfile_cc294de6057481918220530f7cd09fba&account_id=personal) 内的 `skills/v4`。试用时备份现有 `config/numen/server/skills/createlab/create_kinetics`，再用该目录中的 `SKILL.md` 和 `references/` 替换实验实例的同名内容并重启。在一条 Create 委托中让 Numen 查询 `create_kinetics` 和 `references/production.md`，在日志核对两个 `skill → ok` 回执。回退时恢复原 Skill 目录，再重启实例。保留原 `watermill.md`，无需替换 Mod 或改权限。

复跑在克隆仓库的上一级解包为 `trials/`，仓库目录名为 `moonlit`。按 [复现说明](../REPRODUCE.md) 安装隔离依赖，准备 `LAB_MC20` 对应的 Fabric 服务端与 `rcon-client`、`prismarine-nbt`，设置 `LAB_JAR` 指向已核验的旧 JAR。脚本默认 Java 位于 `LAB_MC20` 的相邻 `jdk17/bin/java`。用 `python3 trials/harness/launch.py wash:taught-v4:26596:trials/skills/v4/SKILL.md` 在空目录启动，会在终端不回显地读取 API 密钥。端口仅使用回环地址。考试存档包含区域、玩家、伴侣和施工归属数据。非模型教师实验的命令与流程在 `harness/compare.cjs`，配方、教材结构计数和每轮用量在 [结构化证据](evidence/teaching-0.2.5-dev.json)。

东向水洗工位的 [真实图形客户端截图](https://chatgpt.com/library?file_id=libfile_cfdf982514188191895e9781656fd237&account_id=personal) 来自原存档副本。它展示教师工位，学生施工记录以逐轮实测数据和原始快照为准。

## 2026-11-26 工程施工与助手实体行动解耦

候选 JAR [0.2.6-dev 安装包](https://chatgpt.com/library?file_id=libfile_67a3ea48baa4819188f8d634cd6f9a0f&account_id=personal) 对应 [源码 `3c6ef01`](https://github.com/Msipla/-Moonlit-Halt/commit/3c6ef01ff3dacaa69e95201180ccd0f5c46829ad)，SHA-256 `071821cce873840c3ddf18a29308287b207ec41dd19b30e0d31170b1e04afc03`。保留 0.2.5-dev 的原包及原始实验。候选沿用 Minecraft Java 1.20.1、Fabric Loader 0.17.2、Java 17、Fabric API 0.92.6、Numen 0.1.5-beta、Create Fabric 6.0.8.1，船舶可选 VS2 2.4.11、Eureka 1.6.4。候选包包含确切受测的 Skill 和安装说明。Windows 玩家安装另验。

### 执行路径

- `engineering.site.apply`、`flow`、`inspect_many`、`feed` 继续走原生服务端流程，保留 64 格批量、方块白名单、区块已加载、同维度在线创造房主与创造伴侣、审批、归属和库存检查。新 Skill 先选端点，直接施工和读回。伴侣可停在干处，伴侣身份仍用于授权与归属。
- `water(source,tail)` 在单格或相邻两格干燥水道放一个原版源。要求随身水桶，检查工地边界、围挡、底板、目标及上方流体，并在待授权后重查。水源与余流由 Minecraft 正常更新。尾部支持已验证的 Create 置物台。创造模式保留水桶。用随身空桶及原生破坏权限调用 `drain_water(source)`，等待余流消退后再改围挡。原生手动交互和普通陪伴仍可用。
- `apply` 返回 `configured` 实际设置。纯过滤配置可返回 `cells=0`，读实际过滤和产物。物流规划器指出输入桶紧贴机器造成的缺口，保留垂直漏斗或溜槽的实际输送件。`feed` 沿用现有空测试桶和少量测试品约束，新增 1–3 安山岩。
- 工程区约束作用于附属接口。原生 Numen 寻路、垫脚和施工仍遵循原来的全局权限，本轮没有加入拦截原生工具的强制模式。自主移动基地第一轮调用了原生 `numen.build.place`，25 格工程区底面石块改为橡木木板。该改动在隔离世界且作为失败封存。工程委托使用直接接口及受测 Skill。

### 固定回归

- 东向、南向分别实测直接批量造台、开口拒放、observe 拒放、放水、收水回流、复放。助手坐标和桶库存保持，风扇 +64 RPM，独立首投和复投各 1 面团，两轮均无越界。
- 在真实隔离服要求水源请求先等房主审批，实际 Stop 执行上游同一取消入口，另外使用原生 120 秒征询截止与断连重连。三项均读回目标仍为空气，迟到许可未触发旧写入；回到正常 `ask` 后新水源操作、独立水洗和原生走路通过。测试探针只在隔离服务端使用。
- 固定小麦面团整线在助手原地完成。规划器选出三段物流，测试写错再修正一个过滤值，直接放水、启动自动进料。一台马达同网供能，磨石和风扇各 −64 RPM，独立两批后成品 0→7→10，无残料及越界。
- 已运行无 VS 的输入库存、满槽、错误目标、observe 只读、旧石料线和 NBT 副本回归。石料原件和 NBT 副本均同网，独立两批各从 0→3→6 燧石。旧 0.2.5-dev 留作回退。
- 核对远端 DSH 旧交接及移动基地分支，已按原船舶接口做候选版 VS2/Eureka 固定回归。真正组装、乘舵、前进 2.30 格、偏航约 65.6°、锚停后位姿稳定。当前船体白名单仍未包含 Create 加工方块。船内乘行与移动加工须分别证实。

### 自主对照

同题水洗三组使用真实 API `deepseek-v4-pro`，关闭推理，8192 单次输出上限，260 秒截止，同世界种子、初始工位、权限和题面。房主为非 OP 创造玩家，助手 `ask`。改 JAR 和改 Skill 分两组。测得小样本说明直接放水可减少依赖，复测数量有限。独立考官仅投料和观察，实际通过要同时满足产物与无越界。自动化初期快照保留可恢复的伴侣与归属。模型用量、缓存、峰值上下文、工具秒数、原生走位和终止回执见结构化证据。

| 工况 | 请求 / 总 Token / 秒 | 独立读回 |
| --- | --- | --- |
| 0.2.5-dev ＋ V4 水洗 | 26 / 560,312 / 260.2 | 风扇转动，未放出水源。 |
| 0.2.6-dev ＋ V4 水洗 | 24 / 477,135 / 260.3 | 两次各 1 面团，无越界。仍触发 3 次原生移动任务，并有寻路放块记录。 |
| 0.2.6-dev ＋ 待命 Skill 水洗 | 15 / 275,585 / 132.4 | 11 格净改动。水洗气流到台，助手坐标不变，无寻路及垫脚。两次各 1 面团，无越界。 |
| 自主过滤盲修 | 19 / 369,593 / 280.0 | 只改溜槽过滤值，保留 7 铜锭。自检后成品 1，两批独立投料累计 5、12。旧夹具原有余水推动助手，日志无原生移动调用。 |
| 自主安山岩双磨 | 29 / 611,027 / 239.0 | 14 格，两套 -16 RPM 独立动力，每批 3 安山岩自动得到 3 砂砾，无越界。模型另用原生界面尝试整理成品，不是产线必需步骤。 |
| 安山岩精简提示复跑 | 23 / 549,298 / 355.1 | 助手无移动，成品线因马达与齿轮箱端口未接通而未产出。 |
| 空地面团首轮 | 26 / 714,756 / 420.5 | 11 格只建成独立可运转的受控洗台，未接共享研磨和端到端物流。 |

| 盲修干处待命复跑 | 32 / 763,716 / 310.5 | 原地把同一过滤值改对，原设备和 7 铜锭保留。两批独立投料后成品累计 9、18。 |
| 空地面团共享动力复跑 | 32 / 881,453 / 420.5 | 19 格，磨石与风扇同网运转。截止时尚未放水，也未放下原料桶和成品桶。 |

飞行基地分三个独立空场试验。首轮走错原生施工路径。第二轮全用直接接口，成功装配并锚停，但孤立气球未进入船体，原船落到站点下边界，控制未能达到推进/转向验收。第三轮只补充气球需实际连接、保留余量和读回 JSON 的一般要求，模型自选坐标、朝向与舱体，未提供蓝图。27 次请求、509,738 Token、241.1 秒。模型的一批 50 格施工回执与后续原生装配相符。模型乘舵并实际推进 2.98 格、偏航 14.3°，锚停后两个独立采样的位姿相同，乘舵解除，工程区外无变更。两次航向回读已经转变，锚停判断用静止位姿，不以缓存速度值推断。真实图形客户端重开原船副本，已取得船体截图。模型把锚放在走道地板上，生存测试在该处下落约 0.75 格。用跳跃复跑恢复到接近地板高度，仍未抵达舱内目标格，保留两次真实坐标。 船体白名单尚无 Create 加工方块，行驶中舱内活动和动态加工未验收。

隔离回读第一次以原身份重新召唤时，原生对话触发一条截止后的只读请求，原试验在重连前已封存，没有后续写入证据。复现脚本改为关停模型传输再重连，截止读回会先确认卸载助手。真实测试记录保留实际服务端快照、伴侣归属和模型轨迹。回读串行，固定回归仍由服务器原生 tick 驱动。

接续优先级：保留已通过的直接操作，以模型自选端点完成共享动力面团复投。移动基地接续测试从已装配船体开始，先确认真实舱内行走，再接 DSH 已验证的载具路径做移动期间的舱内验收和加工件兼容，不增设新物理。

[实测 JSON](evidence/decoupled-execution-0.2.6-dev.json) · [原始模型日志、源回执、存档及真实客户端截图](https://chatgpt.com/library?file_id=libfile_06dfff7214e08191901cf8e6832896d9&account_id=personal)。压缩包中的截图来自自主水洗存档副本，施工和产物验收以同一场原始控制证据为准。
