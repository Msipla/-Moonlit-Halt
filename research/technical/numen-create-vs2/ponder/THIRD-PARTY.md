# 第三方许可

## Create（Minecraft 模组）

- 全名：Create: Fabric `6.0.8.1+build.1744-mc1.20.1`
- 许可：**MIT**（取自安装 JAR 的 `fabric.mod.json` 中 `"license": "MIT"`）
- 上游：https://github.com/Creators-of-Create/Create

本目录包含的**派生数据**（`knowledge/scenes.jsonl`、`knowledge/recipes.json`、
`knowledge/index.json`、`nbt-index.json`）由该 JAR 的 Ponder 场景脚本与配方 JSON 提取而来，
不含任何模组二进制、类文件或原始资源。按 MIT 条款保留上述署名。

`extract.py` / `recipes.py` / `pondex.py` 为原创代码。

## Valkyrien Skies 2 / Eureka

本项目不重新分发这两个模组的任何文件。`ship-lab/src/ShipTest.java` 通过反射调用它们的
公开 API（类名与方法名出现在字符串里），没有复制其源代码。

- Valkyrien Skies 2.4.11：**Apache-2.0**
- Eureka 1.6.4：**Apache-2.0**
