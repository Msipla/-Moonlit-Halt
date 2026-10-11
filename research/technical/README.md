# 技术研究与验证

这里记录跨项目技术实验，不以笔记代替可运行工程。

推荐每次实验记录：**目标、环境、操作或实现、结果证据、失败现象、结论、下一步**。

优先方向：移动端 Web 3D 性能与兼容性、场景建模及资源管线、光照材质优化、Minecraft 1.20.1 MOD 构建与游戏内验证。

注意：Minecraft Java MOD 与浏览器 3D 原型通常采用不同运行时和资源格式；除非经过转换和实测，不预设二者资产可以直接通用。

## 已有实验

[《月下小站》v2.0 工程](../../projects/moonlit-halt/)使用原生 WebGL 1、合并静态几何、本地材质图集、有限范围暖光和局部解析遮蔽，无网页构建依赖。已完成逻辑/几何检查与原生 GLES 绘制；真实 Android 浏览器性能仍待验证。

- [实际结果与未验证项目](../../projects/moonlit-halt/TEST-REPORT.md)
- [开发检查与启动方式](../../projects/moonlit-halt/README.md)
- [逻辑与几何检查](../../projects/moonlit-halt/tests/check-scene.cjs)
- [原生 GLES 渲染重放](../../projects/moonlit-halt/tests/render-gles.py)
