# 第三方许可

## Create（Minecraft 模组）

- 全名：Create: Fabric `6.0.8.1+build.1744-mc1.20.1`
- 许可：**MIT**（安装 JAR 的 `fabric.mod.json` 中 `"license": "MIT"`，与本仓库
  [技术调查 README](../README.md) 的许可表一致）
- 上游：https://github.com/Creators-of-Create/Create

本目录的**派生数据**（`knowledge/scenes.jsonl`、`knowledge/recipes.json`、
`knowledge/index.json`、`nbt-index.json`）由该 JAR 的 Ponder 场景脚本与配方 JSON 提取，
不含模组二进制、类文件或原始资源。按 MIT 条款保留上述署名。

`extract.py` / `recipes.py` / `pondex.py` 为原创代码。

## Valkyrien Skies 2 / Eureka

本目录不重新分发这两个模组的任何文件。`ship-lab/src/ShipTest.java` 只通过反射调用它们
的公开 API（类名与方法名出现在字符串里），没有复制源代码。

- Valkyrien Skies 2.4.11：发行页标 **LGPL-3.0**，而 2.4.11 Fabric JAR 的内部元数据写
  **Apache-2.0**（该差异已在[技术调查 README](../README.md)记录）。本目录两种口径都保留，
  仅链接接口，不复制代码。
- Eureka 1.6.4：**Apache-2.0**
