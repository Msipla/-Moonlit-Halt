# 第三方许可

## Create（Minecraft 模组）

- 全名：Create: Fabric `6.0.8.1+build.1744-mc1.20.1`
- 许可按 [Fabric 上游许可文件](https://github.com/Fabricators-of-Create/Create/blob/4c3fe779d135d592548e4cb09da04d742d2637b7/LICENSE.md) 区分。JAR 声明 MIT，审查过的 Fabric 源码快照另对 `src/main/resources/assets/` 保留全部权利。
- 场景文本系场景 Java 字节码中的内联字符串，配方来自 `data/create/recipes/`。索引只引用场景资产位置，未复制 assets 目录文件、NBT 或翻译资源。JAR 发行标识与审查的源码快照分别记录。

本目录的派生数据及打包的场景文本保留以下源码许可：

```text
MIT License

Copyright (c) The Create Team / The Creators of Create

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

`extract.py` / `recipes.py` / `pondex.py` 为原创代码。

## Valkyrien Skies 2 / Eureka

本目录不重新分发这两个模组的任何文件。`ship-lab/src/ShipTest.java` 只通过反射调用它们
的公开 API（类名与方法名出现在字符串里），没有复制源代码。

- Valkyrien Skies 2.4.11：发行页标 **LGPL-3.0**，而 2.4.11 Fabric JAR 的内部元数据写
  **Apache-2.0**（该差异已在[技术调查 README](../README.md)记录）。本目录两种口径都保留，
  仅链接接口，不复制代码。
- Eureka 1.6.4：**Apache-2.0**
