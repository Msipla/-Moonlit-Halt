# Numen Engineering 0.2.0 单机实机验收（2026-11-22）

环境：Minecraft 1.20.1 / Fabric Loader 0.17.2 / Fabric API 0.92.12 / Create 6.0.8.1 build 1744 /
Valkyrien Skies 2.4.11 / Eureka 1.6.4 / Numen 0.1.5 / Numen Engineering **0.2.0**。
真实图形客户端 + 单机集成服务端，创造模式，站点 `-Dengineering.site=-4,-60,-4,8,-52,8`，
`-Dengineering.ships=true`。模型 DeepSeek `deepseek-chat`。

**安装说明**：仓库没有 0.2.0 预编译 JAR。本轮从 `026404b` 的 `live-lab/game-bridge` 与
`live-lab/ship-bridge` 源码用 javac 直接构建（类路径需 Minecraft 中介名、Fabric Loader、
Fabric API 模块、Create 及其内嵌 porting_lib、numen_api、VS、Eureka、kotlin、JOML、Guava），
打包 `lab/`、`engineering/ponder/`、`fabric.mod.json` 后放入客户端 mods。

**测试方法**：合成键盘输入无法送到 LWJGL 窗口，故用测试用 `autotest.jar` 读脚本、以玩家身份
执行命令（`/numen player summon`、`/numen companion … profile`、`/numen chat …`、以及独立复核
命令），聊天与 `[numen-llm]` 指标从客户端日志读取。该夹具不属于产品。

## 结果

| 项 | 结果 | 关键证据 |
| --- | --- | --- |
| 1. 自然语言批量建造机械 | **通过** | 模型在站点内建成 (16,-56,22) 机械压机、下方 (16,-57,22) 置物台、东侧 (18,-56,22) 创造马达与中间轴。独立复核：`create:mechanical_press`/`create:depot`/`create:creative_motor`/`create:shaft` 全部命中，压机方块数据 `Speed = -66.0f`。43 次模型调用，峰值上下文 23,604 token，13:41:43–13:47:06（含一次因创造模式未满足被拒后的重试） |
| 2. 空地自主完整产线 | **部分** | 模型自行选址并建成 64 RPM 研磨头：马达 (20,-58,20) 朝上直驱磨石 (20,-57,20)，独立读到 `Speed = 64.0f`。物流侧未完成：模型自己报告进料桶/溜槽到磨石不导通、`engineering.site.feed` 回执与实际桶内容不一致，最终未产出面粉。71 次调用（含第 3 项），峰值 37,162 token |
| 3. 未知故障检查与自主维修 | **通过** | 把马达格替换成 `create:shaft`，独立确认磨石 `Speed = 0.0f`；只给一句话故障描述，模型 10 秒内定位“马达不见了”，仅写回 (20,-58,20) 一个创造马达并设 64 RPM。独立复核：`create:creative_motor` 存在、磨石 `Speed = 64.0f` |
| 4. 自主设计/装配/驾驶飞行船 | **未通过（发现产品缺陷）** | 站点 `PALETTE` 不含任何 `vs_eureka:` 方块，模型铺完 9×9 橡木甲板后无法写入白气球或橡木舵；`engineering_vessels.assemble` 又要求船体由这些方块组成，形成死锁。独立分支已把 5 个船体方块加入白名单并补充工具说明，复测 6 分钟内模型仍在反复调用工具、未完成装配 |
| 5. 标准 NBT 保存与重建 | **未执行** | 时间用于第 1–4 项与缺陷修复，未跑导出/导入 |
| 6. 单机兼容性、权限与体验 | **部分** | 见下 |

## 第 6 项观察

- 单机集成服务端加载 Create + VS + Eureka + Numen + Engineering 无崩溃；仅 VS 的
  `No data fixer` / 模型贴图缺失等既有告警。
- 权限：`engineering.site` 要求 **创造模式的伴侣 + 同维度在线的创造房主**；玩家为生存或
  owner 不在场时所有站点调用被拒。破坏带方块实体的方块需要房主在场同意（模型尝试换桶时被拒：
  “主人不在场,无法征得同意”）。
- `engineering.site.feed` 至少一次回执“插入 3 个”但桶内实际为空（模型自述，未独立复现）。
- 模型调用期间客户端可正常操作；单次请求 0.6–3.9 秒，任务级 5–10 分钟，token 峰值 3.7 万。
- 体验缺口：没有预编译 JAR，安装需自行编译 6 个源文件；MCP 默认关闭；合成输入不可用使
  自动化验收必须依赖测试夹具。

## 最小补丁（独立分支）

1. `game-bridge/EngineeringSite.java`：`PALETTE` 增加
   `vs_eureka:oak_ship_helm / anchor / balloon / white_balloon / floater`。
2. `ship-bridge/EngineeringShips.java`：`assemble` 的工具说明补上允许的船体方块清单。

补丁只放开已存在的船体方块与说明文字，不改变施工、权限或区域限制。

## 未完成与限制

- 第 4 项补丁后仍未在 6 分钟内完成装配；需继续复测（可能是模型在长工具链中打转）。
- 第 5 项未执行；水面船未单独测试。
- 无完整录屏，仅保留聊天/指标日志；桌面抓帧不含船体画面，未作为证据。
- 以上均为单机创造模式结果，不代表生存或正式存档。

## 证据

`live-lab/evidence/acceptance-0.2.0/`：`t1-chat.txt`、`t2t3-chat.txt`、`t4-chat.txt`、`metrics.json`。
