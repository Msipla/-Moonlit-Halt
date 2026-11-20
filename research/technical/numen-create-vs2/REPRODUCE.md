# 实验复现与 DeepSeek 接入

本页记录已经运行过的 Numen 0.1.5-beta 夹具、原始日志位置和复现办法。研究结论、完整版本/许可证表见 [技术报告](README.md)，精确类、方法和上游源文件见 [源码索引](SOURCE-MAP.md)。

## 文件从哪里来

原 Work 目录为 `/workspace/scratch/d263229ee747/numen-research/`。下面的目录是交付包和 GitHub 研究分支内的相对路径。

| 文件 | 在原 Work 目录中的位置 | 内容 |
| --- | --- | --- |
| `README.md` | `deliverable/Numen-Create-VS2-Research.md` | 上一轮完整报告。本次在交付副本补了索引及归档说明。 |
| `mc20/lab-*.cjs`、`mc21/lab.cjs` | 同名 | 上一轮真实运行的夹具，内嵌每条 Lua、RCON 和校验代码。 |
| `fabric-probe/src/lab/`、`neo-probe/src/lab/` | 同名 | 实际保存的 Java 源文件及 Mod 描述。Fabric 按最后保存的源码整理，包含后加的 VS/Eureka 与 Skill 检查。单独保存的更早 Fabric Java 源码快照未找到。 |
| `fabric-probe/probe*.jar`、`neo-probe/probe.jar` | 同名 | 上一轮各阶段使用过的原始编译包，和最后的源码快照可能包含不同的探针入口。用它们复现不需再编译。 |
| `mc20/config/numen/server/skills/createlab/create_kinetics/SKILL.md` | 同名 | 服务器实际读取的 Create Skill。 |
| `lua/create-repair.lua` | 原 Lua 内嵌于 `mc20/lab-create-final.cjs` 的 `const repair` | 本轮抄出并分行，便于检查。实际执行文本以原脚本和日志为准。 |
| `evidence/run/experiment-*.txt` | `mc20/`、`mc21/` 下的同名文件 | 原始执行输出，包含命令、Lua 回执、断言和部分失败迭代。 |
| `evidence/server/world-*.log` | `mc20/`、`mc21/` 下的同名文件 | 专用服完整控制台，随下载包提供。 |
| `dependencies.tsv`、`server.properties.example` | 上一轮保留的发布元数据、JAR 和服务器配置 | 本轮抽取的固定版本、下载地址、实际 SHA-256 和隔离测试配置。 |

本轮核验和归档文件。原实验为终端驱动，没有用于验收的图形客户端录像，也未生成真实 DeepSeek 会话。下载包包含自制探针及日志。Minecraft/第三方发布包由依赖表下载。

## 准备可复现环境

本轮仅对附件做文件和语法校验。以下是依据原夹具补齐的从空目录准备步骤。

1. 解压后进入 `numen-create-vs2/`。用 Linux 或 WSL，安装 JDK 17、JDK 21、Node.js 24 和 npm。原夹具使用 Node.js `v24.22.3`。Fabric 下载启动器，NeoForge 安装器将生成真正的专用服参数文件。用新的平坦创造测试世界逐个运行脚本。
2. 从根目录下载锁定文件。需要只做 Fabric 或 NeoForge 时，可按表首列选择对应下载行。NeoForge 仍需保留 `mc20/` 的 npm 依赖，因为它复用 RCON 模块。

   ```bash
   while IFS=$'\t' read -r target sha url; do
     [[ $target == target ]] && continue
     mkdir -p "$(dirname "$target")"
     curl -fL --retry 2 "$url" -o "$target" || exit 1
     printf '%s  %s\n' "$sha" "$target" | sha256sum -c - || exit 1
   done < dependencies.tsv

   (cd mc20 && npm ci)
   cp mc20/server.properties.example mc20/server.properties
   cp mc21/server.properties.example mc21/server.properties
   ```

