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
