# Numen × Create：物品交互与施工保护的最小验证

状态：待执行。本阶段只完成本机环境调查与本任务书，未改源码、未启动服务端、未跑模型。上位结论见 [通用机械工程任务书](TASK-NUMEN-CREATE-GENERAL-ENGINEERING.md) 与 [实测记录](numen-create-vs2/live-lab/README.md#2026-11-21-通用自主机械工程闭环)。工作分支：`codex/numen-create-basic-actions`；起点提交：`2fb268f7025526883f5ecf4c29ced119e8fbef3e`。

## 执行前必读

先读取 `Msipla/Conversation-Archives/AGENTS.md` 最新版及本仓库 `AGENTS.md`（如存在），全程遵守适用协作规范；无法读取应报告，不能假装已遵守。核对本分支最新提交、任务书、`live-lab/README.md`、`evidence/watermill.json`、房主 Lua 模块、Skill、施工护栏源码和可恢复的实机证据；有并发修改先比较，不覆盖。

## 已确认事实

水磨坊开口版在同一条件连续两次完整闭环（264.874 秒、229.931 秒），原位侧驱一次完整闭环（361.012 秒），两者均通过独立实机验收。机械压机四轮共 119 次请求、2,565,616 token、全部 420 秒超时：模型实建的结构经独立投料确实产出 1 份铁板，但模型未自验到产物、未按时回执，**不能算完整闭环**。四轮均未调用 `build.diff`，三处候选提示都未产生候选回执。

剩余两个通用瓶颈：**置物台上料失败原因未分离**；**施工前未识别墙体、入口和检修空间被占**。

## 目标

1. **物品交互可靠性。** 用最小实机对照拆开上料失败的变量，判断属于接口能力缺口、站位问题、参数问题还是模型操作问题。允许模型自行选择置物台、漏斗或传送带，验收以真实加工产物和玩家需求完成情况为准。
2. **施工保护与局部检查。** 复用现有 `scan.block`、原始结构快照和 `build.diff`，让施工前的占用与既有结构变动成为勘察回执的默认可见事实；同时查明模型为何不主动调用已有检查。

## 执行边界

- 不新增压机专用上料接口。只有无模型校准证明确属**通用**接口缺口时，才按既有 `scope/WaterAction.java` 的单格动作 + 护栏 + 回执模式做最小扩展，不建第二套框架。
- 不增加保护状态机、专用施工框架或新的复杂规则系统；优先用现有回执和工具流程解决。
- 保留水磨坊、侧驱、压机的实建结构与成功存档；不重跑已通过的历史实验，不重复 PatchouliAI 源码研究，不移植其大型建造框架。
- 不改 Numen 本体，不改 Skill 大段文字来堆提示；同一失败无新证据时停止并报告。
- 实机结论只来自真实游戏运行；无模型校准、独立验收与模型自验必须分别标注。

## 环境（2026-11-21 本机调查）

此前真实游戏实验在 ChatGPT Work 云电脑完成，其服务端、运行依赖和世界存档**不保证**已同步到 GitHub，也不能假设本机能直接访问。本机另有一套独立环境（Minecraft AI 工程助手项目），版本与实验基本对齐：

| 组件 | 本机状态 | 路径或版本 |
| --- | --- | --- |
| JDK 17 | 有，两套 | `D:\Android\openjdk\jdk-17.0.12`（工程脚本使用）、`D:\Minecraft_Modpack_Project\runtime\java17-existing-17.0.8`（OpenJDK 17.0.8） |
| Minecraft 1.20.1 服务端 | 有 | `…\minecraft-ai-minecraft-1-20-1-2\work\server`（`server.jar` + `fabric-server-launch.jar` + `versions/1.20.1`） |
| Fabric Loader 0.17.2 | 有 | 与服务端、实验版本一致 |
| Fabric API | 有 0.92.12 | 实验记录为 0.92.6，差异待核对 |
| Create Fabric 6.0.8.1 build 1744 | 有 | 与实验完全同版 |
| Gradle 8.8 | 有离线安装与缓存 | `work\tools\gradle-8.8`、`work\gradle-home` |
| Python 3.12.7 | 有 | `E:\python\python.exe` |
| Node.js | **无** | `run.cjs` 系列夹具无法直接运行 |
| Numen 0.1.5 | **无** | 全盘未找到 `numen*.jar`，含 API JAR |
| 实验世界 `variant-ready-world` | **无** | 仅在 Work 云电脑 |
| 模型密钥 | 未配置 | 本机工程走 `config/minecraft-engineer.key`（硅基流动），未验证可用 |

工程根目录为 `C:\Users\高\Documents\Codex\2026-11-02\minecraft-ai-minecraft-1-20-1-2`，启动脚本 `outputs/minecraft-engineer/start-server.ps1` 与 `start-client.ps1`；该工程无 Git 工作树，属本地旧环境，本阶段**不改造、不启动**。

最小适配缺口即上表后四项：Numen JAR 与 API、Node.js、实验世界、模型密钥。前两项决定能否编译 `createlab` 护栏与运行现有夹具；后两项决定能否复现同条件对照。

## 实验计划

1. **无模型交互校准。** 在现有夹具上加一条校准，对同一台压机分别改变 `item` 参数、站位高度、是否潜行，逐项记录命中面、库存变化与产物。目标是给出“原生成交的最小条件”，不改 Skill、不改源码。参考既有观察：带 `item` 的右键曾使手持铁锭计数 −1，不带 `item` 时无变化。
2. **按结果二选一。** 若原生存在稳定站位规则，只在 Skill 补一两句事实并让模型复测；若证明属接口不可控，再按 `WaterAction` 模式做最小单格动作。
3. **保护回执默认可见。** 在现有房主模块 `create-survey.lua` 的 `observe()` 增加可选受保护格清单，首次勘察即列出候选改动是否会触及墙、入口与检修位，仍只调 `build.diff`，不新增权限。
4. 只有前三步都成立，才在已成功的压机目标上复测完整玩家闭环；仍失败则保留轨迹并报告。

## 验收

必须分开记录并在结论中区分：

- 模型自主施工（模型自己选址、放置、修错）；
- 模型自验（模型自己读到新产物）；
- 独立实机验收（代理离场后由夹具或人工在原样存档投料）；
- 按时游戏内回执。

只有四者齐全才算完整闭环。压机只认可“结构可用 + 独立验收通过”的既有结论，不得改写成模型闭环成功。新增对照需记录请求数、token、耗时、逐格读取与 Lua 调用，并与历史基线同口径比较。

## 停止条件与交付

允许在授权范围内连续做小幅、证据驱动的修复；连续无新证据的重复失败、需要重构核心、新增大型依赖或改变施工权限时停止报告。优先追加现有 `live-lab/README.md` 与 `evidence/watermill.json`，不新建平行报告或重复文档。完成后提交到既有工作分支并回读 GitHub，报告提交 SHA、对照数据、四类验收状态与未解决瓶颈，明确区分已提交、已测试与已实机验收。

## 来源

- 上位任务书：[TASK-NUMEN-CREATE-GENERAL-ENGINEERING.md](TASK-NUMEN-CREATE-GENERAL-ENGINEERING.md)、[TASK-NUMEN-CREATE-DECISION-TRANSFER.md](TASK-NUMEN-CREATE-DECISION-TRANSFER.md)、[TASK-NUMEN-CREATE-STABILITY.md](TASK-NUMEN-CREATE-STABILITY.md)
- 实测与证据：[live-lab/README.md](numen-create-vs2/live-lab/README.md)、[evidence/watermill.json](numen-create-vs2/live-lab/evidence/watermill.json)、[evidence/basic-actions.json](numen-create-vs2/live-lab/evidence/basic-actions.json)
- 源码：[create-survey.lua](numen-create-vs2/live-lab/create-survey.lua)、[scope/WaterAction.java](numen-create-vs2/live-lab/scope/WaterAction.java)、[scope/ConstructionBounds.java](numen-create-vs2/live-lab/scope/ConstructionBounds.java)、[SKILL.md](numen-create-vs2/mc20/config/numen/server/skills/createlab/create_kinetics/SKILL.md)
- 跨窗口缓冲：`Msipla/Conversation-Archives` 的 `management/chat-handoff/CURRENT.md` 第十二节（2026-11-21）