3. 阅读并接受 Minecraft 的 EULA 后，在两个测试实例分别创建 `eula.txt` 并设 `eula=true`。用各自 JDK 完成准备，将 `JAVA17_HOME`、`JAVA21_HOME` 指向本机 JDK 目录。NeoForge 探针是专用服上的 Mod。Fabric 各探针由夹具按需添加，共用同一个 Mod ID。

   ```bash
   (cd mc21 && "$JAVA21_HOME/bin/java" -jar installer247.jar --installServer)
   cp neo-probe/probe.jar mc21/mods/probe.jar
   ```

   当前配置分别绑定 `127.0.0.1:25576/25577` 和 `127.0.0.1:25586/25587`，对应游戏端口 / RCON 端口。示例里的 `isolated-numen-test` 是原夹具的固定本机测试值。程序使用 OP 走 Numen 的 `numen drive LabBot …` 入口，RCON 负责准备夹具和独立读游戏状态。已附的 `evidence/` 是原记录，下方命令用 `local-*` 新世界避免覆盖。

## 四项实验与核查方法

| 实验 | 环境 | 通过依据 | 原始输出 |
| --- | --- | --- | --- |
| 水车粉碎机 | MC 1.20.1 / JDK 17 / Fabric Loader 0.17.2 / Fabric API 0.92.6 / Numen 0.1.5 / Create Fabric 6.0.8.1 | 46/46 放置，补投料口漏斗，两轮 +8/−8 RPM，应力 64/256 SU。投一份原料得沙 1。注入错误轴向后右轮 0、控制器失效。Lua 清空并重装，复投料后 `scan.container` 和原版 `data get block` 都确认沙 2。 | [命令与回执](evidence/run/experiment-create-verified.txt) |
| Create 单轴变形 | 同上，使用原生工具 | 4/4 放置。服务端 `data get block 8 -59 3` 两次样本记录运行状态、16 RPM 及角度 62.399994° → 120.00003°。`execute if entity @e[type=create:stationary_contraption,limit=1]` 返回一个实体。 | [命令与回执](evidence/run/experiment-bearing-controlled.txt) |
| VS2 与 Eureka | 同 Fabric 基线，加 VS2 2.4.11、Eureka 1.6.4、Trackwork Refabricated 1.2.2 | 十格静态船体，潜行使用船舵，执行真实菜单按钮。两次读同一 ship ID，Y −53.31818 → −59.31819，末速约为零。确认组装和重力位移。 | [命令与回执](evidence/run/experiment-vs-motion.txt) |
| Aeronautics 与 Sable | MC 1.21.1 / JDK 21 / NeoForge 21.1.247 / Numen 0.1.5 / Create 6.0.10 / Aeronautics 1.3.3 / Sable NeoForge 2.0.6 | 10/10 放置，原生按住右键后子世界仍为 0。适配插件触发装配，观测同一子世界 Y −56.93078 → −58.91931，末速约为零。确认装配和重力位移。 | [命令与回执](evidence/run/experiment-aero-motion.txt) |

从解压目录运行。每个脚本自动启动专用服、等待 Numen 就绪、执行断言并退出。原脚本中的 `console.log` 会输出测试标记，专用服另写 `LAB_WORLD.log`。

```bash
# 在同一个终端先设置 JAVA17_HOME 和 JAVA21_HOME，再逐个运行。
(cd mc20 && LAB_JAVA="$JAVA17_HOME/bin/java" LAB_WORLD=local-crusher \
  LAB_MODS=../fabric-probe/probe.jar node lab-create-final.cjs)
(cd mc20 && LAB_JAVA="$JAVA17_HOME/bin/java" LAB_WORLD=local-bearing \
  node lab-bearing.cjs)
(cd mc20 && LAB_JAVA="$JAVA17_HOME/bin/java" LAB_WORLD=local-vs \
  LAB_MODS='../fabric-probe/probe-vs.jar:../extra20/vs.jar:../extra20/eureka.jar:../extra20/trackwork.jar' \
  node lab-vs.cjs)
(cd mc21 && LAB_JAVA="$JAVA21_HOME/bin/java" LAB_WORLD=local-aero node lab.cjs)
```

补充核验的原始输出有 [Skill 注册](evidence/run/experiment-skill.txt)、[Interactive 冷启动](evidence/run/experiment-interactive.txt)、[Clockwork 0.5.6 冷启动](evidence/run/experiment-clockwork.txt)。Interactive 的全日志记录了 `interact_me_not` 配方解析失败和 Mixin 覆盖冲突。Clockwork 0.5.6 使用内含 Kelvin 0.5.0，0.5.7.1 另需 Kelvin ≥0.5.2。后两项验证止于冷启动。这是两次分别加装的测试，构件运动和整合包性能仍待验证。

