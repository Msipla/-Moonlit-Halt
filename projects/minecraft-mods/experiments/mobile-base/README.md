# 智能移动基地装配器 · 船舶原型

玩家自由搭船。装配器识别与核心相连的方块，预览并补齐 Eureka 船舵、燃料引擎、浮力块和锚。Valkyrien Skies 负责实体物理、碰撞和乘员随船运动。当前范围是创造模式的小型水面船舶，项目目标以 [TASK.md](TASK.md) 为准。

## 安装和操作

在 PCL 等启动器中单独创建 Minecraft Java **1.20.1 + Fabric Loader 0.17.2** 的实例。使用 Java 17，将以下模组和[装配器 0.1.0-alpha.1](artifacts/mobile-base-assembler-0.1.0-alpha.1.jar) 放进这个实例的 `mods` 文件夹。独立服务端与客户端使用相同版本。

| 模组 | 版本 | 下载 |
| --- | --- | --- |
| Fabric API | 0.92.6+1.20.1 | [Fabric Maven](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.92.6+1.20.1/fabric-api-0.92.6+1.20.1.jar) |
| Valkyrien Skies | 2.4.10，1.20.1 Fabric | [Modrinth](https://modrinth.com/mod/valkyrien-skies/version/d19WzCuD) |
| Eureka | 1.6.3，1.20.1 Fabric | [Modrinth](https://modrinth.com/mod/eureka/version/aJWa3eWO) |

此版本的 VS2 JAR 自带 Fabric Language Kotlin 和 Forge Config API Port。测试请建新存档。

1. 在水面搭一块约 5×9 的木质甲板，保持船体与岸、码头和建造时的脚手架之间有一格空隙。留出船底下方的水深，以及舵手坐下和离席所需的两格甲板。可加一个箱子与小舱室。
2. 朝船头放置「移动基地核心（船舶）」，核心右边留一个甲板格供锚使用。核心可从创造模式红石分类取得，或用 `/give @s mobilebase:boat_core`。
3. 空手右键核心预览。青色是船体，金色是新增组件。十秒内再点一次确认，结构有改动时会产生新预览。
4. 在船上给 Eureka 引擎加煤。空手右键 Eureka 船舵坐下，按 W/S 前进/后退，A/D 转向，C 切换 Eureka 原生巡航。引擎从冷机启动时需要升温。
5. 潜行、空手右键核心切换抛锚。要解除组装，先用 Eureka 的对齐功能把船调到正方向，抛锚并等船停稳，所有乘员离船。空手右键核心预览落点，再点一次确认。解除后可编辑并再次装配。

## 源码调查与选型

| 上游 | 核实的源码能力 | 结论 |
| --- | --- | --- |
| **VS2 2.4.10** · LGPL-3.0 | `assembleToShip` 接受明确方块坐标集合。模板搬运携带方块实体 NBT，但原件先于目标放置被移除，不能把调用完成等同于事务提交。`relocateBlock` 有旋转和 NBT 搬运，但无目的地保护。上游实体碰撞和载具位置变换承担随船运动。 | 使用上游装配与搬运。确认时记录原位置方块与 NBT，并检查搬运结果和目的地；在可捕获的异常上回滚。 [装配源码](https://github.com/ValkyrienSkies/Valkyrien-Skies-2/blob/05298c63ae088fda71c3f72da12a14612381e20a/common/src/main/kotlin/org/valkyrienskies/mod/common/assembly/ShipAssembler.kt) · [搬运源码](https://github.com/ValkyrienSkies/Valkyrien-Skies-2/blob/05298c63ae088fda71c3f72da12a14612381e20a/common/src/main/kotlin/org/valkyrienskies/mod/util/RelocationUtil.kt) |
| **Eureka 1.6.3** · Apache-2.0 | 核实 FuelRegistry、发动机热量、对船的动力贡献、红石锚和原生巡航。上游默认船舵本身可提供基础速度，原生解体按最近姿态搬运且可强制超时，缺少目标占用检查。 | 自动补齐现成部件。对本项目组装的船关闭船舵基础前进速度，推进来自 Eureka 燃料引擎。锚由核心的定向红石输出驱动。拆船通过核心的静止、姿态、落点及库存检查。 [控制源码](https://github.com/ValkyrienSkies/Eureka/blob/07cf4181d72590731d140d603bda5c9de23c5ae7/common/src/main/kotlin/org/valkyrienskies/eureka/ship/EurekaShipControl.kt) · [燃料引擎](https://github.com/ValkyrienSkies/Eureka/blob/07cf4181d72590731d140d603bda5c9de23c5ae7/common/src/main/kotlin/org/valkyrienskies/eureka/blockentity/EngineBlockEntity.kt) · [原生拆解](https://github.com/ValkyrienSkies/Eureka/blob/07cf4181d72590731d140d603bda5c9de23c5ae7/common/src/main/kotlin/org/valkyrienskies/eureka/util/ShipAssembler.kt) |
| **Create 1.20.1** · MIT | 创造马达和传动都基于旋转动力网络。安装时须考虑转轴方向、转速、动力来源与应力。轴连接由双方方块的轴向和旋转传播决定。 | 用于后续真实传动。此船型的动力链采用 Eureka 原生部件。 [Create 1.20.1 源码](https://github.com/Creators-of-Create/Create/tree/588124239141029c5321afd38d20c2eaf872b32b/src/main/java/com/simibubi/create/content/kinetics) |
| **Trackwork 1.20.1** · 代码 MIT，素材单独授权 | 官方 Forge 1.20.1 分支依赖 VS2 和 Create 6.0.8。轮组件在 VS2 船上初始化，检查相邻轴，动力继承 Create 的 RPM，转向读红石并可联动对侧车轮。 | 房车还需规划双侧安装空间、车轴、旋转动力与转向控制，以及验证移除和重装后轮组件状态。 [轮组源码](https://github.com/Endalion/trackwork/blob/702649650aa6bb630ce5fc71255c598d87240e57/src/main/java/edn/stratodonut/trackwork/tracks/blocks/WheelBlockEntity.java) · [版本锁定](https://github.com/Endalion/trackwork/blob/702649650aa6bb630ce5fc71255c598d87240e57/gradle.properties) |
| **Clockwork 0.5.7.1** · Apache-2.0 | 1.20.1 Forge/Fabric 均有发布，源码依赖 VS2 2.4.11 和 Create 6。螺旋桨轴承要组装桨叶 contraption，转速来自 Create，并通过 `PropellerController` 作用在船体指定位置。 | 适合真实桨叶与传动的后续验证；需要额外的动力网络、桨叶扫掠空间和解除组装流程。 [轴承源码](https://github.com/ValkyrienSkies/Clockwork/blob/a6573f2019070aaf6467237b0f2f21998a03536b/common/src/main/kotlin/org/valkyrienskies/clockwork/content/contraptions/propeller/PropellerBearingBlockEntity.kt) · [推力源码](https://github.com/ValkyrienSkies/Clockwork/blob/a6573f2019070aaf6467237b0f2f21998a03536b/common/src/main/kotlin/org/valkyrienskies/clockwork/content/forces/PropellerController.kt) |

## 构建

在本目录用 JDK 17 和 Gradle 8.8 执行 `gradle build`。Loom 用 Mojang 映射编译，并将成品重映射至 Fabric 可加载的命名空间。成品位于 `build/libs/`。

## 原型边界

- 一次装配只处理一艘船。扫描沿六面相邻方块扩展，距离核心水平或垂直各至多 12 格，总计最多 512 格，预估质量上限 80,000 千克。超限或遇到不支持的方块会拒绝整次装配。
- 容器支持范围限普通箱子和木桶。箱子、木桶、核心与 Eureka 组件的数据按当前块状态和 NBT 迁移。未知模组方块、水浸方块及其他方块实体在此阶段拒绝迁移。
- 浮力按独立水域和船体材料估算。首版为小型木质船体设计。密封舱内水体、极端偏载、长时多人、服务器突然中止和方块搬运同时被第三方附属修改需要独立验证。
- 解除拒绝斜船姿态、非单位缩放、占用落点、船上有实体，以及目标区块不可用的情况。船体已对齐时，预览展示方块网格的实际落点。

VS2 2.4.10 在开放船体测试中把大块船坞区块空气误算为蓄气舱，导致船体剧烈弹跳。对核心组装的水面船，使用 VS2 原生水体阻力与 Eureka 浮力块，停用该船的可选密封气囊浮力。按船标记保存在 Eureka 控制数据里，其他载具的对应原生设定仍由上游管理。 [气囊浮力源码](https://github.com/ValkyrienSkies/Valkyrien-Skies-2/blob/05298c63ae088fda71c3f72da12a14612381e20a/common/src/main/kotlin/org/valkyrienskies/mod/common/util/BuoyancyHandlerAttachment.kt)


## 游戏内验证

使用成品 JAR，在隔离的 Minecraft Java 1.20.1 客户端及专用服进行。船为 5×9 木甲板加小舱室，箱子的第 11 号槽预存 7 颗钻石。记录包括[位置和速度采样](evidence/verification.json)、[专用服日志摘录](evidence/server-excerpt.txt)与[巡航中打开箱子的客户端画面](evidence/chest-underway.png)。

| 项目 | 实际结果 |
| --- | --- |
| 接岸保护与预览 | 连接码头时拒绝装配，连接块与箱中钻石保留。隔离后显示青色船体、金色新增组件。首次右键时舵位仍为空，箱中内容原样保留。 |
| 装配与动力 | 75 格船体自动补 6 个原生组件，含船舵、引擎、锚和 3 格浮力块。真实坐入船舵，空引擎按 W 2.7 秒的水平位移约 0.002 格。用引擎界面装煤后，同样按键 2.7 秒前进约 2.27 格。服务端记录引擎升温、已消耗 1 块煤。 |
| 巡航、离席和容器 | 用 C 开启巡航，潜行离座后在船上走动，飞行状态为关闭。重装后的船在实际巡航中打开箱子，界面显示原来的 7 颗钻石。开箱后的船速约 3.30 格/秒，巡航仍开启。 |
| 抛锚与解体安全 | 核心点亮实际红石锚并停止巡航。解体用隔离测试命令将同一艘停船摆至标准泊位。放入金块时拒绝迁移，金块与船上库存保留。移除障碍、确认预览后，81 格完成迁移，原物理船记录随之清除，箱中第 11 号槽仍有 7 颗钻石。 |
| 转向和原生对齐 | 重装后按 A 0.9 秒得到约 31° 偏航。从原生舵菜单请求解体时，船体保留，解体入口为核心。在原生舵菜单点击 Align 后，实际完成抛锚和核心解体，82 格恢复，其中包含新增玻璃；箱中仍有 7 颗钻石。 |
| 修改和重装 | 解除后加入玻璃并再次装配。82 格全部随新船迁移，新增组件为 0，玻璃与箱中钻石在新船上均可回读。 |

**JAR SHA-256** `0e9e618b85f0c1b2d70c5bd6b6f7adf443437e0b9b21c847446918e2a06ec045`

构建使用 Java 17、Gradle 8.8 和 Fabric Loom 1.6.12。隔离运行环境禁用 Unix 域套接字，构建时在本地让 Loom 将该能力识别为不可用，随后执行正常编译和重映射。交付 JAR 仅包含装配器源码对应的类和资源。

当前为可安装验证的实验版。Windows 玩家实例、长期多人和跨服务器重启待验证。占用保护测试使用的标准泊位由测试指令摆放。重装后的第二次解体使用游戏内船舵对齐与核心抛锚。
