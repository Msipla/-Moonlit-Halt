# Ponder 知识索引（只读查询接口）

从安装版 Create `6.0.8.1+build.1744-mc1.20.1` 的公开 JAR 提取的离线知识库。
供 AI 工具层直接调用，无 HTTP 服务、无第三方依赖（纯 Python 标准库）。

索引包含 110 个提取到的场景脚本、4244 条有序教学步骤、20 类配方索引。Python 用于研究和离线核查。游戏内与直写试验共用 [PonderGuide](../live-lab/game-bridge/PonderGuide.java)，读取同一场景数据，通过 `engineering.site.guide` 或现有试验入口查场景、关键词和安装版配方。

## 接口

```python
import sys; sys.path.insert(0, r"research/technical/numen-create-vs2/ponder")
import pondex

pondex.stats()                        # 覆盖统计、来源、完整性说明
pondex.search("cogwheel", limit=3)    # 标题/教学文本；无命中时回落到配方
pondex.machine("mechanical_mixer")    # 单场景：有序步骤、模拟值、配方链接
pondex.steps("millstone", limit=5)    # 仅教学文本 + 场景时间偏移（tick）
pondex.recipes(recipe_type="mixing")  # 真实配方（按加工类型）
pondex.recipes(item="create:andesite_alloy")
pondex.coverage_gap()                 # 110 脚本 vs 178 场景资源的差异
```

每个返回值都是 dict，带 `source`；列表类结果有 `matched / returned / truncated`；
文本截断 160 字符，`machine` 最多 60 步。

## 覆盖（不要当成完整机械知识库）

| 项 | 数值 |
| --- | --- |
| 提取到的场景脚本 | 110（来自 30 个反编译类） |
| JAR 内场景 NBT 资源 | 178 |
| 具有候选 NBT 路径的脚本 | 109（102 个不同的资产路径，均存在于发行 JAR） |
| 有教学文本 | 109 |
| 含演示转速注入 | 44 |
| 挂真实配方的处理机器 | 5（millstone / press / mixer / deployer / crushing_wheel） |

候选路径包括启发式匹配，部分并非实际绑定关系。资产可被多个脚本复用。`coverage_gap()` 给出 76 个未被候选路径指向的资产总数及前 25 个路径，不代表这些资产没有脚本。

## 模拟值不是物理事实

场景脚本里的 `setKineticSpeed` 等调用是演示状态，统一标记为
`kind=simulated_speed`，并附 `demo value injected by the scene, not a game constraint`。
真实转速/接轴面必须用游戏内读取（见 [CreateReadings.java](../live-lab/game-bridge/CreateReadings.java)）。

## 重新生成

```bash
python extract.py /path/to/decompiled/ponder/scenes
python recipes.py /path/to/create.jar
python pondex.py stats
```

`extract.py` 里的 `ALIASES` 表用于把 15 个无法按名字匹配的脚本映射到正确的 `.nbt`。

## 许可

场景文本与配方来自 Create（MIT）。见 [THIRD-PARTY.md](THIRD-PARTY.md)。本目录不含任何模组二进制。
