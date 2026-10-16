# 2026-10-16 巡检后限定修复与线上复测

**结论：既有Pages展示正常；本轮修复浏览器QA的部署竞态和早期失败留证，不修改HTML、CSS、exhibition.js/json、游戏原图、MOD代码或PR #6。**

- **已查明的失败**：浏览器运行[38597589334](https://github.com/Msipla/-Moonlit-Halt/actions/runs/38597589334)于16:26:48 UTC耗尽约3分钟部署等待，而同源[Pages 38597589003](https://github.com/Msipla/-Moonlit-Halt/actions/runs/38597589003)至16:32:19才成功，尚未进入页面交互。较早[38595302770](https://github.com/Msipla/-Moonlit-Halt/actions/runs/38595302770)为部署服务等待超时，后续成功已取代，未改写历史失败。旧QA的部署断言在try之外，故既无failure.json又导致空目录提交失败。
- **最小修复**：[2e296b6](https://github.com/Msipla/-Moonlit-Halt/commit/2e296b63554f4f2eb3228ed851bacaada8e72333)使main的QA由成功Pages workflow_run触发，并将checkout/目标SHA固定到该部署的head_sha；原分支预览和手动入口保留。部署/浏览器启动失败写入原有failure.json，空证据不提交。JavaScript语法、工作流触发/来源检查及两种早期失败留证模拟通过；模拟不冒充实际浏览器。
- **实际部署与浏览器**：[Pages 38895254578](https://github.com/Msipla/-Moonlit-Halt/actions/runs/38895254578)成功；线上build-info核对为上述2e296b6。随后[Chromium 38895348347](https://github.com/Msipla/-Moonlit-Halt/actions/runs/38895348347)及留证/上传步骤全部成功，`preview=false`、`deployed_source=2e296b63554f4f2eb3228ed851bacaada8e72333`。
- **复测范围**：360/390/430/768/1440px均无横向溢出；九阶段导航、V1十三机位与三组修复前后对照、历史画廊/模式约束、放大窗口和滑块通过；20个永久页面/证据入口HTTP200、页面JavaScript异常0。390px的Phase4–V1详情原图完整载入，下载链接指向既有preview.4，未重新下载或声称校验JAR。已查看390px V1画廊和1440px首页实际截图，未发现本轮需要改动的排版问题。
- **可复查证据**：[a9e14a1](https://github.com/Msipla/-Moonlit-Halt/commit/a9e14a1fe833ed1e00654c8be3ba29b718ebb4ea)保存[summary.json与25份网页原始截图](../../projects/minecraft-mods/experiments/glimmering-abyss/evidence/website/runs/38895348347/)。本记录的后续文档发布不改变已测展示源码；最终部署来源以线上build-info为准。
- **边界**：Chromium手机视口/触控模拟不是实体iQOO、Safari或Windows实测；Pages通过不等于MOD安卓Amethyst复验、V1.0最终美学验收、合并草稿PR或正式发布。

---

# V1.0 Snapshot 4 · 手机端交接（2026-10-15）

用户在安卓手机上查看线上 Pages，截图覆盖首页、Phase 1–7.5及V1.0阶段卡片、V1.0画廊、下载区、preview.3→preview.4断崖对照、时间线和页尾。可见图片加载正常、对照分界线与39%滑块显示正常、状态仍为「安卓复验待确认」，未见明显横向溢出。**本次可见页面基本通过，适合暂时停止网页功能开发。**

未验证：JAR实际下载与SHA、全部机位和滑动手势、部署工作流及线上build-info。导航滚动时背景略透字，暂不为此改动正常样式。截图仅存在本次聊天，未上传仓库。Pages展示验收不等于MOD安卓Amethyst游戏验收或正式1.0发布。

恢复：保留现有网页及历史链接；工程以codex/v1-abyss-world分支HANDOFF、TEST-REPORT-V1.0、38580056771运行证据和PR #6为准。此检查点命名「V1.0 Snapshot 4」，待用户安卓复验后再决定正式版。只修真实问题，不增加冗余文件。

---

# Phase 7 展厅收尾 · 2026-10-13

正式游戏原图来自完整技术通过运行38354855268、源码057afde。现有静态展厅追加第七阶段主世界发现与往返页面、七个画廊机位和实际回站／重连图；保留第六阶段及更早永久入口和对照。第六阶段整体美学按用户最新认可更新；第七阶段新增画面仍待判断。

分支交付源码 `c227ff77498e08d817fe7a1d253041117d4b3978`；真实托管Linux Chromium预览 [38357271286](https://github.com/Msipla/-Moonlit-Halt/actions/runs/38357271286) PASS，证据提交 `6863b39`。核对预览build-info的源码后，360/390/430/768/1440px均无横向溢出、JavaScript异常0；七阶段切换、第七阶段七个机位、旧模式约束和同机位对照、原图窗口、16条永久入口检查通过。390px第七阶段详情8张原图完整加载、无溢出。

完整JSON和17份原始网页帧见 [evidence/website/runs/38357271286](../../projects/minecraft-mods/experiments/glimmering-abyss/evidence/website/runs/38357271286/)。开发侧已查看390px画廊／详情与1440px首页截图。此轮是Actions内本地HTTP预览，`preview=true`，不能当作Pages上线；合并后的实际发布与线上QA另行补齐。托管Chromium移动视口／触控模拟，不是实体手机、Safari或Windows浏览器实测。

正式交付由 [PR #1](https://github.com/Msipla/-Moonlit-Halt/pull/1) 合并，提交 `f68ead9df06e090ed25114d027fce43872fc4f34`。Pages [38357637066](https://github.com/Msipla/-Moonlit-Halt/actions/runs/38357637066) 发布成功；实际线上Chromium [38357637094](https://github.com/Msipla/-Moonlit-Halt/actions/runs/38357637094) 核对该部署源码后PASS，`preview=false`。五种视口与全部上述交互再次通过，16条永久入口HTTP200，JS异常0；原始JSON／17份网页帧见 [evidence/website/runs/38357637094](../../projects/minecraft-mods/experiments/glimmering-abyss/evidence/website/runs/38357637094/)，归档提交 `10c685e`。开发侧已查看线上390px画廊与1440px首页，详情帧与预览内容一致。

独立读取线上build-info得到f68ead9，phase7/latest为TECHNICAL_PASS、最终游戏实跑38354855268；phase7、phase6、phase1页面均HTTP200。此后仅文档／历史工作流入口整理，不修改已验证HTML／JS／CSS／原图／安装包。后续文档触发的Pages版本以最终build-info为准，不把新的文档提交冒称已重新实测的前端源码。

以下保留历次发布记录。

---

# Phase 6 展厅收尾 · 2026-10-13

交付提交 `cf8b775a633a4ced2ab8966271472bb5df340548`；Pages [38276893016](https://github.com/Msipla/-Moonlit-Halt/actions/runs/38276893016) 发布成功。真实托管Linux Chromium QA [38276893132](https://github.com/Msipla/-Moonlit-Halt/actions/runs/38276893132) 核对线上build-info源码后通过；浏览器证据归档提交 `3b31228598b7d2e2469b195f5be693e151c6faf5`。

360/390/430/768/1440px无横向溢出，零JavaScript异常；第六阶段九个机位、按实际原图限制的模式切换、三机位95%及渐变对照、原图窗口与阶段分享入口通过。phase1–6永久页面和历史证据入口均HTTP200。开发侧已查看第六阶段390px画廊、对照和完整详情页的实际网页截图；原图没有网页调色滤镜。浏览器截图及summary见 [evidence/website/runs/38276893132](../../projects/minecraft-mods/experiments/glimmering-abyss/evidence/website/runs/38276893132/)。

这是实际Chromium浏览器运行和移动视口/触控模拟，不能称为实体手机、Safari或Windows实测。后续交接记录只补充文档，不改已验证前端或安装包；文档触发的Pages发布以对应工作流和线上build-info为准。技术通过不等于用户美学通过。

以下为历史报告，保留原始结论与证据。

---

# Phase 5 展厅收尾 · 2026-10-12

前端交付提交 `d0e364cec0539c2c714a21113250a92b1f598854`；正式 Pages [38261641907](https://github.com/Msipla/-Moonlit-Halt/actions/runs/38261641907) 发布成功。真实 Chromium 浏览器 [38261641881](https://github.com/Msipla/-Moonlit-Halt/actions/runs/38261641881) 核对部署 build-info 源码后完成检查，PASS；证据提交 `310cd26f74928b4fb6a98df7e8dbc466cf45c170`。

原始网页截图与汇总：[evidence/website/runs/38261641881](../../projects/minecraft-mods/experiments/glimmering-abyss/evidence/website/runs/38261641881/)。已由开发侧查看手机视口首页与第五阶段画廊。第五阶段正式原图来自游戏实跑38259041182，网页截图不能替代游戏实跑。

- 360/390/430、768/1440px 无横向溢出，画廊保持16:9，机位/模式/放大操作通过。
- 五阶段切换与永久分享入口、第五阶段九机位、原版/铜金控制、同次工坊正面精修对照通过。
- phase1–5.html 全部 HTTP200；旧阶段证据指针、旧平台/Veil截图继续可访问；第五阶段详情原图与对照全部载入。
- 原 Pages 架构、原图和历史入口保留；没有统一门户或月下小站开发。第五阶段美学状态仍为待用户判断。
- 零页面 JavaScript 错误。测试是托管 Linux Chromium 的移动视口/触控模拟，实体 iQOO、Safari 和 Windows 未测。

以下为历史报告，保留原始结论与证据。

---

# 项目展厅测试报告 · 2026-10-12

正式地址：https://msipla.github.io/-Moonlit-Halt/

## 实际验证与来源

前端源码 `570ba163b47c73bd4dbddbfba4aec66f8ed395f8`；Pages 发布 [38239083223](https://github.com/Msipla/-Moonlit-Halt/actions/runs/38239083223) 成功。独立 Ubuntu / Chromium 浏览器 QA [38239083189](https://github.com/Msipla/-Moonlit-Halt/actions/runs/38239083189) 已实际访问发布网站，先核对 build-info.json 的源码提交，再执行检查；结果 PASS，证据提交 `f67223fdfdf790fc0011cde60d6af461d4eee4a9`。

原始网页截图与 summary.json 位于工程 [evidence/website/runs/38239083189](../../projects/minecraft-mods/experiments/glimmering-abyss/evidence/website/runs/38239083189/)。这是网站浏览器证据，不是新的 Minecraft 客户端捕获。游戏原图仍来自各阶段独立证据目录。

| 项目 | 已实际验证 |
| --- | --- |
| 视口 | 360/390/430 × 844 手机触控模拟，768/1440 × 1000 平板/桌面；均无横向溢出 |
| 阶段导航 | 第一/第二/第三阶段切换及带 stage 参数的分享入口；历史机位/模式可选择 |
| 画廊 | 图片加载、16:9 比例、色调和机位切换、原图窗口与原 PNG 链接 |
| 对照 | 滑动控件更新裁切位置；同机位切换；Veil 仅开放有历史原图的平台机位 |
| 手机操作 | 已检查的画廊机位/模式/放大按钮高度均为 44px |
| 永久页面 | phase1.html、phase2.html、phase3.html 均 HTTP 200 |
| 历史证据 | 第一/第二阶段 summary、第一阶段平台图、第二阶段 Veil 平台图及第三阶段 latest.json 均 HTTP 200 |
| 运行错误 | 未捕获到 JavaScript 页面错误 |

开发侧已查看最终手机视口首页/画廊/对照截图。照片显示无调色滤镜；首页与缩略图允许裁切，放大窗口提供原始 PNG。网站明确标注第二阶段美学不通过、第三阶段技术实跑与美学待验收，未使用外部参考图冒充游戏截图。

## 发现并保留的失败

首次展厅源码 `7318333`、浏览器运行 `38238604163` 的旧版自动交互检查通过，但查看其手机视口截图发现图片 height 属性导致画廊/对照过高留白。该轮证据完整保留，不能把其自动 PASS 当作排版无缺陷。`570ba16` 修正图片 height:auto，加入比例断言、等待当前图片解码和实际锚点导航截图；最终独立浏览器复查通过。

## 范围与后续

这是 Linux Chromium 的真实浏览器运行及手机视口/触控模拟，不是实体手机、iQOO、Safari 或 Windows 实测。需要用户设备上的实际浏览反馈。网页功能验证不等于 MOD 美学验收，也不等于 Windows/PCL2/GPU/Sodium/Iris/Distant Horizons 兼容性通过。

第一阶段原首页报告完整保存在 phase1.html；phase2/3 报告、历史 PNG 与原始证据链接继续可访问。后续阶段发布按 [README](README.md) 同步更新 exhibition.json、永久报告页和 Pages 精选证据，保持技术、美学与研发状态分离。本报告之后的文档交接提交不修改已验证前端；最终 Pages 是否发布须核对相应 workflow 与线上 build-info.json。


## Phase 4 展厅发布与复测 · 2026-10-12

前端源码 `488e7ff57ba137ad7c89f9c7daf5ac77a9c8ec9e`；Pages [38251393211](https://github.com/Msipla/-Moonlit-Halt/actions/runs/38251393211) 已成功。真实 Chromium QA [38251393054](https://github.com/Msipla/-Moonlit-Halt/actions/runs/38251393054) 访问发布站，核对 build-info.json 源码后通过。

360/390/430/768/1440px 无横向溢出；第一至第四阶段导航、第四阶段7个机位、单机位控制、原图窗口、放置前后滑动对照及原有第三阶段色调对照检查通过；第四阶段永久原图页及前三阶段旧页面/历史证据HTTP 200；JS异常0。390px第四阶段详情页实际图片加载且不溢出。QA代码和完整JSON在工程 evidence/website/runs/38251393054，开发侧已查看390px首页/画廊截图。

首页引用最新真实客户端原图与第四阶段状态；第三阶段方向认可与第四阶段建筑待验收分开，历史图未覆盖。仍只是 Linux Chromium 手机视口/触控模拟，不是实体 iQOO/Safari/Windows 测试。

此次归档仅追加测试报告和交接证明，不改已验证前端；后续仅文档发布以相应 Pages 工作流和 build-info.json 为准。
