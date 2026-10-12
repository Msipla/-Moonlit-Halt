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
