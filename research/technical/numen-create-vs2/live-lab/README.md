# Numen × DeepSeek｜中文自主施工实测

2026-11-20 · Minecraft Java 1.20.1 · Fabric Loader 0.17.2 · Fabric API 0.92.6 · Numen 0.1.5-beta · Create Fabric 6.0.8.1（build 1744）· Java 17.0.21.1 · Node 24.22.3

[阶段任务书](../TASK.md) · [上一阶段的复现与前置依赖](../REPRODUCE.md) · [固定提交源码索引](../SOURCE-MAP.md) · [依赖与 SHA-256](../dependencies.tsv)

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
