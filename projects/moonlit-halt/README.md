# 月下小站 v2.0 · Moonlit Halt

维多利亚月夜艺术习作。接管 v1.0 的原生 WebGL 1 架构，保留小路、车站、职工小屋、铁路、场景边界及原有漫游操作。本版集中升级建筑、材质与光照；没有增加游戏系统或新区域。

## 直接体验

- [打开 v2.0](https://moonlit-little-station.misty-bass-0146.chatgpt.site/)。
- [打开 v1.0](https://moonlit-little-station.misty-bass-0146.chatgpt.site/v1/)，可以直接体验旧版。
- [对比与渲染图](https://moonlit-little-station.misty-bass-0146.chatgpt.site/studies/)。

这些地址保留原有私有访问范围，可能需要使用项目所属的 ChatGPT 账号登录。

手机：点击「走进月夜」，左下摇杆移动，拖动画面环顾，建议横屏。「声音」开启/关闭环境声；「画质」切换标准/省电；「回到小路」重置。电脑使用 WASD / 方向键移动，鼠标拖动环顾。无室内空间，门不可打开。

## 本地启动，无需构建或安装依赖

下载仓库 ZIP 或克隆仓库，在 `projects/moonlit-halt` 目录打开终端：

```sh
python -m http.server 8000 --bind 0.0.0.0 --directory dist
```

Windows 可将 `python` 换成 `py`。电脑打开 `http://localhost:8000`。手机与电脑接入同一个 Wi-Fi 后打开 `http://电脑局域网IP:8000`；如防火墙提示，只允许私人网络。跨网络访问使用在线预览。

原型仅需静态 HTTP/HTTPS 服务。不需要 npm、Three.js、额外手机应用、外部 CDN 或在线模型服务。手机文件管理器直接打开 HTML 的兼容性不一致，建议使用预览网址。

## 本版变化

- 车站原有 12×7 米主体配纵向板岩屋顶与窄入口山墙，入口略微前凸，仍处于旧版雨棚范围内。
- 分片、错缝、叠压板岩瓦，石材压顶、檐口、齿状砖饰、门窗拱券与分层窗台。
- 凹进的窗玻璃、细窗棂、克制窗帘，深绿面板木门，石框时钟。
- 细铸铁柱、柱脚与柱头、曲线支撑、波形檐口；雨棚沿原站台一侧接续。
- 细砖课程、较合理的砖块比例、灰缝、煤烟颜色沉积；本地原创轻量材质图集表现砖、石、木、铁、板岩、草地、砂砾。
- 月面使用原创银白色纹理与海区/浅陨坑细节，冷色月光塑形，暗部保留层次。
- 煤气灯暖光半径 3.8 米，窗户暖光半径 2 米，超出范围后为零。暖窗和灯罩亮度独立降低。
- 局部解析遮蔽用于建筑接地与雨棚下方；不使用实时投射阴影或浓雾。
- 煤烟改为柔边、透明排序的轻量片元，减少第一版串珠状的外观。
- 小屋、树木、道路与铁路同步材质和颜色；不增加场景规模。
- 触屏、碰撞、环境声、重置及省电模式沿用；帧率显示改用真实帧间隔，运动步长仍有安全限幅。

## 性能设计与边界

标准模式的像素倍率上限为 1.5，省电模式为 1；不按手机原生物理分辨率强行渲染。

静态场景合并为一个缓冲区：219,824 个三角形，约 27.7 MiB GPU 顶点数据。三份纹理共约 228 KiB。核心网页和美术资源合计约 267 KiB，网格在本机生成，不下载大模型。构建后的 CPU 几何数组会释放；透明烟雾每帧仅 840 个顶点（省电 504）。没有后期处理、实时阴影或物理引擎。

v1.0 已由用户在 iQOO 15 上确认能加载、移动、转向、播放声音。v2.0 的手机帧率、耗电、发热和浏览器兼容性尚未实测，不能将离屏渲染结果等同于手机稳定 60 FPS。

## 文件结构

- `dist/`：完整可部署网页；`assets/` 为本地原创纹理。
- `dist/v1/`：未改动的 v1.0 网页与原始源码 ZIP。
- `dist/studies/`：同视角离屏渲染图、对比图、相机参数及查看页面。
- `TEST-REPORT.md`：实际检查、用户确认与未验证项目。
- `ART-BRIEF.md`：项目美术方向、五项反馈与迭代边界。
- `IMPORT-RECORD.md`、`SOURCE-MANIFEST.json`：版本来源与原始资源校验。
- `tests/`：逻辑/几何测试与原生 GLES 渲染重放。
- `tools/`：材质再生成、渲染对比与打包工具；运行原型无需这些工具。

## 恢复 v1.0

无需覆盖 v2.0，直接访问 `/v1/`。要独立部署旧版，把 `dist/v1/index.html`、`style.css`、`scene.js` 放入任意静态站点根目录。要取得完整的旧工程，解压 `dist/v1/moonlit-halt-source.zip`。托管服务也保留原有 v1 版本。

## 复现开发检查

```sh
node --check dist/scene.js
node tests/check-scene.cjs
python tests/render-gles.py
python tools/render-studies.py
```

第二项使用 DOM / GL 替身，验证逻辑并捕获渲染命令。后两项需要 Linux Mesa EGL、Python 和 Pillow；实际在 OpenGL ES 中编译着色器、绘制几何、纹理及透明烟雾并读取像素，输出原生离屏图。它们不运行浏览器 DOM、触控驱动或声音，不能替代 Android 实测。旧版浏览器 Canvas 站牌文字在离屏检查中由 Pillow 近似；v2.0 使用同一份站牌 PNG。

运行 `python tools/make-textures.py` 可重新生成原创纹理，需要 NumPy、Pillow 及 DejaVu 字体。这些只是开发依赖，不进入手机运行环境。

运行 `python tools/package-source.py` 可生成 `dist/moonlit-halt-source.zip`，包含源码、资源、v1 备份、截图和检查工具。生成的 v2 ZIP 不重复提交到 Git；原始 v1 ZIP 已纳入版本管理。

源码仓库与在线预览分别维护，更新 GitHub 不会自动更新现有站点。导入来源和文件校验见 [IMPORT-RECORD.md](IMPORT-RECORD.md)。