```bash
(cd mc20 && LAB_JAVA="$JAVA17_HOME/bin/java" LAB_WORLD=local-skill \
  LAB_MODS=../fabric-probe/probe-skill.jar node lab-skill.cjs)
(cd mc20 && LAB_JAVA="$JAVA17_HOME/bin/java" LAB_WORLD=local-interactive \
  LAB_COMPAT_ONLY=1 LAB_MODS='../extra20/vs.jar:../extra20/interactive.jar' node lab.cjs)
(cd mc20 && LAB_JAVA="$JAVA17_HOME/bin/java" LAB_WORLD=local-clockwork \
  LAB_COMPAT_ONLY=1 LAB_MODS='../extra20/vs.jar:../extra20/clockwork056.jar' node lab.cjs)
```

## 接 DeepSeek

1. 在 1.20.1 装好 Numen 的客户端进入隔离世界，默认按 **G** 打开面板，在**设置 → 模型服务／档案**新增档案，服务商选 **DeepSeek**。源码对应 `SettingsView` 和 `ProfileFormPanel`。
2. 模型可先用上次核对过的 `deepseek-flash`。列表没有时用「手填型号 ID」。默认走 DeepSeek 官方地址。需手填或自定义时，协议选 **Chat Completions（OpenAI 兼容）**，基础 URL 写 `https://api.deepseek.com`。把有效 Key 填到面板的 Key 字段。首轮可把思考设为关闭，握手和工具闭环通过后再开思考模式。
3. 点击档案的「检测」，成功后保存，给召唤卡或已有同伴选择该档案。专用服由服务端读取玩家模型档案，并发出请求，因此要验证的是专用服到 DeepSeek 的网络。配置只在你的游戏实例内输入。
4. 在同伴聊天里发「用工具查看我面前测试区的一个方块，回报它的坐标和方块 ID」，确认回执中真正出现脚本工具调用与结果。之后再做工程回归。检测按钮用于确认基础接口通达。

若检测报 401，先核对 Key、档案和地址。若聊天提到模型但不调用工具，检查同伴绑定的是同一个档案、模型支持工具调用，并查看该回合的工具回执。若只在思考模式的第二轮报错，检查服务端日志里的模型错误和 `reasoning_content` 回传，避免把模型协议问题误判成 Create 故障。上次只核对过源码与文档，这次也未发送付费请求。  
[DeepSeek Tool Calls](https://api-docs.deepseek.com/guides/tool_calls/) · [DeepSeek Chat Completions](https://api-docs.deepseek.com/api/create-chat-completion/)

## 下一轮真实智能体验收

先用已固定的 1.20.1 Fabric 组合。用同尺寸测试区的全新世界，保持同一模型、配置、中文指令和材料，按三个条件各执行一次。有余量再增加重复轮次。

| 条件 | 给模型的能力 | 要测什么 |
| --- | --- | --- |
| A | 原生 Numen | 看是否能从注册表和原生扫描完成建造、读取控制器与木桶。 |
| B | 原生 + 已验证可加载的 Create Skill | 看它是否主动加载 Skill，并选对轴向、投料口与水道。 |
| C | 原生 + Skill + `createlab.machine.inspect` | 看它能否精确定位断轴、只修正确坐标，并用第二份产物验收。 |

统一下达「用水车建一台 Create 粉碎机，用碎石试车。出现故障时检查并修复，最后告诉我哪里坏了、产出了什么」。管理员只提供材料、统一投料和注入故障。等第一份沙进入木桶后，在预定的右侧传动段断开并换装一根错误轴向的轴，然后只向模型报告「机器停了」。独立核验两轮有符号 RPM、网络应力、控制器有效性和木桶新增的沙。记录初始化指令、实际 Lua、误改块、脚手架、任务耗时和模型用量。用原始日志对照模型最终说法。再单独让它按中文需求搭一个船壳并留出安装位，由玩家按现有流程手工确认装配。
