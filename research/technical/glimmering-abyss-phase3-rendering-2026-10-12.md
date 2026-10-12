# Phase 3 渲染路线与来源

日期 2026-10-12。本文件说明实现选择，实际运行与美学结论见项目 TEST-REPORT-PHASE3；没有截图前不得标记美学通过。

## 实际阅读的主来源

- The Betweenlands 固定快照 `eafac1c217cdf304d477b025708e0dadf82b6953`：[FogHandler](https://github.com/Angry-Pixel/The-Betweenlands/blob/eafac1c217cdf304d477b025708e0dadf82b6953/src/main/java/thebetweenlands/client/handler/FogHandler.java)、[FogState](https://github.com/Angry-Pixel/The-Betweenlands/blob/eafac1c217cdf304d477b025708e0dadf82b6953/src/main/java/thebetweenlands/api/misc/FogState.java)、[WorldProviderBetweenlands](https://github.com/Angry-Pixel/The-Betweenlands/blob/eafac1c217cdf304d477b025708e0dadf82b6953/src/main/java/thebetweenlands/common/world/WorldProviderBetweenlands.java)。观察到客户端亮度表、环境参数、目标状态和插值分开；区域/生物群系可以覆盖配置。旧实现使用 Forge 事件与旧渲染状态，不能直接移植到本工程。浓雾事件与短视距机制不采用。
- [Fabric CoreShaderRegistrationCallback 0.85.0+1.20.1](https://maven.fabricmc.net/docs/fabric-api-0.85.0+1.20.1/net/fabricmc/fabric/api/client/rendering/v1/CoreShaderRegistrationCallback.html)：注册自身命名空间 core shader，避免覆盖原版 shader 资源。
- [Fabric WorldRenderEvents](https://maven.fabricmc.net/docs/fabric-api-0.85.0+1.20.1/net/fabricmc/fabric/api/client/rendering/v1/WorldRenderEvents.html)：原生渲染事件与第三方渲染器可以衔接，但直接渲染仍有管线差异限制。
- [Yarn GameRenderer 1.20.1+build.10](https://maven.fabricmc.net/docs/yarn-1.20.1+build.10/net/minecraft/client/render/GameRenderer.html)：核实 solid/cutout/cutout-mipped shader supplier 的固定版本接口与映射。

## 比较与选择

| 路线 | 优点 | 本轮取舍 |
| --- | --- | --- |
| 增大 lightmap 底光 + 原版距离混合 | 侵入少、实现简单 | 第二阶段已有实验。缺少表面方向和世界位置层次，不能只加亮再次当作第三阶段 |
| 自有 core terrain shader | 每个表面按世界位置、朝向和材质着色；距离项可设上限 | 本轮选用，只对测试维度中的 solid/cutout 生效，用户可切回 A |
| 完整后期、G-buffer、体积光或 GI | 可能处理遮挡和复杂光照 | 超出本次最小原型，性能及兼容成本更大，未实现、未验证 |

## 当前实现语义

`SpaceProfile` 配置环境颜色、半球方向光、空间中心/半径/辐照强度、空气颜色/距离/最大混合。`SpaceLighting` 从 `config/glimmeringabyss-visuals.json` 读取，首次由 MOD 默认 JSON 创建；`/abyssrender reload` 更新，非法配置保留上一份有效值。

位置场使用平滑高斯衰减；表面法线决定迎光与背光差异。有限空气透视混合量默认 0.22，上限配置验证为 0.35，保留表面信息；原生边界与状态效果距离仍是单独项。色调不是全屏滤镜，环境色乘表面材质；HUD 不走此 shader。blue 与 copper 除颜色之外使用相同空间参数。blue-clear 与 blue-no-field 为必要的机制消融控制。

服务器方块/天空光和客户端原生 lightmap 不被新系统修改。原版灯笼为可选临时照明控制，不是文明建筑完成证据。15 级本地光权重保护在纯参数测试和游戏帧中分别核查。

## 明确边界

这是预设的、未做遮挡追踪的环境辐照场；厚墙后的泄漏、任意复杂洞穴的光场生成不属于已解决问题。当前只有一处有限洞厅。世界活动、城市、阴影 GI、透明玻璃/水、粒子、实体照明、Sodium/Iris/Distant Horizons 及 Windows/GPU 均不在已验证范围内。第三方管线可能绕过 shader supplier；不能称为通用兼容。

本 MOD 的 Java/GLSL 是为 Fabric 1.20.1 编写的实现，不复制 Betweenlands 源码或外部作品美术资产。
