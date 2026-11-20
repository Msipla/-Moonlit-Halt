# 源码与适配接口索引

Numen 以实测的发布包提交为准。下表行号来自原 Work 目录中的 `tag20/`。另一个实测版本对应 `tag21/`，提交为 `06422d1847e0782f91a1bacc5997e74a0249b6c0`。两棵上游源码树的可核对入口见本页末尾。

| 机制 | 类和方法 | 跨到的实际能力 |
| --- | --- | --- |
| 实体和 tick | [`NumenPlayer.tick()`](https://github.com/Dwinovo/minecraft-numen/blob/2f47e7b9c111a1a116360638f97bb853b7571b18/numen-api/common/src/main/java/com/dwinovo/numen/api/entity/NumenPlayer.java#L555) | 继承 ServerPlayer，服务端更新身体。 |
| 生成与入服 | [`CompanionFactory.spawn()`](https://github.com/Dwinovo/minecraft-numen/blob/2f47e7b9c111a1a116360638f97bb853b7571b18/numen-api/common/src/main/java/com/dwinovo/numen/api/entity/CompanionFactory.java#L64) | 按加载器加入玩家并调用内部 join。 |
| 模拟玩家回包 | [`FakeClient.onOutbound()`](https://github.com/Dwinovo/minecraft-numen/blob/2f47e7b9c111a1a116360638f97bb853b7571b18/numen-api/common/src/main/java/com/dwinovo/numen/api/entity/FakeClient.java#L136) | 处理发给替身的服务端包。 |
| 服务端模型 | [`CompanionBrains.ensure()`](https://github.com/Dwinovo/minecraft-numen/blob/2f47e7b9c111a1a116360638f97bb853b7571b18/numen-api/common/src/main/java/com/dwinovo/numen/api/brain/CompanionBrains.java#L97) | 为同伴建立会话，用服务端玩家档案创建 LlmMind。 |
| 动态循环 | [`CompanionLoop.tick()`](https://github.com/Dwinovo/minecraft-numen/blob/2f47e7b9c111a1a116360638f97bb853b7571b18/numen-api/common/src/main/java/com/dwinovo/numen/api/brain/CompanionLoop.java#L447) | 反映游戏事件并推动同伴回合。 |
| 模型与工具调度 | [`AgentLoop.pump()`](https://github.com/Dwinovo/minecraft-numen/blob/2f47e7b9c111a1a116360638f97bb853b7571b18/agent/src/main/java/com/dwinovo/numen/api/agent/loop/AgentLoop.java#L238) | 推进模型输出、工具派发和状态机。 |
| 原版式感知 | [`ScanApi.block()`](https://github.com/Dwinovo/minecraft-numen/blob/2f47e7b9c111a1a116360638f97bb853b7571b18/numen/common/src/main/java/com/dwinovo/numen/tools/perception/ScanApi.java#L251) | 读取实际状态、朝向和方块属性。同类还提供 map、entities、sight。 |
| 容器能力 | [`ScanApi.container()`](https://github.com/Dwinovo/minecraft-numen/blob/2f47e7b9c111a1a116360638f97bb853b7571b18/numen/common/src/main/java/com/dwinovo/numen/tools/perception/ScanApi.java#L307) | 读取加载器可见的方块容器能力。 |
| 物品、流体与能量 | [`BlockStorageReading`](https://github.com/Dwinovo/minecraft-numen/blob/2f47e7b9c111a1a116360638f97bb853b7571b18/numen-api/common/src/main/java/com/dwinovo/numen/api/platform/services/BlockStorageReading.java#L12) | 归一化侧面物品格、流体和能量容器信息。 |
| 同伴自身 | [`StatusApi.self()`](https://github.com/Dwinovo/minecraft-numen/blob/2f47e7b9c111a1a116360638f97bb853b7571b18/numen/common/src/main/java/com/dwinovo/numen/tools/perception/StatusApi.java#L83) | 位置、身体和运行环境。 |
| 配方 | [`RecipeBook.recipesFor()`](https://github.com/Dwinovo/minecraft-numen/blob/2f47e7b9c111a1a116360638f97bb853b7571b18/numen/common/src/main/java/com/dwinovo/numen/tools/RecipeBook.java#L40) | 以真实配方注册表为输入；暴露的信息取决于配方适配。 |
| 原生建造 | [`BuildApi.place()`](https://github.com/Dwinovo/minecraft-numen/blob/2f47e7b9c111a1a116360638f97bb853b7571b18/numen/common/src/main/java/com/dwinovo/numen/tools/work/BuildApi.java#L75) | 接受命名空间 ID 和标准方块状态，返回任务。 |
| 建造反馈 | [`BuildCompanionTask.Placed`](https://github.com/Dwinovo/minecraft-numen/blob/2f47e7b9c111a1a116360638f97bb853b7571b18/numen/common/src/main/java/com/dwinovo/numen/task/build/BuildCompanionTask.java#L942) | 回执包含目标、清空、直接写入、未完成和路途中修改。 |
| 原生使用 | [`UseApi.block()`](https://github.com/Dwinovo/minecraft-numen/blob/2f47e7b9c111a1a116360638f97bb853b7571b18/numen/common/src/main/java/com/dwinovo/numen/tools/interact/UseApi.java#L101) | 原生点击，包括潜行和持续按住参数。 |
| 任务恢复 | [`RecoveryLadder`](https://github.com/Dwinovo/minecraft-numen/blob/2f47e7b9c111a1a116360638f97bb853b7571b18/numen/common/src/main/java/com/dwinovo/numen/task/base/RecoveryLadder.java#L73) | 通用任务恢复级次。 |
| 系统提示 | [`SystemPromptComposer.compose()`](https://github.com/Dwinovo/minecraft-numen/blob/2f47e7b9c111a1a116360638f97bb853b7571b18/numen-api/common/src/main/java/com/dwinovo/numen/api/agent/request/SystemPromptComposer.java#L23) | 将可用 API、Skill 和行为规则提供给模型。 |
| Skill 自动索引 | [`SkillRegistry.formatXml()`](https://github.com/Dwinovo/minecraft-numen/blob/2f47e7b9c111a1a116360638f97bb853b7571b18/numen-api/common/src/main/java/com/dwinovo/numen/api/agent/skill/SkillRegistry.java#L166) | 根据所有者可见资料生成索引，get 读取特定 Skill。 |
| Skill 工具 | [`SkillTool.invoke()`](https://github.com/Dwinovo/minecraft-numen/blob/2f47e7b9c111a1a116360638f97bb853b7571b18/numen-api/common/src/main/java/com/dwinovo/numen/api/agent/tool/SkillTool.java#L63) | 按需取得 Skill 正文和引用文件。 |
| 脚本执行 | [`ServerPrograms.launch()`](https://github.com/Dwinovo/minecraft-numen/blob/2f47e7b9c111a1a116360638f97bb853b7571b18/numen-api/common/src/main/java/com/dwinovo/numen/api/program/ServerPrograms.java#L179) | 挂接服务器端程序和回执。 |
| OP 脚本入口 | [`NumenCommands.drive()`](https://github.com/Dwinovo/minecraft-numen/blob/2f47e7b9c111a1a116360638f97bb853b7571b18/numen-api/common/src/main/java/com/dwinovo/numen/api/entity/NumenCommands.java#L260) | 复用 ServerPrograms，测试夹具实际使用的入口。 |
| Lua 脚本与模块 | [`LuaEngine`](https://github.com/Dwinovo/minecraft-numen/blob/2f47e7b9c111a1a116360638f97bb853b7571b18/script/src/main/java/com/dwinovo/numen/script/lua/LuaEngine.java#L43) | 该引擎负责把脚本映射到 API 目录和沙箱。 |
| Lua 沙箱 | [`LuaSandbox`](https://github.com/Dwinovo/minecraft-numen/blob/2f47e7b9c111a1a116360638f97bb853b7571b18/lua/src/main/java/com/dwinovo/lua/LuaSandbox.java#L84) | 约束指令、时间和主机桥接。 |
| 插件注册 | [`NumenPlugins.register()`](https://github.com/Dwinovo/minecraft-numen/blob/2f47e7b9c111a1a116360638f97bb853b7571b18/numen-api/common/src/main/java/com/dwinovo/numen/api/NumenPlugins.java#L69) | 注册命名空间和独立插件。 |
| API 声明 | [`NumenApi.api()`](https://github.com/Dwinovo/minecraft-numen/blob/2f47e7b9c111a1a116360638f97bb853b7571b18/numen-api/common/src/main/java/com/dwinovo/numen/api/NumenApi.java#L74) | 用注解和 record 参数导出组和函数。 |
| 工具元数据 | [`ApiDocs.help()`](https://github.com/Dwinovo/minecraft-numen/blob/2f47e7b9c111a1a116360638f97bb853b7571b18/numen-api/common/src/main/java/com/dwinovo/numen/api/sdk/ApiDocs.java#L117) | 生成帮助和按运行环境提供的函数索引。 |
| 行动权限 | [`ServerCall.authorize()`](https://github.com/Dwinovo/minecraft-numen/blob/2f47e7b9c111a1a116360638f97bb853b7571b18/numen-api/common/src/main/java/com/dwinovo/numen/api/sdk/ServerCall.java#L88) | 验证产生世界副作用的行为，再交给实际 Mod。 |
| DeepSeek 兼容 | [`Compats.resolve()`](https://github.com/Dwinovo/minecraft-numen/blob/2f47e7b9c111a1a116360638f97bb853b7571b18/ai/src/main/java/com/dwinovo/numen/api/agent/provider/Compats.java#L37) | 选择供应商协议兼容参数，识别 DeepSeek。 |
| 实际请求 | [`OpenAIProvider`](https://github.com/Dwinovo/minecraft-numen/blob/2f47e7b9c111a1a116360638f97bb853b7571b18/ai/src/main/java/com/dwinovo/numen/api/agent/provider/OpenAIProvider.java#L30) | Chat Completions 请求体、流输出与思考内容回传。 |
| 模型配置 | [`ProviderLibrary`](https://github.com/Dwinovo/minecraft-numen/blob/2f47e7b9c111a1a116360638f97bb853b7571b18/numen-api/common/src/main/java/com/dwinovo/numen/api/agent/llm/ProviderLibrary.java#L26) | 服务器可见的档案、端点和密钥来源。 |

## 适配实际调用的扩展点

`/numen drive LabBot …` 会调用发布包中的 `ServerPrograms`。`NumenPlugins.register(namespace, api -> api.api(group, summary, Functions.class))` 将 `@Fn` 注册成 `namespace.group.function`。协议端只需会执行 Numen 脚本工具。原生 Mod 支持可沿原版注册表、BlockState、菜单和 Fabric/NeoForge 暴露的能力读取，用 `Skill` 告诉模型如何组合。运行结构、原生菜单按钮和专用客户端游戏操作按照下表补齐。

| 力学或操作 | 真实调用 | 对应的原型 |
| --- | --- | --- |
| Create 动力传动 | `KineticBlockEntity.getSpeed()`、`getTheoreticalSpeed()`、`getGeneratedSpeed()`、`isOverStressed()`，读方块实体 NBT `Network.Stress/Capacity` 和 `WaterWheelBlockEntity.flowScore`。有效 RPM 与理论 RPM 分开。 | [CreateProbe.java](fabric-probe/src/lab/CreateProbe.java) |
| Create 粉碎与修复 | 读控制器状态的 `VALID/FACING`、`CrushingWheelControllerBlockEntity.crushingspeed/isOccupied()`。原生 Lua 用 `scan.block` 看轴向，`build.place` 清除并重装，`scan.container` 看产物，由独立 RCON 复核木桶。 | [原始夹具](mc20/lab-create-final.cjs) · [Lua 修复](lua/create-repair.lua) |
| Create 旋转 | 原生 `build.place/use.block` 搭建并尝试交互。实际判定来自方块实体 `Running/Angle/Speed` 与 `create:stationary_contraption`，因此判定动力构件存在和真实转动。 | [原始夹具](mc20/lab-bearing.cjs) |
| Eureka 装配 | 原生 `numen.use.block(pos, {sneak=true})` 后检查当前菜单类型 `ShipHelmScreenMenu`。通过 `ServerCall.authorize(Action.useBlock(...))` 后执行当前菜单 `clickMenuButton(player, 0)`。Fabric 运行时名是 `method_7604`。 | [Ships.java](fabric-probe/src/lab/Ships.java) |
| VS2 位姿 | `IShipObjectWorldServerProvider.getShipObjectWorld().getLoadedShips()` 获取 `LoadedServerShip`，核对 `getId()`、`getTransform().getPositionInWorld()` 与 `getVelocity()`。 | [Ships.java](fabric-probe/src/lab/Ships.java) · [原始夹具](mc20/lab-vs.cjs) |
| Simulated 装配 | 原生 `use.block` 按住对照得到 0 个子世界。桥接检查测试坐标范围、区块、距离和权限后调用 `PhysicsAssemblerBlockEntity.assembleOrDisassemble()`，读取 `getLastAssemblyException()`。 | [AeroProbe.java](neo-probe/src/lab/AeroProbe.java) |
| Sable 运行状态 | `SubLevelContainer.getContainer(level).getAllSubLevels()`，读 `getUniqueId()`、`logicalPose().position()`、`RigidBodyHandle.of(craft).getLinearVelocity()` 与质量。NeoForge 夹具以 `Companions.summon` 启动无客户端测试角色。 | [AeroProbe.java](neo-probe/src/lab/AeroProbe.java) · [原始夹具](mc21/lab.cjs) |

Fabric Java 文件使用发布包的 intermediary 名字和原作者注释。NeoForge 使用 Mojang 名字。试验暴露的装配函数以固定试验台为目标，也纳入了 Numen 的授权处理。正式使用需要与真实选中对象、范围和错误返回绑定。

[Numen 1.20.1 源码树](https://github.com/Dwinovo/minecraft-numen/tree/2f47e7b9c111a1a116360638f97bb853b7571b18) · [Numen 1.21.1 源码树](https://github.com/Dwinovo/minecraft-numen/tree/06422d1847e0782f91a1bacc5997e74a0249b6c0) · [Numen 插件编译接口](https://github.com/Dwinovo/minecraft-numen/blob/06422d1847e0782f91a1bacc5997e74a0249b6c0/numen-api/README_EN.md) · [Create 动力源码](https://github.com/Fabricators-of-Create/Create/tree/mc1.20.1/fabric/dev/src/main/java/com/simibubi/create/content/kinetics) · [Eureka 船舵菜单](https://github.com/ValkyrienSkies/Eureka/blob/1.20.1/main/common/src/main/kotlin/org/valkyrienskies/eureka/gui/shiphelm/ShipHelmScreenMenu.kt) · [VS2 源码](https://github.com/ValkyrienSkies/Valkyrien-Skies-2) · [Simulated 装配器](https://github.com/Creators-of-Aeronautics/Simulated-Project/tree/main/simulated/common/src/main/java/dev/simulated_team/simulated/content/blocks/physics_assembler) · [实测 Sable 2.0.6 接口](https://github.com/ryanhcode/sable/tree/ad8b4d377741fbd923f4d863b2cf9e462daff9a6/common/src/main/java/dev/ryanhcode/sable/api)
