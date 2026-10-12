# 《微光深渊》工程恢复入口

2026-10-12 · v0.4.0 首版地下工坊已完成正式客户端/服务端技术实跑；建筑美学等待用户审阅原图。第三阶段工程已交付，用户认可冷蓝巨柱环境与局部暖光方向。不要因旧档案待反馈描述重做 Phase 3。

## 当前权威

本目录是唯一权威工程。已验证源码 `bfa41181cb3fc263eb62889bff34e060111b4c58`；实跑 [38250075059](https://github.com/Msipla/-Moonlit-Halt/actions/runs/38250075059)，证据 `evidence/phase4/runs/38250075059/`，指针 `evidence/phase4/latest.json`。先读取 main 最新提交、[Phase 4报告](TEST-REPORT-PHASE4.md)、[日志](devlog/2026-10-12-phase4.md) 与 research/aesthetics 的 Phase 4 追加基准，再看最新聊天校订。

## 已实际验证

正式 remapped JAR 在官方发布 Minecraft客户端与正式Fabric服务端安装/加载；原9项单元测试，原生光级与原地形指纹（放置前），20,039处模板方块类型/净空，52个暖窗12级光，60处连接路线检查，重复放置、实际存档重启、真实按键入屋及后墙碰撞，12张原始截图和逐帧数据。Windows/PCL2/目标GPU/长时/第三方渲染兼容尚未实测，音频设备不可用。

包为 artifacts/glimmering-abyss-0.4.0.jar，SHA-256 `67d76fe42732b59e967bfeef3cc70d8fad426b5e82c32c9642f3355ea918cbd1`。只安装一个本MOD版本，配套版本与步骤见 [INSTALL-PHASE4](INSTALL-PHASE4.md)。默认仍为原版A；`/abyss enter` → `/abyssrender blue` → `/abyss building` → `/abyss building view facade`。

一栋原创砖石工坊，坡屋顶、拱窗、烟囱、小塔、局部暖窗及步道；没有村民/常驻NPC。标准 NBT 模板可替换，构造脚本在 tools/author_workshop.py。不改天然生成器；右侧桥口属于有意新增。复放命令重置模板覆盖区域的手动改动/容器内容，研发世界使用。

## 美学与下一步

先让用户看第四阶段原图，判断建筑是否自然地属于冷蓝洞厅，再决定扩大规模。不要因技术通过宣布美学通过。冷蓝方向已获认可；铜金仅保留能力验证。窗当前不透明、室内简略、暖光局部机位被近灯柱遮挡一部分，环境光没有完整墙体遮挡。

未来正式生存版：主世界地下约一至两座稀有城市，睡眠梦境线索，类似末影之眼的入口引导，幻翼牵引封闭观景缆车，海洋附属扩展，Alex's Caves 探索兼容研究。都未实现；实验维度继续用于研发，不因此推翻Fabric。

## 历史与复现

Phase 1/2/3 的证据、旧JAR、报告、永久URL保持；第二阶段美学否决保持。Phase 3 最终实跑38234495224和原21张截图继续可访问。本轮首次启动超时/路线判定失败保留日志，详见Phase4报告；不声称复原旧Work临时世界，也不假装保留了失败轮未复制的PNG。

构建 `./gradlew --no-daemon build stageTestMods`；显示环境中运行 `xvfb-run -a python3 -u tools/run_phase4.py --accept-eula --skip-build --output evidence/phase4/local-unique-run`。选择新目录，不能覆盖历史。正式游戏依赖与测试存档不提交。

## 项目展厅

[手机优先展厅](https://msipla.github.io/-Moonlit-Halt/)；第四阶段 [原图与交付](https://msipla.github.io/-Moonlit-Halt/phase4.html)，前三阶段永久报告保留。阶段/时间线/状态由 site/glimmering-abyss/exhibition.json 维护，截图从同一工程证据引用，不另存网页副本。发布与浏览器QA必须查看实际工作流及 build-info.json；视口模拟不能称为实体手机实测。

聊天档案：[Conversation-Archives Minecraft HANDOFF](https://github.com/Msipla/Conversation-Archives/blob/main/conversations/minecraft/HANDOFF.md)。工程详细证据留在这里，不复制进聊天库或 Archives 正史库。


最终展厅前端检查点 `488e7ff57ba137ad7c89f9c7daf5ac77a9c8ec9e`，Pages38251393211及真实Chromium QA38251393054成功。360–1440px视口、四阶段导航、第四阶段原图/机位、同机位对照及前三阶段旧URL通过；实体手机未测。详细网页证据见 site/glimmering-abyss/TEST-REPORT.md 与 evidence/website/runs/38251393054。下一步是用户审阅建筑原图，不因网页或技术PASS扩大城市规模。
