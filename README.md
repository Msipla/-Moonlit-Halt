# Moonlit Halt · 游戏美学与 Minecraft MOD 实验室

> 独立于 [Archives 世界档案馆](https://github.com/Msipla/Archives) 的实验性游戏开发仓库。

本仓库同时容纳 **《月下小站》（Moonlit Halt）独立 3D 美学原型**、**Minecraft Java 1.20.1 MOD 项目**，以及二者可共享的视觉与技术研究。仓库名称沿用《月下小站》，但范围不限于单个场景。

## 项目入口

| 方向 | 目录 | 定位 |
| --- | --- | --- |
| 《月下小站》 | [projects/moonlit-halt/](projects/moonlit-halt/) | 小规模、可漫游的维多利亚月夜车站 3D 美学原型 |
| Minecraft MOD | [projects/minecraft-mods/](projects/minecraft-mods/) | Minecraft Java 1.20.1 模组与独立玩法实验 |
| 美学研究 | [research/aesthetics/](research/aesthetics/) | 氛围、光照、巨构、阈限空间、深海与洞穴的视觉研究 |
| 技术研究 | [research/technical/](research/technical/) | 建模、材质、性能、工具链、兼容性与验证笔记 |

## 开发原则

1. **先有可验证成果，再宣布完成。** 设计文档、截图、代码、可运行工程与已实测版本分别标注，不能互相冒充。
2. **项目隔离。** 《月下小站》是独立的 3D 艺术实验；Minecraft MOD 另行选型、开发和测试。共享灵感不代表共享工程。
3. **保留迭代记录。** 不覆盖或丢弃可复现的旧版本；重大设计更改记录动机与验证结果。
4. **以实际体验为目标。** 小规模高质量、可漫游、可观察；避免为了堆功能而牺牲氛围与完成度。
5. **不混入跑团正史。** 《艾瑟兰德》世界设定、角色与权威存档仍由 Archives 管理。

## 当前状态

- **仓库结构：** 已建立项目隔离的目录；源码、资源与验证记录已经导入。
- **《月下小站》：** 已导入可独立启动的 v2.0 网页工程、原始 v1.0 备份、本地材质、同视角实际离屏渲染图及测试工具。逻辑检查与原生 GLES 渲染已通过；v2.0 的真实 iQOO 15 浏览器体验和稳定帧率仍待实测。
- **Minecraft MOD：** 《微光深渊》v0.2.0 已构建并取得真实游戏证据；第二阶段技术成果保留，用户美学验收不通过。第三阶段进行可配置空间视觉研发，最新状态以[项目入口](projects/minecraft-mods/experiments/glimmering-abyss/)为准。

## 体验与启动

- [v2.0 手机预览](https://moonlit-little-station.misty-bass-0146.chatgpt.site/)
- [v1.0 备份预览](https://moonlit-little-station.misty-bass-0146.chatgpt.site/v1/)
- [同视角渲染对比](https://moonlit-little-station.misty-bass-0146.chatgpt.site/studies/)

预览保留原有私有访问范围，可能需要项目所属的 ChatGPT 账号登录。GitHub 存放源码，更新此仓库不会自动更新上述站点。

在仓库根目录运行：

```sh
python -m http.server 8000 --bind 0.0.0.0 --directory projects/moonlit-halt/dist
```

电脑访问 `http://localhost:8000`；同一 Wi-Fi 的手机访问 `http://电脑局域网IP:8000`。无需构建或安装网页依赖。详细操作见[项目说明](projects/moonlit-halt/README.md)，验证范围见[测试报告](projects/moonlit-halt/TEST-REPORT.md)。

## 下一步

先在 iQOO 15 手机浏览器体验 v2.0，比较同一路径的建筑、材质、月光与暖光，并记录操作、声音与性能反馈。Minecraft MOD 仍按具体创意建立独立子项目。

仓库维护以 README、各项目文档和实际提交记录为准。
