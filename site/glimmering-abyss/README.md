> Phase 4 维护：新增 phase4.html 与第四阶段画廊，原有 Phase 1–3 永久入口和所有历史截图保留。旧光照对照固定到 Phase 3，建筑放置前后对照固定同次 Phase 4 平台机位。阶段参数继续由 exhibition.json 管理。

# 微光深渊 · GitHub Pages 项目展厅

面向非技术用户，使用原生 HTML/CSS/JS，无构建依赖与第三方字体/图像源。权威工程仍在 projects/minecraft-mods/experiments/glimmering-abyss；本站只展示经过验证的实际游戏帧与清晰状态。

## 文件职责
- index.html：首页与画廊/对照/时间线的语义结构；旧首页的第一阶段报告完整保存在 phase1.html。
- exhibition.css / exhibition.js：响应式排版和浏览交互。
- exhibition.json：阶段导航、显示状态、图片来源、版本与时间线。阶段切换分享入口为 `?stage=phase1#gallery` 等；phase1/2/3.html 是永久完整报告入口。
- tools/verify-gallery.cjs：独立 Linux CI 中使用 Playwright 检查已发布网站；测试工具不是浏览器访客的运行依赖。

## 新阶段发布

1. 先在权威工程保存真实证据与状态。
2. 在 exhibition.json 增加阶段与实际帧路径、模式/机位、技术/美学/研发状态和时间线；更新 latestStage。动态阶段导航和首页状态由此产生。
3. 增加永久 phaseN.html 完整报告入口，并在专属 Pages workflow 中追加该阶段的精选原图与数据，保留旧页面与 URL。
4. 检查 gallery/compare 当前定义是否适用于新阶段；不把不匹配机位的图片包装成严格对照。
5. 发布后核验 build-info.json，再执行独立浏览器视口/交互/历史链接 QA。视口模拟不等于真实手机测试。

首页不显示外部参考图或概念图。原始截图不做亮度滤镜/调色；缩略图和首页允许裁切构图，放大窗口始终提供原始 PNG。

实际发布与浏览器验证记录见 [TEST-REPORT](TEST-REPORT.md)，网页 QA 原始截图保存在工程 evidence/website，和游戏运行证据分开。
