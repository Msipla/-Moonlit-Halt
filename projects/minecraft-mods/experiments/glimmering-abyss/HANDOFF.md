# 《微光深渊》工程恢复入口

2026-10-12 · v0.3.0 技术原型实跑通过，用户美学验收待定。第二阶段美学已明确不通过。

## 当前权威与已验证

本目录是唯一权威工程。最终源码检查点 `7795078bd4c48ea45066df12eee33e67b7537bf9`，最终实跑 `38234495224`；完整结论 [TEST-REPORT-PHASE3](TEST-REPORT-PHASE3.md)，过程 [devlog](devlog/2026-10-12-phase3.md)，证据指针 `evidence/phase3/latest.json`。读取仓库 main 最新提交，不依赖云端临时进程/缓存。

已真实完成官方 JAR 服务端 + 开发客户端：21 张原图、9 项测试、两色调同空间规则、原生光级/lightmap、无光源、主世界作用域、全部捕获队列清空、逐帧记录、行走护栏。历史生成采样指纹一致。Windows 正式客户端/GPU 尚未实测。

当前默认进入原版 A，用 `/abyssrender blue` 或 `copper` 开启新模式；配置为实例 config/glimmeringabyss-visuals.json。候选 JAR 与 SHA-256 在 artifacts/checksums-v0.3.json。只安装一个本 MOD 版本。

## 视觉结论边界

最新基准 [Phase 3 美学修订](../../../../research/aesthetics/glimmering-abyss-phase3-2026-10-12.md) 与原 vision 一并阅读。蓝色不是永久限制。用户参考《地渊珠宝匠》的暗剪影/远景空间光/局部暖光关系，不复制素材；参考图留在聊天附件。

开发侧观察到远壁亮起、暗柱轮廓与暖灯分层，但这是实验洞厅；没有城市或活世界完成结论。不能宣布美学达标，也不能再次让用户选择旧 B/C。

## 待办与执行顺序

1. 用户查看 [第三阶段原图页](https://msipla.github.io/-Moonlit-Halt/phase3.html) 给出视觉验收。
2. 根据反馈调整位置/表面光、空气透视和局部灯光布局，保存新的独立运行证据，不覆盖前两轮。
3. Windows/PCL2 正式客户端、目标 GPU、长时/内存与第三方渲染 MOD 实测。
4. 几何遮挡泄漏、透明材质/实体/粒子、自然洞穴区域光场选择仍未解决。重大架构路线调整先讨论。

## 历史与恢复

Phase 1/2 原始证据及旧 JAR 保持原样；Phase 2 报告只追加明确勘误。Phase 3 初次空间光偏弱的实跑 `38233315597` 保留为早期迭代；本地依赖超时在 failures。没有恢复或承诺恢复旧 Work 临时世界/缓存。本次新世界通过历史采样对比。

可重复构建 `./gradlew build stageTestMods`；有桌面显示时运行 `python tools/run_experiment.py --accept-eula --capture --phase3 --output evidence/phase3/local-unique-run`。服务端只监听 localhost，依赖/世界/缓存不提交。新的输出目录必须唯一。

聊天恢复与治理：[Conversation-Archives](https://github.com/Msipla/Conversation-Archives/blob/main/conversations/minecraft/HANDOFF.md)。工程成果不复制到聊天库或 Archives 正史库。

## 项目展厅（2026-10-12 更新）

用户浏览入口为 [GitHub Pages 项目展厅](https://msipla.github.io/-Moonlit-Halt/)，首页以真实画面、阶段状态、画廊和时间线为主。完整技术报告继续保存在 phase1.html / phase2.html / phase3.html；旧阶段证据 URL 不变。阶段展示清单为 site/glimmering-abyss/exhibition.json，后续发布需同步维护。网站 QA 截图位于 evidence/website，不能混作 Minecraft 实机证据。

最终网页源码 570ba16，Pages 38239083223 与独立 Chromium QA 38239083189 均成功；360–1440px 视口检查通过，实体手机尚未实测。完整网页验证见 [网站测试报告](../../../../site/glimmering-abyss/TEST-REPORT.md)。后续先收用户第三阶段原图反馈及真实设备表现；不要因网页 QA 通过而改变美学待验收状态。
