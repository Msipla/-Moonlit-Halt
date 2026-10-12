# 《微光深渊》工程恢复入口

## Phase 6 正在执行与最高优先级约束（2026-10-12 23:11 用户纠偏）

**MOD 基调为宁静、神秘、宏伟、温暖、安全，不包含危险或恐怖。整个景观区域——小镇、街道、建筑、桥梁、周边洞穴、深渊及探索路线——均禁止普通怪物自然生成，不能仅保护镇内。视觉环境光不是刷怪光照。** 此约束高于所有“外围保留危险/怪物”的旧建议。

目前仅允许将未来的幻翼设计为明确中立、不攻击玩家的特殊景观生物，主要服务缆车；Phase 6 不加入生物，原版攻击型幻翼也排除。其他生物须后续明确决定才可加入。仅在景观边界生效，不修改整个主世界刷怪规则。

当前实现覆盖完整实验景观维度：生物群系无生成表；服务端在 MobEntity 加入时局部拒绝，并清理本维度加载的旧生物。主世界条件不匹配保护边界。正式主世界景观边界留到 Phase 7 自然生成研发时接入同一原则，当前不开发该阶段。

Phase 6 正式任务书为 [TASK-PHASE6.md](TASK-PHASE6.md)。首轮实跑38267037910已保留21原始采集帧与失败记录：原平台北栏杆挡住了新增短巷；高处及远景机位也需修正。不是技术通过。已着手修复，并加入 HARD 难度、doMobSpawning=true、黑暗深渊与主世界对照检查；新安全实现尚待正式实跑验证。下方第五阶段交付为有效历史基准，不能回退或重做。

## 第五阶段历史交付


2026-10-12 · **Phase 5 v0.5.0 小型探索原型技术交付。第五阶段画面与工坊精修待用户审阅。** Phase 3 冷蓝/巨柱与 Phase 4 工坊/暖窗已获认可；不根据旧待验收文字重做旧阶段。

## 当前权威

唯一任务书 [TASK-PHASE5.md](TASK-PHASE5.md)。最新代码检查点 `0adfd2b523fa332b6bfcdb53bfce9792c1f9c421`；真实运行 [38259041182](https://github.com/Msipla/-Moonlit-Halt/actions/runs/38259041182)，生成结构与完整证据提交 `91d4c08163e89ef5bacf20052af6e85d2977d26c`。工作流先运行生成脚本再构建；恢复时读取 main 最新源码、[第五阶段报告](TEST-REPORT-PHASE5.md)、[日志](devlog/2026-10-12-phase5.md) 与 `evidence/phase5/latest.json`。

## 已实现与验证

一栋适度精修的原工坊、54格安全铁桥、有限局部竖井和小俯瞰台。52个原暖窗位置/原生12级光保留；世界高度、原生成器、着色器和视觉配置不变。局部开挖是有意的存档改变，不能声称放置后地形完全不变。保护原近巨柱，不新增第二栋建筑或NPC。

官方发布 Minecraft客户端与正式 Fabric服务端安装同一 remapped JAR；原9项测试、322,459处结构类型/明确属性、217路线采样、212护栏采样、1,800竖井空气采样、十段实际按键往返、后墙及栏杆碰撞、重复放置和同存档重启通过。14采集机位严格pC/pU为零，另3张行走/碰撞图，共17原始PNG。

安装包 [glimmering-abyss-0.5.0.jar](artifacts/glimmering-abyss-0.5.0.jar)，239,404字节，SHA-256 `fba21336abb20d0eb013fd56c4a3f310affdbdd091cb174d1ff1aab76864aa1a`；源码包及校验同目录。见 [INSTALL-PHASE5](INSTALL-PHASE5.md)。进入：`/abyss enter` → `/abyssrender blue` → `/abyss route` → `/abyss route view interior`，走出工坊、过桥、抵达并回望；`/abyss return` 返回。

`core/down/departure/arrival/facade/warmth/interior` 是落地机位；`span` 需创造模式。默认仍保留原版 A，铜金能力保留。复放会重置模板明确覆盖位置与容器内容，使用测试存档；原 `/abyss building` 模板仍保留，但不会清除所有第五阶段模板外装饰，严格旧版比较用新测试存档。

## 美学与限制

最终原图已由开发侧查看。桥下竖井确实存在，深处为蓝黑暗部；有限底面与远侧谷底在一些角度仍可能辨认，栏杆/桥面也有真实前景遮挡。**不宣布“永远看不见底”或用户美学通过。** 工坊精修可通过同次 before-facade / blue-facade 对照判断。先做用户原图评审与目标电脑体验，不继续扩大城市。

托管Linux软件OpenGL已测；Windows/PCL2、目标GPU、长时、第三方渲染兼容与音效听感未测。窗仍不透明，空间光没有完整几何遮挡。历史队列检查的字段前缀误判已定位：Phase 3 blue-tunnel / Phase 4 before-platform 各有pU=01；原图不改，勘误见第五阶段报告。第五阶段最新14机位已严格复核为零。

## 历史、复现与展示

Phase 1–4 原图/JAR/报告/URL及本阶段三轮证据保留。Phase 4 权威原运行38250075059、源码bfa41181；首版工坊与冷暖光的用户认可仍有效。Phase 2 美学否决保留。

构建：`python3 tools/author_route.py`，`./gradlew --no-daemon build stageTestMods`。实跑：`xvfb-run -a python3 -u tools/run_phase5.py --accept-eula --skip-build --output evidence/phase5/new-unique-run`；不能覆盖历史。官方依赖和完整临时测试世界不提交。

[现有手机优先展厅](https://msipla.github.io/-Moonlit-Halt/) · [第五阶段原图与交付](https://msipla.github.io/-Moonlit-Halt/phase5.html)。阶段/时间线由 `site/glimmering-abyss/exhibition.json` 管理，沿用原生静态站，无新门户架构。发布与真实浏览器视口QA记录见网站报告；视口模拟不等于实体手机测试。

不开展月下小站、统一美学实验室、正式主世界生成、梦境或幻翼缆车。长期设计仍在 Conversation-Archives，不复制进本阶段工程交付。下一步等待用户画面判断或目标Windows实测。

## 第五阶段展示交付验收

前端源码 d0e364cec0539c2c714a21113250a92b1f598854；Pages38261641907发布、真实Chromium QA38261641881成功。五阶段入口、360–1440px排版、原图/机位切换、同机位精修对照和旧永久URL通过；开发侧已查看实际网页截图。网页证据已提交310cd26f74928b4fb6a98df7e8dbc466cf45c170。实体手机未测，详见 `site/glimmering-abyss/TEST-REPORT.md`。第五阶段工程与展示收尾，不继续增加功能；等待用户原图判断与目标Windows实测。
